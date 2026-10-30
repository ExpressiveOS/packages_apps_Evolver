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
 * A holiday's picture at the top of Quick Settings, through SystemUI's header image: its static
 * provider shows any app's drawable by name, here one of Settings' own, which has a light and a
 * dark theme version. SystemUI picks it up live. The user's own picture (an OmniStyle pick, a
 * header pack, or their own file, which stays where it is) is kept from before the first holiday
 * and put back after the last, unless they picked another in between: then theirs stays. With the
 * header image off, a holiday turns it on, and off again after; once the user turns it off during
 * a holiday, holidays leave it off.
 *
 * Each picture is a one-frame animation: the static provider hands SystemUI a new drawable at
 * every screen on, and SystemUI would crossfade a still picture in over the last one, nesting one
 * layer deeper each time, while it shows an animation as it is. Only then does it ask again: the
 * dark theme going on or off with the screen on (its Quick Settings tile, Battery Saver) shows the
 * other theme's version from the next screen on.
 */
internal object HolidayQsHeader {
    private const val TAG = "HolidayThemes"

    // In HolidayThemes' preferences: the settings as they were before (one that wasn't set is
    // left out) and as we left them, the switch only while it's on (noted once it is, and no more
    // before we turn it off), so a switch off then is the user's doing; and whether the user
    // turned it off during a holiday, so holidays leave it off
    private const val KEY_ORIGINAL = "qs_header_original"
    private const val KEY_WRITTEN = "qs_header_written"
    private const val KEY_USER_OFF = "qs_header_user_off"

    private const val SWITCH = Settings.System.STATUS_BAR_CUSTOM_HEADER
    private const val PROVIDER = Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER
    private const val IMAGE = Settings.System.STATUS_BAR_CUSTOM_HEADER_IMAGE
    private const val ON = "1"
    private const val STATIC = "static"

    // SystemUI's provider while none is set. It ignores one going back to none, so the user's
    // none is kept and put back as this.
    private const val DAYLIGHT = "daylight"

    // What the holidays' pictures' names start with (Holiday.qsHeader): any of them is ours
    private const val PICTURE = "holiday_qs_header_"

    /** In the order a holiday writes them: the picture before the provider that shows it. */
    private val KEYS = listOf(IMAGE, PROVIDER, SWITCH)

    /**
     * Shows [holiday]'s picture, turning the header image on unless the user turned it off during
     * a holiday. False when the settings couldn't be written.
     */
    fun apply(context: Context, holiday: Holiday, prefs: SharedPreferences): Boolean {
        val resolver = context.contentResolver
        val current = read(resolver)
        val savedOriginal = prefs.getString(KEY_ORIGINAL, null)
        val savedWritten = prefs.getString(KEY_WRITTEN, null)
        // Left off as the user turned it, with their own picture back under it (an earlier
        // holiday's comes off) for when they turn it on
        if (heldOff(prefs, current, parse(savedWritten))) return restore(context, prefs)

        // Going from one holiday straight to the next keeps the settings from before the first,
        // as far as the user hasn't changed them since: then theirs are the ones to come back
        val original = own(context, current, parse(savedOriginal), parse(savedWritten))
        val written = values(context, holiday)
        val on = current.value(SWITCH) == ON
        // Saved before the settings are written, so a crash in between still knows what to put
        // back; the switch is noted once it's on
        val picture = if (on) written else JSONObject(written, arrayOf(IMAGE, PROVIDER))
        save(prefs, original.toString(), picture.toString())

        val ok = try {
            write(resolver, written)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't write ${holiday.id}'s Quick Settings picture", e)
            false
        }
        if (!ok) {
            // What did get written comes off too: with nothing saved, nothing would take it off.
            // Until it has, what's saved stays, so the next try still knows the user's own.
            val undone = try {
                write(resolver, current)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't take ${holiday.id}'s Quick Settings picture off again", e)
                false
            }
            if (undone) save(prefs, savedOriginal, savedWritten)
        } else if (!on) {
            save(prefs, original.toString(), written.toString())
        }
        return ok
    }

    /**
     * Puts the user's own picture back, and the header image off if a holiday turned it on,
     * unless they changed it while the holiday's was on: then their change stays. False when a
     * setting couldn't be written: what's saved stays for the next try.
     */
    fun restore(context: Context, prefs: SharedPreferences): Boolean {
        val savedOriginal = prefs.getString(KEY_ORIGINAL, null)
        val savedWritten = prefs.getString(KEY_WRITTEN, null)
        val original = parse(savedOriginal)
        val written = parse(savedWritten)
        if (original != null && written != null) {
            val resolver = context.contentResolver
            val current = read(resolver)
            if (turnedOff(current, written)) {
                Log.i(TAG, "Header image turned off during the holiday, leaving it off")
                prefs.edit().putBoolean(KEY_USER_OFF, true).commit()
            }
            if (!isHolidayPicture(context, current.value(IMAGE))) {
                Log.i(TAG, "Quick Settings picture changed during the holiday, keeping it")
            }
            val own = own(context, current, original, written)
            // Noted as off before it goes off, as it's noted as on only once it is: cut short in
            // between, the next try doesn't take the switch off for the user's doing
            val switchOff = current.value(SWITCH) == ON && own.value(SWITCH) != ON
            if (switchOff) {
                save(prefs, savedOriginal, JSONObject(written, arrayOf(IMAGE, PROVIDER)).toString())
            }
            val ok = try {
                write(resolver, own)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't put the user's Quick Settings picture back", e)
                false
            }
            if (!ok) {
                // What did go back comes off again: half put back (the switch off, say), the next
                // try would take it for the user's change. What's saved stays for that try, the
                // switch noted as on again once it's back on.
                val undone = try {
                    write(resolver, current)
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't put the holiday's Quick Settings picture back on", e)
                    false
                }
                if (undone && switchOff) save(prefs, savedOriginal, savedWritten)
                return false
            }
        }
        save(prefs, null, null)
        return true
    }

