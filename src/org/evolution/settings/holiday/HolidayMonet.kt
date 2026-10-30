/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.holiday

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

    private const val SYSTEM_PALETTE = "android.theme.customization.system_palette"
    private const val ACCENT_COLOR = "android.theme.customization.accent_color"
    private const val COLOR_SOURCE = "android.theme.customization.color_source"
    private const val THEME_STYLE = "android.theme.customization.theme_style"
    private const val BASE_SEED = "_base_seed_color"
    private const val SECONDARY_SEED = "_secondary_seed_color"
    private const val TIMESTAMP = "_applied_timestamp"

    private val KEYS = listOf(
        SYSTEM_PALETTE, ACCENT_COLOR, BASE_SEED, COLOR_SOURCE, THEME_STYLE, SECONDARY_SEED,
    )

    /** Seeds the system palette from [holiday]. False when the setting couldn't be written. */
    fun apply(context: Context, holiday: Holiday, prefs: SharedPreferences): Boolean {
        val resolver = context.contentResolver
        val current = read(resolver)
        val savedOriginal = prefs.getString(KEY_ORIGINAL, null)
        val savedWritten = prefs.getString(KEY_WRITTEN, null)
        // Going from one holiday straight to the next keeps the colors from before the first,
        // unless the user changed them since: then theirs are the ones to come back.
        val original = parse(savedOriginal)?.takeIf { untouched(current, parse(savedWritten)) }
            ?: snapshot(current)
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
        if (!ok) save(prefs, savedOriginal, savedWritten)
        return ok
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
        prefs.edit().putString(KEY_ORIGINAL, original).putString(KEY_WRITTEN, written).commit()
    }
}
