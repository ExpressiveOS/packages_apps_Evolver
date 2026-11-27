/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.expressiveos.settings.preferences

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.settings.widget.scrollbar
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveDialog
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveFilledButton
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveTextButton
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveTheme
import org.expressiveos.settings.fragments.miscellaneous.insetFocusRing
import org.expressiveos.settings.fragments.miscellaneous.rememberAnimatorsEnabled

private const val TAG = "ComposePickerDialog"

/** Handle for a dialog opened with [showComposeDialog]. */
class ComposeDialogHandle internal constructor(
    private val host: ViewGroup,
    private val view: View,
) {
    /** Closes the dialog. Safe to call more than once. */
    fun dismiss() {
        if (view.parent === host) host.removeView(view)
    }
}

/**
 * Shows Compose [content] (normally a Material 3 `AlertDialog`) from a View-based preference.
 *
 * The composition is hosted by a zero-sized [ComposeView] on the activity's decor view, which
 * already carries the lifecycle and saved-state owners Compose needs. The `AlertDialog` opens its
 * own window from there. [ComposeDialogHandle.dismiss] removes the host, which disposes the
 * composition and closes the dialog. Returns null if [Context] isn't backed by an activity.
 */
fun Context.showComposeDialog(
    content: @Composable (dismiss: () -> Unit) -> Unit,
): ComposeDialogHandle? {
    val activity = findActivity()
    if (activity == null) {
        Log.w(TAG, "No activity behind $this, can't show a Compose dialog")
        return null
    }
    val host = activity.window.decorView as ViewGroup
    val view = ComposeView(activity)
    val handle = ComposeDialogHandle(host, view)
    view.setContent { ExpressiveTheme { content { handle.dismiss() } } }
    host.addView(view, ViewGroup.LayoutParams(0, 0))
    return handle
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * One row of a picker. [icon] is drawn in the row's content colour before the label, [trailing]
 * after it (a preview button, say), and [labelFontFamily] renders the label in its own font. It
 * is a provider so a long list only loads the fonts that scroll into view.
 */
class PickerOption(
    val label: String,
    val labelFontFamily: (() -> FontFamily?)? = null,
    val trailing: (@Composable () -> Unit)? = null,
    val icon: (@Composable () -> Unit)? = null,
)

/** Extra text button next to Cancel, like "Add font". It closes the picker, then runs [onClick]. */
class PickerAction(val label: String, val onClick: () -> Unit)

/**
 * Picker dialog: [options] in a scrollable segmented list (two columns in landscape) with the
 * current one highlighted. [onSelect] returns true to close the dialog, false to keep it open.
 * [action] adds a button before Cancel, and [onClosed] runs however the dialog goes away.
 */
fun Context.showPickerDialog(
    title: CharSequence?,
    options: List<PickerOption>,
    selectedIndex: Int,
    action: PickerAction? = null,
    onClosed: () -> Unit = {},
    onSelect: (Int) -> Boolean,
): ComposeDialogHandle? = showComposeDialog { dismiss ->
    DisposableEffect(Unit) { onDispose(onClosed) }
    PickerDialog(
        title = title?.toString(),
        options = options,
        selectedIndex = selectedIndex,
        action = action,
        onSelect = { index -> if (onSelect(index)) dismiss() },
        onDismiss = dismiss,
    )
}

/**
 * Two-choice prompt for View-based preferences. Unlike [ExpressiveConfirmDialog] the second button
 * does something ([onLater]); tapping outside runs neither.
 */
fun Context.showChoiceDialog(
    title: String,
    message: String,
    confirmText: String,
    laterText: String,
    onConfirm: () -> Unit,
    onLater: () -> Unit,
): ComposeDialogHandle? = showComposeDialog { dismiss ->
    ExpressiveDialog(
        onDismissRequest = dismiss,
        title = { Text(text = title, style = MaterialTheme.typography.headlineSmallEmphasized) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            ExpressiveFilledButton(onClick = { dismiss(); onConfirm() }) { Text(confirmText) }
        },
        dismissButton = {
            ExpressiveTextButton(onClick = { dismiss(); onLater() }) { Text(laterText) }
        },
    )
}

@Composable
private fun PickerDialog(
    title: String?,
    options: List<PickerOption>,
    selectedIndex: Int,
    action: PickerAction?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (landscape) 2 else 1
    val maxHeight = (configuration.screenHeightDp * if (landscape) 0.75f else 0.6f).dp
    val firstVisible = selectedIndex.coerceAtLeast(0)
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = if (landscape) firstVisible - firstVisible % 2 else firstVisible,
    )

    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = title?.let {
            { Text(text = it, style = MaterialTheme.typography.headlineSmallEmphasized) }
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .scrollbar(gridState.scrollIndicatorState, Orientation.Vertical),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(options) { index, option ->
                    PickerOptionRow(
                        option = option,
                        selected = index == selectedIndex,
                        index = index,
                        count = options.size,
                        columns = columns,
                        onClick = { onSelect(index) },
                    )
                }
            }
        },
        confirmButton = {
            ExpressiveTextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        dismissButton = action?.let { extra ->
            {
                ExpressiveTextButton(onClick = { onDismiss(); extra.onClick() }) {
                    Text(extra.label)
                }
            }
        },
    )
}

