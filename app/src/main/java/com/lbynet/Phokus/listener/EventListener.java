package com.lbynet.Phokus.listener;

import com.lbynet.Phokus.utils.SAL;

public abstract class EventListener {

    public enum DataType {
        VIDEO_REC_STAT,
        VIDEO_FPS,
        CAM_LUMA_INFO,
        CAM_FOCAL_LENGTH,
        STRING_INFO,
        STRING_WARNING,
        STRING_ALERT
    }

    //public abstract boolean onEventCreated(String extra);
    public boolean onEventBegan(String extra) {

        SAL.print("EVENT","Began. MSG: " + extra);

        return true;
    }

    public boolean onEventUpdated(String extra) {
        SAL.print("EVENT", "Updated. MSG: " + extra);

        return true;
    }

    public boolean onEventUpdated(DataType dataType, Object data) {
        return true;
    }

    public boolean onEventFinished(boolean isSuccess, String extra) {

        SAL.print("EVENT", (isSuccess ? "Succeeded" : "Failed") + ". MSG: " + extra);

        return true;
    }
}
