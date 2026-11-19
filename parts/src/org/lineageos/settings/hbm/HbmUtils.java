/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.hbm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.display.DcDimmingTileService;
import org.lineageos.settings.utils.FileUtils;

/**
 * Single source of truth for the panel HBM node, its saved brightness and the
 * automatic HBM preferences.
 */
public final class HbmUtils {
    private static final String TAG = "HbmUtils";

    public static final String KEY_HBM = "hbm";
    public static final String KEY_AUTO_HBM = "auto_hbm";
    public static final String KEY_AUTO_HBM_THRESHOLD = "auto_hbm_threshold";
    public static final String KEY_HBM_DISABLE_TIME = "hbm_disable_time";
    public static final String KEY_SAVED_BRIGHTNESS = "hbm_saved_brightness";

    private static final String HBM_ON = "0x10000";
    private static final String HBM_OFF = "0xF0000";

    private static final String BACKLIGHT_NODE =
            "/sys/class/backlight/panel0-backlight/brightness";
    private static final String BACKLIGHT_MAX_NODE =
            "/sys/class/backlight/panel0-backlight/max_brightness";
    private static final int FALLBACK_BACKLIGHT_MAX = 2047;

    private static final String[] DISP_PARAM_CANDIDATES = {
            "/sys/class/drm/card0/card0-DSI-1/disp_param",
            "/sys/class/drm/card0-DSI-1/disp_param",
    };

    public static final int DEFAULT_THRESHOLD = 7000;
    public static final int MIN_THRESHOLD = 0;
    public static final int MAX_THRESHOLD = 60000;
    public static final int DEFAULT_DISABLE_TIME = 1;
    public static final int MIN_DISABLE_TIME = 1;
    public static final int MAX_DISABLE_TIME = 10;

    private static String sDispParam;

    private HbmUtils() {
        // This class is not supposed to be instantiated
    }

    private static String getDispParam() {
        if (sDispParam == null) {
            for (String candidate : DISP_PARAM_CANDIDATES) {
                if (FileUtils.isFileWritable(candidate)) {
                    sDispParam = candidate;
                    break;
                }
            }
        }
        return sDispParam;
    }

    public static boolean isSupported() {
        return getDispParam() != null;
    }

    /**
     * The node reports 0x10000 while HBM is active and 0xF0000 otherwise.
     */
    public static boolean isEnabled() {
        String node = getDispParam();
        if (node == null) {
            return false;
        }
        String value = FileUtils.readOneLine(node);
        if (value == null) {
            return false;
        }
        value = value.trim();
        return HBM_ON.equalsIgnoreCase(value) || "1".equals(value);
    }

    public static boolean setEnabled(Context context, boolean enabled) {
        String node = getDispParam();
        if (node == null) {
            return false;
        }

        final SharedPreferences prefs = getPreferences(context);
        if (isEnabled() == enabled) {
            // Keep the preference in sync even when the node already has the
            // requested state, for example after a service restart.
            prefs.edit().putBoolean(KEY_HBM, enabled).apply();
            return true;
        }

        if (enabled) {
            final int current = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 255);
            prefs.edit().putInt(KEY_SAVED_BRIGHTNESS, current).apply();

            if (!FileUtils.writeLine(node, HBM_ON)) {
                Log.w(TAG, "Cannot enable HBM");
                return false;
            }
            FileUtils.writeLine(BACKLIGHT_NODE, String.valueOf(getBacklightMax()));
            Settings.System.putInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 255);
        } else {
            if (!FileUtils.writeLine(node, HBM_OFF)) {
                Log.w(TAG, "Cannot disable HBM");
                return false;
            }
            final int saved = prefs.getInt(KEY_SAVED_BRIGHTNESS, -1);
            if (saved >= 0) {
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, saved);
                FileUtils.writeLine(BACKLIGHT_NODE,
                        String.valueOf(saved * getBacklightMax() / 255));
                prefs.edit().remove(KEY_SAVED_BRIGHTNESS).apply();
            }
        }

        prefs.edit().putBoolean(KEY_HBM, enabled).apply();
        return true;
    }

    private static int getBacklightMax() {
        String value = FileUtils.readOneLine(BACKLIGHT_MAX_NODE);
        if (value != null) {
            try {
                int max = Integer.parseInt(value.trim());
                if (max > 0) {
                    return max;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to the panel default
            }
        }
        return FALLBACK_BACKLIGHT_MAX;
    }

    public static boolean isAutoEnabled(Context context) {
        return getPreferences(context).getBoolean(KEY_AUTO_HBM, false);
    }

    public static void setAutoEnabled(Context context, boolean enabled) {
        getPreferences(context).edit().putBoolean(KEY_AUTO_HBM, enabled).apply();
    }

    public static boolean isDcDimmingEnabled(Context context) {
        return getPreferences(context).getBoolean(
                DcDimmingTileService.DC_DIMMING_ENABLE_KEY, false);
    }

    public static int getThreshold(Context context) {
        return clamp(readInt(context, KEY_AUTO_HBM_THRESHOLD, DEFAULT_THRESHOLD),
                MIN_THRESHOLD, MAX_THRESHOLD);
    }

    public static int getDisableDelay(Context context) {
        return clamp(readInt(context, KEY_HBM_DISABLE_TIME, DEFAULT_DISABLE_TIME),
                MIN_DISABLE_TIME, MAX_DISABLE_TIME);
    }

    /**
     * Starts or stops the automatic HBM service to match the preference.
     */
    public static void syncAutoService(Context context) {
        Intent intent = new Intent(context, AutoHBMService.class);
        if (isAutoEnabled(context)) {
            context.startServiceAsUser(intent, UserHandle.CURRENT);
        } else {
            context.stopServiceAsUser(intent, UserHandle.CURRENT);
        }
    }

    private static int readInt(Context context, String key, int defValue) {
        String value = getPreferences(context).getString(key, null);
        if (value == null) {
            return defValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static SharedPreferences getPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
