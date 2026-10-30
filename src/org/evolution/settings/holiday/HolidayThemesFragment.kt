/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.holiday

import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Holiday themes: switching on by date, skipping this year's, which parts a theme changes, and
 * trying any holiday's theme now. Every change goes through [HolidayThemes]; the page shows its
 * state again at once and once more when the theme has caught up.
 */
class HolidayThemesFragment : SettingsPreferenceFragment(), Preference.OnPreferenceChangeListener {

    companion object {
        private const val KEY_AUTO = "holiday_auto"
        private const val KEY_STATUS = "holiday_status"
        private const val KEY_SKIP = "holiday_skip"
        private const val KEY_PART = "holiday_part_"
        private const val KEY_TRY = "holiday_try"
        private const val KEY_STOP_TRYING = "holiday_stop_trying"
    }

    private lateinit var auto: SwitchPreferenceCompat
    private lateinit var status: Preference
    private lateinit var skip: Preference
    private lateinit var stopTrying: Preference
    private var parts: Map<HolidayPart, SwitchPreferenceCompat> = emptyMap()
    private val tries = mutableMapOf<Holiday, Preference>()

    /** Whether this year's holiday on by date is skipped, as last shown: what [skip] does. */
    private var skipped = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.holiday_themes_settings)

        auto = findPreference(KEY_AUTO)!!
        status = findPreference(KEY_STATUS)!!
        skip = findPreference(KEY_SKIP)!!
        stopTrying = findPreference(KEY_STOP_TRYING)!!

        auto.onPreferenceChangeListener = this
        skip.setOnPreferenceClickListener {
            val unskip = skipped
            change { app ->
                if (unskip) HolidayThemes.unskip(app) else HolidayThemes.skipThisYear(app)
            }
            true
        }

        parts = HolidayPart.entries.mapNotNull { part ->
            findPreference<SwitchPreferenceCompat>(KEY_PART + part.name.lowercase())
                ?.let { part to it }
        }.toMap()
        parts.values.forEach { it.onPreferenceChangeListener = this }
        // The boot animation is the whole phone's: only its owner colors it, on a build with the
        // holiday animation
        parts[HolidayPart.BOOT_ANIMATION]?.isVisible =
            HolidayThemes.isBootAnimationOffered(preferenceManager.context)

        stopTrying.setOnPreferenceClickListener {
            change { app -> HolidayThemes.setTrial(app, null) }
            true
        }

        // One row per holiday, the one on or coming up soonest first
        val today = LocalDate.now()
        val tryCategory = findPreference<PreferenceCategory>(KEY_TRY)!!
        Holiday.entries
            .sortedBy { upcomingWindow(it, today).start.toEpochDay() }
            .forEach { holiday ->
                val row = Preference(preferenceManager.context).apply {
                    key = "${KEY_TRY}_${holiday.id}"
                    setTitle(holiday.title)
                    isPersistent = false
                    setOnPreferenceClickListener {
                        change { app -> HolidayThemes.setTrial(app, holiday) }
                        true
                    }
                }
                tryCategory.addPreference(row)
                tries[holiday] = row
            }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val on = newValue as? Boolean ?: return false
        if (preference === auto) {
            change { app -> HolidayThemes.setAuto(app, on) }
            return true
        }
        val part = parts.entries.firstOrNull { it.value === preference }?.key ?: return false
        change { app -> HolidayThemes.setPartOn(app, part, on) }
        return true
    }

    override fun getMetricsCategory() = MetricsEvent.EVOLVER

    /** Makes [edit] through the engine, then shows the page again now and once it's applied. */
    private fun change(edit: (Context) -> Unit) {
        val app = context?.applicationContext ?: return
        edit(app)
        refresh()
        HolidayActionReceiver.evaluate(app) { if (isAdded) refresh() }
    }

    /** Shows the engine's state. */
    private fun refresh() {
        val context = context ?: return
        val today = LocalDate.now()
        val isAuto = HolidayThemes.isAuto(context)

        auto.isChecked = isAuto
        status.title = HolidayStatus.read(context, today).text

        // Skipping is about the holiday on by date: offered while its theme is showing, and
        // undone while it's skipped (with Switch on by date on, or skipping means nothing)
        val scheduled = HolidayThemes.scheduled(today)
        skipped = scheduled != null &&
            HolidayThemes.isSkipped(context, scheduled.first, scheduled.second)
        val showing = scheduled != null && HolidayThemes.applied(context) == scheduled.first
        skip.isVisible = if (skipped) isAuto else showing
        skip.setTitle(if (skipped) R.string.holiday_themes_unskip else R.string.holiday_themes_skip)

        parts.forEach { (part, row) -> row.isChecked = HolidayThemes.isPartOn(context, part) }
        // While holidays leave the header image off, the Quick Settings picture's row says why
        parts[HolidayPart.QS_HEADER]?.setSummary(
            if (HolidayThemes.isQsHeaderHeldOff(context)) {
                R.string.holiday_themes_part_qs_header_held_off
            } else {
                R.string.holiday_themes_part_qs_header_summary
            }
        )

        stopTrying.isVisible = HolidayThemes.trial(context) != null
        tries.forEach { (holiday, row) ->
            row.summary = formatDates(context, upcomingWindow(holiday, today))
        }
    }
}

