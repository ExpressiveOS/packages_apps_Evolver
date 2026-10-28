/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings;

import android.os.Bundle;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.evolution.settings.studio.StudioActions;
import org.evolution.settings.studio.StudioHome;

@SearchIndexable
public class EvolutionSettings extends DashboardFragment {

    private static final String TAG = "EvolutionSettings";

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.evolution_settings;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        StudioHome.bind(this, findPreference(StudioHome.KEY));
        // A search result for one of the rows the home hides opens that row's page, once
        final Bundle args = getArguments();
        final String key = args == null ? null
                : args.getString(SettingsActivity.EXTRA_FRAGMENT_ARG_KEY);
        if (savedInstanceState == null && key != null && !StudioHome.KEY.equals(key)) {
            new StudioActions(this).open(key);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.EVOLVER;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.evolution_settings);
}
