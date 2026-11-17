/*
 * Copyright (C) 2024 The LineageOS Project
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

package org.lineageos.settings.chargecontrol;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;

import androidx.preference.Preference;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;
import org.lineageos.settings.hbm.CustomSeekBarPreference;

public class ChargeControlFragment extends SettingsBasePreferenceFragment
        implements OnCheckedChangeListener, Preference.OnPreferenceChangeListener {

    private MainSwitchPreference mChargeControlSwitch;
    private CustomSeekBarPreference mStopChargingPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.charge_control, rootKey);

        mChargeControlSwitch = findPreference(ChargeControlConstants.KEY_CHARGE_CONTROL);
        mStopChargingPreference = findPreference(ChargeControlConstants.KEY_STOP_CHARGING);

        if (mChargeControlSwitch == null || mStopChargingPreference == null) {
            return;
        }

        if (ChargeControlUtils.isSupported()) {
            mStopChargingPreference.setOnPreferenceChangeListener(this);
        } else {
            mStopChargingPreference.setSummary(R.string.kernel_node_access_error);
            mStopChargingPreference.setEnabled(false);
        }

        updateUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        // The QS tile can change the stored state while this screen is in the background.
        updateUi();
    }

    private void updateUi() {
        if (mChargeControlSwitch == null || mStopChargingPreference == null) {
            return;
        }

        boolean enabled = ChargeControlUtils.isEnabled(requireContext());

        // Refresh the switch without triggering the listener, the state is already applied.
        mChargeControlSwitch.removeOnSwitchChangeListener(this);
        mChargeControlSwitch.setChecked(enabled);
        mChargeControlSwitch.addOnSwitchChangeListener(this);

        boolean supported = ChargeControlUtils.isSupported();
        mChargeControlSwitch.setEnabled(supported);
        mStopChargingPreference.setVisible(enabled);
        if (supported) {
            mStopChargingPreference.setValue(ChargeControlUtils.getStopLevel(requireContext()));
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (mStopChargingPreference == null) {
            return;
        }

        ChargeControlUtils.setEnabled(requireContext(), isChecked);
        mStopChargingPreference.setVisible(isChecked);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference != mStopChargingPreference) {
            return false;
        }

        int value = ChargeControlUtils.clampStopLevel(
                Integer.parseInt(String.valueOf(newValue)));

        ChargeControlUtils.setStopLevel(requireContext(), value);
        mStopChargingPreference.setValue(value);
        Toast.makeText(requireContext(), getString(R.string.stop_charging_set_to, value),
                Toast.LENGTH_SHORT).show();

        // Apply the new threshold right away instead of waiting for the next
        // battery broadcast.
        ChargeControlUtils.applyCurrentBatteryState(requireContext());
        return true;
    }
}
