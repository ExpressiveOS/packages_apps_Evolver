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

/**
 * The holiday themes' own broadcasts: the date alarm, the notification's "Skip this year", and a
 * change made on the Holiday themes page.
 */
class HolidayActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM = "org.evolution.settings.holiday.ALARM"
        const val ACTION_SKIP = "org.evolution.settings.holiday.SKIP"
        private const val ACTION_EVALUATE = "org.evolution.settings.holiday.EVALUATE"

        /** ACTION_SKIP's window to skip, as HolidayThemes.skipKey gives it. */
        const val EXTRA_SKIP = "org.evolution.settings.holiday.extra.SKIP"

        /**
         * Puts on and takes off what a change on the page calls for, then calls [onDone] on the
         * main thread. It runs in this receiver, so Settings stays alive until it's done even if
         * the page is swiped away: a wallpaper set cut short would look like the user's own
         * change, and their saved wallpaper could be lost.
         */
        fun evaluate(context: Context, onDone: () -> Unit) {
            val intent = Intent(ACTION_EVALUATE)
                .setClass(context, HolidayActionReceiver::class.java)
            val done = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = onDone()
            }
            context.sendOrderedBroadcast(intent, null, done, null, 0, null, null)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_EVALUATE -> {
                val pending = goAsync()
                HolidayThemes.evaluateAsync(context) { pending.finish() }
            }
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
