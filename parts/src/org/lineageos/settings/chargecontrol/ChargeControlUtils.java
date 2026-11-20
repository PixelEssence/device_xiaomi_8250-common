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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.UserHandle;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;

/**
 * Shared charging-limit logic used by the settings screen, the QS tiles and the service.
 */
public final class ChargeControlUtils {

    private static final String TAG = "ChargeControlUtils";

    private static final int HYSTERESIS = 2;

    /**
     * Last value written to the kernel node, or null when it is unknown.
     */
    private static Boolean sLastApplied;

    private ChargeControlUtils() {
        // This class is not supposed to be instantiated
    }

    public static boolean isSupported() {
        return FileUtils.isFileWritable(ChargeControlConstants.NODE_STOP_CHARGING);
    }

    /**
     * Whether the charging input is currently suspended by the kernel node.
     */
    public static boolean isSuspended() {
        String value = FileUtils.readOneLine(ChargeControlConstants.NODE_STOP_CHARGING);
        return value != null && "1".equals(value.trim());
    }

    public static void startService(Context context) {
        try {
            context.startServiceAsUser(
                    new Intent(context, ChargeControlService.class), UserHandle.CURRENT);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot start charge control service", e);
        }

        if (isSupported() && !isEnabled(context)) {
            // Make sure a suspend left over from a previous boot is released.
            writeNode(false);
        }
    }

    public static boolean isEnabled(Context context) {
        return getPreferences(context)
                .getBoolean(ChargeControlConstants.KEY_CHARGE_CONTROL, false);
    }

    public static int clampStopLevel(int level) {
        return Math.max(ChargeControlConstants.MIN_STOP_CHARGING, Math.min(level, 100));
    }

    public static int getStopLevel(Context context) {
        return clampStopLevel(getPreferences(context).getInt(
                ChargeControlConstants.KEY_STOP_CHARGING,
                ChargeControlConstants.DEFAULT_STOP_CHARGING));
    }

    public static void setStopLevel(Context context, int level) {
        getPreferences(context).edit()
                .putInt(ChargeControlConstants.KEY_STOP_CHARGING, clampStopLevel(level))
                .apply();
    }

    public static boolean isBypassEnabled(Context context) {
        return getPreferences(context)
                .getBoolean(ChargeControlConstants.KEY_BYPASS_CHARGING, false);
    }

    /**
     * Checks whether bypass charging can be turned on right now.
     */
    public static SafetyCheckResult checkBypassSafety(Context context) {
        if (!isSupported()) {
            return new SafetyCheckResult(false,
                    context.getString(R.string.charge_bypass_unavailable));
        }
        if (!isEnabled(context)) {
            return new SafetyCheckResult(false,
                    context.getString(R.string.charge_bypass_requires_charge_control));
        }

        Intent batteryStatus = getBatteryStatus(context);
        if (batteryStatus == null) {
            return new SafetyCheckResult(false,
                    context.getString(R.string.charge_bypass_unavailable));
        }

        if (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) {
            return new SafetyCheckResult(false,
                    context.getString(R.string.charge_bypass_no_charger));
        }

        int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        if (temperature > 0 && temperature >= ChargeControlConstants.MAX_BYPASS_TEMPERATURE) {
            return new SafetyCheckResult(false,
                    context.getString(R.string.charge_bypass_temp_too_high,
                            Math.round(temperature / 10.0f)));
        }

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) {
            int percent = Math.round(level * 100f / scale);
            if (percent < ChargeControlConstants.MIN_BYPASS_LEVEL) {
                return new SafetyCheckResult(false,
                        context.getString(R.string.charge_bypass_level_too_low, percent));
            }
        }

