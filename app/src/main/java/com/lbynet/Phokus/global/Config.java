package com.lbynet.Phokus.global;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Size;

import java.util.HashMap;

public class Config {

    private static HashMap<String, Object> default_ = new HashMap<>();
    private static HashMap<String, Object> modified_ = new HashMap<>();

    static {
        putDefault("VIDEO_RESOLUTION",new Size(3840,2160));
        putDefault("VIDEO_BITRATE_MBPS",100);
        putDefault("VIDEO_WIDESCREEN",true);
        putDefault("VIDEO_FPS",30);
        putDefault("VIDEO_STB",true);
        putDefault("VIDEO_LOG_PROFILE","OFF");
        putDefault("STILL_JPEG_QUALITY",95);
        putDefault("STILL_NR_QUALITY", CameraCharacteristics.NOISE_REDUCTION_MODE_HIGH_QUALITY);
    }

    private static void putDefault(String key, Object value) {
        default_.put(key,value);
    }

    private static void putModified(String key, Object value) {
        modified_.put(key,value);
    }

    private static void set(String key, Object value) {
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
