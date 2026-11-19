/*
 * Copyright (C) 2021 crDroid Android Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings;

import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import org.lineageos.settings.refreshrate.RefreshUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RefreshRateTileService extends TileService {
    private static final String TAG = "RefreshRateTile";

    private final List<Float> mRates = new ArrayList<>();
    private boolean mListening;

    private final ContentObserver mObserver =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    if (mListening) updateTile();
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        loadRates();
    }

    private void loadRates() {
        DisplayManager displayManager = getSystemService(DisplayManager.class);
        Display display = displayManager == null
                ? null : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) return;

        Display.Mode current = display.getMode();
        List<Float> rates = new ArrayList<>();
        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                    && mode.getPhysicalHeight() == current.getPhysicalHeight()) {
                float rate = Float.parseFloat(
                        String.format(Locale.US, "%.02f", mode.getRefreshRate()));
                if (!rates.contains(rate)) {
                    rates.add(rate);
                }
            }
        }
        Collections.sort(rates);

        mRates.clear();
        mRates.addAll(rates);
    }

    private int nearest(float value) {
        if (mRates.isEmpty()) return -1;
        if (Float.isInfinite(value)) return mRates.size() - 1;

        int best = 0;
        float bestDelta = Math.abs(mRates.get(0) - value);
        for (int i = 1; i < mRates.size(); i++) {
            float delta = Math.abs(mRates.get(i) - value);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = i;
            }
        }
        return best;
    }

    private float readSetting(String key, float fallback) {
        return Settings.System.getFloat(getContentResolver(), key, fallback);
    }

    private boolean writeRate(float rate) {
        float oldMin = readSetting(Settings.System.MIN_REFRESH_RATE, rate);
        float oldPeak = readSetting(Settings.System.PEAK_REFRESH_RATE, rate);

        if (!Settings.System.putFloat(getContentResolver(),
                Settings.System.MIN_REFRESH_RATE, rate)) {
            return false;
        }
        if (!Settings.System.putFloat(getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, rate)) {
            Settings.System.putFloat(getContentResolver(),
                    Settings.System.MIN_REFRESH_RATE, oldMin);
            Settings.System.putFloat(getContentResolver(),
                    Settings.System.PEAK_REFRESH_RATE, oldPeak);
            return false;
        }
        return true;
    }

    private String formatRate(float rate) {
        return String.format(Locale.US, "%.02f Hz", rate).replaceAll("[.,]00", "");
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        if (mRates.isEmpty()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.updateTile();
            return;
        }

        float min = mRates.get(nearest(readSetting(
                Settings.System.MIN_REFRESH_RATE, mRates.get(0))));
        float max = mRates.get(nearest(readSetting(
                Settings.System.PEAK_REFRESH_RATE, mRates.get(mRates.size() - 1))));
        String label = min == max
                ? formatRate(min)
                : formatRate(min) + " - " + formatRate(max);

        tile.setContentDescription(label);
        tile.setSubtitle(label);
        tile.setState(min == max ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();

        if (!mListening) {
            mListening = true;
            try {
                getContentResolver().registerContentObserver(
                        Settings.System.getUriFor(Settings.System.MIN_REFRESH_RATE),
                        false, mObserver);
                getContentResolver().registerContentObserver(
                        Settings.System.getUriFor(Settings.System.PEAK_REFRESH_RATE),
                        false, mObserver);
            } catch (RuntimeException e) {
                stopObserving();
                Log.w(TAG, "Cannot observe refresh rate settings", e);
            }
        }

        if (mRates.isEmpty()) loadRates();
        updateTile();
    }

    private void stopObserving() {
        if (!mListening) return;
        mListening = false;
        try {
            getContentResolver().unregisterContentObserver(mObserver);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot stop observing refresh rate settings", e);
        }
    }

    @Override
    public void onStopListening() {
        stopObserving();
        super.onStopListening();
    }

    @Override
    public void onDestroy() {
        stopObserving();
        super.onDestroy();
    }

    @Override
    public void onClick() {
        super.onClick();

        if (mRates.isEmpty()) {
            updateTile();
            return;
        }

        int current = nearest(readSetting(
                Settings.System.MIN_REFRESH_RATE, mRates.get(0)));
        float next = mRates.get((current + 1) % mRates.size());

        if (writeRate(next)) {
            // Keep the per-app baseline in sync when a rule is currently active.
            RefreshUtils.updateUserBaseline(this, next, next);
        } else {
            Toast.makeText(this, R.string.parts_apply_failed, Toast.LENGTH_SHORT).show();
        }
        updateTile();
    }
}