        return new SafetyCheckResult(true, null);
    }

    /**
     * Turns bypass charging on or off. Returns false when the node could not be
     * updated.
     */
    public static boolean setBypassEnabled(Context context, boolean enabled) {
        if (!isSupported() || (enabled && !isEnabled(context))) {
            return false;
        }

        setBypassPreference(context, enabled);
        // Forget the cached value so the state is written even if it did not change.
        sLastApplied = null;
        boolean ok = applyCurrentBatteryState(context);
        if (!ok && enabled) {
            setBypassPreference(context, false);
        }

        updateTiles(context);
        return ok;
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (!isSupported()) {
            return;
        }

        getPreferences(context).edit()
                .putBoolean(ChargeControlConstants.KEY_CHARGE_CONTROL, enabled)
                .putBoolean(ChargeControlConstants.KEY_BYPASS_CHARGING, false)
                .apply();

        if (enabled) {
            // Make sure the battery monitor is running even if the service was
            // killed after an update or a force-stop.
            startService(context);
            // The node was released while disabled, forget the cached value so it gets
            // written again below.
            sLastApplied = null;
            applyCurrentBatteryState(context);
        } else {
            // Being disabled is not the same as "never touched the node": release it so
            // that charging actually resumes.
            writeNode(false);
        }

        updateTiles(context);
    }

    public static boolean applyCurrentBatteryState(Context context) {
        Intent batteryStatus = getBatteryStatus(context);
        if (batteryStatus == null) {
            return false;
        }

        return applyBatteryState(context,
                batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
                batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
                batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0));
    }

    public static boolean applyBatteryState(Context context, int level, int scale,
            boolean plugged, int temperature) {
        if (!isSupported()) {
            return false;
        }

        SharedPreferences prefs = getPreferences(context);
        if (!prefs.getBoolean(ChargeControlConstants.KEY_CHARGE_CONTROL, false)) {
            // Disabled: leave the node alone.
            return true;
        }

        if (level < 0 || scale <= 0) {
            return false;
        }

        int percent = Math.round(level * 100f / scale);

        boolean bypass = prefs.getBoolean(ChargeControlConstants.KEY_BYPASS_CHARGING, false);
        if (bypass && (percent < ChargeControlConstants.MIN_BYPASS_LEVEL
                || (temperature > 0
                        && temperature >= ChargeControlConstants.MAX_BYPASS_TEMPERATURE))) {
            // Safety limits reached while bypassing: fall back to normal charging.
            setBypassPreference(context, false);
            bypass = false;
            updateTiles(context);
        }

        int stopLevel = clampStopLevel(prefs.getInt(
                ChargeControlConstants.KEY_STOP_CHARGING,
                ChargeControlConstants.DEFAULT_STOP_CHARGING));

        boolean suspend;
        if (bypass) {
            // Bypass keeps the input suspended regardless of the battery level.
            suspend = true;
        } else if (!plugged) {
            // Nothing to suspend while unplugged; release the node so a stale
            // suspend does not survive the next connection.
            suspend = false;
        } else {
            // Resume only below the configured level, otherwise the node would be toggled
            // on every battery broadcast while sitting exactly at the threshold.
            suspend = percent >= stopLevel
                    || (Boolean.TRUE.equals(sLastApplied) && percent > stopLevel - HYSTERESIS);
        }

        if (Boolean.valueOf(suspend).equals(sLastApplied)) {
            return true;
        }

        return writeNode(suspend);
    }

    public static void updateTile(Context context) {
        try {
            TileService.requestListeningState(context,
                    new ComponentName(context, ChargeControlTileService.class));
        } catch (Exception e) {
            // The tile is not available, nothing to update
        }
    }

    public static void updateTiles(Context context) {
        updateTile(context);
        BypassChargeTileService.requestUpdate(context);
    }

    private static void setBypassPreference(Context context, boolean enabled) {
        getPreferences(context).edit()
                .putBoolean(ChargeControlConstants.KEY_BYPASS_CHARGING, enabled)
                .apply();
    }

    private static Intent getBatteryStatus(Context context) {
        return context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private static boolean writeNode(boolean suspend) {
        boolean ok = FileUtils.writeLine(
                ChargeControlConstants.NODE_STOP_CHARGING, suspend ? "1" : "0");
        if (ok) {
            sLastApplied = suspend;
        }
        return ok;
    }

    private static SharedPreferences getPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static final class SafetyCheckResult {
        private final boolean mSafe;
        private final String mReason;

        private SafetyCheckResult(boolean safe, String reason) {
            mSafe = safe;
            mReason = reason;
        }

        public boolean isSafe() {
            return mSafe;
        }

        public String getReason() {
            return mReason;
        }
    }
}
