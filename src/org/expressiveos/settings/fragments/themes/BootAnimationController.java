/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package org.expressiveos.settings.fragments.themes;

import android.content.Context;

import com.android.settings.core.BasePreferenceController;

import org.expressiveos.settings.utils.BootAnimationUtils;

public class BootAnimationController extends BasePreferenceController {

    public BootAnimationController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return BootAnimationUtils.isAvailable(mContext)
                ? AVAILABLE
                : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        return BootAnimationUtils.getLabel(mContext, BootAnimationUtils.getEffectiveName());
    }
}
