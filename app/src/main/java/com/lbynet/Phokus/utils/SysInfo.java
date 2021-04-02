package com.lbynet.Phokus.utils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;

import androidx.core.content.ContextCompat;

import com.lbynet.Phokus.listener.BMSListener;
import com.lbynet.Phokus.listener.RotationListener;

import java.util.LinkedList;

public class SysInfo {

    private static LinkedList<BMSListener> bmsListeners = new LinkedList<>();
    private static LinkedList<RotationListener> rotListeners = new LinkedList<>();
    private static Intent batteryIntent_ = null;
    private static SensorManager sm = null;
    private static Sensor accel, magnetic;

    final private static SensorEventListener
            sensorListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) { SysInfo.onSensorChanged(event, event.sensor.getType() == Sensor.TYPE_ACCELEROMETER); }
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

           for(BMSListener i : bmsListeners) i.onUpdate(batteryIntent_);
        }
    };

    public static void initialize(Activity activity) {

        activity.registerReceiver(bmsReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        /**
         * Register accelerometer and magnetometer
         */
        sm = ContextCompat.getSystemService(activity, SensorManager.class);

        accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetic = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

    }

    public static void addListener(Object listener) {

        if(listener instanceof BMSListener) {
            bmsListeners.addLast((BMSListener) listener);
        }
        else if(listener instanceof RotationListener) {
            rotListeners.addLast((RotationListener) listener);
        }
    }

    public static void onSensorChanged(SensorEvent event, boolean isAccelerometer) {

        if(isAccelerometer) {
            System.arraycopy(event.values, 0, accelReading, 0, accelReading.length);
        }
        else {
            System.arraycopy(event.values, 0, magneticReading, 0, magneticReading.length);
        }

        updateSensorInfo();
    }

    //TODO: Fill this out if you need to
    public static void onPause() {
        sm.unregisterListener(sensorListener);
    }

    //TODO: Fill this out if you need to
    public static void onResume() {
        sm.registerListener(sensorListener,accel, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        sm.registerListener(sensorListener, magnetic, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
    }

    private static void updateSensorInfo() {
        SensorManager.getRotationMatrix(rotationMatrix,null,accelReading, magneticReading);
        SensorManager.getOrientation(rotationMatrix,angles);

        //Push new data to listeners
        for(RotationListener i : rotListeners) {i.onUpdate(angles.clone());}
    }

    public static void removeListener(Object listener) {
        if(listener instanceof BMSListener) bmsListeners.remove(listener);
        else if(listener instanceof RotationListener) rotListeners.remove(listener);
    }
}
