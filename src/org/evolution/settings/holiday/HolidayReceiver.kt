/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.holiday

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Looks at the holiday theme again after a boot, a clock or time zone change, or an update of
 * Settings, which can each move today across a holiday's first or last day.
 */
class HolidayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        val pending = goAsync()
        HolidayThemes.evaluateAsync(context) { pending.finish() }
    }
}

/** The holiday themes' own broadcasts: the date alarm, and the notification's "Skip this year". */
class HolidayActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM = "org.evolution.settings.holiday.ALARM"
        const val ACTION_SKIP = "org.evolution.settings.holiday.SKIP"

        /** ACTION_SKIP's window to skip, as HolidayThemes.skipKey gives it. */
        const val EXTRA_SKIP = "org.evolution.settings.holiday.extra.SKIP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ALARM -> {
                val pending = goAsync()
                HolidayThemes.onAlarm(context) { pending.finish() }
            }
            ACTION_SKIP -> {
                val pending = goAsync()
                HolidayThemes.skipThisYear(context, intent.getStringExtra(EXTRA_SKIP)) {
                    pending.finish()
                }
            }
        }
    }
}
