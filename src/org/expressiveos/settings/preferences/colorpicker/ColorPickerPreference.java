/*
 * Copyright (C) 2010 Daniel Nilsson
 *               2013 Slimroms
 *               2015 The TeamEos Project
 *               2020-2021 crDroid Android Project
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.expressiveos.settings.preferences.colorpicker;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

/**
 * Material 3 Expressive color picker preference participating natively in
 * {@code SettingsPreferenceGroupAdapter} segmented cards, with a tabular-numeral hex summary,
 * inline tonal reset pill button, outlined squircle color swatch, and an M3 Expressive picker.
 */
public class ColorPickerPreference extends Preference {

    private static final String ANDROIDNS = "http://schemas.android.com/apk/res/android";
    private static final String SETTINGS_NS = "http://schemas.android.com/apk/res/com.android.settings";
    private static final float DISABLED_ALPHA = 0.38f;

    PreferenceViewHolder mView;
    LinearLayout mWidgetFrameView;
    private int mDefaultValue = Color.BLACK;
    private int mCurrentValue = mDefaultValue;
    private String mCurrentHexValue;
    private float mDensity = 0;
    private boolean mAlphaSliderEnabled = false;
    private boolean mShowReset;
    private boolean mShowPreview;
    private boolean mDividerAbove;
    private boolean mDividerBelow;
    private boolean mAutoSummary = true;

