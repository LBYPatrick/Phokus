package com.lbynet.Phokus.utils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.lbynet.Phokus.listener.BMSListener;

import java.util.LinkedList;

public class SysInfo {

    private static LinkedList<BMSListener> bmsListeners = new LinkedList<>();
    private static Intent batteryIntent_ = null;

    private static BroadcastReceiver bmsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            batteryIntent_ = intent;

           for(BMSListener i : bmsListeners) {
                i.onUpdate(batteryIntent_);
            }
        }
    };

    public static void initialize(Activity activity) {
        activity.registerReceiver(bmsReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    public static Intent addBMSListener(BMSListener listener) {
        bmsListeners.addLast(listener);

        return batteryIntent_;
    }

    public static void removeBMSListener(BMSListener listener) {
        bmsListeners.remove(listener);
    }
}
