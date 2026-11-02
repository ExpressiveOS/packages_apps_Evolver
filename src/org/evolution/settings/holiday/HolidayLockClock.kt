/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.holiday

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import org.json.JSONException
import org.json.JSONObject

/**
 * The lock screen clock in a holiday's color: its first petal, in the tone that reads on the lock
 * wallpaper. The clock's face, font and size stay the user's. The system clocks take the color as
 * the seed in the clock face setting, Evolver's custom clock styles as their custom color; both
 * pick it up live. The user's own values are kept from before the first holiday and put back after
 * the last, unless the user changed the color in between: then their change stays. An Apply in
 * Wallpaper & style writes their own clock color back at once, and that counts as their change.
 */
internal object HolidayLockClock {
    private const val TAG = "HolidayThemes"

    // In HolidayThemes' preferences: which clocks we colored, and their settings as they were
    // before (one that wasn't set is left out) and as we wrote them, last and the time before: a
    // change cut short leaves the one before showing, and that's ours too
    private const val KEY_TARGET = "lock_clock_target"
    private const val KEY_ORIGINAL = "lock_clock_original"
    private const val KEY_WRITTEN = "lock_clock_written"
    private const val KEY_PREVIOUS = "lock_clock_previous"

    private const val CLOCK_FACE = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_FACE
    private const val COLOR_MODE = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_COLOR_MODE
    private const val CUSTOM_COLOR = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_CUSTOM_COLOR
    private const val MODE_CUSTOM = "custom"

    // The clock face JSON's seed (ClockSettings): without one the clock follows Monet
    private const val SEED_COLOR = "seedColor"

    private const val KEY_LOCK_GLYMPS = "lock_glymps_enabled"

    /** The custom styles that ignore color (NO_COLOR_CLOCKS): OOS, OOS2, Analog and Block. */
    private val NO_COLOR_STYLES = setOf(1, 2, 25, 26)

    /** Where the clock's color lives: the settings that hold it. */
    private enum class Target(val keys: List<String>) {
        /** Clock style 0, the system clocks: the seed in the clock face JSON. */
        FACE(listOf(CLOCK_FACE)),

        /** One of Evolver's custom clock styles: its color mode and custom color. */
        STYLE(listOf(COLOR_MODE, CUSTOM_COLOR)),
    }

    /**
     * Colors the lock screen clock in [holiday]'s first petal. False when the settings couldn't
     * be written.
     */
    fun apply(context: Context, holiday: Holiday, prefs: SharedPreferences): Boolean {
        val resolver = context.contentResolver
        val target = currentTarget(resolver)
        val showsColor = target == Target.FACE || styleShowsColor(resolver)
        // An earlier holiday's color on the clocks the user has moved away from since, or on a
        // style that can't show this one's, goes back first
        val applied = appliedTarget(prefs)
        if (applied != null && (applied != target || !showsColor) && !restore(context, prefs)) {
            return false
        }
        // A style that ignores color, or the user's gradient over it: theirs as it is
        if (!showsColor) return true

        val current = read(resolver, target)
        val savedTarget = prefs.getString(KEY_TARGET, null)
        val savedOriginal = prefs.getString(KEY_ORIGINAL, null)
        val savedWritten = prefs.getString(KEY_WRITTEN, null)
        val savedPrevious = prefs.getString(KEY_PREVIOUS, null)
        // Going from one holiday straight to the next keeps the clock from before the first, as
        // far as the user hasn't changed it since: then theirs is the one to come back
        val original = own(
            target, current, parse(savedOriginal), parse(savedWritten), parse(savedPrevious)
        ) ?: current
        val written = values(target, current, colorFor(context, holiday))
        // Ours as it shows now, the last we wrote or, where that was cut short, the one before:
        // until the new one is in, it's still ours
        val showing = listOf(savedWritten, savedPrevious)
            .firstOrNull { saved -> holds(target, current, parse(saved)) }
        // Saved before the settings are written, so a crash in between still knows what to put back
        save(prefs, target.name, original.toString(), written.toString(), showing)

        val ok = try {
            write(resolver, target, written)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't write ${holiday.id}'s clock color", e)
            false
        }
        if (!ok) {
            // What did get written comes off too: with nothing saved, nothing would take it off
            try {
                write(resolver, target, current)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't take ${holiday.id}'s clock color off again", e)
            }
            save(prefs, savedTarget, savedOriginal, savedWritten, savedPrevious)
        }
        return ok
    }

