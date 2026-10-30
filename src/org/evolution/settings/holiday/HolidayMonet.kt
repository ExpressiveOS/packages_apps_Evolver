/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.holiday

import android.app.WallpaperManager
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import org.json.JSONException
import org.json.JSONObject

/**
 * A holiday's Material You colors, written into the theme customization JSON the way Expressive
 * Studio's Colors page writes a preset seed. Only the seed, source, style and second seed are
 * touched; contrast and every other key stay as the user set them. The user's own values are kept
 * from before the first holiday and put back after the last, unless the user changed the colors
 * in between: then their change stays.
 */
internal object HolidayMonet {
    private const val TAG = "HolidayThemes"

    // In HolidayThemes' preferences: the touched keys as they were before (a key that wasn't
    // there is left out), and as we wrote them.
    private const val KEY_ORIGINAL = "monet_original"
    private const val KEY_WRITTEN = "monet_written"

    // The home wallpaper id the saved colors go with, as the engine last left it, and whether the
    // user set a home wallpaper of their own since the colors were saved
    private const val KEY_HOME_ID = "monet_home_id"
    private const val KEY_HOME_CHANGED = "monet_home_changed"

    private const val SYSTEM_PALETTE = "android.theme.customization.system_palette"
    private const val ACCENT_COLOR = "android.theme.customization.accent_color"
    private const val COLOR_SOURCE = "android.theme.customization.color_source"
    private const val THEME_STYLE = "android.theme.customization.theme_style"
    private const val BASE_SEED = "_base_seed_color"
    private const val SECONDARY_SEED = "_secondary_seed_color"
    private const val TIMESTAMP = "_applied_timestamp"

    // A wallpaper color option's keys, which SystemUI drops when the wallpaper changes
    private const val DYNAMIC_COLOR = "android.theme.customization.dynamic_color"
    private const val COLOR_INDEX = "android.theme.customization.color_index"
    private const val SOURCE_PRESET = "preset"
    private const val SOURCE_LOCK = "lock_wallpaper"

    // SystemUI's seed for a wallpaper without a colorful color (ColorScheme.GOOGLE_BLUE)
    private const val GOOGLE_BLUE = 0xFF1B6EF3.toInt()
    private const val OPAQUE = 0xFF000000.toInt()

    private val KEYS = listOf(
        SYSTEM_PALETTE, ACCENT_COLOR, BASE_SEED, COLOR_SOURCE, THEME_STYLE, SECONDARY_SEED,
    )

    private val WALLPAPER_OPTION_KEYS = listOf(
        SYSTEM_PALETTE, ACCENT_COLOR, DYNAMIC_COLOR, COLOR_INDEX,
    )

