/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.holiday

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemProperties
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import java.io.File

/**
 * The boot animation in a holiday's colors, from the next restart: the build's holiday animation,
 * the ExpressiveOS one drawn in four color channels, picked by its name, with the holiday's petals
 * as its colors. SystemUI writes the animation's colors itself at every theme change, its Monet
 * ones; while a holiday's are on it takes them from a setting instead, so the two agree. The
 * animation is the whole phone's, so only the phone's owner (the system user) puts it on. The
 * user's own animation is kept from before the first holiday and put back after the last, unless
 * they picked another in between: then theirs stays. The colors aren't kept: they aren't the
 * user's (SystemUI works them out from the colors showing, and the everyday animation takes none),
 * so SystemUI writes its own again once the setting goes.
 */
internal object HolidayBootAnim {
    private const val TAG = "HolidayThemes"

    // In HolidayThemes' preferences: the animation's name as it was before (empty for none)
    private const val KEY_ORIGINAL = "bootanim_original"

    // In HolidayThemes' preferences: set from just before the user's animation goes back until it
    // has, or ours is on again. Their own showing while it's set was put back by a restore cut
    // short, not picked again, so the holiday asking for ours again puts it back on.
    private const val KEY_RESTORING = "bootanim_restoring"

    // Which animation boots (BootAnimationUtils.PROP_NAME), by name: bootanimation_<name>.zip in
    // /product/media, or the everyday one for none, "default" or a name whose zip is missing
    private const val PROP_NAME = "persist.sys.bootanim.name"
    private const val NONE = ""
    private const val DEFAULT = "default"
    private const val HOLIDAY = "holiday"
    private const val HOLIDAY_ZIP = "/product/media/bootanimation_holiday.zip"

    // The holiday animation's colors, persist.bootanim.color1 to 4, one for each of its channels:
    // signed decimal ARGB, as SystemUI writes them
    private const val PROP_COLOR = "persist.bootanim.color"

    // Has SystemUI write the colors again: the setting's, or its Monet ones without it
    private const val ACTION_STYLE_CHANGED = "org.evolution.intent.action.BOOTANIM_STYLE_CHANGED"

    /**
     * Whether this user has a holiday animation to put on: the phone's owner, on a build that has
     * one. The Holiday themes page shows the part only then.
     */
    fun isOffered(context: Context): Boolean = isOwner(context) && File(HOLIDAY_ZIP).isFile

    /** [isOffered], with the everyday animation booting or ours: one the user picked stays. */
    fun isAllowed(context: Context): Boolean =
        isOffered(context) && name() in listOf(NONE, DEFAULT, HOLIDAY)

