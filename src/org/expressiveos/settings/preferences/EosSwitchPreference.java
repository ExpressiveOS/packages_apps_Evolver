/*
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.expressiveos.settings.preferences;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

/**
 * Base SwitchPreferenceCompat for ExpressiveOS settings with XML constraint evaluation
 * and pluggable boolean persistence.
 */
public abstract class EosSwitchPreference extends SwitchPreferenceCompat {

    private final EosConstraintsHelper mConstraints;

    public EosSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mConstraints = new EosConstraintsHelper(context, attrs, this);
        setPreferenceDataStore(new DataStore());
    }

    public EosSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mConstraints = new EosConstraintsHelper(context, attrs, this);
        setPreferenceDataStore(new DataStore());
    }

    public EosSwitchPreference(Context context) {
        super(context);
        mConstraints = new EosConstraintsHelper(context, null, this);
        setPreferenceDataStore(new DataStore());
    }

    @Override
    public void onAttached() {
        super.onAttached();
        mConstraints.onAttached();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mConstraints.onBindViewHolder(holder);
    }

    public void setAvailable(boolean available) {
        mConstraints.setAvailable(available);
    }

    public boolean isAvailable() {
        return mConstraints.isAvailable();
    }

    protected abstract boolean isPersisted();
    protected abstract void putBoolean(String key, boolean value);
    protected abstract boolean getBoolean(String key, boolean defaultValue);

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        final boolean checked;
        if (!restorePersistedValue || !isPersisted()) {
            boolean defValue = defaultValue == null ? false : (boolean) defaultValue;
            checked = getBoolean(getKey(), defValue);
            if (shouldPersist()) {
                persistBoolean(checked);
            }
        } else {
            checked = getBoolean(getKey(), false /* not used */);
        }
        setChecked(checked);
    }

    private class DataStore extends PreferenceDataStore {
        @Override
        public void putBoolean(String key, boolean value) {
            EosSwitchPreference.this.putBoolean(key, value);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return EosSwitchPreference.this.getBoolean(key, defaultValue);
        }
    }
}