private data class CornerQuad(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp,
)

private fun segmentedPickerCorners(index: Int, count: Int, columns: Int): CornerQuad {
    val outer = 24.dp
    val inner = 4.dp
    if (count <= 1) return CornerQuad(outer, outer, outer, outer)
    if (columns <= 1) {
        return CornerQuad(
            topStart = if (index == 0) outer else inner,
            topEnd = if (index == 0) outer else inner,
            bottomStart = if (index == count - 1) outer else inner,
            bottomEnd = if (index == count - 1) outer else inner,
        )
    }
    val row = index / 2
    val lastRow = (count - 1) / 2
    val isLeft = index % 2 == 0
    return CornerQuad(
        topStart = if (row == 0 && isLeft) outer else inner,
        topEnd = if (row == 0 && !isLeft) outer else inner,
        bottomStart = if (row == lastRow && isLeft) outer else inner,
        bottomEnd = if (
            (!isLeft && row == lastRow) ||
            (isLeft && index == count - 1) ||
            (!isLeft && index == count - 2 && count % 2 == 1)
        ) outer else inner,
    )
}

@Composable
private fun PickerOptionRow(
    option: PickerOption,
    selected: Boolean,
    index: Int,
    count: Int,
    columns: Int,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val animatorsEnabled = rememberAnimatorsEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val resting = remember(index, count, columns) { segmentedPickerCorners(index, count, columns) }
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val spec = if (animatorsEnabled) spatialSpec else SnapSpec(0)

    val topStart by animateDpAsState(if (pressed) 24.dp else resting.topStart, spec, label = "ts")
    val topEnd by animateDpAsState(if (pressed) 24.dp else resting.topEnd, spec, label = "te")
    val bottomStart by animateDpAsState(if (pressed) 24.dp else resting.bottomStart, spec, label = "bs")
    val bottomEnd by animateDpAsState(if (pressed) 24.dp else resting.bottomEnd, spec, label = "be")
    val shape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

    val colors = MaterialTheme.colorScheme
    val contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .insetFocusRing(shape)
            .clip(shape)
            .background(if (selected) colors.secondaryContainer else colors.surfaceBright)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.RadioButton,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            option.icon?.let { icon ->
                Box(
                    modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
                    contentAlignment = Alignment.Center,
                ) { icon() }
                Spacer(Modifier.size(12.dp))
            }
            val family = remember(option) { option.labelFontFamily?.invoke() }
            Text(
                text = option.label,
                style = if (selected) {
                    MaterialTheme.typography.titleMediumEmphasized
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                fontFamily = family,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = if (option.icon == null) 8.dp else 0.dp),
            )
            if (option.trailing != null) {
                option.trailing.invoke()
            } else if (selected) {
                Spacer(Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** The current time as the clock previews show it: hh:mm, or kk:mm with a 24-hour clock. */
fun pickerClockText(context: Context): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "kk:mm" else "hh:mm"
    return DateFormat.format(pattern, System.currentTimeMillis()).toString()
}

/** A 32dp [drawable] tinted with the current content colour. Draws nothing for null. */
@Composable
fun PickerDrawableIcon(drawable: Drawable?) {
    val px = with(LocalDensity.current) { 28.dp.roundToPx() }
    val bitmap = remember(drawable, px) {
        drawable?.let { runCatching { it.toBitmap(px, px).asImageBitmap() }.getOrNull() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            colorFilter = ColorFilter.tint(LocalContentColor.current),
        )
    } else {
        Spacer(Modifier.size(28.dp))
    }
}
