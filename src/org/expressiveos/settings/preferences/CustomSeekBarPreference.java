/*
 * Copyright (C) 2016-2025 crDroid Android Project
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.expressiveos.settings.preferences;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settingslib.widget.SliderPreference;

import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

/**
 * Material 3 Expressive segmented slider preference participating natively in
 * {@code SettingsPreferenceGroupAdapter}'s rounded card sections, with a tabular-numeral tonal
 * value pill badge, inline reset pill button, accessible +/- fine-step buttons, thick pill track,
 * and outcome-gated tactile haptics.
 */
public class CustomSeekBarPreference extends SliderPreference {

    private static final String SETTINGS_NS = "http://schemas.android.com/apk/res/com.android.settings";
    private static final String ANDROIDNS = "http://schemas.android.com/apk/res/android";
    private static final float DISABLED_ALPHA = 0.38f;

    private boolean mShowSign;
    @Nullable
    private String mUnits = "";
    @Nullable
    private String mDefaultValueText;
    private boolean mDefaultValueTextExists;
    private boolean mDefaultValueExists;
    private int mDefaultValue;

    @Nullable
    private CharSequence mUserSummary;
    private boolean mInUserDrag = false;
    private int mLastHapticValue = Integer.MIN_VALUE;

    public CustomSeekBarPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.preference_custom_seekbar_expressive);
        mUserSummary = super.getSummary();
        readLegacyAttrs(context, attrs);
        initDefaults();
    }

    public CustomSeekBarPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomSeekBarPreference(@NonNull Context context) {
        this(context, null, 0);
    }

    private void initDefaults() {
        // Value is rendered live in the header's tabular-numeral secondaryContainer pill badge.
        setShowSliderValue(false);
        // Disable base tick haptic so outcome-gated step vs min/max haptics never double-fire.
        setHapticFeedbackMode(HAPTIC_FEEDBACK_MODE_NONE);
        setLabelFormater(value -> formatValueForSummary(Math.round(value)));
    }

    private void readLegacyAttrs(Context c, @Nullable AttributeSet attrs) {
        if (attrs == null) return;
        final TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.CustomSeekBarPreference);
        try {
            mShowSign = a.getBoolean(R.styleable.CustomSeekBarPreference_showSign, false);
            final String units = a.getString(R.styleable.CustomSeekBarPreference_units);
            if (units != null) mUnits = units;

            final boolean continuous = a.getBoolean(
                    R.styleable.CustomSeekBarPreference_continuousUpdates, false);
            setUpdatesContinuously(continuous);

            mDefaultValueText = a.getString(
                    R.styleable.CustomSeekBarPreference_defaultValueText);
            mDefaultValueTextExists = mDefaultValueText != null && !mDefaultValueText.isEmpty();

            String defaultValue = attrs.getAttributeValue(ANDROIDNS, "defaultValue");
            if (defaultValue == null) {
                defaultValue = attrs.getAttributeValue(SETTINGS_NS, "defaultValue");
            }
            if (defaultValue != null && !defaultValue.isEmpty()) {
                try {
                    mDefaultValue = Integer.parseInt(defaultValue);
                    mDefaultValueExists = true;
                } catch (NumberFormatException ignored) {
                    mDefaultValueExists = false;
                }
            }

            int interval = attrs.getAttributeIntValue(SETTINGS_NS, "interval", 0);
            if (interval == 0) {
                interval = attrs.getAttributeIntValue(ANDROIDNS, "interval", 0);
            }
            if (interval > 0) setSliderIncrement(interval);

            // Guard against improper slider increment
            int min = getMin();
            int max = getMax();
            int span = Math.max(0, max - min);

            int step = getSliderIncrement();
            if (step <= 0 || span == 0) {
                setSliderIncrement(1);
            } else if ((span % step) != 0) {
                int gcd = gcd(span, step);
                if (gcd <= 0) gcd = 1;
                setSliderIncrement(gcd);
            }
        } catch (Throwable ignored) {
            // keep safe defaults
        } finally {
            a.recycle();
        }
    }

    @Override
    public void setSummary(@Nullable CharSequence summary) {
        mUserSummary = summary;
        super.setSummary(summary);
    }

    @Override
    public void setValue(int sliderValue) {
        super.setValue(sliderValue);
    }

    private String formatValueForSummary(int v) {
        if (mDefaultValueExists && mDefaultValueTextExists && v == mDefaultValue) {
            return mDefaultValueText;
        }
        String s = String.valueOf(v);
        if (mShowSign && v > 0) s = "+" + s;
        if (mUnits != null && !mUnits.isEmpty()) {
            // Attach compact symbols (%, °, px) cleanly; keep a space for word units.
            if ("%".equals(mUnits) || "\u00B0".equals(mUnits) || "°".equals(mUnits)) {
                s = s + mUnits;
            } else {
                s = s + " " + mUnits;
            }
        }
        return s;
    }

    @Override
    public void setDefaultValue(Object defaultValue) {
        if (defaultValue instanceof Integer) {
            mDefaultValueExists = true;
            mDefaultValue = (Integer) defaultValue;
        }
        super.setDefaultValue(defaultValue);
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        final Context context = holder.itemView.getContext();
        final boolean enabled = isEnabled();
        final int currentValue = getValue();

        final TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        if (summaryView != null) {
            if (!TextUtils.isEmpty(mUserSummary)) {
                summaryView.setText(mUserSummary);
                summaryView.setVisibility(View.VISIBLE);
            } else {
                summaryView.setVisibility(View.GONE);
            }
        }

        final TextView valueBadge =
                (TextView) holder.findViewById(R.id.custom_seekbar_value_badge);
        if (valueBadge != null) {
            valueBadge.setText(formatValueForSummary(currentValue));
            valueBadge.setAlpha(enabled ? 1f : DISABLED_ALPHA);
        }

        final View resetButton = holder.findViewById(R.id.custom_seekbar_reset_button);
        final Slider slider = (Slider) holder.findViewById(
                com.android.settingslib.widget.preference.slider.R.id.slider);

        if (resetButton != null) {
            installButtonAccessibilityDelegate(resetButton);
            updateResetButtonState(resetButton, currentValue, false /* animate */);
            resetButton.setOnClickListener(v -> {
                if (!isEnabled() || !mDefaultValueExists || getValue() == mDefaultValue) return;
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                applyUserValue(mDefaultValue, slider, valueBadge, resetButton, holder);
            });
        }

        final View labelFrame = holder.findViewById(R.id.custom_seekbar_label_frame);
        final TextView startText = (TextView) holder.findViewById(android.R.id.text1);
        final TextView endText = (TextView) holder.findViewById(android.R.id.text2);
        if (labelFrame != null) {
            boolean hasStart = startText != null && !TextUtils.isEmpty(startText.getText());
            boolean hasEnd = endText != null && !TextUtils.isEmpty(endText.getText());
            labelFrame.setVisibility((hasStart || hasEnd) ? View.VISIBLE : View.GONE);
        }

        final ViewGroup minusFrame = (ViewGroup) holder.findViewById(
                com.android.settingslib.widget.preference.slider.R.id.icon_start_frame);
        final ImageView minusIcon = (ImageView) holder.findViewById(
                com.android.settingslib.widget.preference.slider.R.id.icon_start);

        final ViewGroup plusFrame = (ViewGroup) holder.findViewById(
                com.android.settingslib.widget.preference.slider.R.id.icon_end_frame);
        final ImageView plusIcon = (ImageView) holder.findViewById(
                com.android.settingslib.widget.preference.slider.R.id.icon_end);

        final int stepForClicks = Math.max(1, getSliderIncrement());
        final CharSequence titleText = getTitle() != null ? getTitle() : "";

        if (minusFrame != null && minusIcon != null) {
            styleStepButton(context, minusFrame, minusIcon, R.drawable.ic_custom_seekbar_minus,
                    /* isStart= */ true, titleText + " -");
            minusFrame.setOnClickListener(v -> {
                if (!isEnabled()) return;
                int base = slider != null ? Math.round(slider.getValue()) : getValue();
                int min = getMin();
                int newVal = Math.max(min, base - stepForClicks);
                if (newVal == base) return;
                v.performHapticFeedback(newVal <= min
                        ? HapticFeedbackConstants.CONFIRM
                        : HapticFeedbackConstants.CLOCK_TICK);
                applyUserValue(newVal, slider, valueBadge, resetButton, holder);
            });
        }

        if (plusFrame != null && plusIcon != null) {
            styleStepButton(context, plusFrame, plusIcon, R.drawable.ic_custom_seekbar_plus,
                    /* isStart= */ false, titleText + " +");
            plusFrame.setOnClickListener(v -> {
                if (!isEnabled()) return;
                int base = slider != null ? Math.round(slider.getValue()) : getValue();
                int max = getMax();
                int newVal = Math.min(max, base + stepForClicks);
                if (newVal == base) return;
                v.performHapticFeedback(newVal >= max
                        ? HapticFeedbackConstants.CONFIRM
                        : HapticFeedbackConstants.CLOCK_TICK);
                applyUserValue(newVal, slider, valueBadge, resetButton, holder);
            });
        }

        updatePlusMinusEnabledStates(holder, currentValue);

        if (slider != null) {
            // Enforce M3 Expressive thick pill track & crisp vertical bar thumb geometry.
            slider.setLabelBehavior(LabelFormatter.LABEL_GONE);
            slider.setTrackHeight(dp(context, 18));
            slider.setTrackInsideCornerSize(dp(context, 2));
            slider.setTrackStopIndicatorSize(dp(context, 4));
            slider.setThumbWidth(dp(context, 4));
            slider.setThumbHeight(dp(context, 40));
            slider.setThumbElevation(0f);
            slider.setThumbStrokeWidth(0f);
            slider.setThumbTrackGapSize(dp(context, 6));

            mLastHapticValue = currentValue;
            final String initialStateDesc = formatValueForSummary(currentValue);
            slider.setStateDescription(initialStateDesc);

            slider.addOnChangeListener((s, value, fromUser) -> {
                final int rounded = Math.round(value);
                if (valueBadge != null) {
                    valueBadge.setText(formatValueForSummary(rounded));
                }
                s.setStateDescription(formatValueForSummary(rounded));
                if (resetButton != null) {
                    updateResetButtonState(resetButton, rounded, fromUser /* animate */);
                }
                updatePlusMinusEnabledStates(holder, rounded);

                if (fromUser && rounded != mLastHapticValue) {
                    mLastHapticValue = rounded;
                    if (rounded <= getMin() || rounded >= getMax()) {
                        s.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                    } else {
                        s.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                }
            });

            slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(@NonNull Slider s) {
                    mInUserDrag = true;
                    mLastHapticValue = Math.round(s.getValue());
                }

                @Override
                public void onStopTrackingTouch(@NonNull Slider s) {
                    mInUserDrag = false;
                    final int finalVal = Math.round(s.getValue());
                    if (valueBadge != null) {
                        valueBadge.setText(formatValueForSummary(finalVal));
                    }
                    if (resetButton != null) {
                        updateResetButtonState(resetButton, finalVal, true);
                    }
                    updatePlusMinusEnabledStates(holder, finalVal);
                }
            });
        }
    }

    @Override
    public void onDependencyChanged(@NonNull Preference dependency, boolean disableDependent) {
        super.onDependencyChanged(dependency, disableDependent);
        notifyChanged();
    }

    private void styleStepButton(@NonNull Context context, @NonNull ViewGroup frame,
            @NonNull ImageView icon, int drawableRes, boolean isStart,
            @NonNull CharSequence contentDescription) {
        final int sizePx = dp(context, 36);
        final int iconPx = dp(context, 18);
        final int gapPx = dp(context, 4);

        ViewGroup.LayoutParams rawLp = frame.getLayoutParams();
        if (rawLp instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rawLp;
            lp.width = sizePx;
            lp.height = sizePx;
            if (isStart) {
                lp.setMarginEnd(gapPx);
            } else {
                lp.setMarginStart(gapPx);
            }
            frame.setLayoutParams(lp);
        }

        frame.setVisibility(View.VISIBLE);
        frame.setBackgroundResource(R.drawable.bg_custom_seekbar_step_button);
        frame.setClickable(true);
        frame.setFocusable(true);
        frame.setContentDescription(contentDescription);
        installButtonAccessibilityDelegate(frame);

        ViewGroup.LayoutParams iconLp = icon.getLayoutParams();
        if (iconLp != null) {
            iconLp.width = iconPx;
            iconLp.height = iconPx;
            icon.setLayoutParams(iconLp);
        }
        icon.setBackground(null);
        icon.setImageResource(drawableRes);
        icon.setImageTintList(ColorStateList.valueOf(
                context.getColor(com.android.settingslib.widget.theme.R.color.settingslib_materialColorOnSurface)));
    }

    private static void installButtonAccessibilityDelegate(@NonNull View view) {
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                    @NonNull AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
    }

    private void updateResetButtonState(@NonNull View resetButton, int currentValue,
            boolean animate) {
        final boolean show = mDefaultValueExists && isEnabled() && currentValue != mDefaultValue;
        resetButton.setEnabled(show);
        resetButton.setClickable(show);
        resetButton.animate().cancel();
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            resetButton.setAlpha(show ? 1f : 0f);
            resetButton.setScaleX(1f);
            resetButton.setScaleY(1f);
            resetButton.setVisibility(show ? View.VISIBLE : View.GONE);
            return;
        }
        if (show) {
            if (resetButton.getVisibility() != View.VISIBLE) {
                resetButton.setAlpha(0f);
                resetButton.setScaleX(0.8f);
                resetButton.setScaleY(0.8f);
                resetButton.setVisibility(View.VISIBLE);
            }
            resetButton.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150L)
                    .start();
        } else if (resetButton.getVisibility() == View.VISIBLE) {
            resetButton.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(120L)
                    .withEndAction(() -> resetButton.setVisibility(View.GONE))
                    .start();
        }
    }

    private void applyUserValue(int newVal, @Nullable Slider slider,
            @Nullable TextView valueBadge, @Nullable View resetButton,
            @NonNull PreferenceViewHolder holder) {
        final int clamped = Math.max(getMin(), Math.min(getMax(), newVal));
        if (clamped == getValue()) {
            if (slider != null && Math.round(slider.getValue()) != clamped) {
                slider.setValue(clamped);
            }
            return;
        }
        if (!callChangeListener(clamped)) {
            if (slider != null) slider.setValue(getValue());
            return;
        }
        mLastHapticValue = clamped;
        setValue(clamped);
        if (slider != null && Math.round(slider.getValue()) != clamped) {
            slider.setValue(clamped);
        }
        if (valueBadge != null) {
            valueBadge.setText(formatValueForSummary(clamped));
        }
        if (resetButton != null) {
            updateResetButtonState(resetButton, clamped, true);
        }
        updatePlusMinusEnabledStates(holder, clamped);
    }

    private static int gcd(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        if (a == 0) return b;
        if (b == 0) return a;
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    private void updatePlusMinusEnabledStates(@NonNull PreferenceViewHolder holder, int value) {
        final View minusFrame = holder.findViewById(
                com.android.settingslib.widget.preference.slider.R.id.icon_start_frame);
        final ImageView minusIcon = (ImageView) holder.findViewById(
                com.android.settingslib.widget.preference.slider.R.id.icon_start);
        final View plusFrame = holder.findViewById(
                com.android.settingslib.widget.preference.slider.R.id.icon_end_frame);
        final ImageView plusIcon = (ImageView) holder.findViewById(
                com.android.settingslib.widget.preference.slider.R.id.icon_end);
        final boolean enabled = isEnabled();

        if (minusFrame != null && minusIcon != null) {
            final boolean canDecrease = enabled && (value > getMin());
            minusFrame.setEnabled(canDecrease);
            minusIcon.setEnabled(canDecrease);
            minusFrame.setAlpha(canDecrease ? 1f : DISABLED_ALPHA);
        }
        if (plusFrame != null && plusIcon != null) {
            final boolean canIncrease = enabled && (value < getMax());
            plusFrame.setEnabled(canIncrease);
            plusIcon.setEnabled(canIncrease);
            plusFrame.setAlpha(canIncrease ? 1f : DISABLED_ALPHA);
        }
    }

    private static int dp(@NonNull Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
