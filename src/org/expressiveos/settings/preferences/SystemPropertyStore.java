/*
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.expressiveos.settings.preferences;

import android.os.SystemProperties;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

import com.android.settingslib.development.SystemPropPoker;

/** PreferenceDataStore backed directly by Android SystemProperties. */
public class SystemPropertyStore extends PreferenceDataStore {

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return SystemProperties.getBoolean(key, defValue);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        SystemProperties.set(key, Boolean.toString(value));
        SystemPropPoker.getInstance().poke();
    }

    @Override
    public int getInt(String key, int defValue) {
        return SystemProperties.getInt(key, defValue);
    }

    @Override
    public void putInt(String key, int value) {
        SystemProperties.set(key, Integer.toString(value));
        SystemPropPoker.getInstance().poke();
    }

    @Override
    public long getLong(String key, long defValue) {
        return SystemProperties.getLong(key, defValue);
    }

    @Override
    public void putLong(String key, long value) {
        SystemProperties.set(key, Long.toString(value));
        SystemPropPoker.getInstance().poke();
    }

    @Override
    public float getFloat(String key, float defValue) {
        String raw = SystemProperties.get(key, "");
        if (raw.isEmpty()) return defValue;
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    @Override
    public void putFloat(String key, float value) {
        SystemProperties.set(key, Float.toString(value));
        SystemPropPoker.getInstance().poke();
    }

    @Override
    @Nullable
    public String getString(String key, @Nullable String defValue) {
        String raw = SystemProperties.get(key, "");
        return raw.isEmpty() ? defValue : raw;
    }

    @Override
    public void putString(String key, @Nullable String value) {
        SystemProperties.set(key, value != null ? value : "");
        SystemPropPoker.getInstance().poke();
    }
}