    /**
     * Whether the header image is off where a holiday's picture should show: the user asked for
     * it again on the Holiday themes page, after turning it off during a holiday. False when it
     * can't be told: the engine asks while it decides when to run next.
     */
    fun needsRefresh(context: Context, prefs: SharedPreferences): Boolean = try {
        val current = read(context.contentResolver)
        current.value(SWITCH) != ON &&
            !heldOff(prefs, current, parse(prefs.getString(KEY_WRITTEN, null)))
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't check the header image", e)
        false
    }

    /** Whether holidays leave the header image off: the user turned it off during one. */
    fun isHeldOff(context: Context, prefs: SharedPreferences): Boolean =
        heldOff(prefs, read(context.contentResolver), parse(prefs.getString(KEY_WRITTEN, null)))

    /** The user asks for the picture again: holidays turn the header image on again. */
    fun askAgain(editor: SharedPreferences.Editor) {
        editor.remove(KEY_USER_OFF)
    }

    /**
     * Whether the header image is off because the user turned it off during a holiday: this one,
     * where it was left on, or an earlier one.
     */
    private fun heldOff(prefs: SharedPreferences, current: JSONObject, written: JSONObject?) =
        current.value(SWITCH) != ON &&
            (prefs.getBoolean(KEY_USER_OFF, false) || turnedOff(current, written))

    /** Whether the switch is off where a holiday left it on. */
    private fun turnedOff(current: JSONObject, written: JSONObject?): Boolean =
        written?.value(SWITCH) == ON && current.value(SWITCH) != ON

    /**
     * The user's own settings under [current]: the [original] value of each key that, with the
     * keys written before it, still holds ours, and [current]'s for the rest. A picture the user
     * picks sets the provider too, and keeps the header image on: then those are theirs as well.
     * A key a change cut short didn't reach yet holds the user's own, or going from one holiday
     * to the next, the last one's picture, which is ours as well.
     */
    private fun own(
        context: Context,
        current: JSONObject,
        original: JSONObject?,
        written: JSONObject?,
    ): JSONObject {
        if (original == null || written == null) return current
        val own = JSONObject()
        var ours = true
        for (key in KEYS) {
            val value = current.value(key)
            ours = ours && when (key) {
                // Any holiday's picture: a switch to the next one cut short leaves the last one's
                IMAGE -> isHolidayPicture(context, value)
                // A switch that's on is ours while the picture is: we turned it on (the user's
                // own is off then) or left it on
                SWITCH -> value == ON
                else -> value == written.value(key)
            }
            own.put(key, (if (ours) original else current).value(key))
        }
        return own
    }

    /** Whether [image] is one of the holidays' pictures, as [values] names it. */
    private fun isHolidayPicture(context: Context, image: String?): Boolean =
        image?.startsWith("${context.packageName}/$PICTURE") == true

    /** The settings as [holiday] sets them: its picture, from the static provider, switched on. */
    private fun values(context: Context, holiday: Holiday): JSONObject {
        val name = context.resources.getResourceEntryName(holiday.qsHeader)
        return JSONObject()
            .put(IMAGE, "${context.packageName}/$name")
            .put(PROVIDER, STATIC)
            .put(SWITCH, ON)
    }

    /** The settings as SystemUI takes them; one that isn't set is left out. */
    private fun read(resolver: ContentResolver): JSONObject {
        val values = JSONObject()
        for (key in KEYS) values.put(key, get(resolver, key))
        return values
    }

    /** A setting as SystemUI takes it: the provider is daylight while none is set. */
    private fun get(resolver: ContentResolver, key: String): String? {
        val value = Settings.System.getString(resolver, key)
        return if (key == PROVIDER && value == null) DAYLIGHT else value
    }

    /**
     * Writes [values] to those of the settings that hold something else, clearing one it leaves
     * out. The header image goes on after its picture and off before it changes, and the static
     * provider after its image and away before it, so no other picture shows in between. False
     * when a setting couldn't be written.
     */
    private fun write(resolver: ContentResolver, values: JSONObject): Boolean {
        val picture = if (values.value(PROVIDER) == STATIC) {
            listOf(IMAGE, PROVIDER)
        } else {
            listOf(PROVIDER, IMAGE)
        }
        val keys = if (values.value(SWITCH) == ON) picture + SWITCH else listOf(SWITCH) + picture
        return keys.all { key ->
            val value = values.value(key)
            value == get(resolver, key) || Settings.System.putString(resolver, key, value)
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

    private fun save(prefs: SharedPreferences, original: String?, written: String?) {
        prefs.edit().putString(KEY_ORIGINAL, original).putString(KEY_WRITTEN, written).commit()
    }
}
