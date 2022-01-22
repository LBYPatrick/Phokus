package com.lbynet.phokus.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class BatterySensor(private val context : Context, private val listener : BatteryListener) {

    private val bmsReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            listener.onDataAvailable(intent)
        }
    }

    init {
        context.registerReceiver(bmsReceiver,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun hibernate() {
        context.unregisterReceiver(bmsReceiver)
    }

    fun resume() {
        context.registerReceiver(bmsReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

}