package com.lbynet.Phokus.listener;

import com.lbynet.Phokus.utils.SAL;

public abstract class EventListener {

    //public abstract boolean onEventCreated(String extra);
    public boolean onEventBegan(String extra) {

        SAL.print("EVENT","Began. MSG: " + extra);

        return true;
    }

    public boolean onEventUpdated(String extra) {
        SAL.print("EVENT", "Updated. MSG: " + extra);

        return true;
    }

    public boolean onEventFinished(boolean isSuccess, String extra) {

        SAL.print("EVENT", (isSuccess ? "Succeeded" : "Failed") + ". MSG: " + extra);

        return true;
    }
}
