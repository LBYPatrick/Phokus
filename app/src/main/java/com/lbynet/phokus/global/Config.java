package com.lbynet.phokus.global;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Size;

import androidx.annotation.StringDef;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public class Config {

    private static ConcurrentHashMap<String, String> default_ = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, String> modified_ = new ConcurrentHashMap<>();

    @Retention(SOURCE)
    @StringDef(
            {FRONT_FACING,
                    VIDEO_MODE,
                    VIDEO_RESOLUTION,
                    VIDEO_BITRATE_MBPS,
                    VIDEO_FPS,
                    VIDEO_STB,
            STILL_JPEG_QUALITY,
            NR_QUALITY,
            AWB_LOCK,
            AE_LOCK,
            VIDEO_LOG_PROFILE}
    )
    public @interface Options{}
    final public static String FRONT_FACING = "camera_front_facing",
            VIDEO_MODE = "camera_video_mode",
            VIDEO_RESOLUTION = "camera_video_resolution",
            VIDEO_BITRATE_MBPS  = "camera_video_bitrate_mbps",
            VIDEO_FPS = "camera_video_fps",
            VIDEO_STB = "camera_video_stablization",
            STILL_JPEG_QUALITY = "camera_still_jpeg_quality",
            NR_QUALITY = "camera_noise_reduction_quality",
            AWB_LOCK = "camera_awb_lock",
            AE_LOCK = "camera_ae_lock",
            VIDEO_LOG_PROFILE = "camera_video_log_profile";

    static {
        putDefault(FRONT_FACING, "false");
        putDefault(VIDEO_MODE, "false");
        putDefault(VIDEO_RESOLUTION, "UHD");
        putDefault(VIDEO_BITRATE_MBPS, "100");
        putDefault(VIDEO_FPS, "30");
        putDefault(VIDEO_STB, "true");
        putDefault(VIDEO_LOG_PROFILE, "false");
        putDefault(STILL_JPEG_QUALITY, "95");
        putDefault(NR_QUALITY, "high_quality");
        putDefault(AWB_LOCK, "false");
        putDefault(AE_LOCK, "false");
    }

    private static void putDefault(@Options String key, String value) {
        default_.put(key,value);
    }

    private static void putModified(@Options String key, String value) {
        modified_.put(key,value);
    }

    public static void set(@Options String key, String value) {
        putModified(key,value);
    }

    public static String get(@Options String key) {

        String r = "";

        if((r = modified_.get(key)) != null
                || (r = default_.get(key)) != null) return r;

        return null;
    }

    //TODO: Fill this out
    public static void loadConfig() {

    }

}