    /**
     * Puts the user's own clock color back, unless they changed it while the holiday's was on.
     * False when a setting couldn't be written: what's saved stays for the next try.
     */
    fun restore(context: Context, prefs: SharedPreferences): Boolean {
        val target = appliedTarget(prefs)
        val original = parse(prefs.getString(KEY_ORIGINAL, null))
        val written = parse(prefs.getString(KEY_WRITTEN, null))
        val previous = parse(prefs.getString(KEY_PREVIOUS, null))
        if (target != null && original != null && written != null) {
            val resolver = context.contentResolver
            val own = own(target, read(resolver, target), original, written, previous)
            if (own == null) {
                Log.i(TAG, "Clock color changed during the holiday, keeping it")
            } else if (!write(resolver, target, own)) {
                // Kept, so the next try still puts it back
                Log.w(TAG, "Couldn't put the user's clock color back")
                return false
            }
        }
        save(prefs, null, null, null, null)
        return true
    }

    /**
     * Whether the clock still shows a color we wrote, but not [holiday]'s in the tone for the lock
     * wallpaper up now: the user's own came back or was set during the holiday, Lock Glymps went
     * on, or a change to it was cut short. False when it can't be told: the engine asks while it
     * decides when to run next.
     */
    fun needsRefresh(context: Context, holiday: Holiday, prefs: SharedPreferences): Boolean = try {
        val target = appliedTarget(prefs)
        val written = parse(prefs.getString(KEY_WRITTEN, null))
        val previous = parse(prefs.getString(KEY_PREVIOUS, null))
        val resolver = context.contentResolver
        target != null && written != null && target == currentTarget(resolver) &&
            read(resolver, target).let { current ->
                untouched(target, current, written, previous) &&
                    colorOf(target, current) != colorFor(context, holiday)
            }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't check the clock color", e)
        false
    }

