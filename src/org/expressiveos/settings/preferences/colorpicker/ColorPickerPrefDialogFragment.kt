/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.expressiveos.settings.preferences.colorpicker

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import com.android.settings.R
import org.expressiveos.settings.fragments.miscellaneous.ExpressiveTheme
import org.expressiveos.settings.utils.PreferenceColorPickerDialog

/**
 * Hosts the Material 3 Expressive color picker for [ColorPickerPreference] and reports the chosen
 * ARGB color back through the FragmentManager result API.
 */
class ColorPickerPrefDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Transparent)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val args = requireArguments()
        val requestKey = args.getString(ARG_REQUEST_KEY).orEmpty()
        val title = args.getString(ARG_TITLE)
        val initialColor = args.getInt(ARG_INITIAL_COLOR)
        val hasDefault = args.getBoolean(ARG_HAS_DEFAULT, false)
        val defaultColor = if (hasDefault) args.getInt(ARG_DEFAULT_COLOR, Color.BLACK) else null
        val alphaEnabled = args.getBoolean(ARG_ALPHA_ENABLED)
        val showReset = args.getBoolean(ARG_SHOW_RESET, false)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ExpressiveTheme {
                    PreferenceColorPickerDialog(
                        title = title,
                        initialColor = initialColor,
                        alphaEnabled = alphaEnabled,
                        onDismiss = { dismiss() },
                        onColorSelected = { color ->
                            parentFragmentManager.setFragmentResult(
                                requestKey,
                                Bundle().apply { putInt(RESULT_COLOR, color) }
                            )
                            dismiss()
                        },
                        defaultColor = defaultColor,
                        showReset = showReset,
                    )
                }
            }
        }
    }

    companion object {
        const val TAG = "ColorPickerPrefDialogFragment"
        const val RESULT_COLOR = "color"

        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_TITLE = "title"
        private const val ARG_INITIAL_COLOR = "initial_color"
        private const val ARG_DEFAULT_COLOR = "default_color"
        private const val ARG_HAS_DEFAULT = "has_default"
        private const val ARG_ALPHA_ENABLED = "alpha_enabled"
        private const val ARG_SHOW_RESET = "show_reset"

        @JvmStatic
        fun newInstance(
            requestKey: String,
            title: String?,
            initialColor: Int,
            alphaEnabled: Boolean
        ): ColorPickerPrefDialogFragment = newInstance(
            requestKey = requestKey,
            title = title,
            initialColor = initialColor,
            defaultColor = initialColor,
            alphaEnabled = alphaEnabled,
            showReset = false,
        )

        @JvmStatic
        fun newInstance(
            requestKey: String,
            title: String?,
            initialColor: Int,
            defaultColor: Int,
            alphaEnabled: Boolean,
            showReset: Boolean
        ): ColorPickerPrefDialogFragment = ColorPickerPrefDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_REQUEST_KEY, requestKey)
                putString(ARG_TITLE, title)
                putInt(ARG_INITIAL_COLOR, initialColor)
                putInt(ARG_DEFAULT_COLOR, defaultColor)
                putBoolean(ARG_HAS_DEFAULT, true)
                putBoolean(ARG_ALPHA_ENABLED, alphaEnabled)
                putBoolean(ARG_SHOW_RESET, showReset)
            }
        }
    }
}
