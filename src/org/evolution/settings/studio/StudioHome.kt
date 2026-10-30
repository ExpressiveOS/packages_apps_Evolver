/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.studio

import android.app.WallpaperManager
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceFragmentCompat
import com.android.settings.R
import com.android.settingslib.widget.LayoutPreference
import org.evolution.settings.fragments.miscellaneous.ExpressiveTheme
import org.evolution.settings.holiday.HolidayStatus
import org.evolution.settings.holiday.HolidayThemes

/**
 * Expressive Studio's home, the Bento Bloom layout: a live lock screen beside the look-and-feel
 * tiles, the holiday themes, clock and what's-new cards, then a grid of the other sections. Each
 * tile opens the page of one of the screen's rows, which the home replaces on screen but keeps for
 * search. While a holiday theme has Studio in its colours, the tiles are drawn in them.
 */
object StudioHome {

    const val KEY = "studio_home"

    /** Draws the home into [preference], a LayoutPreference of R.layout.studio_compose. */
    @JvmStatic
    fun bind(fragment: PreferenceFragmentCompat, preference: LayoutPreference?) {
        val view = preference?.findViewById<ComposeView>(R.id.studio_compose) ?: return
        val actions = StudioActions(fragment)
        StudioPalette.holiday = HolidayThemes.studioHoliday(fragment.requireContext())
        view.setContent {
            ExpressiveTheme {
                HomeScreen(actions)
            }
        }
    }
}

// The screen's rows the tiles open (res/xml/evolution_settings.xml)
private const val KEY_LOCK_SCREEN = "lock_screen"
private const val KEY_THEMES = "themes"
private const val KEY_COLORS = "studio_colors"
private const val KEY_HOLIDAY_THEMES = "studio_holiday_themes"
private const val KEY_WALLPAPER_STYLES = "studio_wallpaper_styles"
private const val KEY_CLOCK_STYLES = "studio_clock_styles"
private const val KEY_CHANGELOG = "studio_changelog"

private class Section(
    val key: String,
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val petal: Petal,
    val tinted: Boolean = true,
)

private val sections = listOf(
    Section("status_bar", R.string.status_bar_title, R.drawable.ic_status_bar, Petal.Blue),
    Section("quick_settings", R.string.quick_settings_title, R.drawable.ic_quick_settings, Petal.Blue),
    Section("notifications", R.string.notifications_title, R.drawable.ic_notifications, Petal.Blue),
    Section("power_menu", R.string.power_menu_title, R.drawable.ic_power_menu, Petal.Coral),
    Section("miscellaneous", R.string.miscellaneous_title, R.drawable.ic_miscellaneous, Petal.Green),
    Section("about", R.string.about_title, R.drawable.studio_bloom_mark, Petal.Green, tinted = false),
)

private val gap = 12.dp

