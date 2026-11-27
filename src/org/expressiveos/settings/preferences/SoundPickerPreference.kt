/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.expressiveos.settings.preferences

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.Preference
import com.android.settings.R
import org.expressiveos.settings.fragments.miscellaneous.insetFocusRing
import org.expressiveos.settings.fragments.miscellaneous.rememberAnimatorsEnabled
import org.expressiveos.settings.utils.SystemUtils
import java.io.File

class SoundPickerPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

    private var dialog: ComposeDialogHandle? = null
    private var mediaPlayer: MediaPlayer? = null

    /** Path of the sound being previewed. Read by the rows, so they redraw when it changes. */
    private var playingPath by mutableStateOf<String?>(null)

    // These are set from XML attributes or programmatically
    @JvmField var entries: Array<CharSequence> = emptyArray()
    @JvmField var entryValues: Array<CharSequence> = emptyArray()
    @JvmField var settingKey: String = ""
    @JvmField var defaultValue: String = ""

    fun applyConfig() {
        updateSummary()
    }

    override fun onDetached() {
        releasePlayer()
        dialog?.dismiss()
        dialog = null
        super.onDetached()
    }

    override fun onClick() {
        val ctx = context
        dialog?.dismiss()

        val current = getCurrentValue()
        val selectedIndex = entryValues.indexOfFirst { it.toString() == current }.coerceAtLeast(0)
        val options = entries.indices.map { index ->
            val value = entryValues[index].toString()
            PickerOption(label = entries[index].toString(), trailing = {
                val view = LocalView.current
                val animatorsEnabled = rememberAnimatorsEnabled()
                val playing = playingPath == value
                val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
                val cornerRadius by animateDpAsState(
                    targetValue = if (playing) 12.dp else 20.dp,
                    animationSpec = if (animatorsEnabled) spatialSpec else SnapSpec(0),
                    label = "soundPlayShape",
                )
                val btnShape = RoundedCornerShape(cornerRadius)
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        togglePreview(value)
                    },
                    shape = btnShape,
                    color = if (playing) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (playing) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .insetFocusRing(btnShape),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(
                                if (playing) R.drawable.ic_sound_pause else R.drawable.ic_sound_play
                            ),
                            contentDescription = stringResource(
                                if (playing) R.string.sound_preview_stop else R.string.sound_preview_play
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            })
        }

        dialog = ctx.showPickerDialog(
            title = title,
            options = options,
            selectedIndex = selectedIndex,
            onClosed = {
                releasePlayer()
                dialog = null
            },
        ) { index ->
            val value = entryValues[index].toString()
            // Same sound again: leave the picker open.
            if (value == current) return@showPickerDialog false
            releasePlayer()
            Settings.Global.putString(ctx.contentResolver, settingKey, value)
            updateSummary()
            SystemUtils.showSystemUiRestartDialog(ctx)
            true
        }
    }

    private fun getCurrentValue(): String {
        return Settings.Global.getString(context.contentResolver, settingKey) ?: defaultValue
    }

    private fun updateSummary() {
        val current = getCurrentValue()
        val index = entryValues.indexOfFirst { it.toString() == current }
        summary = if (index >= 0) entries[index] else current
    }

    private fun releasePlayer() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        playingPath = null
    }

    private fun togglePreview(path: String) {
        // Tapping the sound that's playing stops it.
        if (playingPath == path && mediaPlayer?.isPlaying == true) {
            releasePlayer()
            return
        }

        releasePlayer()

        try {
            val file = File(path)
            if (!file.exists()) return

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                prepare()
                start()
                setOnCompletionListener { releasePlayer() }
            }
            playingPath = path
        } catch (_: Exception) {
            releasePlayer()
        }
    }
}
