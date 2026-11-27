/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package org.expressiveos.settings.fragments.lockscreen;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.android.OmniJawsClient;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.List;

import org.expressiveos.settings.studio.StudioLockHeader;
import org.expressiveos.settings.utils.DeviceUtils;
import org.expressiveos.settings.utils.PreferenceUtils;
import org.expressiveos.settings.utils.SystemUtils;

@SearchIndexable
public class LockScreen extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "LockScreen";

    private static final String KEY_FACE_ANIMATION = "face_unlock_animation";
    private static final String KEY_FP_ERROR = "fp_error_vibrate";
    private static final String KEY_FP_SUCCESS = "fp_success_vibrate";
    private static final String KEY_RIPPLE_EFFECT = "enable_ripple_effect";
    private static final String KEY_WEATHER = "lockscreen_weather_enabled";
    private static final String LOCKSCREEN_GESTURES_CATEGORY = "lockscreen_gestures_category";

    private Preference mRippleEffect;
    private SwitchPreferenceCompat mFpErrorVib;
    private SwitchPreferenceCompat mFpSuccessVib;
    private SwitchPreferenceCompat mWeather;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.studio_settings_lock_screen);
        StudioLockHeader.bind(this, findPreference(StudioLockHeader.KEY));

        final Context context = getContext();

        final PreferenceCategory gestCategory = findPreference(LOCKSCREEN_GESTURES_CATEGORY);

        mFpSuccessVib = findPreference(KEY_FP_SUCCESS);
        mFpErrorVib = findPreference(KEY_FP_ERROR);
        mRippleEffect = findPreference(KEY_RIPPLE_EFFECT);

        boolean hasFingerprint = DeviceUtils.hasFingerprint(context);
        if (!hasFingerprint && gestCategory != null && mRippleEffect != null) {
            gestCategory.removePreference(mRippleEffect);
        }

        if ((!hasFingerprint || !DeviceUtils.hasVibrator(context)) && gestCategory != null) {
            if (mFpSuccessVib != null) gestCategory.removePreference(mFpSuccessVib);
            if (mFpErrorVib != null) gestCategory.removePreference(mFpErrorVib);
        }

        if (!DeviceUtils.hasFace(context)) {
            final Preference faceAnim = findPreference(KEY_FACE_ANIMATION);
            if (faceAnim != null && faceAnim.getParent() != null) {
                faceAnim.getParent().removePreference(faceAnim);
            }
        }

        mWeather = findPreference(KEY_WEATHER);
        if (mWeather != null) {
            mWeather.setOnPreferenceChangeListener(this);
        }

        updateWeatherSettings();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mWeather) {
            SystemUtils.showSystemUiRestartDialog(getContext());
            return true;
        }
        return false;
    }

    private void updateWeatherSettings() {
        if (mWeather == null) return;
        boolean weatherEnabled = OmniJawsClient.get().isOmniJawsEnabled(getContext());
        mWeather.setEnabled(weatherEnabled);
        mWeather.setSummary(weatherEnabled
                ? R.string.lockscreen_weather_summary
                : R.string.lockscreen_weather_enabled_info);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateWeatherSettings();
        PreferenceUtils.reloadCustomPrimarySwitches(getPreferenceScreen());
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.EXPRESSIVE_STUDIO;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.studio_settings_lock_screen) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    boolean hasFingerprint = DeviceUtils.hasFingerprint(context);
                    if (!hasFingerprint) {
                        keys.add(KEY_RIPPLE_EFFECT);
                    }

                    if (!hasFingerprint || !DeviceUtils.hasVibrator(context)) {
                        keys.add(KEY_FP_SUCCESS);
                        keys.add(KEY_FP_ERROR);
                    }

                    if (!DeviceUtils.hasFace(context)) {
                        keys.add(KEY_FACE_ANIMATION);
                    }

                    return keys;
                }
            };
}
