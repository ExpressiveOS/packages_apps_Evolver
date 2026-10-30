/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.holiday

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.WorkerThread
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/** What a holiday theme changes. Each part can be switched off on its own. */
enum class HolidayPart {
    /** The holiday's picture on the home and lock screens. */
    WALLPAPER,

    /** The Material You colors, from the holiday's seed. */
    COLORS,

    /** The icons on Settings' main page, in the holiday's petals. */
    ICONS,

    /** Expressive Studio's tiles, in the holiday's petals. */
    LOOK,

    /** The glowing ExpressiveOS wordmark in About and the Updater. */
    BANNER,
}

/**
 * ExpressiveOS's holiday themes: which holiday's theme should be showing, and putting it on and
 * taking it off part by part. The user's own wallpaper and colors are saved before the first
 * holiday and come back after the last one, unless the user changed them in between: then their
 * change stays.
 *
 * State lives in credential-protected storage, so nothing happens until the user has unlocked.
 * All the work runs on one background thread, never two reconciles at once.
 */
object HolidayThemes {
    private const val TAG = "HolidayThemes"

    private const val PREFS = "holiday_themes"
    private const val KEY_AUTO = "auto"
    private const val KEY_PART = "part_"
    private const val KEY_SKIPPED = "skipped"
    private const val KEY_TRIAL = "trial"
    private const val KEY_APPLIED = "applied"
    private const val KEY_APPLIED_PARTS = "applied_parts"
    private const val KEY_NOTIFIED = "notified"

    /** How long a theme change waits while the screen is on, before it looks again. */
    private val SCREEN_ON_DELAY_MS = TimeUnit.MINUTES.toMillis(15)

