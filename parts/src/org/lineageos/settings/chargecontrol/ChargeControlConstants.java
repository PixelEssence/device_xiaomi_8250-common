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

public final class ChargeControlConstants {

    private ChargeControlConstants() {
        // This class is not supposed to be instantiated
    }

    public static final String KEY_CHARGE_CONTROL = "charge_control";
    public static final String KEY_STOP_CHARGING = "stop_charging";

    public static final String NODE_STOP_CHARGING =
            "/sys/class/power_supply/battery/input_suspend";

    public static final int DEFAULT_STOP_CHARGING = 100;

    // Keep in sync with android:min in res/xml/charge_control.xml
    public static final int MIN_STOP_CHARGING = 20;
}
