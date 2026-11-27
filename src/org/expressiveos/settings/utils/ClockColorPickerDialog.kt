/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.expressiveos.settings.utils

import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.settings.R
import com.android.settings.widget.scrollbar
import com.github.skydoves.colorpicker.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveCorners
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveDialog
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveFilledButton
import org.expressiveos.settings.fragments.miscellaneous.ExpressivePrimaryAction
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveTextButton
import org.expressiveos.settings.fragments.miscellaneous.expressiveVerticalScroll
import org.expressiveos.settings.fragments.miscellaneous.insetFocusRing
import org.expressiveos.settings.fragments.miscellaneous.rememberAnimatorsEnabled

private val CLOCK_COLOR_PRESETS = listOf(
    Color.White,
    Color.Black,
    Color(0xFF6750A4), // Monet default purple
    Color(0xFFE91E63), // Pink
    Color(0xFF2196F3), // Blue
    Color(0xFF4CAF50), // Green
    Color(0xFFFF9800), // Orange
    Color(0xFF00BCD4), // Cyan
    Color(0xFFFF5722), // Deep Orange
    Color(0xFF9C27B0), // Purple
    Color(0xFF3F51B5), // Indigo
    Color(0xFF009688), // Teal
    Color(0xFFF44336), // Red
    Color(0xFFFFEB3B), // Yellow
    Color(0xFF8BC34A), // Light Green
    Color(0xFF607D8B), // Blue Grey
)

private enum class ClockColorTab { PRESET, WALLPAPER, CUSTOM }