    /**
     * [holiday]'s first petal in the tone that reads on the lock wallpaper: the bright mid tone on
     * a dark one (or one without colors, as a live one can be), the deep tone on a light one. Lock
     * Glymps changes the lock wallpaper at every screen on and off, so with it on, the deep tone,
     * which keeps 3:1 or more on black and on white.
     */
    private fun colorFor(context: Context, holiday: Holiday): Int {
        val petal = holiday.colors.petals[0]
        if (Settings.System.getInt(context.contentResolver, KEY_LOCK_GLYMPS, 0) != 0) {
            return petal.deep
        }
        val hints = WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_LOCK)?.colorHints ?: return petal.mid
        val dark = (hints and WallpaperColors.HINT_SUPPORTS_DARK_THEME) != 0
        return if (dark) petal.mid else petal.deep
    }

    /** The clocks showing: the system ones at clock style 0, otherwise a custom style. */
    private fun currentTarget(resolver: ContentResolver): Target {
        val style = Settings.Secure.getInt(
            resolver, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE, 0
        )
        return if (style == 0) Target.FACE else Target.STYLE
    }

    /** The clocks we colored, if we did. */
    private fun appliedTarget(prefs: SharedPreferences): Target? =
        prefs.getString(KEY_TARGET, null)
            .let { name -> Target.entries.firstOrNull { it.name == name } }

    /** Whether the custom style showing takes a color: one that doesn't ignore it, no gradient. */
    private fun styleShowsColor(resolver: ContentResolver): Boolean {
        val style = Settings.Secure.getInt(
            resolver, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE, 0
        )
        val gradient = Settings.Secure.getInt(
            resolver, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_ENABLED, 0
        )
        return style !in NO_COLOR_STYLES && gradient == 0
    }

    /**
     * [current] with the user's own values back wherever ours still are: what [restore] puts back,
     * and what the next holiday keeps as the user's. Null when nothing is saved or none of ours is
     * left: the user's change stays as it is.
     */
    private fun own(
        target: Target,
        current: JSONObject,
        original: JSONObject?,
        written: JSONObject?,
        previous: JSONObject?,
    ): JSONObject? {
        if (original == null || written == null) return null
        // Ours as we wrote it last, or the time before where the last change was cut short
        val ours = listOfNotNull(written, previous)
        return when (target) {
            Target.FACE -> {
                val face = current.value(CLOCK_FACE)
                when {
                    !untouched(target, current, written, previous) -> null
                    // Nothing else changed: the user's setting goes back as it was, to the byte
                    ours.any { face == it.value(CLOCK_FACE) } -> original
                    // Another clock or its axes picked with our seed kept: those stay, and the
                    // seed goes back to the user's (or none)
                    else -> JSONObject()
                        .put(CLOCK_FACE, withSeed(face, seed(original.value(CLOCK_FACE))))
                }
            }
            Target.STYLE -> {
                val color = current.value(CUSTOM_COLOR)
                val shown = ours.firstOrNull { it.value(CUSTOM_COLOR) == color } ?: return null
                // A mode the user picked since stays, with their own color back under it for
                // when they pick Custom again
                val mode = if (current.value(COLOR_MODE) == shown.value(COLOR_MODE)) {
                    original
                } else {
                    current
                }
                JSONObject()
                    .put(COLOR_MODE, mode.value(COLOR_MODE))
                    .put(CUSTOM_COLOR, original.value(CUSTOM_COLOR))
            }
        }
    }

    /**
     * Whether [target]'s settings still hold our color as we wrote it last, or the time before:
     * a change cut short leaves that one.
     */
    private fun untouched(
        target: Target,
        current: JSONObject,
        written: JSONObject,
        previous: JSONObject?,
    ): Boolean = holds(target, current, written) || holds(target, current, previous)

    /** Whether [target]'s settings hold the color as [values] put it on; false for none. */
    private fun holds(target: Target, current: JSONObject, values: JSONObject?): Boolean =
        values != null && when (target) {
            Target.FACE -> seed(current.value(CLOCK_FACE)) == seed(values.value(CLOCK_FACE))
            Target.STYLE -> target.keys.all { key -> current.value(key) == values.value(key) }
        }

    /** [target]'s settings with [color] in: the face's seed, or custom mode in [color]. */
    private fun values(target: Target, current: JSONObject, color: Int): JSONObject =
        when (target) {
            Target.FACE -> JSONObject().put(CLOCK_FACE, withSeed(current.value(CLOCK_FACE), color))
            // As Evolver's clock color picker stores it, the ARGB int
            Target.STYLE -> JSONObject()
                .put(COLOR_MODE, MODE_CUSTOM)
                .put(CUSTOM_COLOR, color.toString())
        }

    /** The color [values] put on the clock. */
    private fun colorOf(target: Target, values: JSONObject): Int? = when (target) {
        Target.FACE -> seed(values.value(CLOCK_FACE))
        Target.STYLE -> values.value(CUSTOM_COLOR)?.toIntOrNull()
    }

    /** [target]'s settings as they are; one that isn't set is left out. */
    private fun read(resolver: ContentResolver, target: Target): JSONObject {
        val values = JSONObject()
        for (key in target.keys) values.put(key, Settings.Secure.getString(resolver, key))
        return values
    }

    /**
     * Writes [values] to those of [target]'s settings that hold something else, clearing one it
     * leaves out. Custom mode goes on after its color and off before it changes, so no other color
     * shows in between. False when a setting couldn't be written.
     */
    private fun write(resolver: ContentResolver, target: Target, values: JSONObject): Boolean {
        val custom = values.value(COLOR_MODE) == MODE_CUSTOM
        val keys = if (custom) target.keys.reversed() else target.keys
        return keys.all { key ->
            val value = values.value(key)
            value == Settings.Secure.getString(resolver, key) ||
                Settings.Secure.putString(resolver, key, value)
        }
    }

    /** The seed in clock face JSON [face], or null for none. */
    private fun seed(face: String?): Int? {
        val json = faceJson(face)
        return if (json.isNull(SEED_COLOR)) null else json.optInt(SEED_COLOR)
    }

    /** Clock face JSON [face] with [seed] in it, or none for null. Its clock and axes stay. */
    private fun withSeed(face: String?, seed: Int?): String {
        val json = faceJson(face)
        if (seed != null) json.put(SEED_COLOR, seed) else json.remove(SEED_COLOR)
        return json.toString()
    }

    /**
     * Clock face JSON [face] parsed; empty for none or one that isn't JSON, as the "0" SystemUI
     * writes while a custom style is on.
     */
    private fun faceJson(face: String?): JSONObject {
        if (face.isNullOrEmpty()) return JSONObject()
        return try {
            JSONObject(face)
        } catch (e: JSONException) {
            JSONObject()
        }
    }

    /** A setting's value in [this]; null when it wasn't set. */
    private fun JSONObject.value(key: String): String? = opt(key) as? String

    private fun parse(json: String?): JSONObject? {
        if (json.isNullOrEmpty()) return null
        return try {
            JSONObject(json)
        } catch (e: JSONException) {
            null
        }
    }

    private fun save(
        prefs: SharedPreferences,
        target: String?,
        original: String?,
        written: String?,
        previous: String?,
    ) {
        prefs.edit()
            .putString(KEY_TARGET, target)
            .putString(KEY_ORIGINAL, original)
            .putString(KEY_WRITTEN, written)
            .putString(KEY_PREVIOUS, previous)
            .commit()
    }
}
