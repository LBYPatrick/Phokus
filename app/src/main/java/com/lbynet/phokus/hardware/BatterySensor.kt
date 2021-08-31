package com.lbynet.phokus.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.lbynet.phokus.global.SysInfo
import com.lbynet.phokus.template.BatteryListener

class BatterySensor(context : Context, listener : BatteryListener) {


    private val listener : BatteryListener = listener
    private val context : Context = context

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