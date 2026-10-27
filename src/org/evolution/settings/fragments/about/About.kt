/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.fragments.about

import android.os.Bundle
import android.view.View
import androidx.preference.Preference

import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settings.deviceinfo.firmwareversion.EvolutionLogoAnimator
import com.android.settings.search.BaseSearchIndexProvider
import com.android.settingslib.search.SearchIndexable
import com.android.settingslib.widget.LayoutPreference

@SearchIndexable
class About : SettingsPreferenceFragment(), Preference.OnPreferenceChangeListener {

    companion object {
        @JvmField
        val SEARCH_INDEX_DATA_PROVIDER =
            BaseSearchIndexProvider(R.xml.evolution_settings_about)
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.evolution_settings_about)

        findPreference<LayoutPreference>("evolution_logo")
            ?.findViewById<View>(R.id.evolution_logo_container)
            ?.let { EvolutionLogoAnimator.bind(it) }

        findPreference<Preference>("about_maintainer")?.let {
            GithubAvatarLoader.getInstance().load(requireContext(), it, "denson9874")
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?) = false

    override fun getMetricsCategory() = MetricsEvent.EVOLVER
}
