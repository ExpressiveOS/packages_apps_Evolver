/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.holiday

import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.WorkerThread

import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * The holiday wallpaper on the home and lock screens. The user's own wallpaper is saved before the
 * first holiday sets one (a copy of a static image, or a live wallpaper's component) and put back
 * when the holidays end. The wallpaper ids from right after our set tell whether a screen still
 * shows the holiday wallpaper: a screen the user changed since keeps what they chose.
 */
object HolidayWallpaper {
    private const val TAG = "HolidayWallpaper"

    private const val PREFS = "holiday_wallpaper"
    private const val BACKUP_DIR = "holiday_wallpaper"
    private const val KEY_LOCK_GLYMPS = "lock_glymps_enabled"

    // The wallpaper ids right after we set the holiday wallpaper, and whose it is
    private const val KEY_SET_HOME_ID = "set_home_id"
    private const val KEY_SET_LOCK_ID = "set_lock_id"
    private const val KEY_SET_HOLIDAY = "set_holiday"
    private const val NO_ID = -1

    // What a screen showed before the holidays, stored under the screen's key
    private const val KIND_IMAGE = "image" // a static image, copied to the screen's file
    private const val KIND_LIVE = "live" // a live wallpaper, its component under componentKey
    private const val KIND_DEFAULT = "default" // no image to copy: the built-in wallpaper
    private const val KIND_SHARED = "shared" // lock screen only: it showed the home wallpaper

    private enum class Screen(val flag: Int, val key: String, val fileName: String) {
        HOME(FLAG_SYSTEM, "home", "home.img"),
        LOCK(FLAG_LOCK, "lock", "lock.img");

        val componentKey: String get() = "${key}_component"
    }

    /** Wallpapers can be set for this user and Wallpaper Glymps is off. */
    fun isAllowed(context: Context): Boolean = try {
        val wm = WallpaperManager.getInstance(context)
        wm != null && canSet(context, wm) &&
            // Glymps keeps changing the wallpaper itself
            Settings.System.getInt(context.contentResolver, KEY_LOCK_GLYMPS, 0) == 0
    } catch (e: Exception) {
        Log.w(TAG, "Can't tell whether the wallpaper may be set", e)
        false
    }

    /** Whether the wallpaper service takes wallpapers from this user: it ignores them otherwise. */
    private fun canSet(context: Context, wm: WallpaperManager): Boolean {
        val um = context.getSystemService(UserManager::class.java)
        return wm.isWallpaperSupported && wm.isSetWallpaperAllowed &&
            um?.hasUserRestriction(UserManager.DISALLOW_SET_WALLPAPER) != true
    }

    /**
     * The id of the holiday whose wallpaper we set, until the user's own goes back: one that
     * couldn't go back yet can still show an earlier holiday's. Blocking IO.
     */
    @WorkerThread
    fun setHoliday(context: Context): String? = try {
        context.createCredentialProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SET_HOLIDAY, null)
    } catch (e: Exception) {
        Log.w(TAG, "Can't tell which holiday's wallpaper is set", e)
        null
    }

    /** Whether the home screen shows the holiday wallpaper we set. Blocking IO. */
    @WorkerThread
    fun showsHolidayHome(context: Context): Boolean = try {
        val prefs = context.createCredentialProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        showsOurs(WallpaperManager.getInstance(context), prefs, Screen.HOME)
    } catch (e: Exception) {
        Log.w(TAG, "Can't tell whether the holiday wallpaper is showing", e)
        false
    }

    /** Shows [holiday]'s wallpaper on home and lock, saving the user's own first. Blocking IO. */
    @WorkerThread
    fun apply(context: Context, holiday: Holiday): Boolean {
        return try {
            val storage = context.createCredentialProtectedStorageContext()
            val prefs = storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val dir = File(storage.filesDir, BACKUP_DIR)
            val wm = WallpaperManager.getInstance(context)
            // Going straight from one holiday to the next keeps the backup from before the first;
            // a screen the user changed in between is backed up again, as their new choice
            val held = prefs.contains(Screen.HOME.key)
            val homeOurs = held && showsOurs(wm, prefs, Screen.HOME)
            val lockOurs = held && showsOurs(wm, prefs, Screen.LOCK)
            if (!homeOurs || !lockOurs) {
                if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Can't create $dir")
                val edit = prefs.edit()
                if (!homeOurs) backUp(context, wm, Screen.HOME, dir, edit)
                if (!lockOurs) backUp(context, wm, Screen.LOCK, dir, edit)
                edit.commit()
            }

            // allowBackup false: the holiday art must never be backed up as the user's wallpaper
            val id = context.resources.openRawResource(holiday.wallpaper).use {
                wm.setStream(it, null, false, FLAG_SYSTEM or FLAG_LOCK)
            }
            if (id == 0) throw IOException("The wallpaper service refused the wallpaper")
            prefs.edit()
                .putInt(KEY_SET_HOME_ID, wm.getWallpaperId(FLAG_SYSTEM))
                .putInt(KEY_SET_LOCK_ID, wm.getWallpaperId(FLAG_LOCK))
                .putString(KEY_SET_HOLIDAY, holiday.id)
                .commit()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set the ${holiday.id} wallpaper", e)
            // Not applied: whatever of ours is still showing goes back to the user's own
            restore(context)
            false
        }
    }

    /**
     * Puts the user's own wallpaper back unless they changed it since. Blocking IO. False when the
     * wallpaper service failed: the backup stays for the next try.
     */
    @WorkerThread
    fun restore(context: Context): Boolean {
        return try {
            val storage = context.createCredentialProtectedStorageContext()
            val prefs = storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val dir = File(storage.filesDir, BACKUP_DIR)
            if (prefs.contains(KEY_SET_HOME_ID)) {
                val wm = WallpaperManager.getInstance(context)
                // Nothing would go back, and the backup is still needed for when it can
                if (!canSet(context, wm)) throw IOException("Wallpapers can't be set now")
                val homeOurs = showsOurs(wm, prefs, Screen.HOME)
                val lockOurs = showsOurs(wm, prefs, Screen.LOCK)
                val lockShared = prefs.getString(Screen.LOCK.key, null) == KIND_SHARED
                if (homeOurs && lockOurs &&
                    (lockShared || !usable(context, prefs, dir, Screen.LOCK))
                ) {
                    // The lock screen showed the home wallpaper before, or its own can't come
                    // back: both get the home one at once. Clearing the lock screen after putting
                    // home back would announce the home wallpaper before its colors are known,
                    // and SystemUI would drop a color option of it.
                    putBack(context, wm, prefs, dir, Screen.HOME, FLAG_SYSTEM or FLAG_LOCK)
                } else {
                    // Home first: setting home alone moves a shared wallpaper to the lock screen,
                    // which the lock backup then replaces
                    val homeBack =
                        homeOurs && putBack(context, wm, prefs, dir, Screen.HOME, FLAG_SYSTEM)
                    if (lockOurs) {
                        if (lockShared) {
                            // The lock screen showed the home wallpaper before, so it does again
                            wm.clear(FLAG_LOCK)
                        } else if (
                            !putBack(
                                context, wm, prefs, dir, Screen.LOCK, FLAG_LOCK, clear = !homeBack
                            ) && homeBack
                        ) {
                            // The lock backup failed after all: the home one just put back goes
                            // there too, for the same reason as above
                            putBack(context, wm, prefs, dir, Screen.HOME, FLAG_LOCK)
                        }
                    }
                }
            }
            // Done with the backup, whether it went back or the user had changed the wallpaper
            prefs.edit().clear().commit()
            dir.deleteRecursively()
            true
        } catch (e: Exception) {
            // The backup stays: the next apply or restore can still use it
            Log.w(TAG, "Failed to put the wallpaper back", e)
            false
        }
    }

    /**
     * Whether [screen] still shows the wallpaper we set, by the ids stored right after setting it.
     * A lock screen without a wallpaper of its own shows the home one. One that got its own when
     * only the home wallpaper was changed carries over our wallpaper's id.
     */
    private fun showsOurs(wm: WallpaperManager, prefs: SharedPreferences, screen: Screen): Boolean {
        val setHome = prefs.getInt(KEY_SET_HOME_ID, NO_ID)
        if (setHome < 0) return false
        val homeOurs = wm.getWallpaperId(FLAG_SYSTEM) == setHome
        if (screen == Screen.HOME) return homeOurs
        val lock = wm.getWallpaperId(FLAG_LOCK)
        return if (lock < 0) homeOurs
        else lock == setHome || lock == prefs.getInt(KEY_SET_LOCK_ID, NO_ID)
    }

    /** Records what [screen] shows now in [edit], copying a static image to the backup folder. */
    private fun backUp(
        context: Context,
        wm: WallpaperManager,
        screen: Screen,
        dir: File,
        edit: SharedPreferences.Editor,
    ) {
        val file = File(dir, screen.fileName)
        file.delete()
        edit.remove(screen.componentKey)
        if (screen == Screen.LOCK && wm.getWallpaperId(FLAG_LOCK) < 0) {
            edit.putString(screen.key, KIND_SHARED)
            return
        }
        val live = wm.getWallpaperInfo(screen.flag)
        if (live != null) {
            edit.putString(screen.key, KIND_LIVE)
                .putString(screen.componentKey, live.component.flattenToString())
            return
        }
        val pfd = wm.getWallpaperFile(screen.flag)
        if (pfd == null) {
            // In the background a user's live wallpaper isn't running, and looks the same
            if (context.getSystemService(UserManager::class.java)?.isUserForeground != true) {
                throw IOException("Can't tell the ${screen.key} wallpaper of a background user")
            }
            edit.putString(screen.key, KIND_DEFAULT)
            return
        }
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        edit.putString(screen.key, KIND_IMAGE)
    }

    /**
     * Whether [screen]'s backup can go back: a saved copy, a live wallpaper still installed, or the
     * built-in wallpaper on a lock screen of its own.
     */
    private fun usable(
        context: Context,
        prefs: SharedPreferences,
        dir: File,
        screen: Screen,
    ): Boolean = when (prefs.getString(screen.key, null)) {
        KIND_IMAGE -> File(dir, screen.fileName).isFile
        KIND_LIVE -> liveComponent(context, prefs, screen) != null
        KIND_DEFAULT -> screen == Screen.LOCK
        else -> false
    }

    /**
     * [screen]'s saved live wallpaper, while it's still installed and enabled. The wallpaper
     * service reports one that isn't as set, and leaves the screen as it was.
     */
    private fun liveComponent(
        context: Context,
        prefs: SharedPreferences,
        screen: Screen,
    ): ComponentName? {
        val component = prefs.getString(screen.componentKey, null)
            ?.let { ComponentName.unflattenFromString(it) } ?: return null
        return try {
            context.packageManager.getServiceInfo(component, 0)
            component
        } catch (_: PackageManager.NameNotFoundException) {
            Log.w(TAG, "The ${screen.key} live wallpaper $component is gone")
            null
        }
    }

    /**
     * Puts [screen]'s backup on [which]. When there's no usable backup (the built-in home
     * wallpaper, a missing copy, an uninstalled live wallpaper), the screens go back to the
     * built-in one unless not to [clear] them: this is only called for screens that still show
     * the holiday wallpaper. False when the backup didn't go back.
     */
    private fun putBack(
        context: Context,
        wm: WallpaperManager,
        prefs: SharedPreferences,
        dir: File,
        screen: Screen,
        which: Int,
        clear: Boolean = true,
    ): Boolean {
        val restored = try {
            when (prefs.getString(screen.key, null)) {
                KIND_IMAGE -> {
                    val file = File(dir, screen.fileName)
                    file.isFile && FileInputStream(file).use {
                        wm.setStream(it, null, true, which)
                    } != 0
                }
                KIND_LIVE -> {
                    val component = liveComponent(context, prefs, screen)
                    component != null && wm.setWallpaperComponentWithFlags(component, which)
                }
                // A lock screen of its own showing the built-in image: clearing it would show
                // the home wallpaper there instead. Not the user's own, so not backed up.
                KIND_DEFAULT -> screen == Screen.LOCK &&
                    (WallpaperManager.openDefaultWallpaper(context, FLAG_LOCK)?.use {
                        wm.setStream(it, null, false, FLAG_LOCK)
                    } ?: 0) != 0
                else -> false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to put back the ${screen.key} wallpaper", e)
            false
        }
        if (!restored && clear) wm.clear(which)
        return restored
    }
}
