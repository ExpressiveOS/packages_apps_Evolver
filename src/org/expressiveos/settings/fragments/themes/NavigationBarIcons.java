/*
 * Copyright (C) 2022-2025 crDroid Android Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.expressiveos.ThemeUtils;
import com.android.internal.util.expressiveos.Utils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.search.SearchIndexableRaw;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SearchIndexable
public class NavigationBarIcons extends SettingsPreferenceFragment {

    private static final ExecutorService OVERLAY_EXECUTOR = Executors.newSingleThreadExecutor();

    private RecyclerView mRecyclerView;
    private ThemeUtils mThemeUtils;
    private final String mCategory = "android.theme.customization.navbar";

    private List<String> mPkgs;
    private String mLauncherPackage;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().setTitle(R.string.themes_navigation_bar_icons_title);

        mThemeUtils = ThemeUtils.getInstance(requireActivity());
        mLauncherPackage = getNavBarPackage();
        mPkgs = mThemeUtils.getOverlayPackagesForCategory(mCategory, mLauncherPackage);
    }

    /**
     * The 3-button bar on phones is drawn by the Recents app's taskbar (Expressive Launcher,
     * or Pixel Launcher in Pixel mode), so the style overlays must target that package.
     */
    private String getNavBarPackage() {
        final String recents = getResources().getString(
                com.android.internal.R.string.config_recentsComponentName);
        final ComponentName component = TextUtils.isEmpty(recents)
                ? null : ComponentName.unflattenFromString(recents);
        if (component != null
                && Utils.isPackageInstalled(getContext(), component.getPackageName(), false)) {
            return component.getPackageName();
        }
        return Utils.isPackageInstalled(getContext(), "com.google.android.apps.nexuslauncher", false)
                ? "com.google.android.apps.nexuslauncher"
                : "com.android.launcher3";
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.item_view, container, false);

        mRecyclerView = view.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new GridLayoutManager(requireActivity(), 2));
        mRecyclerView.setAdapter(new Adapter(requireActivity()));

        return view;
    }

    @Override
    public void setDivider(@Nullable final Drawable divider) {
        RecyclerView list = getListView();
        if (list == null) {
            View root = getView();
            if (root != null) {
                root.post(() -> setDivider(divider));
            }
            return;
        }
        super.setDivider(divider);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mRecyclerView != null) {
            mRecyclerView.setAdapter(null);
            mRecyclerView = null;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.EXPRESSIVE_STUDIO;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
                    SearchIndexableRaw raw = new SearchIndexableRaw(context);
                    raw.key = "android.theme.customization.navbar";
                    raw.title = context.getString(R.string.themes_navigation_bar_icons_title);
                    raw.summaryOn = context.getString(R.string.themes_navigation_bar_icons_summary);
                    raw.screenTitle = context.getString(R.string.themes_navigation_bar_icons_title);
                    return Collections.singletonList(raw);
                }
            };

    public class Adapter extends RecyclerView.Adapter<Adapter.CustomViewHolder> {
        final Context context;
        String mSelectedPkg;
        String mAppliedPkg;

        public Adapter(Context context) {
            this.context = context;
            mAppliedPkg = mThemeUtils.getOverlayInfos(mCategory, mLauncherPackage).stream()
                    .filter(info -> info.isEnabled())
                    .map(info -> info.packageName)
                    .findFirst()
                    .orElse(mLauncherPackage);
            mSelectedPkg = mAppliedPkg;
        }

        @NonNull
        @Override
        public CustomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.navbar_option, parent, false);
            return new CustomViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull CustomViewHolder holder, final int position) {
            String navPkg = mPkgs.get(position);

            holder.image1.setBackground(getDrawable(holder.image1.getContext(), navPkg, "ic_sysbar_back"));
            holder.image2.setBackground(getDrawable(holder.image2.getContext(), navPkg, "ic_sysbar_home"));
            holder.image3.setBackground(getDrawable(holder.image3.getContext(), navPkg, "ic_sysbar_recent"));

            String label = Objects.equals(mLauncherPackage, navPkg)
                    ? holder.name.getContext().getString(R.string.themes_default_label)
                    : getLabel(holder.name.getContext(), navPkg);
            holder.name.setText(label);

            holder.itemView.setActivated(Objects.equals(navPkg, mSelectedPkg));
            holder.itemView.setOnClickListener(v -> {
                if (!Objects.equals(navPkg, mSelectedPkg)) {
                    String prevSelected = mSelectedPkg;
                    mSelectedPkg = navPkg;
                    mAppliedPkg = navPkg;
                    updateActivatedStatus(prevSelected, false);
                    updateActivatedStatus(navPkg, true);
                    enableOverlays(navPkg);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mPkgs.size();
        }

        public class CustomViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            ImageView image1;
            ImageView image2;
            ImageView image3;
            public CustomViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.option_label);
                image1 = itemView.findViewById(R.id.image1);
                image2 = itemView.findViewById(R.id.image2);
                image3 = itemView.findViewById(R.id.image3);
            }
        }

        private void updateActivatedStatus(String pkg, boolean isActivated) {
            if (pkg == null || mRecyclerView == null) return;
            int index = mPkgs.indexOf(pkg);
            if (index < 0) return;
            RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(index);
            if (holder != null && holder.itemView != null) {
                holder.itemView.setActivated(isActivated);
            }
        }
    }

    @Nullable
    public Drawable getDrawable(Context context, String pkg, String drawableName) {
        if (Objects.equals(pkg, mLauncherPackage)) {
            pkg = "com.android.settings";
        }
        try {
            PackageManager pm = context.getPackageManager();
            Resources res = pm.getResourcesForApplication(pkg);
            int resId = res.getIdentifier(drawableName, "drawable", pkg);
            if (resId == 0) {
                return null;
            }
            return res.getDrawable(resId, context.getTheme());
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
            return null;
        }
    }

    public String getLabel(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    public void enableOverlays(String pkg) {
        OVERLAY_EXECUTOR.execute(() ->
                mThemeUtils.setOverlayEnabled(mCategory, pkg, mLauncherPackage));
    }
}
