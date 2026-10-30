/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.holiday

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.text.format.DateFormat
import com.android.settings.R
import com.android.settings.SettingsActivity
import com.android.settings.SubSettings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The quiet notification a holiday theme posts when it switches on by date. */
internal object HolidayNotifications {
    private const val CHANNEL_ID = "holiday_themes"
    private const val NOTIFICATION_TAG = "holiday_themes"
    private const val NOTIFICATION_ID = 1

    /** "[holiday] theme is on", until the last day of [window], with "Skip this year". */
    fun showOn(context: Context, holiday: Holiday, window: DateWindow) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.holiday_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SubSettings::class.java)
                .putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, HolidayThemesFragment::class.java.name)
                .putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT_TITLE_RESID, R.string.holiday_themes_title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Names its own window: tapped while the theme waits to switch, "today" is the next one's
        val skip = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, HolidayActionReceiver::class.java)
                .setAction(HolidayActionReceiver.ACTION_SKIP)
                .putExtra(
                    HolidayActionReceiver.EXTRA_SKIP, HolidayThemes.skipKey(holiday, window)
                ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val text = context.getString(R.string.holiday_notification_on_text, formatDay(window.end))
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_themes)
            .setContentTitle(
                context.getString(R.string.holiday_notification_on_title, context.getString(holiday.title))
            )
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_themes),
                    context.getString(R.string.holiday_notification_skip),
                    skip,
                ).build()
            )
            .build()
        manager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
    }

    /** [date] as the locale writes a short month and day, e.g. "Oct 31". */
    private fun formatDay(date: LocalDate): String {
        val locale = Locale.getDefault()
        return try {
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
                .format(date)
        } catch (e: Exception) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
        }
    }
}
