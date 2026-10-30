/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.settings.R
import org.evolution.settings.fragments.miscellaneous.insetFocusRing
import org.evolution.settings.holiday.Holiday
import org.evolution.settings.holiday.HolidayPetal
import org.evolution.settings.holiday.HolidayThemes

/**
 * The four petals of ExpressiveOS's Bloom mark, the colours Expressive Studio is drawn in.
 * [light], [mid] and [deep] are the petal's own colours. [ink] (text and icons) and [tile]
 * (the squircle behind an icon) follow the theme: light ink on a deep tile in dark mode, deep ink
 * on a pale tile in light mode, each pair at 4.8:1 contrast or more. While a holiday theme has
 * Studio in its colours ([StudioPalette]), each petal takes those of the holiday's petal in its
 * place.
 */
enum class Petal(
    private val bloomLight: Color,
    private val bloomMid: Color,
    private val bloomDeep: Color,
    private val bloomDarkInk: Color,
    private val bloomDarkTile: Color,
    private val bloomLightInk: Color,
    private val bloomLightTile: Color,
) {
    /** Look and feel: themes, colours, fonts, wallpapers. */
    Violet(
        Color(0xFFF3B1FC), Color(0xFFAE69FC), Color(0xFF6B2BBB),
        Color(0xFFC9A6FD), Color(0xFF2A1D47), Color(0xFF6B2BBB), Color(0xFFEDDCFF),
    ),

    /** System UI: status bar, quick settings, notifications. */
    Blue(
        Color(0xFF76BCFB), Color(0xFF417BFC), Color(0xFF1842D1),
        Color(0xFF8FB2FD), Color(0xFF142553), Color(0xFF1842D1), Color(0xFFDBE4FF),
    ),

    /** Lock screen, clocks and the power menu. */
    Coral(
        Color(0xFFFDCF8E), Color(0xFFFD7771), Color(0xFFD33952),
        Color(0xFFFFA39D), Color(0xFF3D1A24), Color(0xFFB42A45), Color(0xFFFFDAD7),
    ),

    /** The device itself: miscellaneous, about, what's new. */
    Green(
        Color(0xFFACEFB3), Color(0xFF4AD29D), Color(0xFF18A07C),
        Color(0xFF7FE0B7), Color(0xFF10322A), Color(0xFF0A6B53), Color(0xFFC4F1DA),
    );

    /** The holiday's petal standing in for this one, in the same place. */
    private val holiday: HolidayPetal?
        get() = StudioPalette.holiday?.colors?.petals?.getOrNull(ordinal)

    val light: Color
        get() = holiday?.let { Color(it.light) } ?: bloomLight

    val mid: Color
        get() = holiday?.let { Color(it.mid) } ?: bloomMid

    val deep: Color
        get() = holiday?.let { Color(it.deep) } ?: bloomDeep

    private val darkInk: Color
        get() = holiday?.let { Color(it.darkInk) } ?: bloomDarkInk

    private val darkTile: Color
        get() = holiday?.let { Color(it.darkTile) } ?: bloomDarkTile

    private val lightInk: Color
        get() = holiday?.let { Color(it.lightInk) } ?: bloomLightInk

    private val lightTile: Color
        get() = holiday?.let { Color(it.lightTile) } ?: bloomLightTile

    val ink: Color
        @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) darkInk else lightInk

    val tile: Color
        @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) darkTile else lightTile

    /** Ink for text on the dark scrim over a wallpaper, in either theme. */
    val onScrim: Color
        get() = darkInk

    companion object {
        /** The mark's petals clockwise from the top right, as the logo draws them. */
        val mark = listOf(Blue, Green, Coral, Violet)

        /** The deep navy the mark sits on. */
        val ground = Color(0xFF141329)

        /** The mark's mids in a sweep, for rings and highlights. */
        val sweep: Brush
            get() = Brush.linearGradient(listOf(Violet.mid, Blue.mid, Green.mid, Coral.mid))
    }
}

/**
 * The holiday Expressive Studio is drawn in ([HolidayThemes.studioHoliday]), or null for Bloom's
 * own colours. Studio's screens set it as they're drawn and again each time they resume
 * ([FollowStudioPalette]). It's Compose state, so the tiles redraw when it changes.
 */
object StudioPalette {
    var holiday: Holiday? by mutableStateOf<Holiday?>(null)
}

/**
 * Reads [StudioPalette.holiday] again each time the screen resumes, e.g. back from Holiday themes
 * with Studio's part switched off.
 */
@Composable
fun FollowStudioPalette() {
    val context = LocalContext.current
    val resumes = rememberResumeCount()
    LaunchedEffect(resumes) {
        StudioPalette.holiday = HolidayThemes.studioHoliday(context)
    }
}

/**
 * Google Sans Flex with its rounded terminals (ROND 100), ExpressiveOS's display face. Studio sets
 * it in bold only, so it ships as that one static weight.
 */
val BloomRounded = FontFamily(
    Font(R.font.google_sans_flex_rounded_bold, FontWeight.Bold),
)

/** Corner radii of Studio's cards: Material 3 Expressive's large and extra-large shapes. */
object BloomShapes {
    val tile = RoundedCornerShape(28.dp)
    val gridTile = RoundedCornerShape(24.dp)
    val chip = RoundedCornerShape(16.dp)
    val thumb = RoundedCornerShape(12.dp)
}

/**
 * A Studio card: the surface Settings' rows use, outlined instead of shadowed. Tapping it runs
 * [onClick]; its text is read out as one button, and it rings like Settings' rows when a keyboard
 * moves to it.
 */
@Composable
fun BloomCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = BloomShapes.tile,
    color: Color = MaterialTheme.colorScheme.surfaceBright,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .insetFocusRing(shape)
            .semantics { role = Role.Button },
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Box(content = content)
    }
}

/** An icon on its petal's squircle, as on the home screen's section tiles. */
@Composable
fun PetalChip(
    petal: Petal,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier.size(size),
        shape = BloomShapes.chip,
        color = petal.tile,
        contentColor = petal.ink,
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}
