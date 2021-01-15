package com.lbynet.Phokus.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;

import androidx.camera.camera2.internal.compat.CameraManagerCompat;

import java.util.HashMap;

public class CameraUtils {

    public static HashMap<Integer, CameraCharacteristics> ccMap = new HashMap<>();
    public static HashMap<Integer,Float> focalLengthMap = new HashMap<>(),
                                         cropFactorMap = new HashMap<>();
    final public static String TAG = CameraUtils.class.getSimpleName();

    @SuppressLint("RestrictedApi")
    public static CameraCharacteristics getCameraCharacteristics(Context context, int cameraId) {

        if (ccMap.containsKey(cameraId)) return ccMap.get(cameraId);

        try {
            CameraCharacteristics cc =
                    CameraManagerCompat
                            .from(context)
                            .unwrap()
                            .getCameraCharacteristics(Integer.toString(cameraId));

            ccMap.put(cameraId, cc);
            return cc;

        } catch (Exception e) {
            SAL.print(TAG, "Failed to obtain CameraCharacteristics of cameraId " + cameraId + ".");
            SAL.print(e);
            return null;
        }
    }

    @SuppressLint("RestrictedApi")
    public static int getCameraCount(Context context) {

        try {
            return CameraManagerCompat.from(context).unwrap().getCameraIdList().length;
        } catch (Exception e) {
            SAL.print(TAG,"Failed to obtain camera count.");
            SAL.print(e);
            return -1;
        }
    }

    public static float getFocalLength(Context context,int cameraId) {

        if(focalLengthMap.containsKey(cameraId)) return focalLengthMap.get(cameraId);

        CameraCharacteristics cc = getCameraCharacteristics(context,cameraId);
        if(cc == null) {
            SAL.print("Failed to obtain focal length.");
            return -1;
        }

        float r = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)[0];

        focalLengthMap.put(cameraId,r);

        return r;
    }

    public static float getCropFactor(Context context, int cameraId) {

        if(cropFactorMap.containsKey(cameraId)) return cropFactorMap.get(cameraId);

        CameraCharacteristics cc = getCameraCharacteristics(context,cameraId);
        if(cc == null) {
            SAL.print("Failed to obtain crop factor.");
            return -1;
        }

        float r = MathTools.getCropFactor(cc.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE));

        cropFactorMap.put(cameraId,r);

        return r;
    }

}
