/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.studio

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.evolution.settings.fragments.lockscreen.ClockUtils

/**
 * A count that goes up each time the screen resumes, so what Studio shows of the lock screen is read
 * again after a picker (the clock faces, the wallpaper picker) returns to it.
 */
@Composable
fun rememberResumeCount(): Int {
    // From -1: LifecycleEventEffect replays the screen's current resume when it starts observing,
    // and that one isn't a return
    var count by remember { mutableIntStateOf(-1) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { count++ }
    return count.coerceAtLeast(0)
}

/** The lock screen's clock face: an index into [ClockUtils.CLOCK_LAYOUTS], 0 for the default. */
@Composable
fun rememberClockStyle(resumeCount: Int): Int {
    val context = LocalContext.current
    return remember(resumeCount) {
        Settings.Secure.getIntForUser(
            context.contentResolver,
            Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE,
            0,
            UserHandle.USER_CURRENT
        )
    }
}

/** The name the clock picker gives [style]. */
fun clockName(style: Int): String {
    val names = ClockUtils.getClockNames()
    return names.getOrNull(style) ?: names[0]
}

/**
 * A wallpaper as Studio draws it: its [image], null for a live wallpaper with no thumbnail, and
 * whether text over it reads best dark ([darkText]), as its colours tell the lock screen.
 */
class Wallpaper(val image: ImageBitmap?, val darkText: Boolean)

/**
 * The [which] wallpaper (WallpaperManager.FLAG_LOCK or FLAG_SYSTEM), [widthPx] wide, read off the
 * main thread. The lock screen falls back to the home screen's when it has none of its own. Null
 * until it's read, or when it can't be.
 */
@Composable
fun rememberWallpaper(which: Int, widthPx: Int, resumeCount: Int): Wallpaper? {
    val context = LocalContext.current
    var wallpaper by remember { mutableStateOf<Wallpaper?>(null) }
    LaunchedEffect(which, widthPx, resumeCount) {
        if (widthPx <= 0) return@LaunchedEffect
        withContext(Dispatchers.IO) { loadWallpaper(context, which, widthPx) }
            ?.let { wallpaper = it }
    }
    return wallpaper
}

private fun loadWallpaper(context: Context, which: Int, widthPx: Int): Wallpaper? = runCatching {
    val wm = WallpaperManager.getInstance(context)
    // Asking for a lock screen wallpaper the phone doesn't have empties WallpaperManager's cache of
    // the home screen's, so ask only when there is one
    val flag = if (which == WallpaperManager.FLAG_LOCK &&
        wm.getWallpaperId(WallpaperManager.FLAG_LOCK) > 0
    ) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
    val hints = wm.getWallpaperColors(flag)?.colorHints ?: 0
    // A live wallpaper is shown by its thumbnail: getDrawable gives Android's default for it
    val drawable = when (val live = wm.getWallpaperInfo(flag)) {
        null -> wm.getDrawable(flag)
        else -> live.loadThumbnail(context.packageManager)
    }
    Wallpaper(
        image = drawable?.let { scaled(it, widthPx) },
        darkText = (hints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0,
    )
}.getOrNull()

private fun scaled(drawable: Drawable, widthPx: Int): ImageBitmap {
    val w = drawable.intrinsicWidth
    val h = drawable.intrinsicHeight
    val height = if (w > 0 && h > 0) (widthPx.toLong() * h / w).toInt() else widthPx * 2
    return drawable.toBitmap(widthPx, height.coerceAtLeast(1)).asImageBitmap()
}

/** The time now, updated on each new minute. */
@Composable
fun rememberMinute(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(60_000L - value % 60_000L)
    }
}

/**
 * A small copy of the lock screen: its wallpaper and the clock face picked for it, the way the
 * clock picker previews faces, and the lock at its foot unless [showLockIcon] is false. [overlay]
 * draws over it, e.g. a label. Screen readers skip the clock: it's a picture of the lock screen.
 */
@Composable
fun LockScreenPreview(
    modifier: Modifier = Modifier,
    shape: Shape = BloomShapes.tile,
    showLockIcon: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val resumes = rememberResumeCount()
    val style = rememberClockStyle(resumes)
    BoxWithConstraints(modifier.clip(shape).background(Petal.ground)) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val wallpaper = rememberWallpaper(WallpaperManager.FLAG_LOCK, widthPx, resumes)
        wallpaper?.image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        }
        // The default face turns dark over a light wallpaper, as the lock screen's does. The
        // other faces are drawn white: a scrim like the lock screen's own keeps them readable
        val ink = if (isDefaultFace(style) && wallpaper?.darkText == true) DarkInk else Color.White
        if (ink == Color.White) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.32f),
                            0.5f to Color.Transparent,
                        )
                    )
            )
        }
        // As wide as the phone's clock, or narrower when the preview is short for its width
        // (landscape), so the face fits above the lock
        val faceWidth = minOf(maxWidth, maxHeight * 0.62f)
        ClockFace(
            style = style,
            width = faceWidth,
            ink = ink,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(faceWidth)
                .padding(top = faceWidth * 0.12f)
                .clearAndSetSemantics {},
        )
        if (showLockIcon) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = ink,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .size(18.dp),
            )
        }
        overlay()
    }
}

