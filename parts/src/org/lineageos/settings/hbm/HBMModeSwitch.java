/*
 * Copyright (C) 2016 The OmniROM Project
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package org.lineageos.settings.hbm;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;

public class HBMModeSwitch implements OnPreferenceChangeListener {

    private final Context mContext;

    public HBMModeSwitch(Context context) {
        mContext = context;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!(newValue instanceof Boolean) || HbmUtils.isDcDimmingEnabled(mContext)) {
            return false;
        }

        boolean enabled = (Boolean) newValue;
        // Keep the switch in its previous position if the node could not be written.
        return HbmUtils.setEnabled(mContext, enabled);
    }
}
