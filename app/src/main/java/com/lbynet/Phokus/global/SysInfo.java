package com.lbynet.Phokus.global;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.core.content.ContextCompat;

import com.lbynet.Phokus.template.BatteryListener;
import com.lbynet.Phokus.template.RotationListener;

import java.util.HashSet;

public class SysInfo {

    private static HashSet<BatteryListener> batteryListeners = new HashSet<>();
    private static HashSet<RotationListener> rotationListeners = new HashSet<>();

    private static Intent batteryIntent_ = null;
    private static boolean isPaused_ = false;
    private static SensorManager sm = null;
    private static Sensor accel, magnetic;
    private static Activity activity_;

    final private static SensorEventListener
            sensorListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) { SysInfo.onRotationSensorChanged(event, event.sensor.getType() == Sensor.TYPE_ACCELEROMETER); }
                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {/*Shhhh*/}
            };
    final private static float [] angles = new float[3],
                            rotationMatrix = new float[9],
                            accelReading = new float[3],
                            magneticReading = new float[3];


    private static BroadcastReceiver bmsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            batteryIntent_ = intent;
           for(BatteryListener i : batteryListeners)
               i.onDataAvailable(batteryIntent_);
        }
    };

    public static void initialize(Activity activity) {

        activity_ = activity;

        /**
         * Setup accelerometer and magnetometer
         */
        sm = ContextCompat.getSystemService(activity, SensorManager.class);
        accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetic = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

    }

    public static void onRotationSensorChanged(SensorEvent event, boolean isAccelerometer) {

        if(isAccelerometer) System.arraycopy(event.values, 0, accelReading, 0, accelReading.length);
        else  System.arraycopy(event.values, 0, magneticReading, 0, magneticReading.length);

        updateRotationSensorInfo();
    }

    private static void updateRotationSensorInfo() {

        new Thread( () -> {
            SensorManager.getRotationMatrix(rotationMatrix, null, accelReading, magneticReading);
            SensorManager.getOrientation(rotationMatrix, angles);

            //Push new data to listeners
            for (RotationListener i : rotationListeners) {
                i.onDataAvailable(angles[0], angles[1], angles[2]);
            }
        }).start();
    }

    //TODO: Fill this out if you need to
    public static void onPause() {
        enableRotationSensor(false);
    }

    //TODO: Fill this out if you need to
    public static void onResume() {
        if(isRotationSensorEnabled()) enableRotationSensor(true);
    }

    public static void addListener(Object listener) {

        if(listener instanceof RotationListener) {
            rotationListeners.add((RotationListener) listener);
            if(rotationListeners.size() == 1) enableRotationSensor(true);
        }
        else {
            batteryListeners.add((BatteryListener) listener);
            if(batteryListeners.size() == 1) enableBatterySensor(true);
        }
    }

    public static void removeListener(Object listener) {

        if(listener instanceof RotationListener) {

            rotationListeners.remove((RotationListener)listener);
            if(rotationListeners.size() == 0) enableRotationSensor(false);
        }
        else {
            batteryListeners.remove((BatteryListener)listener);
            if(batteryListeners.size() == 0) enableBatterySensor(false);
        }
    }

    private static boolean isRotationSensorEnabled() {
        return rotationListeners.size() > 0;
    }


    private static void enableBatterySensor(boolean isEnabled) {

        if(isEnabled) activity_.registerReceiver(bmsReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        else activity_.unregisterReceiver(bmsReceiver);

    }

    private static void enableRotationSensor(boolean isEnabled) {

        if(isEnabled) {
            sm.registerListener(sensorListener,accel, SensorManager.SENSOR_DELAY_GAME, SensorManager.SENSOR_DELAY_GAME);
            sm.registerListener(sensorListener, magnetic, SensorManager.SENSOR_DELAY_GAME, SensorManager.SENSOR_DELAY_GAME);
        }
        else sm.unregisterListener(sensorListener);

    }

}
