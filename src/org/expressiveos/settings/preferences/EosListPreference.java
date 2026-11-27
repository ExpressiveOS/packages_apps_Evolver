/*
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.expressiveos.settings.preferences;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceViewHolder;

/**
 * Base ListPreference for ExpressiveOS settings with XML constraint evaluation
 * and pluggable string persistence.
 */
public abstract class EosListPreference extends ListPreference {

    private final EosConstraintsHelper mConstraints;
    private ComposeDialogHandle mPickerDialog;

    public EosListPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mConstraints = new EosConstraintsHelper(context, attrs, this);
        setPreferenceDataStore(new DataStore());
    }

    public EosListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mConstraints = new EosConstraintsHelper(context, attrs, this);
        setPreferenceDataStore(new DataStore());
    }

    public EosListPreference(Context context) {
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
    protected void onClick() {
        mPickerDialog = ListPreferencePicker.openListPicker(this, mPickerDialog);
    }

    @Override
    public void onDetached() {
        if (mPickerDialog != null) {
            mPickerDialog.dismiss();
            mPickerDialog = null;
        }
        super.onDetached();
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
    protected abstract void putString(String key, String value);
    protected abstract String getString(String key, String defaultValue);

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        final String value;
        if (!restorePersistedValue || !isPersisted()) {
            if (defaultValue == null) {
                return;
            }
            value = (String) defaultValue;
            if (shouldPersist()) {
                persistString(value);
            }
        } else {
            value = getString(getKey(), null /* not used */);
        }
        setValue(value);
    }

    private class DataStore extends PreferenceDataStore {
        @Override
        public void putString(String key, String value) {
            EosListPreference.this.putString(key, value);
        }

        @Override
        public String getString(String key, String defaultValue) {
            return EosListPreference.this.getString(key, defaultValue);
        }
    }
}
