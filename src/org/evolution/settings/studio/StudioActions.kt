/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.studio

import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.settings.R
import com.android.settings.core.SubSettingLauncher
import com.android.settings.display.WallpaperPreferenceController
import org.evolution.settings.fragments.lockscreen.ClockPickerFragment
import org.evolution.settings.preferences.showConfirmDialog

/** What Expressive Studio's tiles and buttons open, from the screen [fragment] shows them on. */
class StudioActions(private val fragment: PreferenceFragmentCompat) {

    private val wallpaper by lazy {
        WallpaperPreferenceController(fragment.requireContext(), KEY_WALLPAPER)
    }

    /**
     * Whether the phone has a wallpaper picker for [openWallpaperPicker] to open, and the user may
     * change the wallpaper.
     */
    val hasWallpaperPicker: Boolean
        get() = wallpaper.isAvailable &&
            fragment.context?.getSystemService(UserManager::class.java)
                ?.hasUserRestriction(UserManager.DISALLOW_SET_WALLPAPER) != true

    /** "Wallpaper" or "Wallpaper & style", as Settings' Display page names the picker. */
    val wallpaperTitle: String
        get() = wallpaper.title

    /**
     * Opens the page behind [fragment]'s row [key] the way tapping that row does: its fragment,
     * or else its intent.
     */
    fun open(key: String) {
        if (!fragment.isAdded) return
        val row = fragment.findPreference<Preference>(key) ?: return
        if (!fragment.onPreferenceTreeClick(row)) {
            row.intent?.let { fragment.startActivity(it) }
        }
    }

    /** The system's wallpaper picker, opened as Settings' Display page opens it. */
    fun openWallpaperPicker() {
        val context = fragment.context ?: return
        wallpaper.handlePreferenceTreeClick(Preference(context).apply { key = KEY_WALLPAPER })
    }

    /**
     * The lock screen's clock face picker. The first time, it shows the advice the clock styles
     * page shows before it (switch off Smartspace, weather and the large clock).
     */
    fun openClockPicker() {
        val activity = fragment.activity ?: return
        val launch = {
            SubSettingLauncher(activity)
                .setDestination(ClockPickerFragment::class.java.name)
                .setTitleRes(R.string.lockscreen_custom_clock_style_title)
                .setSourceMetricsCategory(MetricsEvent.EVOLVER)
                .launch()
        }
        val resolver = activity.contentResolver
        if (Settings.System.getInt(resolver, KEY_DISCLAIMER_DISMISSED, 0) != 0) {
            launch()
            return
        }
        showConfirmDialog(
            context = activity,
            title = activity.getString(R.string.clock_styles_disclaimer_title),
            message = activity.getString(R.string.clock_styles_disclaimer_message),
            confirmText = activity.getString(android.R.string.ok),
            cancelable = false,
            onConfirm = {
                Settings.System.putIntForUser(
                    resolver, KEY_DISCLAIMER_DISMISSED, 1, UserHandle.USER_CURRENT
                )
                launch()
            },
        )
    }

    private companion object {
        const val KEY_WALLPAPER = "studio_wallpaper"
        // ClockStyles' flag: the advice is shown once, from either page
        const val KEY_DISCLAIMER_DISMISSED = "custom_clock_disclaimer_dismissed"
    }
}
