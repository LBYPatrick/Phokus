package com.lbynet.phokus.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat

class RotationSensor(private val context : Context, private val listener : RotationListener) {

    private var sm : SensorManager? = null
    private var magnetic : Sensor? = null
    private var accel : Sensor? = null
    private val angles = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val accelReading = FloatArray(3)
    private val magneticReading = FloatArray(3)

    private val sensorListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            onRotationSensorChanged(event, event.sensor.type == Sensor.TYPE_ACCELEROMETER)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) { /*Shhhh*/ }
    }

    init {
        sm = ContextCompat.getSystemService(context,SensorManager::class.java)
        accel = sm!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetic = sm!!.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        sm!!.registerListener(
            sensorListener,
            accel,
            SensorManager.SENSOR_DELAY_GAME,
            SensorManager.SENSOR_DELAY_GAME)

        sm!!.registerListener(
            sensorListener,
            magnetic,
            SensorManager.SENSOR_DELAY_GAME,
            SensorManager.SENSOR_DELAY_GAME)

    }

    fun hibernate() {
        sm!!.unregisterListener(sensorListener)
    }

    fun resume() {
        sm!!.registerListener(
            sensorListener,
            accel,
            SensorManager.SENSOR_DELAY_GAME,
            SensorManager.SENSOR_DELAY_GAME)

        sm!!.registerListener(
            sensorListener,
            magnetic,
            SensorManager.SENSOR_DELAY_GAME,
            SensorManager.SENSOR_DELAY_GAME)

    }

    private fun onRotationSensorChanged(event: SensorEvent, isAccelerometer: Boolean) {
        if (isAccelerometer) System.arraycopy(event.values, 0,
            accelReading, 0, accelReading.size) else System.arraycopy(event.values, 0,
            magneticReading, 0, magneticReading.size)

        updateRotationSensorInfo()
    }

    private fun updateRotationSensorInfo() {
        Thread {

            SensorManager.getRotationMatrix(rotationMatrix, null, accelReading, magneticReading)
            SensorManager.getOrientation(rotationMatrix, angles)

            listener.onDataAvailable(angles[0],angles[1],angles[2])

        }.start()
    }

}