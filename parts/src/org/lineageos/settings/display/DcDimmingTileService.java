/*
* Copyright (C) 2018 The OmniROM Project
* Copyright (C) 2026 The LineageOS Project
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
package org.lineageos.settings.display;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.hbm.HbmUtils;
import org.lineageos.settings.utils.FileUtils;

public class DcDimmingTileService extends TileService {

    public static final String DC_DIMMING_ENABLE_KEY = "dc_dimming_enable";
    private static final String DC_DIMMING_NODE =
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/dimlayer_exposure";

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                setEnabled(context, false);
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

    static void setEnabled(Context context, boolean enabled) {
        FileUtils.writeLine(DC_DIMMING_NODE, enabled ? "1" : "0");
        if (enabled) {
            // DC dimming and HBM cannot be active at the same time.
            HbmUtils.setEnabled(context, false);
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(DC_DIMMING_ENABLE_KEY, enabled).apply();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(mScreenStateReceiver,
                new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mScreenStateReceiver);
        super.onDestroy();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        updateUI(prefs.getBoolean(DC_DIMMING_ENABLE_KEY, false));
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean enabled = !prefs.getBoolean(DC_DIMMING_ENABLE_KEY, false);
        setEnabled(this, enabled);
        updateUI(enabled);
    }
}
