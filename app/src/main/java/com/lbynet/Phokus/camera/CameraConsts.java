package com.lbynet.Phokus.camera;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Size;

import androidx.camera.core.AspectRatio;

public class CameraConsts {

    final public static int USECASE_PREVIEW = 0,
                      USECASE_VIDEO_CAPTURE = 1,
                      USECASE_IMAGE_CAPTURE = 2,
                      USECASE_IMAGE_ANALYSIS = 3;

    final public static String FRONT_FACING = "camera_front_facing",
            PREVIEW_ASPECT_RATIO = "camera_preview_aspect_ratio",
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
}