/**
 * What the holiday themes are doing, in a line: the status row of Holiday themes and the summary
 * of its card in Expressive Studio. [holiday] is the theme the line is about, or else the next one
 * due (for the card's colours).
 */
class HolidayStatus private constructor(val text: String, val holiday: Holiday?) {

    companion object {
        fun read(context: Context, today: LocalDate = LocalDate.now()): HolidayStatus {
            HolidayThemes.trial(context)?.let { trial ->
                val text = context.getString(
                    R.string.holiday_themes_status_trying, context.getString(trial.title)
                )
                return HolidayStatus(text, trial)
            }
            HolidayThemes.applied(context)?.let { applied ->
                val name = context.getString(applied.title)
                // Null for the moment a theme stays on past its last day, until the engine runs
                val end = applied.windowContaining(today)?.end
                val text = if (end != null) {
                    context.getString(
                        R.string.holiday_themes_status_on, name, formatDate(context, end)
                    )
                } else {
                    context.getString(R.string.holiday_themes_status_on_now, name)
                }
                return HolidayStatus(text, applied)
            }
            val next = HolidayCatalog.next(today)
            if (next != null && HolidayThemes.isAuto(context)) {
                val (holiday, window) = next
                val text = context.getString(
                    R.string.holiday_themes_status_next,
                    context.getString(holiday.title),
                    formatDate(context, window.start),
                )
                return HolidayStatus(text, holiday)
            }
            return HolidayStatus(
                context.getString(R.string.holiday_themes_status_off), next?.first
            )
        }
    }
}

/** [holiday]'s window that holds [today], or else its next one. */
private fun upcomingWindow(holiday: Holiday, today: LocalDate): DateWindow =
    holiday.windowContaining(today)
        ?: listOf(today.year, today.year + 1)
            .map { holiday.window(it) }
            .first { it.start.isAfter(today) }

/** [date] at noon here: DateUtils takes a range that ends at midnight to end the day before. */
private fun millis(date: LocalDate): Long =
    date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private const val DATE_FLAGS =
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_MONTH

/** "Oct 31". */
private fun formatDate(context: Context, date: LocalDate): String =
    DateUtils.formatDateTime(context, millis(date), DATE_FLAGS)

/** "Nov 23 – 29", or "Dec 27 – Jan 2". */
private fun formatDates(context: Context, window: DateWindow): String =
    DateUtils.formatDateRange(context, millis(window.start), millis(window.end), DATE_FLAGS)
