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

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import com.android.settingslib.widget.SliderPreference;

import org.lineageos.settings.R;

public class ChargeControlFragment extends SettingsBasePreferenceFragment
        implements OnCheckedChangeListener, Preference.OnPreferenceChangeListener {

    private MainSwitchPreference mChargeControlSwitch;
    private SliderPreference mStopChargingPreference;
    private SwitchPreferenceCompat mBypassChargingPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.charge_control, rootKey);

        mChargeControlSwitch = findPreference(ChargeControlConstants.KEY_CHARGE_CONTROL);
        mStopChargingPreference = findPreference(ChargeControlConstants.KEY_STOP_CHARGING);
        mBypassChargingPreference = findPreference(ChargeControlConstants.KEY_BYPASS_CHARGING);

        if (mChargeControlSwitch == null || mStopChargingPreference == null
                || mBypassChargingPreference == null) {
            return;
        }

        mStopChargingPreference.setSliderIncrement(1);
        mStopChargingPreference.setShowSliderValue(true);
        mStopChargingPreference.setLabelFormater(value -> (int) value + "%");

        if (ChargeControlUtils.isSupported()) {
            mStopChargingPreference.setOnPreferenceChangeListener(this);
            mBypassChargingPreference.setOnPreferenceChangeListener(this);
        } else {
            mStopChargingPreference.setSummary(R.string.kernel_node_access_error);
            mStopChargingPreference.setEnabled(false);
            mBypassChargingPreference.setSummary(R.string.charge_bypass_unavailable);
            mBypassChargingPreference.setEnabled(false);
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
        if (mChargeControlSwitch == null || mStopChargingPreference == null
                || mBypassChargingPreference == null) {
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
        updateBypassUi(enabled, supported);
        if (supported) {
            mStopChargingPreference.setValue(ChargeControlUtils.getStopLevel(requireContext()));
        }
    }

    private void updateBypassUi(boolean enabled, boolean supported) {
        if (mBypassChargingPreference == null) {
            return;
        }

        mBypassChargingPreference.setChecked(
                enabled && ChargeControlUtils.isBypassEnabled(requireContext()));
        if (supported) {
            // Bypass is part of charging control: without the main switch it is
            // disabled as well.
            mBypassChargingPreference.setEnabled(enabled);
            mBypassChargingPreference.setSummary(enabled
                    ? R.string.charge_bypass_summary
                    : R.string.charge_bypass_requires_charge_control);
        } else {
            mBypassChargingPreference.setEnabled(false);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (mStopChargingPreference == null) {
            return;
        }

        ChargeControlUtils.setEnabled(requireContext(), isChecked);
        mStopChargingPreference.setVisible(isChecked);
        updateBypassUi(isChecked, ChargeControlUtils.isSupported());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mBypassChargingPreference) {
            return onBypassChargingChange(newValue);
        }

        if (preference != mStopChargingPreference || !(newValue instanceof Integer)) {
            return false;
        }

        int value = ChargeControlUtils.clampStopLevel((Integer) newValue);

        ChargeControlUtils.setStopLevel(requireContext(), value);
        mStopChargingPreference.setValue(value);
        // Keep the QS tile subtitle in sync while it is listening.
        ChargeControlUtils.updateTile(requireContext());
        Toast.makeText(requireContext(), getString(R.string.stop_charging_set_to, value),
                Toast.LENGTH_SHORT).show();

        // Apply the new threshold right away instead of waiting for the next
        // battery broadcast.
        ChargeControlUtils.applyCurrentBatteryState(requireContext());
        return true;
    }

    private boolean onBypassChargingChange(Object newValue) {
        if (mBypassChargingPreference == null || !(newValue instanceof Boolean)) {
            return false;
        }

        if (!(Boolean) newValue) {
            ChargeControlUtils.setBypassEnabled(requireContext(), false);
            return true;
        }

        ChargeControlUtils.SafetyCheckResult safetyCheck =
                ChargeControlUtils.checkBypassSafety(requireContext());
        if (!safetyCheck.isSafe()) {
            Toast.makeText(requireContext(), safetyCheck.getReason(), Toast.LENGTH_LONG).show();
            return false;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.charge_bypass_title)
                .setMessage(R.string.charge_bypass_warning)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (!ChargeControlUtils.setBypassEnabled(requireContext(), true)) {
                        Toast.makeText(requireContext(), R.string.charge_bypass_write_failed,
                                Toast.LENGTH_LONG).show();
                    }
                    mBypassChargingPreference.setChecked(
                            ChargeControlUtils.isBypassEnabled(requireContext()));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();

        // The switch is only updated once the warning is accepted.
        return false;
    }
}
