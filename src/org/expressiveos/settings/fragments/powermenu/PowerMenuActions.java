/*
 * Copyright (C) 2016-2024 crDroid Android Project
 * Copyright (C) 2026 The ExpressiveOS Project
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

package org.expressiveos.settings.fragments.powermenu;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.service.controls.ControlsProviderService;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.applications.ServiceListing;
import com.android.settingslib.search.SearchIndexable;

import org.expressiveos.settings.utils.TelephonyUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SearchIndexable
public class PowerMenuActions extends SettingsPreferenceFragment {
    final static String TAG = "PowerMenuActions";

    private static final String CATEGORY_POWER_MENU_ITEMS = "power_menu_items";
    private static final String SETTING_POWER_MENU_ACTIONS = "power_menu_actions";

    private static final String GLOBAL_ACTION_KEY_POWER = "power";
    private static final String GLOBAL_ACTION_KEY_RESTART = "restart";
    private static final String GLOBAL_ACTION_KEY_SCREENSHOT = "screenshot";
    private static final String GLOBAL_ACTION_KEY_ONTHEGO = "onthego";
    private static final String GLOBAL_ACTION_KEY_AIRPLANE = "airplane";
    private static final String GLOBAL_ACTION_KEY_USERS = "users";
    private static final String GLOBAL_ACTION_KEY_SETTINGS = "settings";
    private static final String GLOBAL_ACTION_KEY_LOCKDOWN = "lockdown";
    private static final String GLOBAL_ACTION_KEY_BUGREPORT = "bugreport";
    private static final String GLOBAL_ACTION_KEY_SILENT = "silent";
    private static final String GLOBAL_ACTION_KEY_VOICEASSIST = "voiceassist";
    private static final String GLOBAL_ACTION_KEY_ASSIST = "assist";
    private static final String GLOBAL_ACTION_KEY_LOGOUT = "logout";
    private static final String GLOBAL_ACTION_KEY_EMERGENCY = "emergency";
    private static final String GLOBAL_ACTION_KEY_DEVICECONTROLS = "devicecontrols";
    private static final String GLOBAL_ACTION_KEY_SYSTEM_UPDATE = "system_update";
    private static final String GLOBAL_ACTION_KEY_STANDBY = "standby";
    private static final String GLOBAL_ACTION_KEY_LOCK = "lock";

    private static final String[] ALL_ACTIONS = {
        GLOBAL_ACTION_KEY_EMERGENCY,
        GLOBAL_ACTION_KEY_LOCKDOWN,
        GLOBAL_ACTION_KEY_POWER,
        GLOBAL_ACTION_KEY_RESTART,
        GLOBAL_ACTION_KEY_SCREENSHOT,
        GLOBAL_ACTION_KEY_ONTHEGO,
        GLOBAL_ACTION_KEY_AIRPLANE,
        GLOBAL_ACTION_KEY_USERS,
        GLOBAL_ACTION_KEY_SETTINGS,
        GLOBAL_ACTION_KEY_BUGREPORT,
        GLOBAL_ACTION_KEY_SILENT,
        GLOBAL_ACTION_KEY_VOICEASSIST,
        GLOBAL_ACTION_KEY_ASSIST,
        GLOBAL_ACTION_KEY_DEVICECONTROLS,
        GLOBAL_ACTION_KEY_LOGOUT,
        GLOBAL_ACTION_KEY_SYSTEM_UPDATE,
        GLOBAL_ACTION_KEY_STANDBY,
        GLOBAL_ACTION_KEY_LOCK,
    };

    private PreferenceCategory mPowerMenuItemsCategory;

    private SwitchPreferenceCompat mScreenshotPref;
    private SwitchPreferenceCompat mOnTheGoPref;
    private SwitchPreferenceCompat mAirplanePref;
    private SwitchPreferenceCompat mUsersPref;
    private SwitchPreferenceCompat mLockDownPref;
    private SwitchPreferenceCompat mEmergencyPref;
    private SwitchPreferenceCompat mDeviceControlsPref;

    Context mContext;
    private LockPatternUtils mLockPatternUtils;
    private UserManager mUserManager;

    private List<String> getUserConfig() {
        final ContentResolver resolver = mContext.getContentResolver();
        final String savedActions = Settings.Secure.getStringForUser(
                resolver, SETTING_POWER_MENU_ACTIONS, UserHandle.USER_CURRENT);
        final String[] array;
        if (savedActions == null) {
            array = mContext.getResources().getStringArray(
                    com.android.internal.R.array.config_globalActionsList);
        } else if (savedActions.isEmpty()) {
            array = new String[0];
        } else {
            array = savedActions.split("\\|");
        }
        return new ArrayList<>(Arrays.asList(array));
    }

    private boolean userConfigContains(String action) {
        return getUserConfig().contains(action);
    }

    private void updateUserConfig(boolean enabled, String action) {
        final List<String> config = getUserConfig();
        if (enabled) {
            if (!config.contains(action)) {
                config.add(action);
            }
        } else {
            config.remove(action);
        }

        final List<String> ordered = new ArrayList<>();
        for (String candidate : ALL_ACTIONS) {
            if (config.contains(candidate)) {
                ordered.add(candidate);
            }
        }
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                SETTING_POWER_MENU_ACTIONS,
                String.join("|", ordered),
                UserHandle.USER_CURRENT);
    }

    private void removePowerMenuPreference(Preference pref) {
        if (pref == null) return;
        if (mPowerMenuItemsCategory != null) {
            mPowerMenuItemsCategory.removePreference(pref);
        } else {
            getPreferenceScreen().removePreference(pref);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.power_menu_actions);
        mContext = getActivity().getApplicationContext();
        mLockPatternUtils = new LockPatternUtils(mContext);
        mUserManager = UserManager.get(mContext);

        mPowerMenuItemsCategory = findPreference(CATEGORY_POWER_MENU_ITEMS);

        for (String action : ALL_ACTIONS) {
            if (action.equals(GLOBAL_ACTION_KEY_SCREENSHOT)) {
                mScreenshotPref = (SwitchPreferenceCompat) findPreference(GLOBAL_ACTION_KEY_SCREENSHOT);
            } else if (action.equals(GLOBAL_ACTION_KEY_ONTHEGO)) {
                mOnTheGoPref = (SwitchPreferenceCompat) findPreference(GLOBAL_ACTION_KEY_ONTHEGO);
            } else if (action.equals(GLOBAL_ACTION_KEY_AIRPLANE)) {
                mAirplanePref = (SwitchPreferenceCompat) findPreference(GLOBAL_ACTION_KEY_AIRPLANE);
            } else if (action.equals(GLOBAL_ACTION_KEY_USERS)) {
                mUsersPref = (SwitchPreferenceCompat) findPreference(GLOBAL_ACTION_KEY_USERS);
            } else if (action.equals(GLOBAL_ACTION_KEY_LOCKDOWN)) {
                mLockDownPref = (SwitchPreferenceCompat) findPreference(GLOBAL_ACTION_KEY_LOCKDOWN);
            } else if (action.equals(GLOBAL_ACTION_KEY_EMERGENCY)) {
                mEmergencyPref = (SwitchPreferenceCompat) findPreference(GLOBAL_ACTION_KEY_EMERGENCY);
            } else if (action.equals(GLOBAL_ACTION_KEY_DEVICECONTROLS)) {
                mDeviceControlsPref = findPreference(GLOBAL_ACTION_KEY_DEVICECONTROLS);
            }
        }

        if (!TelephonyUtils.isVoiceCapable(getActivity())) {
            removePowerMenuPreference(mEmergencyPref);
            mEmergencyPref = null;
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mScreenshotPref != null) {
            mScreenshotPref.setChecked(userConfigContains(GLOBAL_ACTION_KEY_SCREENSHOT));
        }

        if (mOnTheGoPref != null) {
            mOnTheGoPref.setChecked(userConfigContains(GLOBAL_ACTION_KEY_ONTHEGO));
        }

        if (mAirplanePref != null) {
            mAirplanePref.setChecked(userConfigContains(GLOBAL_ACTION_KEY_AIRPLANE));
        }

        if (mEmergencyPref != null) {
            mEmergencyPref.setChecked(userConfigContains(GLOBAL_ACTION_KEY_EMERGENCY));
        }

        if (mDeviceControlsPref != null) {
            mDeviceControlsPref.setChecked(userConfigContains(GLOBAL_ACTION_KEY_DEVICECONTROLS));

            // Enable preference if any device control app is installed
            ServiceListing serviceListing = new ServiceListing.Builder(mContext)
                    .setIntentAction(ControlsProviderService.SERVICE_CONTROLS)
                    .setPermission(Manifest.permission.BIND_CONTROLS)
                    .setNoun("Controls Provider")
                    .setSetting("controls_providers")
                    .setTag("controls_providers")
                    .build();
            serviceListing.addCallback(
                    services -> mDeviceControlsPref.setEnabled(!services.isEmpty()));
            serviceListing.reload();
        }

        updatePreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        boolean value;

        if (preference == mScreenshotPref) {
            value = mScreenshotPref.isChecked();
            updateUserConfig(value, GLOBAL_ACTION_KEY_SCREENSHOT);

        } else if (preference == mOnTheGoPref) {
            value = mOnTheGoPref.isChecked();
            updateUserConfig(value, GLOBAL_ACTION_KEY_ONTHEGO);

        } else if (preference == mAirplanePref) {
            value = mAirplanePref.isChecked();
            updateUserConfig(value, GLOBAL_ACTION_KEY_AIRPLANE);

        } else if (preference == mUsersPref) {
            value = mUsersPref.isChecked();
            updateUserConfig(value, GLOBAL_ACTION_KEY_USERS);

        } else if (preference == mLockDownPref) {
            value = mLockDownPref.isChecked();
            updateUserConfig(value, GLOBAL_ACTION_KEY_LOCKDOWN);

        } else if (preference == mEmergencyPref) {
            value = mEmergencyPref.isChecked();
            updateUserConfig(value, GLOBAL_ACTION_KEY_EMERGENCY);

        } else if (preference == mDeviceControlsPref) {
            value = mDeviceControlsPref.isChecked();
            updateUserConfig(value, GLOBAL_ACTION_KEY_DEVICECONTROLS);

        } else {
            return super.onPreferenceTreeClick(preference);
        }
        return true;
    }

    private void updatePreferences() {
        boolean isKeyguardSecure = mLockPatternUtils.isSecure(UserHandle.myUserId());
        if (mLockDownPref != null) {
            mLockDownPref.setEnabled(isKeyguardSecure);
            mLockDownPref.setChecked(userConfigContains(GLOBAL_ACTION_KEY_LOCKDOWN));
            if (isKeyguardSecure) {
                mLockDownPref.setSummary(null);
            } else {
                mLockDownPref.setSummary(R.string.power_menu_actions_lockdown_unavailable);
            }
        }
        if (mUsersPref != null) {
            if (!UserHandle.MU_ENABLED || !UserManager.supportsMultipleUsers()) {
                removePowerMenuPreference(mUsersPref);
                mUsersPref = null;
            } else {
                List<UserInfo> users = mUserManager.getUsers();
                boolean enabled = (users.size() > 1);
                mUsersPref.setChecked(userConfigContains(GLOBAL_ACTION_KEY_USERS) && enabled);
                mUsersPref.setEnabled(enabled);
            }
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.EXPRESSIVE_STUDIO;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.power_menu_actions);
}
