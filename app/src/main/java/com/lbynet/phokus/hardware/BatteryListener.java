package com.lbynet.phokus.hardware;

import android.content.Intent;

public interface BatteryListener {
    void onDataAvailable(Intent batteryIntent);
}
