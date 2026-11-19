/*
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.expressiveos.settings.preferences;

import android.content.Context;
import android.util.AttributeSet;

/**
 * A {@link SecureSettingListPreference} that opens its entries in the Expressive picker.
 */
public class SecureSettingPickerListPreference extends SecureSettingListPreference {

    public SecureSettingPickerListPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public SecureSettingPickerListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SecureSettingPickerListPreference(Context context) {
        super(context);
    }
}
