/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.expressiveos.settings.studio

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceFragmentCompat
import com.android.settings.R
import com.android.settingslib.widget.LayoutPreference
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveTheme
import org.expressiveos.settings.fragments.miscellaneous.expressiveButtonShapes
import org.expressiveos.settings.fragments.miscellaneous.insetFocusRing
import org.expressiveos.settings.holiday.HolidayThemes

/**
 * The top of Expressive Studio's lock screen page: the lock screen as it is now, with its clock
 * face's name and buttons for the face picker and the wallpaper picker. The page's own options
 * follow it.
 */
object StudioLockHeader {

    const val KEY = "studio_lock_preview"

    /** Draws the header into [preference], a LayoutPreference of R.layout.studio_compose. */
    @JvmStatic
    fun bind(fragment: PreferenceFragmentCompat, preference: LayoutPreference?) {
        val view = preference?.findViewById<ComposeView>(R.id.studio_compose) ?: return
        val actions = StudioActions(fragment)
        StudioPalette.holiday = HolidayThemes.studioHoliday(fragment.requireContext())
        view.setContent {
            ExpressiveTheme {
                LockHeader(actions)
            }
        }
    }
}

@Composable
private fun LockHeader(actions: StudioActions) {
    val view = LocalView.current
    val resumes = rememberResumeCount()
    val style = rememberClockStyle(resumes)
    val hasPicker = remember { actions.hasWallpaperPicker }
    val buttonShapes = expressiveButtonShapes()
    val previewShape = RoundedCornerShape(22.dp)
    FollowStudioPalette()

    Surface(
        shape = BloomShapes.tile,
        color = MaterialTheme.colorScheme.surfaceBright,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A phone's proportions, 9 by 19.5
            LockScreenPreview(
                shape = previewShape,
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(9f / 19.5f)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        previewShape,
                    ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.studio_lock_clock_face),
                    color = Petal.Coral.ink,
                    fontFamily = BloomRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(
                    text = clockName(style),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    fontFamily = BloomRounded,
                )
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            actions.openClockPicker()
                        },
                        shapes = buttonShapes,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Petal.Blue.deep,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .insetFocusRing(
                                buttonShapes.shape,
                                ringHeight = ButtonDefaults.MinHeight,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.studio_lock_browse_clocks),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                        )
                    }
                    if (hasPicker) {
                        FilledTonalButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                actions.openWallpaperPicker()
                            },
                            shapes = buttonShapes,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Petal.Blue.tile,
                                contentColor = Petal.Blue.ink,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .insetFocusRing(
                                    buttonShapes.shape,
                                    ringHeight = ButtonDefaults.MinHeight,
                                ),
                        ) {
                            Text(
                                text = remember { actions.wallpaperTitle },
                                style = MaterialTheme.typography.labelLargeEmphasized,
                            )
                        }
                    }
                }
            }
        }
    }
}
