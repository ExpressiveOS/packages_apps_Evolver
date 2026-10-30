/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.holiday

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.android.settings.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * One petal of a holiday's palette, standing in for one of the Bloom mark's petals. [light], [mid]
 * and [deep] are the petal's own colours, as the Bloom petals have. The ink/tile pairs are what
 * Expressive Studio draws its tiles in (dark theme, then light theme), each at 4.8:1 or more. The
 * home colours replace one of Settings' homepage icon colour families: [homeFg] on [homeBgLight]
 * in the light theme, on [homeBgDark] in the dark one.
 */
class HolidayPetal(
    light: Long,
    mid: Long,
    deep: Long,
    darkInk: Long,
    darkTile: Long,
    lightInk: Long,
    lightTile: Long,
    homeFg: Long,
    homeBgLight: Long,
    homeBgDark: Long,
) {
    val light = light.toInt()
    val mid = mid.toInt()
    val deep = deep.toInt()
    val darkInk = darkInk.toInt()
    val darkTile = darkTile.toInt()
    val lightInk = lightInk.toInt()
    val lightTile = lightTile.toInt()
    val homeFg = homeFg.toInt()
    val homeBgLight = homeBgLight.toInt()
    val homeBgDark = homeBgDark.toInt()
}

/**
 * A holiday's colours: the Material You seed (and a second seed where two colours belong
 * together) with the theme style SystemUI builds the system palette in, and four petals in the
 * order of the Bloom petals they stand in for (violet, blue, coral, green: Petal's order).
 */
class HolidayColors(
    seed: Long,
    secondarySeed: Long?,
    /** A ThemeStyle name, as Expressive Studio's Colors page writes it. */
    val style: String,
    val petals: List<HolidayPetal>,
) {
    val seed = seed.toInt()
    val secondarySeed = secondarySeed?.toInt()
}

