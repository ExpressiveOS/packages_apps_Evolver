/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.holiday

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import com.android.settings.R
import com.android.settings.core.BasePreferenceController
import com.android.settings.deviceinfo.firmwareversion.EvolutionLogoAnimator
import com.android.settings.deviceinfo.firmwareversion.GlowView
import com.android.settingslib.widget.LayoutPreference

/**
 * The holiday header at the top of Settings' main page, while a theme's LOOK part is on: the
 * glowing wordmark, a greeting and what the theme is doing, opening Holiday themes when tapped.
 * The wordmark takes the colours the BANNER part writes, so it's Bloom's while that part is off.
 *
 * The controller holds the header's category, and only makes the header while it shows: the rest
 * of the year the page doesn't build the wordmark. It looks at the theme each time the page is
 * shown, and follows it while the page shows: a change from Holiday themes or the notification
 * lands once the engine is done, which can be after the page is back.
 */
class HolidayHeaderController(context: Context, key: String) :
    BasePreferenceController(context, key), DefaultLifecycleObserver {

    companion object {
        private const val KEY_HEADER = "top_level_holiday_header"
    }

    private var category: PreferenceGroup? = null
    private var header: LayoutPreference? = null

    // Kept here, as the engine's state holds it weakly
    private val listener = HolidayThemes.listener { applied ->
        refresh()
        // The banner part's colours are in by the time the parts are noted
        if (applied) restartAnimation()
    }

    override fun getAvailabilityStatus(): Int =
        if (HolidayThemes.studioHoliday(mContext) != null) {
            AVAILABLE_UNSEARCHABLE
        } else {
            CONDITIONALLY_UNAVAILABLE
        }

    override fun displayPreference(screen: PreferenceScreen) {
        super.displayPreference(screen)
        category = screen.findPreference(preferenceKey)
        header = category?.findPreference(KEY_HEADER)
        refresh()
    }

    override fun onStart(owner: LifecycleOwner) {
        HolidayThemes.listen(mContext, listener)
        refresh()
        restartAnimation()
    }

    override fun onStop(owner: LifecycleOwner) {
        HolidayThemes.unlisten(mContext, listener)
    }

    /** One sweep, each time the page is shown or the theme changes, in the colours as they are. */
    private fun restartAnimation() {
        header?.findViewById<GlowView>(R.id.evolution_rgb_glow)?.restartAnimation()
    }

    /**
     * Shows the header for the holiday on now, or hides it. The page only shows or hides its
     * preferences as it makes them, so the controller does it itself.
     */
    private fun refresh() {
        val category = category ?: return
        val holiday = HolidayThemes.studioHoliday(mContext)
        category.isVisible = holiday != null
        if (holiday == null) {
            header?.let { category.removePreference(it) }
            header = null
            return
        }
        val header = this.header ?: addHeader(category).also { this.header = it }
        // The status line while it's about this holiday, which it isn't for a moment after
        // another holiday is picked to try
        val status = HolidayStatus.read(mContext).takeIf { it.holiday == holiday }?.text
        header.findViewById<TextView>(R.id.holiday_header_greeting)?.setText(holiday.greeting)
        header.findViewById<TextView>(R.id.holiday_header_status)?.apply {
            text = status
            visibility = if (status != null) View.VISIBLE else View.GONE
        }
    }

    private fun addHeader(category: PreferenceGroup): LayoutPreference {
        val header = LayoutPreference(category.context, R.layout.holiday_header).apply {
            key = KEY_HEADER
            fragment = HolidayThemesFragment::class.java.name
        }
        header.findViewById<View>(R.id.evolution_logo_container)?.let { logo ->
            EvolutionLogoAnimator.bind(logo)
            // The wordmark spells the name; the greeting under it is what's read out
            logo.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        // One sweep, then still, so the main page isn't drawn again every frame
        header.findViewById<GlowView>(R.id.evolution_rgb_glow)?.setAnimationLimit(GlowView.SWEEP_MS)
        // Accessibility services don't see the frame LayoutPreference takes taps on, so the card
        // takes their click and passes it on: then it's read out and scanned as something to open
        header.findViewById<View>(R.id.holiday_header_root)?.let { card ->
            ViewCompat.replaceAccessibilityAction(
                card,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                null,
            ) { view, _ -> (view.parent as? View)?.performClick() ?: false }
        }
        category.addPreference(header)
        return header
    }
}
