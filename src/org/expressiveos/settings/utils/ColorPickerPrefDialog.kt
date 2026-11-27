/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.expressiveos.settings.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color as AndroidColor
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.android.settings.R
import com.github.skydoves.colorpicker.compose.*
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveCorners
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveDialog
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveFilledButton
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveTextButton
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveTextField
import org.expressiveos.settings.fragments.miscellaneous.expressiveVerticalScroll
import org.expressiveos.settings.fragments.miscellaneous.insetFocusRing
import org.expressiveos.settings.fragments.miscellaneous.rememberAnimatorsEnabled

private fun toHex(argb: Int, withAlpha: Boolean): String =
    if (withAlpha) String.format("%08X", argb)
    else String.format("%06X", argb and 0xFFFFFF)

/**
 * Dynamic Monet tonal swatches derived from the live [ColorScheme] paired with vibrant Expressive
 * accents, morphing from circle to squircle on selection with a `CLOCK_TICK` haptic.
 */
@Composable
internal fun ExpressiveTonalSwatchStrip(
    selectedArgb: Int,
    alphaEnabled: Boolean,
    onSwatchSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val animatorsEnabled = rememberAnimatorsEnabled()
    val swatches = remember(scheme) {
        listOf(
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
            scheme.primaryContainer,
            scheme.secondaryContainer,
            scheme.tertiaryContainer,
            scheme.inversePrimary,
            Color(0xFFAE69FC),
            Color(0xFF417BFC),
            Color(0xFF4AD29D),
            Color(0xFFFD7771),
            Color.White,
            Color.Black,
        ).distinctBy { it.toArgb() and 0xFFFFFF }
    }
    val selectedRgb = selectedArgb and 0xFFFFFF
    val preservedAlpha = if (alphaEnabled) (selectedArgb and 0xFF000000.toInt()) else 0xFF000000.toInt()
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        swatches.forEach { swatch ->
            val swatchRgb = swatch.toArgb() and 0xFFFFFF
            val isSelected = swatchRgb == selectedRgb
            val targetCorner = if (isSelected) 12.dp else 20.dp
            val cornerRadius by animateDpAsState(
                targetValue = targetCorner,
                animationSpec = if (animatorsEnabled) spatialSpec else SnapSpec(0),
                label = "swatchCorner",
            )
            val shape = RoundedCornerShape(cornerRadius)
            val checkColor = if (swatch.luminance() > 0.55f) Color(0xFF1B1B1F) else Color.White
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .insetFocusRing(shape)
                    .clip(shape)
                    .background(swatch)
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) scheme.primary else scheme.outlineVariant,
                        shape = shape,
                    )
                    .clickable(role = Role.RadioButton) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onSwatchSelected(preservedAlpha or swatchRgb)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = checkColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Material 3 Expressive color picker dialog with live preview card, tabular-numeral `#HEX` pill
 * badge (tap to copy), inline Reset chip, dynamic tonal Monet swatches, and HSV/brightness/alpha
 * controls.
 */