/** A run of days, both ends included. */
data class DateWindow(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/** The holidays ExpressiveOS has a theme for, in calendar order from November. */
enum class Holiday(
    /** Stable id: stored in settings and in the brand palette string. */
    val id: String,
    @StringRes val title: Int,
    @RawRes val wallpaper: Int,
    val colors: HolidayColors,
) {
    /** The week of Thanksgiving, Monday to Sunday (US, the fourth Thursday of November). */
    THANKSGIVING(
        id = "thanksgiving",
        title = R.string.holiday_thanksgiving,
        wallpaper = R.raw.holiday_wallpaper_thanksgiving,
        colors = HolidayColors(
            seed = 0xFFB5651D,
            secondarySeed = 0xFF8E2A3B,
            style = "VIBRANT",
            petals = listOf(
                HolidayPetal(0xFFFFE2B5, 0xFFF2A541, 0xFF9A5B00, 0xFFFFC076, 0xFF422600, 0xFF764800, 0xFFFFE4C8, 0xFF663E00, 0xFFFFDDB9, 0xFFFFB963), // amber
                HolidayPetal(0xFFFFD2BC, 0xFFE07B39, 0xFFA8441C, 0xFFFFBE99, 0xFF4C1F00, 0xFF883D00, 0xFFFFE3D4, 0xFF763400, 0xFFFFDBC9, 0xFFFFB68C), // rust
                HolidayPetal(0xFFFFC7C9, 0xFFD6455B, 0xFF8E2A2B, 0xFFFFBABE, 0xFF5F0019, 0xFFA01B37, 0xFFFFE1E2, 0xFF8F092C, 0xFFFFDADB, 0xFFFFB2B7), // cranberry
                HolidayPetal(0xFFFFF0C2, 0xFFE9C46A, 0xFF7A5C00, 0xFFEDC86D, 0xFF392A00, 0xFF684F00, 0xFFFFE6AF, 0xFF5A4400, 0xFFFFDF96, 0xFFE7C268), // harvest
            ),
        ),
    ),
    /** December 13 to 26. */
    CHRISTMAS(
        id = "christmas",
        title = R.string.holiday_christmas,
        wallpaper = R.raw.holiday_wallpaper_christmas,
        colors = HolidayColors(
            seed = 0xFFC62828,
            secondarySeed = 0xFF1B7F4D,
            style = "VIBRANT",
            petals = listOf(
                HolidayPetal(0xFFFFC7C7, 0xFFFF5252, 0xFFC62828, 0xFFFFBBB6, 0xFF60000A, 0xFFA70A1C, 0xFFFFE1DF, 0xFF930015, 0xFFFFDAD7, 0xFFFFB3AE), // red
                HolidayPetal(0xFFBDF2D2, 0xFF3DDC84, 0xFF1B7F4D, 0xFF4BE78D, 0xFF003419, 0xFF005F33, 0xFF90FFB4, 0xFF00522B, 0xFF66FEA2, 0xFF43E188), // evergreen
                HolidayPetal(0xFFFFEBB0, 0xFFF4C95D, 0xFF9A6A00, 0xFFF2C75B, 0xFF392A00, 0xFF684F00, 0xFFFFE6AF, 0xFF5A4400, 0xFFFFDF97, 0xFFECC156), // gold
                HolidayPetal(0xFFF4F9FF, 0xFFDDEBFF, 0xFF2E4A62, 0xFFADCFF7, 0xFF1B2D41, 0xFF465464, 0xFFDBE9FD, 0xFF314962, 0xFFD0E4FF, 0xFFB0C9E7), // snow
            ),
        ),
    ),
    /** December 27 to January 2. */
    NEW_YEAR(
        id = "newyear",
        title = R.string.holiday_newyear,
        wallpaper = R.raw.holiday_wallpaper_newyear,
        colors = HolidayColors(
            seed = 0xFFC9A227,
            secondarySeed = 0xFF2A3FA8,
            style = "VIBRANT",
            petals = listOf(
                HolidayPetal(0xFFFFE7A3, 0xFFF2C25A, 0xFF9A6A00, 0xFFF5C55D, 0xFF3B2900, 0xFF6A4E00, 0xFFFFE5B6, 0xFF5C4300, 0xFFFFDEA0, 0xFFEFC058), // gold
                HolidayPetal(0xFFEEF2FA, 0xFFC9D3E6, 0xFF4A5873, 0xFFB2CEF9, 0xFF1E2D41, 0xFF495363, 0xFFDFE9FC, 0xFF344863, 0xFFD3E3FF, 0xFFB3C8E9), // silver
                HolidayPetal(0xFFD6E2FF, 0xFF8FB2FD, 0xFF2A3FA8, 0xFFB6CCFF, 0xFF002A62, 0xFF2A5195, 0xFFE0E8FF, 0xFF1B4588, 0xFFD8E2FF, 0xFFAEC6FF), // ice
                HolidayPetal(0xFFFFF4D6, 0xFFF7DDA0, 0xFF7A5A12, 0xFFE6C982, 0xFF382A00, 0xFF625021, 0xFFFFE6AC, 0xFF564516, 0xFFFAE0A3, 0xFFDDC489), // champagne
            ),
        ),
    ),
    /** February 8 to 14. */
    VALENTINES(
        id = "valentines",
        title = R.string.holiday_valentines,
        wallpaper = R.raw.holiday_wallpaper_valentines,
        colors = HolidayColors(
            seed = 0xFFE0457B,
            secondarySeed = 0xFF7B1FA2,
            style = "VIBRANT",
            petals = listOf(
                HolidayPetal(0xFFFFC1D3, 0xFFFF5C8A, 0xFFC2185B, 0xFFFFBAC6, 0xFF5E0025, 0xFFA20E48, 0xFFFFE1E5, 0xFF90003D, 0xFFFFD9DF, 0xFFFFB1C0), // rose
                HolidayPetal(0xFFFFB3B8, 0xFFFF3B4E, 0xFFB71C2C, 0xFFFFBBB9, 0xFF600010, 0xFFA80024, 0xFFFFE1E0, 0xFF92001E, 0xFFFFDAD8, 0xFFFFB3B2), // red
                HolidayPetal(0xFFFFD6EA, 0xFFFF9CC9, 0xFFAD1457, 0xFFFFB8D6, 0xFF550E37, 0xFF85365F, 0xFFFFE0EB, 0xFF772B53, 0xFFFFD8E6, 0xFFFFAFD2), // pink
                HolidayPetal(0xFFE8D5FF, 0xFFC79BFF, 0xFF7B1FA2, 0xFFDDC0FF, 0xFF3E0E70, 0xFF663D9A, 0xFFF2E2FF, 0xFF5A308D, 0xFFEEDCFF, 0xFFD9B9FF), // lavender
            ),
        ),
    ),
    /** March 14 to 17. */
    ST_PATRICKS(
        id = "stpatricks",
        title = R.string.holiday_stpatricks,
        wallpaper = R.raw.holiday_wallpaper_stpatricks,
        colors = HolidayColors(
            seed = 0xFF1E9E5A,
            secondarySeed = 0xFFC99A1E,
            style = "TONAL_SPOT",
            petals = listOf(
                HolidayPetal(0xFFB9F5CF, 0xFF3CCB7F, 0xFF0B7A45, 0xFF5BE596, 0xFF00341A, 0xFF005F35, 0xFF8EFFB7, 0xFF00522D, 0xFF73FCAB, 0xFF54DF91), // shamrock
                HolidayPetal(0xFF9EE6BE, 0xFF1FB86A, 0xFF0F5132, 0xFF5BE591, 0xFF003419, 0xFF005F33, 0xFF90FFB4, 0xFF00522B, 0xFF74FCA6, 0xFF55DF8C), // emerald
                HolidayPetal(0xFFFFE9A8, 0xFFF2C14E, 0xFF8A6400, 0xFFF7C552, 0xFF3A2900, 0xFF6A4E00, 0xFFFFE5B4, 0xFF5B4300, 0xFFFFDF9D, 0xFFF0C04D), // gold
                HolidayPetal(0xFFDFF7C8, 0xFF9BE07A, 0xFF3F7A1E, 0xFF99DE78, 0xFF0D3400, 0xFF215F04, 0xFFB4FB91, 0xFF195200, 0xFFAFF58C, 0xFF94D873), // clover
            ),
        ),
    ),
    /** Good Friday to Easter Monday. */
    EASTER(
        id = "easter",
        title = R.string.holiday_easter,
        wallpaper = R.raw.holiday_wallpaper_easter,
        colors = HolidayColors(
            seed = 0xFFA07BEA,
            secondarySeed = 0xFF5CC09C,
            style = "TONAL_SPOT",
            petals = listOf(
                HolidayPetal(0xFFEBDDFF, 0xFFC9A7F5, 0xFF6B3FB8, 0xFFDCC0FF, 0xFF391B60, 0xFF62448A, 0xFFF2E2FF, 0xFF55387D, 0xFFEDDCFF, 0xFFD8B9FF), // lilac
                HolidayPetal(0xFFD2F5E6, 0xFF8FE0BE, 0xFF12805C, 0xFF8BDCBA, 0xFF003324, 0xFF005E45, 0xFFA7F9D6, 0xFF00513B, 0xFFA1F3D0, 0xFF86D6B5), // mint
                HolidayPetal(0xFFFFF2BF, 0xFFFFE07A, 0xFF8A6A00, 0xFFE8CA67, 0xFF362B00, 0xFF635100, 0xFFFFE79D, 0xFF564500, 0xFFFFE07D, 0xFFE2C462), // butter
                HolidayPetal(0xFFD6ECFF, 0xFF93C8F7, 0xFF1E5FA8, 0xFF9FD1FF, 0xFF002F4A, 0xFF185780, 0xFFD7EAFF, 0xFF004B73, 0xFFCCE5FF, 0xFF97CCFB), // sky
            ),
        ),
    ),
    /** July 1 to 5. */
    JULY_4(
        id = "july4",
        title = R.string.holiday_july4,
        wallpaper = R.raw.holiday_wallpaper_july4,
        colors = HolidayColors(
            seed = 0xFF2F5BD3,
            secondarySeed = 0xFFC1121F,
            style = "VIBRANT",
            petals = listOf(
                HolidayPetal(0xFFFFC9CD, 0xFFFF4D5A, 0xFFC1121F, 0xFFFFBBBA, 0xFF600011, 0xFFA80025, 0xFFFFE1E0, 0xFF92001F, 0xFFFFDAD9, 0xFFFFB3B2), // red
                HolidayPetal(0xFFFFFFFF, 0xFFF4F6FB, 0xFF4A5568, 0xFFA9D0F5, 0xFF182E3F, 0xFF4F5357, 0xFFE6E8ED, 0xFF2D4960, 0xFFCCE5FF, 0xFFADCAE6), // white
                HolidayPetal(0xFFC9D8FF, 0xFF4F7DFF, 0xFF1D4ED8, 0xFFBCCAFF, 0xFF002571, 0xFF0047C5, 0xFFE3E7FF, 0xFF003DAB, 0xFFDBE1FF, 0xFFB5C4FF), // blue
                HolidayPetal(0xFFDCE7FF, 0xFF9FC0FF, 0xFF1D3557, 0xFFB4CDFF, 0xFF002B5D, 0xFF2F528A, 0xFFDFE8FF, 0xFF22467D, 0xFFD7E3FF, 0xFFABC7FF), // sky
            ),
        ),
    ),
    /** October 24 to 31. */
    HALLOWEEN(
        id = "halloween",
        title = R.string.holiday_halloween,
        wallpaper = R.raw.holiday_wallpaper_halloween,
        colors = HolidayColors(
            seed = 0xFFE8650A,
            secondarySeed = 0xFF6B21A8,
            style = "VIBRANT",
            petals = listOf(
                HolidayPetal(0xFFFFD8B0, 0xFFFF8A1F, 0xFFC2410C, 0xFFFFBE91, 0xFF492100, 0xFF824100, 0xFFFFE3D2, 0xFF713700, 0xFFFFDCC5, 0xFFFFB783), // pumpkin
                HolidayPetal(0xFFE5CCFF, 0xFFB26BFF, 0xFF6B21A8, 0xFFDFBFFF, 0xFF420078, 0xFF7224BD, 0xFFF3E2FF, 0xFF650DB1, 0xFFEFDBFF, 0xFFDBB8FF), // witch
                HolidayPetal(0xFFFFE7B8, 0xFFFFB347, 0xFFB45309, 0xFFFFC06F, 0xFF402700, 0xFF744900, 0xFFFFE4C6, 0xFF643F00, 0xFFFFDDB6, 0xFFFFB95A), // candle
                HolidayPetal(0xFFD4F7C2, 0xFF7CE35A, 0xFF3F7A1E, 0xFF7CE45A, 0xFF093400, 0xFF176000, 0xFFA2FF80, 0xFF135200, 0xFF92FB6F, 0xFF77DE55), // slime
            ),
        ),
    );

    /** The days this holiday's theme is on for the holiday that falls in [year], inclusive. */
    fun window(year: Int): DateWindow = when (this) {
        THANKSGIVING -> {
            val thursday = LocalDate.of(year, 11, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY))
            DateWindow(thursday.minusDays(3), thursday.plusDays(3))
        }
        CHRISTMAS -> DateWindow(LocalDate.of(year, 12, 13), LocalDate.of(year, 12, 26))
        NEW_YEAR -> DateWindow(LocalDate.of(year, 12, 27), LocalDate.of(year + 1, 1, 2))
        VALENTINES -> DateWindow(LocalDate.of(year, 2, 8), LocalDate.of(year, 2, 14))
        ST_PATRICKS -> DateWindow(LocalDate.of(year, 3, 14), LocalDate.of(year, 3, 17))
        EASTER -> {
            val sunday = easterSunday(year)
            DateWindow(sunday.minusDays(2), sunday.plusDays(1))
        }
        JULY_4 -> DateWindow(LocalDate.of(year, 7, 1), LocalDate.of(year, 7, 5))
        HALLOWEEN -> DateWindow(LocalDate.of(year, 10, 24), LocalDate.of(year, 10, 31))
    }

    /** The window that holds [date], if [date] is in one. */
    fun windowContaining(date: LocalDate): DateWindow? =
        // New Year's starts in one year and ends in the next
        listOf(date.year - 1, date.year).map { window(it) }.firstOrNull { date in it }

    companion object {
        /** Easter Sunday in the Gregorian calendar (the anonymous Gregorian algorithm). */
        fun easterSunday(year: Int): LocalDate {
            val a = year % 19
            val b = year / 100
            val c = year % 100
            val d = b / 4
            val e = b % 4
            val f = (b + 8) / 25
            val g = (b - f + 1) / 3
            val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4
            val k = c % 4
            val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val month = (h + l - 7 * m + 114) / 31
            val day = (h + l - 7 * m + 114) % 31 + 1
            return LocalDate.of(year, month, day)
        }
    }
}