    /** Colors before the wallpaper, so the new wallpaper's colors don't win. */
    private val APPLY_ORDER = listOf(
        HolidayPart.COLORS,
        HolidayPart.WALLPAPER,
        HolidayPart.ICONS,
        HolidayPart.BANNER,
        HolidayPart.LOOK,
    )
    private val RESTORE_ORDER = APPLY_ORDER.reversed()

    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, TAG) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val evaluating = Any()

    /** A theme to show: [holiday] in [parts], by date in [window], or a trial when it's null. */
    private class Plan(val holiday: Holiday, val window: DateWindow?, val parts: Set<HolidayPart>)

    // -------------------------------------------------------------------------
    // Settings
    // -------------------------------------------------------------------------

    fun isAuto(context: Context): Boolean = prefs(context)?.getBoolean(KEY_AUTO, true) ?: true

    fun setAuto(context: Context, on: Boolean) {
        prefs(context)?.edit()?.putBoolean(KEY_AUTO, on)?.apply()
        evaluateAsync(context)
    }

    fun isPartOn(context: Context, part: HolidayPart): Boolean =
        prefs(context)?.getBoolean(KEY_PART + part.name, true) ?: true

    fun setPartOn(context: Context, part: HolidayPart, on: Boolean) {
        prefs(context)?.edit()?.putBoolean(KEY_PART + part.name, on)?.apply()
        evaluateAsync(context)
    }

    fun trial(context: Context): Holiday? = prefs(context)?.let { trial(it) }

    fun setTrial(context: Context, holiday: Holiday?) {
        prefs(context)?.edit()?.putString(KEY_TRIAL, holiday?.id)?.apply()
        evaluateAsync(context)
    }

    /** The theme showing now. */
    fun applied(context: Context): Holiday? = prefs(context)?.let { applied(it) }

    /** The holiday whose dates hold [today], with its window, ignoring auto and skips. */
    fun scheduled(today: LocalDate = LocalDate.now()): Pair<Holiday, DateWindow>? {
        val holiday = HolidayCatalog.activeOn(today) ?: return null
        val window = holiday.windowContaining(today) ?: return null
        return holiday to window
    }

    fun isSkipped(context: Context, holiday: Holiday, window: DateWindow): Boolean =
        prefs(context)?.let { isSkipped(it, holiday, window) } ?: false

    /** Turns [scheduled]'s theme off until the holiday comes round again next year. */
    fun skipThisYear(context: Context) {
        skipThisYear(context, null, null)
    }

    /**
     * Skips the window [key] names (a [skipKey], as the notification carries it: by the time it's
     * tapped the date may have moved on to the next holiday), or [scheduled]'s when it's null.
     */
    internal fun skipThisYear(context: Context, key: String?, onDone: (() -> Unit)?) {
        val prefs = prefs(context)
        val named: Holiday? = key?.let { HolidayCatalog.byId(it.substringBefore(':')) }
        val skip: Pair<Holiday, String>? = if (key != null && named != null) {
            named to key
        } else {
            scheduled()?.let { (holiday, window) -> holiday to skipKey(holiday, window) }
        }
        if (prefs != null && skip != null) {
            val (holiday, windowKey) = skip
            val editor = prefs.edit().putStringSet(KEY_SKIPPED, skipped(prefs) + windowKey)
            // Trying the very holiday that's on would keep it showing
            if (trial(prefs) == holiday) editor.remove(KEY_TRIAL)
            editor.apply()
        }
        evaluateAsync(context, onDone)
    }

    /** Undoes [skipThisYear] for [scheduled]'s window. */
    fun unskip(context: Context) {
        val prefs = prefs(context)
        val scheduled = scheduled()
        if (prefs != null && scheduled != null) {
            val (holiday, window) = scheduled
            prefs.edit()
                .putStringSet(KEY_SKIPPED, skipped(prefs) - skipKey(holiday, window))
                .apply()
        }
        evaluateAsync(context)
    }

    /** What Studio's tiles are drawn in: the applied holiday while its LOOK part is applied. */
    fun studioHoliday(context: Context): Holiday? {
        val prefs = prefs(context) ?: return null
        return if (HolidayPart.LOOK in appliedParts(prefs)) applied(prefs) else null
    }

    // -------------------------------------------------------------------------
    // Reconciling
    // -------------------------------------------------------------------------

    /**
     * Puts on the theme that should be showing now and takes off what shouldn't, then sets the
     * alarm for the next date that can change it. Blocking IO; [evaluateAsync] runs it on the
     * engine's thread.
     */
    @WorkerThread
    fun evaluate(context: Context) {
        synchronized(evaluating) {
            val prefs = prefs(context) ?: return
            // A work profile shares its parent's screen, and the parent shows the theme
            if (context.getSystemService(UserManager::class.java)?.isProfile == true) return
            val today = LocalDate.now()
            val plan = try {
                planFor(context, prefs, today)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't work out the holiday theme", e)
                return
            }
            val applied = try {
                reconcile(context, prefs, plan)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't update the holiday theme", e)
                appliedParts(prefs)
            }
            try {
                updateNotification(context, prefs, plan, applied)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't update the holiday theme notification", e)
            }
            try {
                schedule(context, today)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't schedule the next holiday theme change", e)
            }
        }
    }

    /** Runs [evaluate] on the engine's thread, then [onDone] on the main thread. */
    fun evaluateAsync(context: Context, onDone: (() -> Unit)? = null) {
        runAsync(context, onDone) { evaluate(it) }
    }

    /**
     * The alarm at a holiday's start or end. Themes switch while the screen is off, so nothing
     * jumps while the phone is in use: with the screen on, a change waits 15 minutes and looks
     * again.
     */
    fun onAlarm(context: Context) {
        onAlarm(context, null)
    }

    internal fun onAlarm(context: Context, onDone: (() -> Unit)?) {
        runAsync(context, onDone) { app ->
            val interactive = app.getSystemService(PowerManager::class.java)?.isInteractive == true
            val prefs = prefs(app)
            if (interactive && prefs != null && wouldChange(app, prefs)) {
                setAlarm(app, System.currentTimeMillis() + SCREEN_ON_DELAY_MS)
            } else {
                evaluate(app)
            }
        }
    }

    private fun runAsync(context: Context, onDone: (() -> Unit)?, work: (Context) -> Unit) {
        val app = context.applicationContext ?: context
        try {
            executor.execute {
                try {
                    work(app)
                } catch (t: Throwable) {
                    Log.w(TAG, "Holiday themes failed", t)
                } finally {
                    if (onDone != null) mainHandler.post { onDone() }
                }
            }
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "Couldn't queue holiday themes work", e)
            if (onDone != null) mainHandler.post { onDone() }
        }
    }

    /** What should be showing: the trial, else the date's holiday unless off or skipped. */
    private fun planFor(context: Context, prefs: SharedPreferences, today: LocalDate): Plan? {
        val trial = trial(prefs)
        val dated = if (trial == null && prefs.getBoolean(KEY_AUTO, true)) {
            scheduled(today)?.takeIf { (holiday, window) -> !isSkipped(prefs, holiday, window) }
        } else {
            null
        }
        val holiday = trial ?: dated?.first ?: return null
        val parts = HolidayPart.entries.filter { part ->
            prefs.getBoolean(KEY_PART + part.name, true) &&
                (part != HolidayPart.WALLPAPER || wallpaperAllowed(context))
        }.toSet()
        // A theme with every part off is no theme
        return if (parts.isEmpty()) null else Plan(holiday, dated?.second, parts)
    }

    private fun wouldChange(context: Context, prefs: SharedPreferences): Boolean {
        val plan = planFor(context, prefs, LocalDate.now())
        return plan?.holiday != applied(prefs) || plan?.parts.orEmpty() != appliedParts(prefs)
    }

    /** Applies and restores part by part, persists what's on and returns it. */
    private fun reconcile(context: Context, prefs: SharedPreferences, plan: Plan?): Set<HolidayPart> {
        val target = plan?.holiday
        val wanted = plan?.parts.orEmpty()
        val before = appliedParts(prefs)
        // Going straight from one holiday to the next, the new one goes on over the old one:
        // the user's own wallpaper and colors stay saved from before the first.
        val switching = target != applied(prefs)
        val now = before.toMutableSet()

        // A part whose restore fails stays recorded as on, so the next evaluate tries again
        for (part in RESTORE_ORDER) {
            if (part in before && part !in wanted) {
                if (restorePart(context, part, prefs)) now -= part
            }
        }
        if (target != null) {
            for (part in APPLY_ORDER) {
                if (part !in wanted || (part in before && !switching)) continue
                if (applyPart(context, part, target, prefs)) {
                    now += part
                } else if (part in before) {
                    // Don't leave the last holiday's version showing
                    if (restorePart(context, part, prefs)) now -= part
                }
            }
        }

        val editor = prefs.edit()
        // While a part is still on (one that didn't come off), so is the holiday it shows
        val shown = target ?: applied(prefs)
        if (shown != null && now.isNotEmpty()) {
            editor.putString(KEY_APPLIED, shown.id)
        } else {
            editor.remove(KEY_APPLIED)
        }
        editor.putStringSet(KEY_APPLIED_PARTS, now.mapTo(HashSet()) { it.name })
        editor.commit()
        return if (target != null) now else emptySet()
    }

    /** Puts [part] of [holiday] on. False (or a throw, caught here) when it didn't go on. */
    private fun applyPart(
        context: Context,
        part: HolidayPart,
        holiday: Holiday,
        prefs: SharedPreferences,
    ): Boolean = try {
        when (part) {
            HolidayPart.COLORS -> HolidayMonet.apply(context, holiday, prefs)
            HolidayPart.WALLPAPER -> HolidayWallpaper.apply(context, holiday)
            HolidayPart.ICONS -> HolidayOverlay.apply(context, holiday)
            HolidayPart.BANNER -> Settings.Secure.putString(
                context.contentResolver,
                HolidayCatalog.KEY_BRAND_PALETTE,
                HolidayCatalog.brandPalette(holiday),
            )
            // Studio reads studioHoliday()
            HolidayPart.LOOK -> true
        }.also { applied ->
            if (!applied) Log.w(TAG, "Couldn't apply ${holiday.id}'s ${part.name}")
        }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't apply ${holiday.id}'s ${part.name}", e)
        false
    }

    /** Takes [part] off. False (or a throw, caught here) when it's still on. */
    private fun restorePart(
        context: Context,
        part: HolidayPart,
        prefs: SharedPreferences,
    ): Boolean = try {
        when (part) {
            HolidayPart.COLORS -> HolidayMonet.restore(context, prefs)
            HolidayPart.WALLPAPER -> HolidayWallpaper.restore(context)
            HolidayPart.ICONS -> HolidayOverlay.remove(context)
            HolidayPart.BANNER -> Settings.Secure.putString(
                context.contentResolver, HolidayCatalog.KEY_BRAND_PALETTE, null
            )
            HolidayPart.LOOK -> true
        }.also { restored ->
            if (!restored) Log.w(TAG, "Couldn't restore ${part.name}")
        }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't restore ${part.name}", e)
        false
    }

    private fun wallpaperAllowed(context: Context): Boolean = try {
        HolidayWallpaper.isAllowed(context)
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't check whether the wallpaper can be set", e)
        false
    }

    /** Posts "theme is on" once per holiday window by date; cancels it when that's not showing. */
    private fun updateNotification(
        context: Context,
        prefs: SharedPreferences,
        plan: Plan?,
        applied: Set<HolidayPart>,
    ) {
        val window = plan?.window
        if (plan == null || window == null || applied.isEmpty()) {
            HolidayNotifications.cancel(context)
            return
        }
        val key = skipKey(plan.holiday, window)
        if (prefs.getString(KEY_NOTIFIED, null) != key) {
            HolidayNotifications.showOn(context, plan.holiday, window)
            prefs.edit().putString(KEY_NOTIFIED, key).commit()
        }
    }

    // -------------------------------------------------------------------------
    // Scheduling
    // -------------------------------------------------------------------------

    /** Wakes the engine at the start of the next day a theme can start or end. */
    private fun schedule(context: Context, today: LocalDate) {
        val next = HolidayCatalog.nextChange(today)
        setAlarm(context, next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    private fun setAlarm(context: Context, atMillis: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, HolidayActionReceiver::class.java)
                .setAction(HolidayActionReceiver.ACTION_ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** The engine's state, or null while the user's storage is still locked. */
    private fun prefs(context: Context): SharedPreferences? {
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked != true) return null
        return try {
            context.createCredentialProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.w(TAG, "Holiday theme state isn't available", e)
            null
        }
    }

    private fun trial(prefs: SharedPreferences): Holiday? =
        HolidayCatalog.byId(prefs.getString(KEY_TRIAL, null))

    private fun applied(prefs: SharedPreferences): Holiday? =
        HolidayCatalog.byId(prefs.getString(KEY_APPLIED, null))

    private fun appliedParts(prefs: SharedPreferences): Set<HolidayPart> =
        prefs.getStringSet(KEY_APPLIED_PARTS, null).orEmpty()
            .mapNotNullTo(HashSet()) { name -> HolidayPart.entries.firstOrNull { it.name == name } }

    private fun skipped(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_SKIPPED, null).orEmpty().toSet()

    private fun isSkipped(prefs: SharedPreferences, holiday: Holiday, window: DateWindow) =
        skipKey(holiday, window) in skipped(prefs)

    /** "<id>:<window start>", e.g. "halloween:2026-10-24". */
    internal fun skipKey(holiday: Holiday, window: DateWindow) = "${holiday.id}:${window.start}"
}
