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

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.lineageos.settings.R;

public class ChargeControlTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!ChargeControlUtils.isSupported()) {
            return;
        }
        ChargeControlUtils.setEnabled(this, !ChargeControlUtils.isEnabled(this));
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        if (!ChargeControlUtils.isSupported()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(getString(R.string.kernel_node_access_error));
            tile.updateTile();
            return;
        }

        if (ChargeControlUtils.isEnabled(this)) {
            final int stopLevel = ChargeControlUtils.getStopLevel(this);
            tile.setState(Tile.STATE_ACTIVE);
            tile.setSubtitle(getString(
                    ChargeControlUtils.isSuspended()
                            ? R.string.charge_control_tile_paused
                            : R.string.charge_control_tile_subtitle,
                    stopLevel));
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle(getString(R.string.charge_control_tile_disabled));
        }
        tile.updateTile();
    }
}