@Composable
fun ClockColorPickerDialog(
    initialColor: String = "FFFFFF",
    defaultColor: String? = null,
    title: String? = null,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val parsedDefault = remember(defaultColor) {
        defaultColor?.let {
            runCatching { Color(android.graphics.Color.parseColor("#$it")) }.getOrNull()
        }
    }

    var selectedColor by remember {
        mutableStateOf(
            try { Color(android.graphics.Color.parseColor("#$initialColor")) }
            catch (_: Exception) { Color.White }
        )
    }

    var activeTab by remember { mutableStateOf(ClockColorTab.PRESET) }

    val wallpaperController = rememberColorPickerController()
    var wallpaperBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isWallpaperLoading by remember { mutableStateOf(false) }

    val customController = rememberColorPickerController()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isWallpaperLoading = true
            coroutineScope.launch {
                val bmp = withContext(Dispatchers.IO) { loadAndScaleBitmap(context, uri) }
                wallpaperBitmap = bmp
                bmp?.let { wallpaperController.setPaletteImageBitmap(it.asImageBitmap()) }
                isWallpaperLoading = false
            }
        }
    }

    LaunchedEffect(activeTab) {
        if (activeTab == ClockColorTab.WALLPAPER && wallpaperBitmap == null) {
            isWallpaperLoading = true
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val wm = WallpaperManager.getInstance(context)
                    val drawable = wm.drawable
                    if (drawable is BitmapDrawable) scaleBitmap(drawable.bitmap) else null
                } catch (_: Exception) { null }
            }
            wallpaperBitmap = bmp
            bmp?.let { wallpaperController.setPaletteImageBitmap(it.asImageBitmap()) }
            isWallpaperLoading = false
        }
    }

    val displayHex = remember(selectedColor) {
        String.format("#%06X", selectedColor.toArgb() and 0xFFFFFF)
    }
    val showReset = parsedDefault != null &&
        (selectedColor.toArgb() and 0xFFFFFF) != (parsedDefault.toArgb() and 0xFFFFFF)

    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title ?: stringResource(R.string.color_picker_clock_title),
                style = MaterialTheme.typography.headlineSmallEmphasized
            )
        },
        text = {
            Column(modifier = Modifier.expressiveVerticalScroll()) {
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
                            .background(selectedColor)
                            .border(
                                1.5.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                ExpressiveCorners.picker
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

                    if (showReset && parsedDefault != null) {
                        Surface(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                selectedColor = parsedDefault
                                runCatching { customController.selectByColor(parsedDefault, false) }
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

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ClockColorTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            Surface(
                                onClick = {
                                    if (activeTab != tab) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        activeTab = tab
                                    }
                                },
                                shape = CircleShape,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 38.dp)
                                    .insetFocusRing(CircleShape),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = stringResource(
                                            when (tab) {
                                                ClockColorTab.PRESET -> R.string.color_picker_tab_preset
                                                ClockColorTab.WALLPAPER -> R.string.color_picker_tab_image
                                                ClockColorTab.CUSTOM -> R.string.color_picker_tab_custom
                                            }
                                        ),
                                        style = if (selected) {
                                            MaterialTheme.typography.labelLargeEmphasized
                                        } else {
                                            MaterialTheme.typography.labelLarge
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                when (activeTab) {
                    ClockColorTab.PRESET -> PresetTab(
                        selected = selectedColor,
                        onSelect = { color ->
                            selectedColor = color
                            runCatching { customController.selectByColor(color, false) }
                        }
                    )
                    ClockColorTab.WALLPAPER -> WallpaperTab(
                        bitmap = wallpaperBitmap,
                        isLoading = isWallpaperLoading,
                        controller = wallpaperController,
                        onColorChanged = { selectedColor = it },
                        onImportClick = { imagePickerLauncher.launch("image/*") }
                    )
                    ClockColorTab.CUSTOM -> CustomTab(
                        initialColor = selectedColor,
                        controller = customController,
                        onColorChanged = { selectedColor = it }
                    )
                }
            }
        },
        confirmButton = {
            ExpressiveFilledButton(onClick = { onColorSelected(selectedColor) }) {
                Text(stringResource(R.string.color_picker_apply))
            }
        },
        dismissButton = {
            ExpressiveTextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun PresetTab(selected: Color, onSelect: (Color) -> Unit) {
    val view = LocalView.current
    val animatorsEnabled = rememberAnimatorsEnabled()
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val selectedRgb = selected.toArgb() and 0xFFFFFF
    val gridState = rememberLazyGridState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExpressiveTonalSwatchStrip(
            selectedArgb = selected.toArgb(),
            alphaEnabled = false,
            onSwatchSelected = { argb -> onSelect(Color(argb)) },
        )

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .height(212.dp)
                .scrollbar(gridState.scrollIndicatorState, Orientation.Vertical),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CLOCK_COLOR_PRESETS) { color ->
                val isSelected = (color.toArgb() and 0xFFFFFF) == selectedRgb
                val cornerRadius by animateDpAsState(
                    targetValue = if (isSelected) 14.dp else 26.dp,
                    animationSpec = if (animatorsEnabled) spatialSpec else SnapSpec(0),
                    label = "clockPresetCorner",
                )
                val shape = RoundedCornerShape(cornerRadius)
                val checkColor = if (color.luminance() > 0.55f) Color(0xFF1B1B1F) else Color.White
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .insetFocusRing(shape)
                        .clip(shape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                            shape = shape
                        )
                        .clickable(role = Role.RadioButton) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSelect(color)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = checkColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperTab(
    bitmap: Bitmap?,
    isLoading: Boolean,
    controller: ColorPickerController,
    onColorChanged: (Color) -> Unit,
    onImportClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ExpressivePrimaryAction(
            text = stringResource(R.string.color_picker_import_gallery),
            icon = Icons.Rounded.Image,
            onClick = onImportClick,
            inset = false
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(ExpressiveCorners.preview)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ExpressiveCorners.preview),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> LoadingIndicator()
                bitmap != null -> {
                    ImageColorPicker(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(ExpressiveCorners.picker),
                        controller = controller,
                        paletteImageBitmap = bitmap.asImageBitmap(),
                        paletteContentScale = PaletteContentScale.FIT,
                        onColorChanged = { envelope -> onColorChanged(envelope.color) }
                    )
                }
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        stringResource(R.string.color_picker_wallpaper_error),
                        color = MaterialTheme.colorScheme.error
                    )
                    ExpressivePrimaryAction(
                        text = stringResource(R.string.color_picker_import_image),
                        icon = Icons.Rounded.Image,
                        onClick = onImportClick,
                        inset = false
                    )
                }
            }
        }
        if (bitmap != null && !isLoading) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.color_picker_hint_image),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun CustomTab(
    initialColor: Color,
    controller: ColorPickerController,
    onColorChanged: (Color) -> Unit
) {
    val startColor = remember { initialColor }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HsvColorPicker(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(ExpressiveCorners.picker),
            controller = controller,
            initialColor = startColor,
            onColorChanged = { envelope -> onColorChanged(envelope.color) }
        )
        BrightnessSlider(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(CircleShape),
            controller = controller,
            initialColor = startColor,
        )
        AlphaSlider(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(CircleShape),
            controller = controller,
            tileOddColor = Color.White,
            tileEvenColor = Color.LightGray,
            initialColor = startColor,
        )
    }
}

private fun scaleBitmap(src: Bitmap, maxSize: Int = 800): Bitmap {
    val scale = minOf(maxSize.toFloat() / src.width, maxSize.toFloat() / src.height, 1f)
    return if (scale < 1f)
        Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
    else src
}

private fun loadAndScaleBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            scaleBitmap(BitmapFactory.decodeStream(stream) ?: return null)
        }
    } catch (_: Exception) { null }
}
