/*
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.expressiveos.settings.preferences;

import android.content.Context;
import android.util.AttributeSet;

/**
 * A {@link SystemSettingListPreference} that opens its entries in the Expressive picker.
 */
public class SystemSettingPickerListPreference extends SystemSettingListPreference {

    public SystemSettingPickerListPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public SystemSettingPickerListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SystemSettingPickerListPreference(Context context) {
        super(context);
    }
}
