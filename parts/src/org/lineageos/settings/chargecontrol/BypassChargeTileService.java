/*
 * Copyright (C) 2025 The LineageOS Project
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
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import org.lineageos.settings.R;

public class BypassChargeTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();

        if (!ChargeControlUtils.isSupported()) {
            Toast.makeText(this, R.string.charge_bypass_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ChargeControlUtils.isEnabled(this)) {
            Toast.makeText(this, R.string.charge_bypass_requires_charge_control,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean enable = !ChargeControlUtils.isBypassEnabled(this);
        if (enable) {
            ChargeControlUtils.SafetyCheckResult safetyCheck =
                    ChargeControlUtils.checkBypassSafety(this);
            if (!safetyCheck.isSafe()) {
                Toast.makeText(this, safetyCheck.getReason(), Toast.LENGTH_LONG).show();
                updateTile();
                return;
            }
        }

        if (!ChargeControlUtils.setBypassEnabled(this, enable)) {
            Toast.makeText(this, R.string.charge_bypass_write_failed, Toast.LENGTH_LONG).show();
        }
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        if (!ChargeControlUtils.isSupported()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(getString(R.string.charge_bypass_unavailable));
        } else if (!ChargeControlUtils.isEnabled(this)) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(getString(R.string.charge_bypass_requires_charge_control));
        } else if (ChargeControlUtils.isBypassEnabled(this)) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setSubtitle(getString(R.string.charge_bypass_tile_active));
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle(getString(R.string.charge_bypass_tile_inactive));
        }
        tile.updateTile();
    }

    public static void requestUpdate(Context context) {
        try {
            TileService.requestListeningState(context,
                    new ComponentName(context, BypassChargeTileService.class));
        } catch (Exception e) {
            // The tile is not available, nothing to update
        }
    }
}
