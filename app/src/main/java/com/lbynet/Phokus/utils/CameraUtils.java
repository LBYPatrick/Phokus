package com.lbynet.Phokus.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.TonemapCurve;

import androidx.camera.camera2.internal.compat.CameraManagerCompat;

import java.util.Arrays;
import java.util.HashMap;

public class CameraUtils {

    public static HashMap<Integer, CameraCharacteristics> ccMap = new HashMap<>();
    public static HashMap<Integer,Float> focalLengthMap = new HashMap<>(),
                                         cropFactorMap = new HashMap<>();
    final public static String TAG = CameraUtils.class.getSimpleName();
    
    public enum LogScheme {
        CLOG,
        SLOG;
    }

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


    public static TonemapCurve makeToneMapCurve(CameraCharacteristics cc) {
        return makeToneMapCurve(LogScheme.CLOG, cc);
    }

    public static TonemapCurve makeToneMapCurve(LogScheme scheme, CameraCharacteristics cc) {
        return makeToneMapCurve(scheme, cc.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS));
    }

    //TODO: Finish this
    public static TonemapCurve makeToneMapCurve(LogScheme scheme, final int points) {

        //草，植树问题
        final double incr = 1 / (double)(points - 2);
        float [] ptArray = new float[points * 2];
        float x = 0;

        ptArray[0] = 0;
        ptArray[1] = 0;

        x += incr;

        for(int i = 2; i < ptArray.length; i += 2) {



            if(x >= 1) x = 1;

            ptArray[i] = x;

            //Equation: y = a * tanh(bx), where a = 0.9, b = exp
            //ptArray[i+1] = (float)(0.9 * Math.tanh((x * (double)exp)));

            /**
             * Sony S-LOG: y = (0.432699*log10(x+0.037584)+0.616596)+0.03
             *             (x {0,10}, y {0,1000}, 14 bit to 10 bit)
             *
             * Source: http://www.theodoropoulos.info/attachments/076_on%20S-Log.pdf
             *
             * Canon Log: y = (0.529136 * log10(10.1596x + 1) + 0.0730597);
             *             (x {-0.045, 8.001}, y {-0.0685, 1.087}, 14 bit to 8 bit)
             *
             * Source: https://support.usa.canon.com/resources/sites/CANON/content/live/ARTICLES/170000/ART170247/en_US/White_Paper_Clog_optoelectronic.pdf
             *
             * Since we want y {0,1] and x {0,1} and the output is in 8 bit,
             * modification to these formulas is necessary.
             */

            ptArray[i+1] = x;

            switch (scheme) {
                case CLOG:
                    ptArray[i+1]
                            //= (float)(0.529136 * Math.log10(10.1596 * 4 * x + 1) + 0.0730597);
                            = (float)(1 / 1.0865* (0.529136 *Math.log10(10.1596*8*x+1) +0.0730597));
                    break;
                case SLOG:
                    ptArray[i+1] = (float)(1/1.08 * ((0.432699*Math.log10(10*x +0.037584)+0.616596)+0.03));
                    break;

            }


            //Cap ptArray[i+1] to 1 if the LOG value is greater than 1
            if(ptArray[i+1] > 1) ptArray[i+1] = 1;

            x += incr;
        }

        SAL.print("Final X: " + x +"\n");
        SAL.print("Array: " + Arrays.toString(ptArray) + "\n");

        return new TonemapCurve(ptArray,ptArray,ptArray);
    }

}
