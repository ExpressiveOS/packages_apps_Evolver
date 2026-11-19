/*
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.expressiveos.settings.preferences;

import android.content.Context;
import android.util.AttributeSet;

import com.android.settingslib.widget.GroupSectionDividerMixin;
import com.android.settingslib.widget.MainSwitchPreference;

/**
 * MainSwitchPreference backed by {@link android.provider.Settings.System}
 * via {@link SystemSettingsStore}.
 */
public class SystemSettingMainSwitchPreference extends MainSwitchPreference
        implements GroupSectionDividerMixin {

    public SystemSettingMainSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setPreferenceDataStore(new SystemSettingsStore(context.getContentResolver()));
    }

    public SystemSettingMainSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPreferenceDataStore(new SystemSettingsStore(context.getContentResolver()));
    }

    public SystemSettingMainSwitchPreference(Context context) {
        super(context, null);
        setPreferenceDataStore(new SystemSettingsStore(context.getContentResolver()));
    }
}
