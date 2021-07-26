package com.lbynet.Phokus.global;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Size;

import androidx.annotation.StringDef;
import androidx.camera.core.AspectRatio;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public class Config {

    private static HashMap<String, Object> default_ = new HashMap<>();
    private static HashMap<String, Object> modified_ = new HashMap<>();

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

        putDefault(FRONT_FACING, false);
        putDefault(VIDEO_MODE, false);
        putDefault(VIDEO_RESOLUTION, new Size(3840, 2160));
        putDefault(VIDEO_BITRATE_MBPS, 100);
        putDefault(VIDEO_FPS, 30);
        putDefault(VIDEO_STB, true);
        putDefault(VIDEO_LOG_PROFILE, "OFF");
        putDefault(STILL_JPEG_QUALITY, 95);
        putDefault(NR_QUALITY, CameraCharacteristics.NOISE_REDUCTION_MODE_HIGH_QUALITY);
        putDefault(AWB_LOCK, false);
        putDefault(AE_LOCK, false);
    }

    private static void putDefault(@Options String key, Object value) {
        default_.put(key,value);
    }

    private static void putModified(@Options String key, Object value) {
        modified_.put(key,value);
    }

    public static void set(@Options String key, Object value) {
        putModified(key,value);
    }

    public static @NotNull Object get(@Options String key) {
        if(modified_.containsKey(key)) return modified_.get(key);
        return default_.get(key);
    }

    //TODO: Fill this out
    public static void loadConfig() {

    }

}
