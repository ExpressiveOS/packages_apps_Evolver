/*
 * Copyright (C) 2023-2024 the risingOS Android Project
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.expressiveos.settings.fragments.themes;

import android.os.Bundle;
import android.os.SystemProperties;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.expressiveos.settings.utils.SystemUtils;

@SearchIndexable
public class Wallpaper extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    public static final String TAG = "Wallpaper";
    private static final String KEY_APPLY_BANNER = "wallpaper_styles_apply";

    private Preference mApplyBannerPref;
    private Preference mBlurWpPref;
    private Preference mBlurWpStylePref;
    private Preference mEffectTypePref;
    private Preference mEffectTargetPref;
    private Preference mSaturationPref;
    private Preference mPixelationPref;
    private Preference mVignettePref;
    private Preference mPosterizePref;
    private Preference mDimPref;
    private Preference mDimLvlPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.studio_wallpaper_styles);

        mApplyBannerPref = findPreference(KEY_APPLY_BANNER);
        if (mApplyBannerPref != null) {
            mApplyBannerPref.setOnPreferenceClickListener(pref -> {
                SystemUtils.showSystemUiRestartDialog(getContext());
                return true;
            });
        }

        mBlurWpPref = findPreference("persist.sys.wallpaper.blur_enabled");
        mBlurWpPref.setOnPreferenceChangeListener(this);

        mBlurWpStylePref = findPreference("persist.sys.wallpaper.blur_type");
        mBlurWpStylePref.setOnPreferenceChangeListener(this);

        mEffectTypePref = findPreference("persist.sys.wallpaper.effect_type");
        mEffectTypePref.setOnPreferenceChangeListener(this);

        mEffectTargetPref = findPreference("persist.sys.wallpaper.effect_target");
        mEffectTargetPref.setOnPreferenceChangeListener(this);

        mSaturationPref = findPreference("persist.sys.wallpaper.saturation_level");
        mSaturationPref.setOnPreferenceChangeListener(this);

        mPixelationPref = findPreference("persist.sys.wallpaper.pixelation_size");
        mPixelationPref.setOnPreferenceChangeListener(this);

        mVignettePref = findPreference("persist.sys.wallpaper.vignette_intensity");
        mVignettePref.setOnPreferenceChangeListener(this);

        mPosterizePref = findPreference("persist.sys.wallpaper.posterize_levels");
        mPosterizePref.setOnPreferenceChangeListener(this);

        mDimPref = findPreference("persist.sys.wallpaper.dim_enabled");
        mDimPref.setOnPreferenceChangeListener(this);

        mDimLvlPref = findPreference("persist.sys.wallpaper.dim_level");
        mDimLvlPref.setOnPreferenceChangeListener(this);

        updateEffectDependencies();
        updateBlurDependencies();
        updateDimDependencies();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mBlurWpPref
                || preference == mBlurWpStylePref
                || preference == mEffectTypePref
                || preference == mEffectTargetPref
                || preference == mSaturationPref
                || preference == mPixelationPref
                || preference == mVignettePref
                || preference == mPosterizePref
                || preference == mDimPref
                || preference == mDimLvlPref) {

            // The property is persisted by SystemPropertyListPreference / SystemPropertySeekBarPreference
            // right after this callback returns true. Update dependent visibility using newValue.
            if (preference == mEffectTypePref) {
                updateEffectDependencies(Integer.parseInt(newValue.toString()));
            } else if (preference == mBlurWpPref) {
                updateBlurDependencies(Integer.parseInt(newValue.toString()));
            } else if (preference == mDimPref) {
                updateDimDependencies(Integer.parseInt(newValue.toString()));
            }

            // Show non-blocking apply banner instead of interrupting slider drags with a restart dialog.
            if (mApplyBannerPref != null) {
                mApplyBannerPref.setVisible(true);
            }
            return true;
        }
        return false;
    }

    private void updateEffectDependencies() {
        updateEffectDependencies(
                SystemProperties.getInt("persist.sys.wallpaper.effect_type", 0));
    }

    private void updateEffectDependencies(int effectType) {
        mEffectTargetPref.setVisible(effectType > 0);
        mVignettePref.setVisible(effectType == 3);
        mPixelationPref.setVisible(effectType == 4);
        mSaturationPref.setVisible(effectType == 5);
        mPosterizePref.setVisible(effectType == 11);
    }

    private void updateBlurDependencies() {
        updateBlurDependencies(
                SystemProperties.getInt("persist.sys.wallpaper.blur_enabled", 0));
    }

    private void updateBlurDependencies(int blurEnabled) {
        mBlurWpStylePref.setVisible(blurEnabled > 0);
    }

    private void updateDimDependencies() {
        updateDimDependencies(
                SystemProperties.getInt("persist.sys.wallpaper.dim_enabled", 0));
    }

    private void updateDimDependencies(int dimEnabled) {
        mDimLvlPref.setVisible(dimEnabled > 0);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.EXPRESSIVE_STUDIO;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.studio_wallpaper_styles);
}
