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
 * MainSwitchPreference backed by {@link android.provider.Settings.Secure}
 * via {@link SecureSettingsStore}.
 */
public class SecureSettingMainSwitchPreference extends MainSwitchPreference
        implements GroupSectionDividerMixin {

    public SecureSettingMainSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setPreferenceDataStore(new SecureSettingsStore(context.getContentResolver()));
    }

    public SecureSettingMainSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPreferenceDataStore(new SecureSettingsStore(context.getContentResolver()));
    }

    public SecureSettingMainSwitchPreference(Context context) {
        super(context, null);
        setPreferenceDataStore(new SecureSettingsStore(context.getContentResolver()));
    }
}