    /** Seeds the system palette from [holiday]. False when the setting couldn't be written. */
    fun apply(context: Context, holiday: Holiday, prefs: SharedPreferences): Boolean {
        val resolver = context.contentResolver
        val current = read(resolver)
        val savedOriginal = prefs.getString(KEY_ORIGINAL, null)
        val savedWritten = prefs.getString(KEY_WRITTEN, null)
        // Going from one holiday straight to the next keeps the colors from before the first,
        // unless the user changed them since: then theirs are the ones to come back.
        val carried = parse(savedOriginal)?.takeIf { untouched(current, parse(savedWritten)) }
        val original = carried ?: snapshot(current)
        val written = values(holiday)
        // Saved before the setting is written, so a crash in between still knows what to put back
        save(prefs, original.toString(), written.toString())

        for (key in KEYS) current.put(key, written.get(key))
        current.put(TIMESTAMP, System.currentTimeMillis())
        val ok = try {
            Settings.Secure.putString(
                resolver, Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, current.toString()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't write ${holiday.id}'s colors", e)
            false
        }
        if (!ok) {
            save(prefs, savedOriginal, savedWritten)
        } else if (carried == null) {
            // Newly saved colors go with the home wallpaper showing now
            prefs.edit().remove(KEY_HOME_CHANGED).commit()
            trackHomeWallpaper(context, prefs)
        }
        return ok
    }

    /**
     * Runs [change], the engine setting or putting back wallpapers, so the holiday wallpaper going
     * on isn't taken for the user's own change: one the user made before it is noted first. The
     * user's wallpaper put back while the holiday's colors are still on counts as a change: under
     * the holiday's preset SystemUI didn't judge the saved colors against it.
     */
    fun <T> changingWallpaper(context: Context, prefs: SharedPreferences, change: () -> T): T {
        checkHomeWallpaper(context, prefs)
        try {
            return change()
        } finally {
            if (!HolidayWallpaper.showsHolidayHome(context)) checkHomeWallpaper(context, prefs)
            trackHomeWallpaper(context, prefs)
        }
    }

    /**
     * Puts the user's own colors back, unless they changed them while the holiday's were on.
     * False when the setting couldn't be written: the saved colors stay for the next try.
     */
    fun restore(context: Context, prefs: SharedPreferences): Boolean {
        val original = parse(prefs.getString(KEY_ORIGINAL, null))
        val written = parse(prefs.getString(KEY_WRITTEN, null))
        if (original != null && written != null) {
            val resolver = context.contentResolver
            val current = read(resolver)
            if (untouched(current, written)) {
                for (key in KEYS) {
                    if (original.has(key)) current.put(key, original.get(key)) else current.remove(key)
                }
                if (homeWallpaperReplaced(context, prefs, original)) {
                    // A wallpaper color option of a home wallpaper the user has since replaced,
                    // which SystemUI left alone under the holiday's preset: drop it as it would
                    // have, so the colors come from the wallpaper showing now
                    Log.i(TAG, "Home wallpaper changed during the holiday, colors follow it")
                    for (key in WALLPAPER_OPTION_KEYS) current.remove(key)
                }
                current.put(TIMESTAMP, System.currentTimeMillis())
                val ok = Settings.Secure.putString(
                    resolver, Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, current.toString()
                )
                if (!ok) {
                    // Kept, so the next try still puts them back
                    Log.w(TAG, "Couldn't put the user's colors back")
                    return false
                }
            } else {
                Log.i(TAG, "Colors changed during the holiday, keeping them")
            }
        }
        save(prefs, null, null)
        return true
    }

    /**
     * Whether the saved [original] colors are a wallpaper color option that SystemUI would have
     * dropped, had they been on when the user changed the home wallpaper: unless they came from
     * the lock screen and the lock screen kept its own wallpaper, or the palette is one of the
     * new wallpaper's colors. The holiday wallpaper going on doesn't count, and once the colors
     * are back, SystemUI judges the wallpaper the holiday puts back after them itself.
     */
    private fun homeWallpaperReplaced(
        context: Context,
        prefs: SharedPreferences,
        original: JSONObject,
    ): Boolean {
        val source = original.optString(COLOR_SOURCE)
        if (source == SOURCE_PRESET) return false
        // Without a palette the colors already come from the wallpaper
        val palette = parseColor(original.optString(SYSTEM_PALETTE)) ?: return false
        checkHomeWallpaper(context, prefs)
        if (!prefs.getBoolean(KEY_HOME_CHANGED, false)) return false
        // Still the holiday's wallpaper: the user's goes back after the colors, and SystemUI
        // judges them against it then
        if (HolidayWallpaper.showsHolidayHome(context)) return false
        return try {
            val wm = WallpaperManager.getInstance(context)
            // SystemUI keeps lock screen colors when only the home wallpaper changes
            if (source == SOURCE_LOCK && wm.getWallpaperId(WallpaperManager.FLAG_LOCK) >= 0) {
                return false
            }
            val home = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                // None yet from the one the engine just put back: SystemUI judges the colors
                // when they come. One the user set without colors has none to keep them by.
                ?: return home != prefs.getInt(KEY_HOME_ID, home)
            val seeds = HashSet<Int>()
            // Every color SystemUI could take as a seed (ColorScheme.getSeedColors)
            for (color in colors.allColors.keys) seeds += color or OPAQUE
            for (color in colors.mainColors) seeds += color.toArgb() or OPAQUE
            seeds += GOOGLE_BLUE
            palette !in seeds
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the home wallpaper's colors", e)
            true
        }
    }

    /** Notes a home wallpaper change since the engine last left it: the user's doing. */
    private fun checkHomeWallpaper(context: Context, prefs: SharedPreferences) {
        if (prefs.getString(KEY_ORIGINAL, null) == null || !prefs.contains(KEY_HOME_ID)) return
        try {
            val home = WallpaperManager.getInstance(context)
                .getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            if (home != prefs.getInt(KEY_HOME_ID, home)) {
                prefs.edit().putBoolean(KEY_HOME_CHANGED, true).commit()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't check the home wallpaper", e)
        }
    }

    /** Remembers the home wallpaper showing now as the one the engine left, with colors saved. */
    private fun trackHomeWallpaper(context: Context, prefs: SharedPreferences) {
        if (prefs.getString(KEY_ORIGINAL, null) == null) return
        try {
            val home = WallpaperManager.getInstance(context)
                .getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            prefs.edit().putInt(KEY_HOME_ID, home).commit()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't note the home wallpaper", e)
        }
    }

    /** An RRGGBB (or #RRGGBB) color as the JSON holds it, opaque; null if it isn't one. */
    private fun parseColor(value: String): Int? {
        val hex = value.removePrefix("#")
        if (hex.length != 6 || !hex.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }) {
            return null
        }
        return hex.toInt(16) or OPAQUE
    }

    /** The touched keys as [holiday] sets them: its seed as a preset, in its style. */
    private fun values(holiday: Holiday): JSONObject {
        val colors = holiday.colors
        val seed = hex(colors.seed)
        return JSONObject()
            .put(SYSTEM_PALETTE, seed)
            .put(ACCENT_COLOR, seed)
            .put(BASE_SEED, seed)
            .put(COLOR_SOURCE, "preset")
            .put(THEME_STYLE, colors.style)
            .put(SECONDARY_SEED, colors.secondarySeed?.let { hex(it) } ?: "")
    }

    private fun hex(color: Int) = String.format("%06X", 0xFFFFFF and color)

    /** The touched keys in [json]; one that isn't there stays out. */
    private fun snapshot(json: JSONObject): JSONObject {
        val saved = JSONObject()
        for (key in KEYS) json.opt(key)?.let { saved.put(key, it) }
        return saved
    }

    /** Whether every touched key in [current] still holds what we wrote. */
    private fun untouched(current: JSONObject, written: JSONObject?): Boolean =
        written != null &&
            KEYS.all { key -> current.opt(key)?.toString() == written.opt(key)?.toString() }

    private fun read(resolver: ContentResolver): JSONObject {
        val json = Settings.Secure.getString(
            resolver, Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES
        )
        if (json.isNullOrEmpty()) return JSONObject()
        return try {
            JSONObject(json)
        } catch (e: JSONException) {
            Log.w(TAG, "Ignoring unreadable theme customization", e)
            JSONObject()
        }
    }

    private fun parse(json: String?): JSONObject? {
        if (json.isNullOrEmpty()) return null
        return try {
            JSONObject(json)
        } catch (e: JSONException) {
            null
        }
    }

    private fun save(prefs: SharedPreferences, original: String?, written: String?) {
        val edit = prefs.edit().putString(KEY_ORIGINAL, original).putString(KEY_WRITTEN, written)
        // No saved colors, no wallpaper for them to go with
        if (original == null) edit.remove(KEY_HOME_ID).remove(KEY_HOME_CHANGED)
        edit.commit()
    }
}