/** Whether [style] is drawn as Android's own clock: 0, or a face this build doesn't have. */
private fun isDefaultFace(style: Int) =
    style == 0 || ClockUtils.CLOCK_LAYOUTS.getOrNull(style) == null

/** The lock screen's text over a light wallpaper. */
private val DarkInk = Color(0xFF1B1B1F)

/**
 * The face for [style], drawn [width] wide as if on the phone's full-width lock screen. The
 * default face is drawn in [ink]; the others in their own colours.
 */
@Composable
private fun ClockFace(style: Int, width: Dp, ink: Color, modifier: Modifier) {
    if (isDefaultFace(style)) {
        DefaultClock(width, ink, modifier)
        return
    }
    val layout = ClockUtils.CLOCK_LAYOUTS[style]
    key(layout) {
        AndroidView(factory = { ScaledClockFrame(it, layout) }, modifier = modifier)
    }
}

/** Android's own large clock: the hours over the minutes, then the date. */
@Composable
private fun DefaultClock(width: Dp, ink: Color, modifier: Modifier) {
    val context = LocalContext.current
    val now by rememberMinute()
    val time = DateFormat.format(
        if (DateFormat.is24HourFormat(context)) "HH\nmm" else "hh\nmm", now
    ).toString()
    val date = DateFormat.format(
        DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd"), now
    ).toString()
    val density = LocalDensity.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = time,
            color = ink,
            fontFamily = BloomRounded,
            fontWeight = FontWeight.Bold,
            fontSize = with(density) { (width * 0.36f).toSp() },
            lineHeight = 0.92.em,
            textAlign = TextAlign.Center,
        )
        Text(
            text = date,
            color = ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = with(density) { (width * 0.07f).toSp() },
            modifier = Modifier.padding(top = width * 0.03f),
        )
    }
}

/**
 * Lays a clock face out at the screen's width and scales it down to fit, so its text keeps the
 * proportions it has on the lock screen.
 */
private class ScaledClockFrame(context: Context, @LayoutRes layout: Int) : FrameLayout(context) {

    private val screenWidth = context.resources.displayMetrics.let {
        min(it.widthPixels, it.heightPixels)
    }
    private val face: View = LayoutInflater.from(context).inflate(layout, this, false)

    init {
        isClickable = false
        isFocusable = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(face, LayoutParams(screenWidth, LayoutParams.WRAP_CONTENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        face.measure(
            MeasureSpec.makeMeasureSpec(screenWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val scale = if (screenWidth > 0) width.toFloat() / screenWidth else 1f
        face.pivotX = 0f
        face.pivotY = 0f
        face.scaleX = scale
        face.scaleY = scale
        setMeasuredDimension(
            width,
            resolveSize((face.measuredHeight * scale).roundToInt(), heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        face.layout(0, 0, face.measuredWidth, face.measuredHeight)
    }
}
