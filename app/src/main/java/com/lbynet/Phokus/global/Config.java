package com.lbynet.Phokus.global;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Size;

import androidx.camera.core.AspectRatio;

import com.lbynet.Phokus.camera.CameraConsts;

import java.util.HashMap;

public class Config {

    private static HashMap<String, Object> default_ = new HashMap<>();
    private static HashMap<String, Object> modified_ = new HashMap<>();

    static {

        putDefault(CameraConsts.FRONT_FACING,false);

        putDefault(CameraConsts.FRONT_FACING,false);
        putDefault(CameraConsts.PREVIEW_ASPECT_RATIO, AspectRatio.RATIO_4_3);
        putDefault(CameraConsts.VIDEO_MODE,false);
        putDefault(CameraConsts.VIDEO_RESOLUTION,new Size(3840,2160));
        putDefault(CameraConsts.VIDEO_BITRATE_MBPS,100);
        putDefault(CameraConsts.VIDEO_FPS,30);
        putDefault(CameraConsts.VIDEO_STB,true);
        putDefault(CameraConsts.VIDEO_LOG_PROFILE,"OFF");
        putDefault(CameraConsts.STILL_JPEG_QUALITY,95);
        putDefault(CameraConsts.NR_QUALITY, CameraCharacteristics.NOISE_REDUCTION_MODE_HIGH_QUALITY);
        putDefault(CameraConsts.AWB_LOCK,false);
        putDefault(CameraConsts.AE_LOCK,false);
    }

    private static void putDefault(String key, Object value) {
        default_.put(key,value);
    }

    private static void putModified(String key, Object value) {
        modified_.put(key,value);
    }

    public static void set(String key, Object value) {
        putModified(key,value);
    }

    public static Object get(String key) {
        if(modified_.containsKey(key)) return modified_.get(key);
        else if(default_.containsKey(key)) return default_.get(key);
        else return null;
    }

    //TODO: Fill this out
    public static void loadConfig() {

    }

}
