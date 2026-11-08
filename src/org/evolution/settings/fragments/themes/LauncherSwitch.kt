/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.fragments.themes

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.ServiceManager
import android.os.SystemProperties
import android.util.Log
import android.widget.Toast
import com.android.internal.statusbar.IStatusBarService
import com.android.settings.R
import org.evolution.settings.preferences.showConfirmDialog

/**
 * Expressive Studio's launcher switch: Expressive Launcher or Pixel Launcher as the Home app.
 *
 * Pixel Launcher only works as the Home app while it is also the Recents app, and the framework
 * reads the Recents app at boot, so a switch sets three things and restarts the phone:
 * - [KEY], which turns the ROM's Pixel mode overlay (Recents = Pixel Launcher) on or off at the
 *   next boot;
 * - Pixel Launcher's Home activity, off by default (a sysconfig component override) so it never
 *   runs as Home in Expressive mode, and needed on in Pixel mode because its Recents code looks
 *   it up;
 * - the Home app (the HOME role).
 * Settings runs as the system uid, which may do all three; the shell may not.
 */
object LauncherSwitch {

    const val KEY = "persist.sys.expressiveos.launcher"
    private const val EXPRESSIVE = "expressive"
    private const val PIXEL = "pixel"

    private const val TAG = "LauncherSwitch"
    private const val EXPRESSIVE_LAUNCHER = "dev.launcher.expressive.l3"
    private const val PIXEL_LAUNCHER = "com.google.android.apps.nexuslauncher"
    private val PIXEL_HOME = ComponentName(
        PIXEL_LAUNCHER, "com.google.android.apps.nexuslauncher.NexusLauncherActivity"
    )

    /** Whether this build has both launchers, so there is something to switch between. */
    @JvmStatic
    fun isAvailable(context: Context): Boolean {
        val pm = context.packageManager
        return isInstalled(pm, EXPRESSIVE_LAUNCHER) && isInstalled(pm, PIXEL_LAUNCHER)
    }

    /** Asks, from [activity], whether to restart into [mode], then switches and restarts. */
    @JvmStatic
    fun confirmAndSwitch(activity: Context, mode: String) {
        val pixel = mode == PIXEL
        if (pixel == (SystemProperties.get(KEY) == PIXEL)) return
        val app = activity.applicationContext
        showConfirmDialog(
            context = activity,
            title = activity.getString(R.string.launcher_switch_dialog_title),
            message = activity.getString(
                if (pixel) R.string.launcher_switch_dialog_pixel
                else R.string.launcher_switch_dialog_expressive
            ),
            confirmText = activity.getString(R.string.launcher_switch_restart),
            dismissText = activity.getString(android.R.string.cancel),
            onConfirm = { switchTo(app, pixel) },
        )
    }

    private fun switchTo(context: Context, pixel: Boolean) {
        val pm = context.packageManager
        val before = SystemProperties.get(KEY)
        // The property first: it only acts at the next boot, and if SELinux refuses it nothing
        // else has changed yet.
        if (!setMode(if (pixel) PIXEL else EXPRESSIVE)) {
            failed(context)
            return
        }
        // Pixel Launcher only counts as a Home app with its Home activity on.
        if (pixel) setPixelHome(pm, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        context.getSystemService(RoleManager::class.java).addRoleHolderAsUser(
            RoleManager.ROLE_HOME,
            if (pixel) PIXEL_LAUNCHER else EXPRESSIVE_LAUNCHER,
            RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
            Process.myUserHandle(),
            context.mainExecutor,
        ) { ok ->
            if (ok != true) {
                Log.e(TAG, "Couldn't change the Home app, pixel=$pixel")
                if (pixel) setPixelHome(pm, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                setMode(before)
                failed(context)
                return@addRoleHolderAsUser
            }
            if (!pixel) setPixelHome(pm, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            reboot()
        }
    }

    private fun setMode(value: String): Boolean =
        runCatching { SystemProperties.set(KEY, value) }
            .onFailure { Log.e(TAG, "Couldn't set $KEY", it) }
            .isSuccess

    private fun setPixelHome(pm: PackageManager, state: Int) {
        runCatching {
            pm.setComponentEnabledSetting(PIXEL_HOME, state, PackageManager.DONT_KILL_APP)
        }.onFailure { Log.e(TAG, "Couldn't set Pixel Launcher's Home activity to $state", it) }
    }

    private fun failed(context: Context) {
        Toast.makeText(context, R.string.launcher_switch_failed, Toast.LENGTH_LONG).show()
    }

    private fun reboot() {
        runCatching {
            IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar"))
                .reboot(false, null)
        }.onFailure { Log.e(TAG, "Couldn't restart", it) }
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean =
        runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess
}
