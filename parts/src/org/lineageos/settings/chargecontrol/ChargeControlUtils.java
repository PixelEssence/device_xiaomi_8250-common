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

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

/**
 * Shared charging-limit logic used by the settings screen, the QS tile and the service.
 */
public final class ChargeControlUtils {

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

    public static void startService(Context context) {
        context.startServiceAsUser(
                new Intent(context, ChargeControlService.class), UserHandle.CURRENT);

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

    public static void setEnabled(Context context, boolean enabled) {
        if (!isSupported()) {
            return;
        }

        getPreferences(context).edit()
                .putBoolean(ChargeControlConstants.KEY_CHARGE_CONTROL, enabled)
                .apply();

        if (enabled) {
            // The node was released while disabled, forget the cached value so it gets
            // written again below.
            sLastApplied = null;
            applyCurrentBatteryState(context);
        } else {
            // Being disabled is not the same as "never touched the node": release it so
            // that charging actually resumes.
            writeNode(false);
        }

        updateTile(context);
    }

    public static boolean applyCurrentBatteryState(Context context) {
        Intent batteryStatus = context.registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) {
            return false;
        }

        return applyBatteryState(context,
                batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1));
    }

    public static boolean applyBatteryState(Context context, int level, int scale) {
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

        int stopLevel = clampStopLevel(prefs.getInt(
                ChargeControlConstants.KEY_STOP_CHARGING,
                ChargeControlConstants.DEFAULT_STOP_CHARGING));
        int percent = Math.round(level * 100f / scale);

        // Resume only below the configured level, otherwise the node would be toggled
        // on every battery broadcast while sitting exactly at the threshold.
        boolean suspend = percent >= stopLevel
                || (Boolean.TRUE.equals(sLastApplied) && percent > stopLevel - HYSTERESIS);

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
}
