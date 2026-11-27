/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.expressiveos.settings.studio

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.android.settings.R
import org.expressiveos.settings.fragments.miscellaneous.insetFocusRing
import org.expressiveos.settings.fragments.miscellaneous.rememberAnimatorsEnabled
import org.expressiveos.settings.holiday.Holiday
import org.expressiveos.settings.holiday.HolidayPetal
import org.expressiveos.settings.holiday.HolidayThemes

/**
 * The four petals of ExpressiveOS's Bloom mark, harmonized toward the active Monet primary accent
 * so Expressive Studio keeps playful category distinction without clashing with the wallpaper
 * palette. While a holiday theme has Studio in its colours ([StudioPalette]), each petal takes
 * those of the holiday's petal in its place. Every [ink] pair is contrast-checked to >= 4.8:1
 * against both its icon [tile] and card surface in Light and Dark themes.
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

    /** Mid-tone accent harmonized toward Monet primary unless a holiday palette is active. */
    val harmonizedMid: Color
        @Composable @ReadOnlyComposable get() {
            val base = mid
            return if (holiday != null) base
            else harmonizeToward(base, MaterialTheme.colorScheme.primary, factor = 0.40f, maxShiftDeg = 28f)
        }

    val tile: Color
        @Composable @ReadOnlyComposable get() {
            val dark = isSystemInDarkTheme()
            val base = if (dark) darkTile else lightTile
            return if (holiday != null) base
            else harmonizeToward(base, MaterialTheme.colorScheme.primary, factor = 0.36f, maxShiftDeg = 24f)
        }

    val ink: Color
        @Composable @ReadOnlyComposable get() {
            val dark = isSystemInDarkTheme()
            val rawInk = if (dark) darkInk else lightInk
            val base = if (holiday != null) rawInk
            else harmonizeToward(rawInk, MaterialTheme.colorScheme.primary, factor = 0.38f, maxShiftDeg = 26f)
            val cardSurface = MaterialTheme.colorScheme.surfaceBright
            val tileBg = tile
            return ensureContrast(
                foreground = base,
                bg1 = tileBg,
                bg2 = cardSurface,
                isDark = dark,
                minRatio = 4.8,
            )
        }

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

        /** Monet-harmonized sweep gradient for Studio thumbnails and highlights. */
        @Composable
        @ReadOnlyComposable
        fun harmonizedSweep(): Brush = Brush.linearGradient(
            listOf(Violet.harmonizedMid, Blue.harmonizedMid, Green.harmonizedMid, Coral.harmonizedMid)
        )
    }
}

/**
 * Shifts [design]'s hue gently toward [source] along the shortest arc (capped at [maxShiftDeg]),
 * preserving [design]'s saturation and lightness so Studio's four category hues stay distinct while
 * blending naturally with the user's active Monet palette.
 */
private fun harmonizeToward(
    design: Color,
    source: Color,
    factor: Float,
    maxShiftDeg: Float,
): Color {
    val dHsl = FloatArray(3)
    val sHsl = FloatArray(3)
    ColorUtils.colorToHSL(design.toArgb(), dHsl)
    ColorUtils.colorToHSL(source.toArgb(), sHsl)
    // Skip hue pull when Monet primary is nearly achromatic (Monochrome/Neutral themes).
    if (sHsl[1] < 0.08f) return design
    var diff = (sHsl[0] - dHsl[0]) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    val shift = (diff * factor).coerceIn(-maxShiftDeg, maxShiftDeg)
    dHsl[0] = (dHsl[0] + shift + 360f) % 360f
    return Color(ColorUtils.HSLToColor(dHsl)).copy(alpha = design.alpha)
}

/**
 * Adjusts [foreground] lightness in HSL until WCAG contrast against both [bg1] and [bg2] reaches
 * at least [minRatio] (lightening in dark theme, darkening in light theme).
 */
private fun ensureContrast(
    foreground: Color,
    bg1: Color,
    bg2: Color,
    isDark: Boolean,
    minRatio: Double,
): Color {
    val fgArgb = foreground.toArgb()
    val bg1Argb = bg1.toArgb()
    val bg2Argb = bg2.toArgb()
    if (ColorUtils.calculateContrast(fgArgb, bg1Argb) >= minRatio &&
        ColorUtils.calculateContrast(fgArgb, bg2Argb) >= minRatio
    ) {
        return foreground
    }
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(fgArgb, hsl)
    val step = if (isDark) 0.03f else -0.03f
    var current = fgArgb
    for (i in 0 until 24) {
        hsl[2] = (hsl[2] + step).coerceIn(0.05f, 0.96f)
        current = ColorUtils.HSLToColor(hsl)
        if (ColorUtils.calculateContrast(current, bg1Argb) >= minRatio &&
            ColorUtils.calculateContrast(current, bg2Argb) >= minRatio
        ) {
            break
        }
    }
    return Color(current).copy(alpha = foreground.alpha)
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
 * A Studio card: borderless tonal surface matching SettingsLib's expressive preference cards.
 * Tapping it runs [onClick]; its text is read out as one button, and it rings like Settings' rows
 * when a keyboard moves to it.
 */
@Composable
fun BloomCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = BloomShapes.tile,
    restRadius: Dp = if (shape == BloomShapes.gridTile) 24.dp else 28.dp,
    pressedRadius: Dp = if (shape == BloomShapes.gridTile) 16.dp else 20.dp,
    color: Color = MaterialTheme.colorScheme.surfaceBright,
    content: @Composable BoxScope.() -> Unit,
) {
    val view = LocalView.current
    val animatorsEnabled = rememberAnimatorsEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dpSpec = if (animatorsEnabled) {
        MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    } else {
        SnapSpec(0)
    }
    val floatSpec = if (animatorsEnabled) {
        MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    } else {
        SnapSpec(0)
    }
    val animatedRadius by animateDpAsState(
        targetValue = if (pressed) pressedRadius else restRadius,
        animationSpec = dpSpec,
        label = "bloomCorner",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = floatSpec,
        label = "bloomScale",
    )
    val morphShape = RoundedCornerShape(animatedRadius)

    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .insetFocusRing(morphShape)
            .semantics { role = Role.Button },
        shape = morphShape,
        color = color,
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