@Composable
private fun HomeScreen(actions: StudioActions) {
    // The bento's fixed heights grow with the font size, up to half again
    val grow = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    val hasPicker = remember { actions.hasWallpaperPicker }
    FollowStudioPalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp * grow),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            LockTile(
                onClick = { actions.open(KEY_LOCK_SCREEN) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                val tile = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                ThemesTile(onClick = { actions.open(KEY_THEMES) }, modifier = tile)
                if (hasPicker) {
                    WallpaperTile(
                        title = remember { actions.wallpaperTitle },
                        onClick = { actions.openWallpaperPicker() },
                        modifier = tile,
                    )
                } else {
                    WallpaperTile(
                        title = stringResource(R.string.wallpaper_styles_title),
                        onClick = { actions.open(KEY_WALLPAPER_STYLES) },
                        modifier = tile,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp * grow),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            val tile = Modifier
                .weight(1f)
                .fillMaxHeight()
            ColorsTile(onClick = { actions.open(KEY_COLORS) }, modifier = tile)
            FontsTile(onClick = { actions.open(KEY_THEMES) }, modifier = tile)
        }
        HolidayThemesCard(onClick = { actions.open(KEY_HOLIDAY_THEMES) })
        ClockStylesCard(onClick = { actions.open(KEY_CLOCK_STYLES) })
        WhatsNewCard(onClick = { actions.open(KEY_CHANGELOG) })
        sections.chunked(3).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                row.forEach { section ->
                    SectionTile(
                        section = section,
                        onClick = { actions.open(section.key) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** A tile's name and what it holds, at the foot of the tile. */
@Composable
private fun TileText(title: String, summary: String, petal: Petal) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = petal.ink,
            fontFamily = BloomRounded,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 1.25.em,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The content of a bento tile: a picture at the top, its text at the foot. The text keeps its
 * room when it wraps (a narrow phone, large text) and the picture gets what's left.
 */
@Composable
private fun TileBody(picture: @Composable () -> Unit, text: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.weight(1f, fill = false)) { picture() }
        text()
    }
}

@Composable
private fun LockTile(onClick: () -> Unit, modifier: Modifier) {
    BloomCard(onClick = onClick, modifier = modifier, color = Petal.ground) {
        LockScreenPreview(modifier = Modifier.fillMaxSize(), showLockIcon = false) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                shape = CircleShape,
                color = Color(0xCC07070D),
            ) {
                Text(
                    text = stringResource(R.string.lock_screen_title),
                    color = Petal.Coral.onScrim,
                    fontFamily = BloomRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Overlapping dots, each ringed in [ring] so they read as separate. */
@Composable
private fun Dots(colors: List<Color>, size: Dp, step: Dp, ring: Color) {
    Box(Modifier.size(width = size + step * (colors.size - 1), height = size)) {
        colors.forEachIndexed { i, color ->
            Box(
                Modifier
                    .offset(x = step * i)
                    .size(size)
                    .border(2.dp, ring, CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun ThemesTile(onClick: () -> Unit, modifier: Modifier) {
    BloomCard(onClick = onClick, modifier = modifier) {
        TileBody(
            picture = {
                BoxWithConstraints {
                    // Closer together when the tile is narrow
                    val petals = Petal.entries.map { it.mid }
                    Dots(
                        colors = petals,
                        size = 40.dp,
                        step = minOf(28.dp, (maxWidth - 40.dp) / (petals.size - 1))
                            .coerceAtLeast(0.dp),
                        ring = MaterialTheme.colorScheme.surfaceBright,
                    )
                }
            },
            text = {
                TileText(
                    title = stringResource(R.string.themes_title),
                    summary = stringResource(R.string.themes_summary),
                    petal = Petal.Violet,
                )
            },
        )
    }
}

@Composable
private fun WallpaperTile(title: String, onClick: () -> Unit, modifier: Modifier) {
    BloomCard(onClick = onClick, modifier = modifier) {
        TileBody(
            picture = {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .border(2.dp, Petal.sweep, BloomShapes.thumb)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Petal.ground)
                ) {
                    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                    val resumes = rememberResumeCount()
                    rememberWallpaper(WallpaperManager.FLAG_SYSTEM, widthPx, resumes)?.image?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            },
            text = {
                TileText(
                    title = title,
                    summary = stringResource(R.string.studio_wallpaper_summary),
                    petal = Petal.Violet,
                )
            },
        )
    }
}

@Composable
private fun ColorsTile(onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val palettes = listOf(
        listOf(scheme.primary, scheme.primaryContainer, scheme.onPrimaryContainer),
        listOf(scheme.secondary, scheme.secondaryContainer, scheme.onSecondaryContainer),
        listOf(scheme.tertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer),
        // The neutrals that stand out from the card in both themes
        listOf(scheme.outline, scheme.onSurfaceVariant, scheme.inverseSurface),
    )
    BloomCard(onClick = onClick, modifier = modifier) {
        TileBody(
            picture = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    palettes.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach {
                                Dots(it, size = 26.dp, step = 18.dp, ring = scheme.surfaceBright)
                            }
                        }
                    }
                }
            },
            text = {
                TileText(
                    title = stringResource(R.string.studio_colors_title),
                    summary = stringResource(R.string.studio_colors_summary),
                    petal = Petal.Violet,
                )
            },
        )
    }
}

@Composable
private fun FontsTile(onClick: () -> Unit, modifier: Modifier) {
    // In the theme's own type, which follows the font picked in Themes
    val type = MaterialTheme.typography
    BloomCard(onClick = onClick, modifier = modifier) {
        TileBody(
            picture = {
                // A sample, not words: the tile reads as its title and summary
                Column(Modifier.clearAndSetSemantics {}) {
                    Text(
                        text = stringResource(R.string.studio_fonts_specimen_name),
                        style = type.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.studio_fonts_specimen_letters),
                        style = type.titleMedium,
                        color = Petal.Violet.ink,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.studio_fonts_specimen_digits),
                        style = type.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            },
            text = {
                TileText(
                    title = stringResource(R.string.studio_fonts_title),
                    summary = stringResource(R.string.studio_fonts_summary),
                    petal = Petal.Violet,
                )
            },
        )
    }
}

/** A full-width card: its text, then [trailing]. */
@Composable
private fun WideCard(
    title: String,
    summary: String,
    petal: Petal,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    BloomCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp),
    ) {
        // Centred in the card when the text is shorter than its least height
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = petal.ink,
                    fontFamily = BloomRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 1.25.em,
                )
                Text(
                    text = summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

@Composable
private fun ClockStylesCard(onClick: () -> Unit) {
    val resumes = rememberResumeCount()
    val style = rememberClockStyle(resumes)
    WideCard(
        title = stringResource(R.string.ls_clock_title),
        summary = stringResource(R.string.studio_clock_using, clockName(style)),
        petal = Petal.Coral,
        onClick = onClick,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .size(width = 60.dp, height = 72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Petal.ground),
            contentAlignment = Alignment.Center,
        ) {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            rememberWallpaper(WallpaperManager.FLAG_LOCK, widthPx, resumes)?.image?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
                // Keeps the time readable on any wallpaper
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                )
            }
            val context = LocalContext.current
            val now by rememberMinute()
            Text(
                text = DateFormat.format(
                    if (DateFormat.is24HourFormat(context)) "HH\nmm" else "hh\nmm", now
                ).toString(),
                color = Petal.Coral.light,
                fontFamily = BloomRounded,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 0.92.em,
                textAlign = TextAlign.Center,
                // The card's summary names the face; the time here is only its picture
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun HolidayThemesCard(onClick: () -> Unit) {
    val context = LocalContext.current
    val resumes = rememberResumeCount()
    val status = remember(resumes) { HolidayStatus.read(context) }
    WideCard(
        title = stringResource(R.string.holiday_themes_title),
        summary = status.text,
        petal = Petal.Coral,
        onClick = onClick,
    ) {
        // The colours of the theme that's on, or of the next one
        status.holiday?.let { holiday ->
            Dots(
                colors = holiday.colors.petals.map { Color(it.mid) },
                size = 28.dp,
                step = 20.dp,
                ring = MaterialTheme.colorScheme.surfaceBright,
            )
        }
    }
}

@Composable
private fun WhatsNewCard(onClick: () -> Unit) {
    WideCard(
        title = stringResource(R.string.studio_whats_new_title),
        summary = stringResource(R.string.changelog_summary),
        petal = Petal.Green,
        onClick = onClick,
    ) {
        PetalChip(Petal.Green, size = 52.dp) {
            Icon(
                painter = painterResource(R.drawable.ic_changelog),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun SectionTile(section: Section, onClick: () -> Unit, modifier: Modifier) {
    BloomCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = 108.dp),
        shape = BloomShapes.gridTile,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            PetalChip(section.petal) {
                Icon(
                    painter = painterResource(section.icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (section.tinted) section.petal.ink else Color.Unspecified,
                )
            }
            Text(
                text = stringResource(section.title),
                color = section.petal.ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 1.25.em,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
