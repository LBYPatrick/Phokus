package com.lbynet.phokus.global

import com.lbynet.phokus.template.BatteryListener
import com.lbynet.phokus.template.RotationListener
import android.content.Intent
import android.hardware.SensorManager
import android.app.Activity
import android.hardware.SensorEventListener
import android.hardware.SensorEvent
import android.content.BroadcastReceiver
import android.content.Context
import androidx.core.content.ContextCompat
import android.content.IntentFilter
import android.hardware.Sensor
import java.util.HashSet

object SysInfo {

    private val batteryListeners = HashSet<BatteryListener>()
    private val rotationListeners = HashSet<RotationListener>()
    private var batteryIntent_: Intent? = null
    private const val isPaused_ = false
    private var sm: SensorManager? = null
    private var accel: Sensor? = null
    private var magnetic: Sensor? = null
    private val sensorListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            onRotationSensorChanged(event, event.sensor.type == Sensor.TYPE_ACCELEROMETER)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) { /*Shhhh*/
        }
    }

    private val angles = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val accelReading = FloatArray(3)
    private val magneticReading = FloatArray(3)
    private val bmsReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            batteryIntent_ = intent
            for (i in batteryListeners) i.onDataAvailable(batteryIntent_)
        }
    }

    fun onRotationSensorChanged(event: SensorEvent, isAccelerometer: Boolean) {
        if (isAccelerometer) System.arraycopy(event.values, 0, accelReading, 0, accelReading.size) else System.arraycopy(event.values, 0, magneticReading, 0, magneticReading.size)
        updateRotationSensorInfo()
    }

    private fun updateRotationSensorInfo() {
        Thread {
            SensorManager.getRotationMatrix(rotationMatrix, null, accelReading, magneticReading)
            SensorManager.getOrientation(rotationMatrix, angles)

            //Push new data to listeners
            for (i in rotationListeners) {
                i.onDataAvailable(angles[0], angles[1], angles[2])
            }
        }.start()
    }

    //TODO: Fill this out if you need to
    @JvmStatic
    fun onPause() {
        enableRotationSensor(false)
    }

    //TODO: Fill this out if you need to
    @JvmStatic
    fun onResume() {
        if (isRotationSensorEnabled) enableRotationSensor(true)
    }

    @JvmStatic
    fun addListener(context : Context, listener: Any) {
        if (listener is RotationListener) {
            rotationListeners.add(context,listener)
            if (rotationListeners.size == 1) enableRotationSensor(true)
        } else {
            batteryListeners.add(listener as BatteryListener)
            if (batteryListeners.size == 1) enableBatterySensor(context,true)
        }
    }

    @JvmStatic
    fun removeListener(activity : Activity, listener: Any) {
        if (listener is RotationListener) {
            rotationListeners.remove(listener)
            if (rotationListeners.size == 0) enableRotationSensor(false)
        } else {
            batteryListeners.remove(listener as BatteryListener)
            if (batteryListeners.size == 0) enableBatterySensor(activity,false)
        }
    }

    @JvmStatic
    private val isRotationSensorEnabled: Boolean
        get() = rotationListeners.size > 0

    @JvmStatic
    private fun enableBatterySensor(context : Context, isEnabled: Boolean) {
        if (isEnabled) context.registerReceiver(bmsReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) else context.unregisterReceiver(bmsReceiver)
    }

    @JvmStatic
    private fun enableRotationSensor(context : Context, isEnabled: Boolean) {
        if (isEnabled) {

            sm = ContextCompat.getSystemService(context, SensorManager::class.java)
            accel = sm!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetic = sm!!.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

            sm!!.registerListener(sensorListener, accel, SensorManager.SENSOR_DELAY_GAME, SensorManager.SENSOR_DELAY_GAME)
            sm!!.registerListener(sensorListener, magnetic, SensorManager.SENSOR_DELAY_GAME, SensorManager.SENSOR_DELAY_GAME)
        }

        else sm!!.unregisterListener(sensorListener)
    }
}