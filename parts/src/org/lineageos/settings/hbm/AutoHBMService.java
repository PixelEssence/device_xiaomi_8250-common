/*
 * Copyright (C) 2018-2021 crDroid Android Project
 * Copyright (C) 2019-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.hbm;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

/**
 * Enables HBM while ambient light stays above the configured threshold and
 * turns it off again after the configured delay.
 *
 * All state is handled on the main thread: the sensor listener and the delayed
 * disable callback both run on the main looper.
 */
public class AutoHBMService extends Service {
    private static final String TAG = "AutoHBMService";
    private static final boolean DEBUG = false;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private SensorManager mSensorManager;
    private Sensor mLightSensor;

    private boolean mListening;
    private boolean mAutoActive;
    private float mLastLux = -1;

    private final Runnable mDisableRunnable = () -> {
        if (!mAutoActive) {
            return;
        }
        if (mLastLux >= HbmUtils.getThreshold(this)) {
            // Light came back up while we were waiting.
            return;
        }
        if (DEBUG) Log.d(TAG, "Leaving HBM, ambient light is low");
        mAutoActive = false;
        HbmUtils.setEnabled(this, false);
    };

    private final SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values.length == 0) {
                return;
            }

            mLastLux = event.values[0];
            final int threshold = HbmUtils.getThreshold(AutoHBMService.this);

            if (mLastLux >= threshold) {
                mHandler.removeCallbacks(mDisableRunnable);
                if (mAutoActive || !canEnable()) {
                    return;
                }
                if (DEBUG) Log.d(TAG, "Entering HBM, ambient light is high");
                mAutoActive = HbmUtils.setEnabled(AutoHBMService.this, true);
            } else {
                scheduleDisable();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };

    private boolean canEnable() {
        if (!HbmUtils.isAutoEnabled(this)) {
            return false;
        }
        KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
        if (keyguardManager != null && keyguardManager.isKeyguardLocked()) {
            return false;
        }
        return !HbmUtils.isDcDimmingEnabled(this);
    }

    private void scheduleDisable() {
        if (!mAutoActive) {
            return;
        }
        mHandler.removeCallbacks(mDisableRunnable);
        mHandler.postDelayed(mDisableRunnable, HbmUtils.getDisableDelay(this) * 1000L);
    }

    private void startListening() {
        if (mListening || mSensorManager == null || mLightSensor == null) {
            return;
        }
        mSensorManager.registerListener(mSensorListener, mLightSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        mListening = true;
    }

    private void stopListening() {
        if (!mListening) {
            return;
        }
        mSensorManager.unregisterListener(mSensorListener);
        mListening = false;
    }

    private void disableForScreenOff() {
        mHandler.removeCallbacks(mDisableRunnable);
        if (mAutoActive) {
            mAutoActive = false;
            HbmUtils.setEnabled(this, false);
        }
    }

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                startListening();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                stopListening();
                disableForScreenOff();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        mSensorManager = getSystemService(SensorManager.class);
        mLightSensor = mSensorManager == null
                ? null : mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        PowerManager powerManager = getSystemService(PowerManager.class);
        if (powerManager != null && powerManager.isInteractive()) {
            startListening();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!HbmUtils.isAutoEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        PowerManager powerManager = getSystemService(PowerManager.class);
        if (powerManager != null && powerManager.isInteractive()) {
            startListening();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        stopListening();
        unregisterReceiver(mScreenStateReceiver);
        disableForScreenOff();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
