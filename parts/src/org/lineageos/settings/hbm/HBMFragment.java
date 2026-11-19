/*
* Copyright (C) 2016 The OmniROM Project
* Copyright (C) 2018-2021 crDroid Android Project
* Copyright (C) 2019-2026 The LineageOS Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package org.lineageos.settings.hbm;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class HBMFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    public static final String KEY_HBM_SWITCH = HbmUtils.KEY_HBM;
    public static final String KEY_AUTO_HBM_SWITCH = HbmUtils.KEY_AUTO_HBM;
    public static final String KEY_AUTO_HBM_THRESHOLD = HbmUtils.KEY_AUTO_HBM_THRESHOLD;
    public static final String KEY_AUTO_HBM_THRESHOLD_RESET = "auto_hbm_threshold_reset";
    public static final String KEY_HBM_DISABLE_TIME = HbmUtils.KEY_HBM_DISABLE_TIME;

    private TwoStatePreference mHBMModeSwitch;
    private TwoStatePreference mAutoHBMSwitch;
    private AutoHBMThresholdPreference mThresholdPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.hbm_settings, rootKey);

        final Context context = getContext();
        if (context == null) {
            return;
        }

        mHBMModeSwitch = findPreference(KEY_HBM_SWITCH);
        if (mHBMModeSwitch != null) {
            mHBMModeSwitch.setChecked(HbmUtils.isEnabled());
            mHBMModeSwitch.setOnPreferenceChangeListener(new HBMModeSwitch(context));
        }

        mAutoHBMSwitch = findPreference(KEY_AUTO_HBM_SWITCH);
        if (mAutoHBMSwitch != null) {
            mAutoHBMSwitch.setChecked(HbmUtils.isAutoEnabled(context));
            mAutoHBMSwitch.setOnPreferenceChangeListener(this);
        }

        mThresholdPref = findPreference(KEY_AUTO_HBM_THRESHOLD);

        final Preference resetPref = findPreference(KEY_AUTO_HBM_THRESHOLD_RESET);
        if (resetPref != null) {
            resetPref.setOnPreferenceClickListener(preference -> {
                if (mThresholdPref != null) {
                    mThresholdPref.resetToDefault();
                }
                return true;
            });
        }
    }

    public static boolean isAUTOHBMEnabled(Context context) {
        return HbmUtils.isAutoEnabled(context);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mAutoHBMSwitch) {
            if (!(newValue instanceof Boolean)) {
                return false;
            }
            final Context context = getContext();
            if (context == null) {
                return false;
            }
            HbmUtils.setAutoEnabled(context, (Boolean) newValue);
            HbmUtils.syncAutoService(context);
            return true;
        }

        return false;
    }
}