@Composable
fun PreferenceColorPickerDialog(
    title: String?,
    initialColor: Int,
    alphaEnabled: Boolean,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
    defaultColor: Int? = null,
    showReset: Boolean = false,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val startArgb = if (alphaEnabled) initialColor else initialColor or 0xFF000000.toInt()
    val resolvedDefault = defaultColor?.let {
        if (alphaEnabled) it else it or 0xFF000000.toInt()
    }
    var selectedArgb by rememberSaveable { mutableIntStateOf(startArgb) }
    var hex by rememberSaveable { mutableStateOf(toHex(startArgb, alphaEnabled)) }
    var hexError by remember { mutableStateOf(false) }

    val pickerStart = remember { Color(selectedArgb) }
    val controller = rememberColorPickerController()
    val expectedLength = if (alphaEnabled) 8 else 6
    val displayHex = "#${toHex(selectedArgb, alphaEnabled)}"

    fun selectProgrammatically(argb: Int) {
        selectedArgb = argb
        hex = toHex(argb, alphaEnabled)
        hexError = false
        runCatching { controller.selectByColor(Color(argb), false) }
    }

    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = if (!title.isNullOrEmpty()) {
            { Text(text = title, style = MaterialTheme.typography.headlineSmallEmphasized) }
        } else {
            null
        },
        text = {
            Column(modifier = Modifier.expressiveVerticalScroll()) {
                // Header preview row: live swatch + tabular hex pill badge + optional reset chip.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(ExpressiveCorners.picker)
                            .background(Color(selectedArgb))
                            .border(
                                1.5.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                ExpressiveCorners.picker,
                            )
                    )

                    Surface(
                        onClick = {
                            val cm = context.getSystemService(ClipboardManager::class.java)
                            cm?.setPrimaryClip(ClipData.newPlainText("HEX", displayHex))
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .height(40.dp)
                            .insetFocusRing(CircleShape),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = displayHex,
                                style = MaterialTheme.typography.labelLargeEmphasized.copy(
                                    fontFeatureSettings = "tnum",
                                ),
                                maxLines = 1,
                            )
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(android.R.string.copy),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }

                    if (showReset && resolvedDefault != null && selectedArgb != resolvedDefault) {
                        Surface(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                selectProgrammatically(resolvedDefault)
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(40.dp)
                                .insetFocusRing(CircleShape),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.color_picker_reset),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                ExpressiveTonalSwatchStrip(
                    selectedArgb = selectedArgb,
                    alphaEnabled = alphaEnabled,
                    onSwatchSelected = { argb -> selectProgrammatically(argb) },
                )

                Spacer(Modifier.height(14.dp))

                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clip(ExpressiveCorners.picker),
                    controller = controller,
                    initialColor = pickerStart,
                    onColorChanged = { envelope ->
                        val newArgb = if (alphaEnabled) {
                            envelope.color.toArgb()
                        } else {
                            envelope.color.toArgb() or 0xFF000000.toInt()
                        }
                        selectedArgb = newArgb
                        if (envelope.fromUser) {
                            hex = toHex(newArgb, alphaEnabled)
                            hexError = false
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                BrightnessSlider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(CircleShape),
                    controller = controller,
                    initialColor = pickerStart
                )

                if (alphaEnabled) {
                    Spacer(Modifier.height(12.dp))
                    AlphaSlider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .clip(CircleShape),
                        controller = controller,
                        tileOddColor = Color.White,
                        tileEvenColor = Color.LightGray,
                        initialColor = pickerStart
                    )
                }

                Spacer(Modifier.height(14.dp))

                ProvideTextStyle(
                    MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")
                ) {
                    ExpressiveTextField(
                        value = hex,
                        onValueChange = { input ->
                            val cleaned = input.replace("#", "").uppercase()
                            if (cleaned.length <= expectedLength &&
                                cleaned.all { it in "0123456789ABCDEF" }
                            ) {
                                hex = cleaned
                                hexError = false
                                if (cleaned.length == expectedLength) {
                                    try {
                                        val parsed = AndroidColor.parseColor("#$cleaned")
                                        selectedArgb = parsed
                                        controller.selectByColor(Color(parsed), false)
                                    } catch (_: IllegalArgumentException) {
                                        hexError = true
                                    }
                                }
                            }
                        },
                        label = stringResource(
                            if (alphaEnabled) R.string.color_picker_argb
                            else R.string.color_picker_hex
                        ),
                        leadingIcon = { Text("#", style = MaterialTheme.typography.titleMediumEmphasized) },
                        isError = hexError,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            ExpressiveFilledButton(onClick = {
                val out = if (alphaEnabled) selectedArgb
                          else selectedArgb or 0xFF000000.toInt()
                onColorSelected(out)
            }) { Text(stringResource(R.string.color_picker_apply)) }
        },
        dismissButton = {
            ExpressiveTextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
