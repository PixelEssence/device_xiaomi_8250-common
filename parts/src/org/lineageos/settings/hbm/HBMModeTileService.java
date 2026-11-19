/*
 * Copyright (C) 2018 The OmniROM Project
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package org.lineageos.settings.hbm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class HBMModeTileService extends TileService {

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                HbmUtils.setEnabled(context, false);
                updateUI(false);
            }
        }
    };

    private void updateUI(boolean enabled) {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(mScreenStateReceiver,
                new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mScreenStateReceiver);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateUI(HbmUtils.isEnabled());
    }

    @Override
    public void onClick() {
        super.onClick();
        if (HbmUtils.isDcDimmingEnabled(this)) {
            return;
        }

        final boolean enabled = !HbmUtils.isEnabled();
        if (HbmUtils.setEnabled(this, enabled)) {
            updateUI(enabled);
        } else {
            // Reflect the real node state if the write failed.
            updateUI(HbmUtils.isEnabled());
        }
    }
}
