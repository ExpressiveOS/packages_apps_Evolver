/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceFragmentCompat
import com.android.settings.R
import com.android.settingslib.widget.LayoutPreference
import org.evolution.settings.fragments.miscellaneous.ExpressiveTheme
import org.evolution.settings.fragments.miscellaneous.insetFocusRing
import org.evolution.settings.holiday.HolidayThemes

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
    val resumes = rememberResumeCount()
    val style = rememberClockStyle(resumes)
    val hasPicker = remember { actions.hasWallpaperPicker }
    FollowStudioPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A phone's proportions, 9 by 19.5
        LockScreenPreview(
            modifier = Modifier
                .width(148.dp)
                .aspectRatio(9f / 19.5f),
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
                // No Surface above this header to give it the theme's text colour
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = BloomRounded,
                fontWeight = FontWeight.Bold,
            )
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { actions.openClockPicker() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Petal.Blue.deep,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .insetFocusRing(
                            ButtonDefaults.shape,
                            ringHeight = ButtonDefaults.MinHeight,
                        ),
                ) {
                    Text(stringResource(R.string.studio_lock_browse_clocks))
                }
                if (hasPicker) {
                    FilledTonalButton(
                        onClick = { actions.openWallpaperPicker() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Petal.Blue.tile,
                            contentColor = Petal.Blue.ink,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .insetFocusRing(
                                ButtonDefaults.filledTonalShape,
                                ringHeight = ButtonDefaults.MinHeight,
                            ),
                    ) {
                        Text(remember { actions.wallpaperTitle })
                    }
                }
            }
        }
    }
}
