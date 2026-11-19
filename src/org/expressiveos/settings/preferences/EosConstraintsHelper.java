/*
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.expressiveos.settings.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Constraint evaluator for {@link EosSwitchPreference} and {@link EosListPreference}.
 * Evaluates XML constraints (requiresConfig, requiresFeature, requiresPackage,
 * requiresProperty, requiresOwner, requiresAction, requiresService, replacesKey)
 * directly against standard Android platform APIs.
 */
public class EosConstraintsHelper {

    private final Context mContext;
    private final AttributeSet mAttrs;
    private final Preference mPref;
    private boolean mAvailable = true;
    private int mSummaryMinLines = -1;
    private String[] mReplacesKey = null;

    public EosConstraintsHelper(Context context, AttributeSet attrs, Preference pref) {
        mContext = context;
        mAttrs = attrs;
        mPref = pref;

        if (attrs != null) {
            TypedArray a = context.getResources().obtainAttributes(
                    attrs, R.styleable.EosSelfRemovingPreference);
            try {
                mSummaryMinLines = a.getInteger(
                        R.styleable.EosSelfRemovingPreference_minSummaryLines, -1);
                setAvailable(checkConstraints(a));
                String replacesKey = a.getString(
                        R.styleable.EosSelfRemovingPreference_replacesKey);
                if (replacesKey != null && mAvailable) {
                    mReplacesKey = replacesKey.split("\\|");
                }
            } finally {
                a.recycle();
            }
        }
    }

    public void setAvailable(boolean available) {
        mAvailable = available;
        if (!available && mPref.getKey() != null) {
            Graveyard.get(mContext).addTombstone(mPref.getKey());
        }
    }

    public boolean isAvailable() {
        return mAvailable;
    }

    private static boolean isNegated(String key) {
        return key != null && key.startsWith("!");
    }

    private void checkIntent() {
        Intent i = mPref.getIntent();
        if (i != null && !resolveIntent(mContext, i)) {
            if (mPref.getKey() != null) {
                Graveyard.get(mContext).addTombstone(mPref.getKey());
            }
            mAvailable = false;
        }
    }

    private boolean checkConstraints(TypedArray a) {
        boolean rOwner = a.getBoolean(
                R.styleable.EosSelfRemovingPreference_requiresOwner, false);
        if (rOwner && UserHandle.myUserId() != UserHandle.USER_SYSTEM) {
            return false;
        }

        String rPackage = a.getString(R.styleable.EosSelfRemovingPreference_requiresPackage);
        if (rPackage != null) {
            boolean negated = isNegated(rPackage);
            if (negated) {
                rPackage = rPackage.substring(1);
            }
            boolean available = isPackageInstalled(mContext, rPackage, false);
            if (available == negated) {
                return false;
            }
        }

        String rAction = a.getString(R.styleable.EosSelfRemovingPreference_requiresAction);
        if (rAction != null) {
            boolean negated = isNegated(rAction);
            if (negated) {
                rAction = rAction.substring(1);
            }
            boolean available = resolveIntent(mContext, rAction);
            if (available == negated) {
                return false;
            }
        }

        String rFeature = a.getString(R.styleable.EosSelfRemovingPreference_requiresFeature);
        if (rFeature != null) {
            boolean negated = isNegated(rFeature);
            if (negated) {
                rFeature = rFeature.substring(1);
            }
            boolean available = hasSystemFeature(mContext, rFeature);
            if (available == negated) {
                return false;
            }
        }

        String rProperty = a.getString(R.styleable.EosSelfRemovingPreference_requiresProperty);
        if (rProperty != null) {
            boolean negated = isNegated(rProperty);
            if (negated) {
                rProperty = rProperty.substring(1);
            }
            String value = SystemProperties.get(rProperty);
            boolean available = value != null && Boolean.parseBoolean(value);
            if (available == negated) {
                return false;
            }
        }

        TypedValue tv = a.peekValue(R.styleable.EosSelfRemovingPreference_requiresConfig);
        if (tv != null && tv.resourceId != 0) {
            if (tv.type == TypedValue.TYPE_STRING
                    && mContext.getResources().getString(tv.resourceId) == null) {
                return false;
            } else if (tv.type == TypedValue.TYPE_INT_BOOLEAN && tv.data == 0) {
                return false;
            } else if (tv.type == TypedValue.TYPE_INT_DEC) {
                int mask = a.getInt(
                        R.styleable.EosSelfRemovingPreference_requiresConfigMask, -1);
                if (tv.data == 0 || (mask >= 0 && (tv.data & mask) == 0)) {
                    return false;
                }
            }
        }

        String rService = a.getString(R.styleable.EosSelfRemovingPreference_requiresService);
        if (rService != null) {
            boolean negated = isNegated(rService);
            if (negated) {
                rService = rService.substring(1);
            }
            IBinder value = ServiceManager.getService(rService);
            boolean available = value != null;
            if (available == negated) {
                return false;
            }
        }

        return true;
    }