    /**
     * Has [holiday]'s animation boot, in its colors, from the next restart. Where it isn't
     * [isAllowed], nothing goes on and an earlier holiday's comes off. False when it couldn't be
     * put on: what did get written comes off again.
     */
    fun apply(context: Context, holiday: Holiday, prefs: SharedPreferences): Boolean {
        if (!isAllowed(context)) return restore(context, prefs)
        val current = name()
        val currentPalette = palette(context.contentResolver)
        val savedOriginal = prefs.getString(KEY_ORIGINAL, null)
        // Going from one holiday straight to the next keeps the animation from before the first,
        // while ours still boots: otherwise the user picked theirs since, and it's the one to come
        // back. Ours with nothing saved (the engine's state was lost) goes back to none.
        val original = if (current == HOLIDAY) savedOriginal ?: NONE else current
        // Saved before anything is written, so a crash in between still knows what to put back
        save(prefs, original)

        val ok = try {
            write(
                context,
                HOLIDAY,
                HolidayCatalog.bootPalette(holiday),
                HolidayCatalog.bootColors(holiday),
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't put ${holiday.id}'s boot animation on", e)
            false
        }
        if (ok) {
            // On again: a restore cut short before this is over
            noteRestoring(prefs, false)
        } else {
            // What did get written comes off too: with nothing saved, nothing would take it off.
            // Until it has, what's saved stays, so the next try still knows the user's own.
            val undone = try {
                write(context, current, currentPalette, null)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Couldn't take ${holiday.id}'s boot animation off again", e)
                false
            }
            if (undone) save(prefs, savedOriginal)
        }
        return ok
    }

    /**
     * Puts the user's own animation back, unless they picked another while the holiday's was on,
     * and has SystemUI write its own colors again. False when that couldn't be done: what did go
     * back comes off again, and what's saved stays for the next try.
     */
    fun restore(context: Context, prefs: SharedPreferences): Boolean {
        // The animation isn't another user's to change
        if (!isOwner(context)) return true
        val original = prefs.getString(KEY_ORIGINAL, null)
        val current = name()
        val palette = palette(context.contentResolver)
        // Nothing of ours on: nothing saved (it goes last, so a restore cut short still has it),
        // no setting, and not our animation booting
        if (original == null && current != HOLIDAY && palette.isNullOrEmpty()) return true
        if (current != HOLIDAY && original != null && current != original) {
            Log.i(TAG, "Boot animation changed during the holiday, keeping it")
        }
        // Ours comes off for the user's own, or none with nothing saved; theirs stays
        val own = if (current == HOLIDAY) original ?: NONE else current
        // Noted before anything goes back: cut short in between, ours is off while it's still
        // recorded as on, and the holiday asking for it again puts it on again (needsRefresh)
        noteRestoring(prefs, true)
        val ok = try {
            write(context, own, null, null).also { written ->
                if (!written) Log.w(TAG, "Couldn't put the user's boot animation back")
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't put the user's boot animation back", e)
            false
        }
        if (!ok) {
            // What did go back comes off again: half put back (the user's name with the holiday's
            // setting), ours would be off while it's recorded as on, and the holiday asking for
            // it again wouldn't put it back on. What's saved stays for the next try, and the
            // restore is noted as over once ours is back on.
            val undone = try {
                write(context, current, palette, null)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Couldn't put the holiday's boot animation back on", e)
                false
            }
            if (undone) noteRestoring(prefs, false)
            return false
        }
        // Put back: nothing saved, and nothing under way
        prefs.edit().remove(KEY_ORIGINAL).remove(KEY_RESTORING).commit()
        return true
    }

    /**
     * Whether ours should be put on again while it's recorded as on: a restore cut short (or that
     * couldn't come off again) left the user's own back, or a switch that couldn't go on and then
     * couldn't come off left an earlier holiday's colors. One the user picked stays: only ours,
     * or the user's own a restore put back, is put on again. False when it can't be told: the
     * engine asks while it decides when to run next.
     */
    fun needsRefresh(context: Context, holiday: Holiday, prefs: SharedPreferences): Boolean = try {
        val restoring = prefs.getBoolean(KEY_RESTORING, false)
        isOwner(context) && when (val current = name()) {
            // Ours in an earlier holiday's colors, or with a restore cut short before it wrote
            // anything: put on again, that restore is noted as over, so the user picking their
            // own later isn't taken for one cut short
            HOLIDAY -> restoring || palette(context.contentResolver)
                .let { !it.isNullOrEmpty() && it != HolidayCatalog.bootPalette(holiday) }
            // The user's own, put back by a restore cut short: not theirs picked again
            else -> restoring && current == prefs.getString(KEY_ORIGINAL, null)
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "Couldn't check the boot animation", e)
        false
    }

    /**
     * Takes off a holiday animation the engine doesn't have [applied]: its state was lost
     * (Settings' data cleared), or a crash or a write that couldn't be undone left it on. Its
     * setting would keep SystemUI writing a holiday's colors, and ours would boot for good. With
     * nothing saved our name goes back to none; on its own, without the setting or anything saved,
     * it may be the user's pick, and stays.
     */
    fun sweep(context: Context, prefs: SharedPreferences, applied: Boolean) {
        if (applied) return
        try {
            if (!isOwner(context)) return
            if (prefs.contains(KEY_ORIGINAL) ||
                !palette(context.contentResolver).isNullOrEmpty()
            ) {
                Log.i(TAG, "Boot animation left on by an earlier change, taking it off")
                restore(context, prefs)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't take the boot animation off", e)
        }
    }

    /**
     * Has [name] boot, with SystemUI taking the colors from [palette] (its Monet ones for none) and
     * [colors], where given, written now, then has SystemUI write them again. Only what holds
     * something else is written. The holiday animation goes on after its colors and off before
     * they go, so it doesn't boot in others. False when the setting couldn't be written; a
     * property init won't set throws.
     */
    private fun write(
        context: Context,
        name: String,
        palette: String?,
        colors: List<Int>?,
    ): Boolean {
        val resolver = context.contentResolver
        if (name != HOLIDAY) set(PROP_NAME, name)
        if (palette != palette(resolver) &&
            !Settings.Secure.putStringForUser(
                resolver, HolidayCatalog.KEY_BOOT_PALETTE, palette, UserHandle.USER_SYSTEM
            )
        ) {
            return false
        }
        colors?.forEachIndexed { index, color -> set("$PROP_COLOR${index + 1}", color.toString()) }
        if (name == HOLIDAY) set(PROP_NAME, name)
        context.sendBroadcast(Intent(ACTION_STYLE_CHANGED))
        return true
    }

    /** Sets property [key] to [value], unless it holds it already. Throws where init won't. */
    private fun set(key: String, value: String) {
        if (SystemProperties.get(key, NONE) != value) SystemProperties.set(key, value)
    }

    /** The animation that boots, by name; empty for none. */
    private fun name(): String = SystemProperties.get(PROP_NAME, NONE)

    /** The colors the setting has SystemUI write; null for its Monet ones. */
    private fun palette(resolver: ContentResolver): String? = Settings.Secure.getStringForUser(
        resolver, HolidayCatalog.KEY_BOOT_PALETTE, UserHandle.USER_SYSTEM
    )

    /** Whether this is the system user: the phone's owner, whose boot animation is the phone's. */
    private fun isOwner(context: Context): Boolean =
        context.getSystemService(UserManager::class.java)?.isSystemUser == true

    private fun save(prefs: SharedPreferences, original: String?) {
        prefs.edit().putString(KEY_ORIGINAL, original).commit()
    }

    /** Notes that a restore is under way, or with false, that it's over. */
    private fun noteRestoring(prefs: SharedPreferences, restoring: Boolean) {
        val editor = prefs.edit()
        if (restoring) editor.putBoolean(KEY_RESTORING, true) else editor.remove(KEY_RESTORING)
        editor.commit()
    }
}
