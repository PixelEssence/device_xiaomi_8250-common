/*
 * Copyright (C) 2018 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.display;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;

import org.lineageos.settings.R;
import org.lineageos.settings.hbm.HbmUtils;
import org.lineageos.settings.utils.FileUtils;

public class DcDimmingSettingsFragment extends PreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String DC_DIMMING_ENABLE_KEY = "dc_dimming_enable";
    private static final String DC_DIMMING_NODE =
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/dimlayer_exposure";

    private SwitchPreference mDcDimmingPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.dcdimming_settings, rootKey);
        mDcDimmingPreference = findPreference(DC_DIMMING_ENABLE_KEY);
        if (mDcDimmingPreference == null) {
            return;
        }

        if (FileUtils.isFileWritable(DC_DIMMING_NODE)) {
            mDcDimmingPreference.setEnabled(true);
            mDcDimmingPreference.setOnPreferenceChangeListener(this);
        } else {
            mDcDimmingPreference.setSummary(R.string.dc_dimming_enable_summary_not_supported);
            mDcDimmingPreference.setEnabled(false);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!DC_DIMMING_ENABLE_KEY.equals(preference.getKey())
                || !(newValue instanceof Boolean)) {
            return true;
        }

        final boolean enabled = (Boolean) newValue;
        FileUtils.writeLine(DC_DIMMING_NODE, enabled ? "1" : "0");
        if (enabled) {
            // DC dimming and HBM cannot be active at the same time.
            HbmUtils.setEnabled(getContext(), false);
        }
        return true;
    }
}