    public static boolean hasSystemFeature(Context context, String feature) {
        return context.getPackageManager().hasSystemFeature(feature);
    }

    public static boolean isPackageInstalled(Context context, String pkg, boolean ignoreState) {
        if (pkg != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
                if (!pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
        return true;
    }

    public static boolean resolveIntent(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> results = pm.queryIntentActivitiesAsUser(
                intent, PackageManager.MATCH_SYSTEM_ONLY, UserHandle.myUserId());
        for (ResolveInfo resolveInfo : results) {
            if ((resolveInfo.activityInfo.applicationInfo.flags
                    & ApplicationInfo.FLAG_SYSTEM) != 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean resolveIntent(Context context, String action) {
        return resolveIntent(context, new Intent(action));
    }

    public void onAttached() {
        checkIntent();
        if (isAvailable() && mReplacesKey != null) {
            Graveyard.get(mContext).addTombstones(mReplacesKey);
        }
        Graveyard.get(mContext).summonReaper(mPref.getPreferenceManager());
    }

    public void onBindViewHolder(PreferenceViewHolder holder) {
        if (!isAvailable()) {
            return;
        }
        if (mSummaryMinLines > 0) {
            TextView textView = (TextView) holder.itemView.findViewById(android.R.id.summary);
            if (textView != null) {
                textView.setMinLines(mSummaryMinLines);
            }
        }
    }

    private static class Graveyard {
        private Set<String> mDeathRow = new ArraySet<>();
        private static Graveyard sInstance;

        public static synchronized Graveyard get(Context context) {
            if (sInstance == null) {
                sInstance = new Graveyard();
            }
            return sInstance;
        }

        public void addTombstone(String pref) {
            if (pref == null) return;
            synchronized (mDeathRow) {
                mDeathRow.add(pref);
            }
        }

        public void addTombstones(String[] prefs) {
            synchronized (mDeathRow) {
                mDeathRow.addAll(Arrays.asList(prefs));
            }
        }

        private PreferenceGroup getParent(PreferenceGroup root, Preference preference) {
            if (root == null) return null;
            for (int i = 0; i < root.getPreferenceCount(); i++) {
                Preference p = root.getPreference(i);
                if (p == preference) return root;
                if (p instanceof PreferenceGroup) {
                    PreferenceGroup parent = getParent((PreferenceGroup) p, preference);
                    if (parent != null) return parent;
                }
            }
            return null;
        }

        private void hidePreference(PreferenceManager mgr, Preference pref) {
            pref.setVisible(false);
            final PreferenceGroup group = getParent(mgr.getPreferenceScreen(), pref);
            if (group == null) return;
            boolean allHidden = true;
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                if (group.getPreference(i).isVisible()) {
                    allHidden = false;
                    break;
                }
            }
            if (allHidden) {
                group.setVisible(false);
            }
        }

        public void summonReaper(PreferenceManager mgr) {
            if (mgr == null) return;
            synchronized (mDeathRow) {
                Set<String> notReadyForReap = new ArraySet<>();
                for (String dead : mDeathRow) {
                    Preference deadPref = mgr.findPreference(dead);
                    if (deadPref != null) {
                        hidePreference(mgr, deadPref);
                    } else {
                        notReadyForReap.add(dead);
                    }
                }
                mDeathRow = notReadyForReap;
            }
        }
    }
}
