package com.lbynet.phokus.template;

import android.content.Intent;

public abstract class BatteryListener {
    abstract public void onDataAvailable(Intent batteryIntent);
}
