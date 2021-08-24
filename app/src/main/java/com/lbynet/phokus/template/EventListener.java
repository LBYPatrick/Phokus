package com.lbynet.phokus.template;

import com.lbynet.phokus.utils.SAL;

public abstract class EventListener {

    public enum DataType {
        INT_VIDEO_FPS,
        INT_ARR_LUMA_INFO,
        INT_ARR_VIEW_DIMENSION,
        FLOAT_CAM_FOCAL_LENGTH,
        VOID_CAMERA_BOUND,
        VOID_CAMERA_BINDING,
        STRING_INFO,
        STRING_WARNING,
        STRING_ALERT,
        STRING_FOCUS_STAT,
        URI_PICTURE_SAVED,
        URI_VIDEO_SAVED,
        INTENT_BMS,
        FLOAT_ARR_ROTATION
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

    public boolean onEventUpdated(DataType dataType, Object extra) {

        SAL.print("EVENT",
                "Type: "
                        + dataType.toString() + "\tData:"
                        + (extra == null ? "" : extra.toString()));

        return true;
    }

    public boolean onEventFinished(boolean isSuccess, String extra) {

        SAL.print("EVENT", (isSuccess ? "Succeeded" : "Failed") + ". MSG: " + extra);

        return true;
    }
}
