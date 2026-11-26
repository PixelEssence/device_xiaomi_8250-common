/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.hbm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
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

    private static final long HBM_ON_MASK = 0x10000L;
    private static final long HBM_OFF_MASK = 0xF0000L;
    private static final String HBM_ON = "0x10000";
    private static final String HBM_OFF = "0xF0000";

    private static final String BACKLIGHT_NODE =
            "/sys/class/backlight/panel0-backlight/brightness";
    private static final String BACKLIGHT_MAX_NODE =
            "/sys/class/backlight/panel0-backlight/max_brightness";
    private static final int FALLBACK_BRIGHTNESS_MAX = 255;

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
     * The node prints the last disp_param mask as "0x%08X", so HBM on reads
     * back as 0x00010000 rather than 0x10000. Other display features can
     * overwrite the value, so fall back to the saved preference when the mask
     * is neither the HBM-on nor the HBM-off value.
     */
    public static boolean isEnabled(Context context) {
        final String node = getDispParam();
        if (node != null) {
            final String value = FileUtils.readOneLine(node);
            if (value != null) {
                final Long mask = parseDispParam(value);
                if (mask != null) {
                    if (mask == HBM_ON_MASK) {
                        return true;
                    }
                    if (mask == HBM_OFF_MASK) {
                        return false;
                    }
                }
            }
        }
        return getPreferences(context).getBoolean(KEY_HBM, false);
    }

    private static Long parseDispParam(String value) {
        value = value.trim();
        try {
            if (value.startsWith("0x") || value.startsWith("0X")) {
                return Long.parseLong(value.substring(2), 16);
            }
            return Long.parseLong(value, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean setEnabled(Context context, boolean enabled) {
        final String node = getDispParam();
        if (node == null) {
            return false;
        }

        final SharedPreferences prefs = getPreferences(context);
        if (isEnabled(context) == enabled) {
            // Keep the preference in sync even when the node already has the
            // requested state, for example after a service restart.
            prefs.edit().putBoolean(KEY_HBM, enabled).apply();
            if (!enabled) {
                // The panel can leave HBM by itself (screen off) while the
                // saved brightness is still pending.
                restoreBrightness(context, prefs);
            }
            return true;
        }

        if (enabled) {
            final int current = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, getBrightnessSettingMax(context));
            prefs.edit().putInt(KEY_SAVED_BRIGHTNESS, current).apply();

            if (!FileUtils.writeLine(node, HBM_ON)) {
                Log.w(TAG, "Cannot enable HBM");
                prefs.edit().remove(KEY_SAVED_BRIGHTNESS).apply();
                return false;
            }
            setBrightness(context, getBrightnessSettingMax(context));
        } else {
            if (!FileUtils.writeLine(node, HBM_OFF)) {
                Log.w(TAG, "Cannot disable HBM");
                return false;
            }
            restoreBrightness(context, prefs);
        }

        prefs.edit().putBoolean(KEY_HBM, enabled).apply();
        return true;
    }

    private static void restoreBrightness(Context context, SharedPreferences prefs) {
        final int saved = prefs.getInt(KEY_SAVED_BRIGHTNESS, -1);
        if (saved >= 0) {
            setBrightness(context, saved);
            prefs.edit().remove(KEY_SAVED_BRIGHTNESS).apply();
        }
    }

    /**
     * The framework setting uses its own range (0..255 on alioth) while the
     * backlight node uses the panel's max_brightness range (2047 or 4095), so
     * the raw write has to be scaled.
     */
    private static void setBrightness(Context context, int settingValue) {
        final int settingMax = getBrightnessSettingMax(context);
        final int backlightMax = getBacklightMax(context);
        final int nodeValue = (int) Math.min(backlightMax,
                (long) settingValue * backlightMax / settingMax);

        FileUtils.writeLine(BACKLIGHT_NODE, String.valueOf(nodeValue));
        Settings.System.putInt(context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, settingValue);
    }

    private static int getBrightnessSettingMax(Context context) {
        final PowerManager powerManager = context.getSystemService(PowerManager.class);
        if (powerManager != null) {
            final int max = powerManager.getMaximumScreenBrightnessSetting();
            if (max > 0) {
                return max;
            }
        }
        return FALLBACK_BRIGHTNESS_MAX;
    }

    private static int getBacklightMax(Context context) {
        final String value = FileUtils.readOneLine(BACKLIGHT_MAX_NODE);
        if (value != null) {
            try {
                final int max = Integer.parseInt(value.trim());
                if (max > 0) {
                    return max;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to the framework range
            }
        }
        return getBrightnessSettingMax(context);
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
        final SharedPreferences prefs = getPreferences(context);
        try {
            // SliderPreference (auto HBM threshold) stores an int, while the
            // older CustomSeekBarPreference controls stored a string.
            return prefs.getInt(key, defValue);
        } catch (ClassCastException e) {
            final String value = prefs.getString(key, null);
            if (value == null) {
                return defValue;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException numberFormatException) {
                return defValue;
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static SharedPreferences getPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
