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

import static com.android.internal.util.expressiveos.ThemeUtils.ICON_SHAPE_KEY;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.expressiveos.ThemeUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.Utils;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.search.SearchIndexableRaw;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SearchIndexable
public class IconShapes extends SettingsPreferenceFragment {

    private static final String TAG = "IconShapes";

    private RecyclerView mRecyclerView;
    private ThemeUtils mThemeUtils;
    private final String mCategory = ICON_SHAPE_KEY;
    private List<String> mPkgs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isAdded()) return;
        requireActivity().setTitle(R.string.themes_icon_shape_title);
        mThemeUtils = ThemeUtils.getInstance(requireContext());
        mPkgs = mThemeUtils.getOverlayPackagesForCategory(mCategory, "android");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.item_view, container, false);
        mRecyclerView = view.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        mRecyclerView.setAdapter(new Adapter(requireContext(), mPkgs, mThemeUtils, mCategory, mRecyclerView));
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
                    raw.key = "adaptive_icon_shape";
                    raw.title = context.getString(R.string.themes_icon_shape_title);
                    raw.summaryOn = context.getString(R.string.themes_adaptive_icon_shape_summary);
                    raw.screenTitle = context.getString(R.string.themes_icon_shape_title);
                    return Collections.singletonList(raw);
                }
            };

    public static class Adapter extends RecyclerView.Adapter<Adapter.CustomViewHolder> {
        private static final ExecutorService OVERLAY_EXECUTOR = Executors.newSingleThreadExecutor();

        private final WeakReference<Context> contextRef;
        private final List<String> mPkgs;
        private final ThemeUtils mThemeUtils;
        private final String mCategory;
        private final RecyclerView mRecyclerView;
        private String mAppliedPkg;
        private String mSelectedPkg;

        public Adapter(Context context, List<String> pkgs, ThemeUtils themeUtils, String category, RecyclerView recyclerView) {
            this.contextRef = new WeakReference<>(context);
            this.mPkgs = pkgs;
            this.mThemeUtils = themeUtils;
            this.mCategory = category;
            this.mRecyclerView = recyclerView;

            mAppliedPkg = mThemeUtils.getOverlayInfos(mCategory).stream()
                    .filter(info -> info.isEnabled())
                    .map(info -> info.packageName)
                    .findFirst()
                    .orElse("android");

            mSelectedPkg = mAppliedPkg;
        }

        @NonNull
        @Override
        public CustomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_option, parent, false);
            return new CustomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CustomViewHolder holder, int position) {
            Context context = contextRef.get();
            if (context == null) return;

            String pkg = mPkgs.get(position);
            Drawable drawable = mThemeUtils.createShapeDrawable(pkg);
            holder.image.setBackground(drawable);

            String label = "android".equals(pkg)
                    ? context.getString(R.string.themes_default_label)
                    : getLabel(context, pkg);
            holder.name.setText(label);

            boolean isSelected = Objects.equals(pkg, mSelectedPkg);
            int color = ColorUtils.setAlphaComponent(
                    Utils.getColorAttrDefaultColor(context, android.R.attr.colorAccent),
                    isSelected ? 190 : 75);
            holder.image.setBackgroundTintList(ColorStateList.valueOf(color));

            holder.itemView.findViewById(R.id.option_tile).setBackground(null);
            holder.itemView.setActivated(isSelected);
            holder.itemView.setOnClickListener(view -> {
                if (!Objects.equals(pkg, mSelectedPkg)) {
                    mSelectedPkg = pkg;
                    mAppliedPkg = pkg;
                    notifyDataSetChanged();
                    OVERLAY_EXECUTOR.execute(() ->
                            mThemeUtils.setOverlayEnabled(mCategory, pkg, "android"));
                }
            });
        }

        @Override
        public int getItemCount() {
            return mPkgs.size();
        }

        public static class CustomViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            ImageView image;
            public CustomViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.option_label);
                image = itemView.findViewById(R.id.option_thumbnail);
            }
        }

        private String getLabel(Context context, String pkg) {
            PackageManager pm = context.getPackageManager();
            try {
                return pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Label load failed for pkg: " + pkg, e);
            }
            return pkg;
        }
    }
}