    public ColorPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ColorPickerPreference(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.preferenceStyle);
    }

    public ColorPickerPreference(Context context) {
        this(context, null);
    }

    public ColorPickerPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray ta, int index) {
        return ta.getInt(index, Color.BLACK);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (defaultValue == null) {
            defaultValue = mDefaultValue;
        }
        mCurrentValue = getPersistedInt((Integer) defaultValue);
        setColor(mCurrentValue);
    }

    private void init(Context context, AttributeSet attrs) {
        mDensity = getContext().getResources().getDisplayMetrics().density;
        if (attrs != null) {
            mAlphaSliderEnabled = attrs.getAttributeBooleanValue(null, "alphaSlider", false)
                    || attrs.getAttributeBooleanValue(SETTINGS_NS, "alphaSlider", false);
            mDefaultValue = attrs.getAttributeIntValue(ANDROIDNS, "defaultValue",
                    attrs.getAttributeIntValue(SETTINGS_NS, "defaultValue", Color.BLACK));
            mShowReset = attrs.getAttributeBooleanValue(SETTINGS_NS, "showReset", true);
            mShowPreview = attrs.getAttributeBooleanValue(SETTINGS_NS, "showPreview", true);
            mDividerAbove = attrs.getAttributeBooleanValue(SETTINGS_NS, "dividerAbove", false);
            mDividerBelow = attrs.getAttributeBooleanValue(SETTINGS_NS, "dividerBelow", false);
        }
    }

    @Override
    public void onAttached() {
        super.onAttached();
        registerResultListener();
    }

    @Override
    public void onDetached() {
        FragmentActivity activity = getFragmentActivity();
        if (activity != null) {
            activity.getSupportFragmentManager().clearFragmentResultListener(getRequestKey());
        }
        super.onDetached();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        mView = view;
        super.onBindViewHolder(view);
        view.setDividerAllowedAbove(mDividerAbove);
        view.setDividerAllowedBelow(mDividerBelow);

        final View summaryView = view.findViewById(android.R.id.summary);
        if (summaryView instanceof TextView && mAutoSummary) {
            ((TextView) summaryView).setFontFeatureSettings("tnum");
        }

        view.itemView.setOnClickListener(v -> {
            if (!isEnabled()) return;
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            showDialog();
        });

        mWidgetFrameView = (LinearLayout) view.findViewById(android.R.id.widget_frame);
        if (mWidgetFrameView != null) {
            mWidgetFrameView.setOrientation(LinearLayout.HORIZONTAL);
            mWidgetFrameView.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            mWidgetFrameView.setVisibility(View.VISIBLE);
            mWidgetFrameView.setMinimumWidth(0);
            mWidgetFrameView.setPaddingRelative(
                    mWidgetFrameView.getPaddingStart(),
                    mWidgetFrameView.getPaddingTop(),
                    dp(4),
                    mWidgetFrameView.getPaddingBottom());
            mWidgetFrameView.removeAllViews();
            setDefaultButton();
            setPreviewColor();
        }
    }

    private void setDefaultButton() {
        if (!mShowReset || mView == null || mWidgetFrameView == null) {
            return;
        }

        View oldView = mWidgetFrameView.findViewWithTag("default");
        if (oldView != null) {
            mWidgetFrameView.removeView(oldView);
        }

        final int normalizedCurrent = mAlphaSliderEnabled
                ? mCurrentValue : (mCurrentValue | 0xFF000000);
        final int normalizedDefault = mAlphaSliderEnabled
                ? mDefaultValue : (mDefaultValue | 0xFF000000);
        if (normalizedCurrent == normalizedDefault) {
            return;
        }

        final Context context = getContext();
        FrameLayout resetTarget = new FrameLayout(context);
        resetTarget.setTag("default");
        LinearLayout.LayoutParams targetLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        targetLp.setMarginEnd(dp(4));
        resetTarget.setLayoutParams(targetLp);
        resetTarget.setContentDescription(context.getString(R.string.color_picker_reset));
        resetTarget.setEnabled(isEnabled());
        resetTarget.setClickable(isEnabled());
        resetTarget.setFocusable(isEnabled());
        resetTarget.setAlpha(isEnabled() ? 1f : DISABLED_ALPHA);
        resetTarget.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });

        FrameLayout resetPill = new FrameLayout(context);
        resetPill.setLayoutParams(new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
        resetPill.setBackgroundResource(R.drawable.bg_custom_seekbar_reset_pill);
        resetPill.setDuplicateParentStateEnabled(true);

        ImageView resetIcon = new ImageView(context);
        FrameLayout.LayoutParams iconLp =
                new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER);
        resetIcon.setLayoutParams(iconLp);
        resetIcon.setImageResource(R.drawable.ic_custom_seekbar_reset);
        resetIcon.setImageTintList(ColorStateList.valueOf(context.getColor(
                com.android.settingslib.widget.theme.R.color.settingslib_materialColorOnSurfaceVariant)));
        resetIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        resetPill.addView(resetIcon);
        resetTarget.addView(resetPill);

        resetTarget.setOnClickListener(v -> {
            if (!isEnabled()) return;
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            onColorChanged(mDefaultValue);
        });
        mWidgetFrameView.addView(resetTarget, 0);
    }

    private void setPreviewColor() {
        if (!mShowPreview || mView == null || mWidgetFrameView == null) {
            return;
        }

        View oldPreview = mWidgetFrameView.findViewWithTag("preview");
        if (oldPreview != null) {
            mWidgetFrameView.removeView(oldPreview);
        }

        final Context context = getContext();
        final int size = dp(32);
        View swatchView = new View(context);
        swatchView.setTag("preview");
        swatchView.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        swatchView.setAlpha(isEnabled() ? 1f : DISABLED_ALPHA);
        swatchView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        final int swatchColor = mAlphaSliderEnabled
                ? mCurrentValue : (0xFF000000 | mCurrentValue);
        final int borderColor = context.getColor(
                com.android.settingslib.widget.theme.R.color.settingslib_materialColorOutlineVariant);
        swatchView.setBackground(createSwatchDrawable(swatchColor, borderColor));
        mWidgetFrameView.addView(swatchView);
    }

    private GradientDrawable createSwatchDrawable(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(mDensity * 12f);
        drawable.setColor(fillColor);
        drawable.setStroke(Math.max(1, Math.round(mDensity * 1.5f)), strokeColor);
        return drawable;
    }

    /**
     * Updates the swatch and summary to {@code color} without persisting it or
     * notifying the change listener.
     */
    public void setColor(int color) {
        mCurrentValue = color;
        mCurrentHexValue = formatSummary(color);
        if (mAutoSummary) setSummary(mCurrentHexValue);
        setDefaultButton();
        setPreviewColor();
    }

    /** #RRGGBB, or #AARRGGBB when the alpha slider is enabled. */
    private String formatSummary(int color) {
        return mAlphaSliderEnabled
                ? String.format("#%08X", color)
                : String.format("#%06X", color & 0xFFFFFF);
    }

    public void onColorChanged(int color) {
        if (!callChangeListener(color)) {
            return;
        }
        mCurrentValue = color;
        mCurrentHexValue = formatSummary(color);
        if (mAutoSummary) setSummary(mCurrentHexValue);
        setDefaultButton();
        setPreviewColor();
        persistInt(color);
    }

    protected void showDialog() {
        if (!isEnabled()) return;

        FragmentActivity activity = getFragmentActivity();
        if (activity == null) return;

        FragmentManager fm = activity.getSupportFragmentManager();
        if (fm.findFragmentByTag(ColorPickerPrefDialogFragment.TAG) != null) return;

        registerResultListener();
        CharSequence title = getTitle();
        ColorPickerPrefDialogFragment.newInstance(
                getRequestKey(),
                title != null ? title.toString() : null,
                mCurrentValue,
                mDefaultValue,
                mAlphaSliderEnabled,
                mShowReset)
                .show(fm, ColorPickerPrefDialogFragment.TAG);
    }

    private void registerResultListener() {
        FragmentActivity activity = getFragmentActivity();
        if (activity == null) return;
        activity.getSupportFragmentManager().setFragmentResultListener(getRequestKey(), activity,
                (requestKey, result) -> onColorChanged(
                        result.getInt(ColorPickerPrefDialogFragment.RESULT_COLOR)));
    }

    private String getRequestKey() {
        return "color_picker_pref:" + getKey();
    }

    private FragmentActivity getFragmentActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof FragmentActivity) {
                return (FragmentActivity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public void setAlphaSliderEnabled(boolean enable) {
        mAlphaSliderEnabled = enable;
    }

    public static String convertToRGB(int color) {
        return String.format("#%02x%02x%02x", Color.red(color), Color.green(color),
                Color.blue(color));
    }

    public void setNewPreviewColor(int color) {
        onColorChanged(color);
    }

    public void setDefaultValue(int value) {
        mDefaultValue = value;
        setDefaultButton();
    }

    public static String convertToARGB(int color) {
        return String.format("#%02x%02x%02x%02x", Color.alpha(color), Color.red(color),
                Color.green(color), Color.blue(color));
    }

    public static int convertToColorInt(String argb) throws NumberFormatException {
        if (argb.startsWith("#")) {
            argb = argb.replace("#", "");
        }

        int alpha = -1, red = -1, green = -1, blue = -1;
        if (argb.length() == 8) {
            alpha = Integer.parseInt(argb.substring(0, 2), 16);
            red = Integer.parseInt(argb.substring(2, 4), 16);
            green = Integer.parseInt(argb.substring(4, 6), 16);
            blue = Integer.parseInt(argb.substring(6, 8), 16);
        } else if (argb.length() == 6) {
            alpha = 255;
            red = Integer.parseInt(argb.substring(0, 2), 16);
            green = Integer.parseInt(argb.substring(2, 4), 16);
            blue = Integer.parseInt(argb.substring(4, 6), 16);
        }

        return Color.argb(alpha, red, green, blue);
    }

    public void setAutoSummaryEnabled(boolean enable) {
        mAutoSummary = enable;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setDefaultButton();
        setPreviewColor();
    }

    private int dp(int value) {
        return Math.round(mDensity * value);
    }
}