/** Finding holidays by date, and the brand palette the other apps read. */
object HolidayCatalog {

    /**
     * Settings.Secure key holding the active theme's banner colours, read by Settings' GlowView
     * and the Updater's wordmark. Empty or unset means the Bloom colours. See [brandPalette].
     */
    const val KEY_BRAND_PALETTE = "expressiveos_holiday_palette"

    fun byId(id: String?): Holiday? = Holiday.entries.firstOrNull { it.id == id }

    /** The holiday whose theme is on on [date]; when two overlap, the shorter one. */
    fun activeOn(date: LocalDate): Holiday? = Holiday.entries
        .mapNotNull { holiday -> holiday.windowContaining(date)?.let { holiday to it } }
        .minByOrNull { (_, window) -> window.end.toEpochDay() - window.start.toEpochDay() }
        ?.first

    /** The next holiday to start after [date], with its window. */
    fun next(after: LocalDate): Pair<Holiday, DateWindow>? = Holiday.entries
        .flatMap { holiday -> listOf(after.year, after.year + 1).map { holiday to holiday.window(it) } }
        .filter { (_, window) -> window.start.isAfter(after) }
        .minByOrNull { (_, window) -> window.start.toEpochDay() }

    /** The first day after [date] on which [activeOn] can give a different answer. */
    fun nextChange(after: LocalDate): LocalDate = Holiday.entries
        .flatMap { holiday ->
            (after.year - 1..after.year + 1).flatMap { year ->
                val window = holiday.window(year)
                listOf(window.start, window.end.plusDays(1))
            }
        }
        .filter { it.isAfter(after) }
        .minBy { it.toEpochDay() }

    /**
     * The [KEY_BRAND_PALETTE] value for [holiday]: its id, then the colours as RRGGBB hex, the
     * groups split by ';' and the colours by ','. Mids for the wordmark's sweep on dark surfaces,
     * deeps for light ones (petal order), then the glow's left and right fringe on dark (the
     * second and first petals' lights) and on light (their deeps), as GlowView's Bloom colours do.
     * For example "halloween;FF8A1F,B26BFF,FFB347,7CE35A;C2410C,...;E5CCFF,FFD8B0;6B21A8,C2410C".
     */
    fun brandPalette(holiday: Holiday): String {
        val petals = holiday.colors.petals
        fun hex(color: Int) = String.format("%06X", 0xFFFFFF and color)
        return listOf(
            holiday.id,
            petals.joinToString(",") { hex(it.mid) },
            petals.joinToString(",") { hex(it.deep) },
            listOf(petals[1].light, petals[0].light).joinToString(",") { hex(it) },
            listOf(petals[1].deep, petals[0].deep).joinToString(",") { hex(it) },
        ).joinToString(";")
    }
}
