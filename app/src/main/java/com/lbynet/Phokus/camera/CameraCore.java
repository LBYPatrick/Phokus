package com.lbynet.Phokus.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CaptureRequest;
import android.util.EventLog;
import android.util.Range;
import android.util.Size;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCase;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.lbynet.Phokus.global.Config;
import com.lbynet.Phokus.template.EventListener;
import com.lbynet.Phokus.utils.SAL;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

//TODO: Finish this
public class CameraCore {

    final public static String TAG = CameraCore.class.getCanonicalName();

    //From frontend
    static Context context_;
    static PreviewView preview_view_;
    static CaptureRequestOptions.Builder crob_ = new CaptureRequestOptions.Builder();

    //CameraX components
    static CameraSelector cs_;
    static Camera camera_;
    static ImageCapture image_capture_;
    static VideoCapture video_capture_;
    static ProcessCameraProvider pcp;
    static float default_zoom_ = -1,
            prev_zoom_ = -1;
    static Executor main_executor_;
    static FocusAction focus_action_;

    //Other internal variables
    static boolean is_front_facing_ = false,
            is_recording_ = false;

    public static void initialize() {
        Config.loadConfig();
    }

    public static void start(PreviewView preview_view) {

        context_ = preview_view.getContext();
        preview_view_ = preview_view;
        main_executor_ = ContextCompat.getMainExecutor(context_);

        ListenableFuture<ProcessCameraProvider> listenable_future_ = ProcessCameraProvider.getInstance(context_);

        listenable_future_.addListener(() -> {
                    try {
                        pcp = listenable_future_.get();
                        bindCameraX();
                    } catch (Exception e) {
                        SAL.print(e);
                    }
                }
                , ContextCompat.getMainExecutor(context_)
        );
    }

    @SuppressLint("RestrictedApi")
    private static void bindCameraX() {

        pcp.unbindAll();

        SAL.print(TAG, "CameraX binding...");

        CameraSelector cs = new CameraSelector.Builder()
                .requireLensFacing(is_front_facing_ ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();

        boolean is_video_mode = (Boolean) Config.get(CameraConsts.VIDEO_MODE);

        camera_ = pcp.bindToLifecycle((LifecycleOwner) context_, cs, buildUseCaseArray(
                CameraConsts.USECASE_PREVIEW,
                (is_video_mode ? CameraConsts.USECASE_VIDEO_CAPTURE :
                        CameraConsts.USECASE_IMAGE_CAPTURE)
        ));

        default_zoom_ = CameraUtils.get35FocalLength(context_, is_front_facing_ ? 1 : 0);
        prev_zoom_ = default_zoom_;

        updateCameraConfig();

        SAL.print(TAG, "CameraX bound.");
    }

    public static UseCase[] buildUseCaseArray(int... types) {

        UseCase[] r = new UseCase[types.length];

        int cnt = 0;
        while (cnt < types.length) {
            r[cnt] = buildUseCase(types[cnt]);
            ++cnt;
        }
        return r;
    }

    @SuppressLint("RestrictedApi")
    public static UseCase buildUseCase(int type) {

        switch (type) {
            case CameraConsts.USECASE_PREVIEW:

                Preview p = new Preview.Builder()
                        .setTargetAspectRatio((Integer) Config.get(CameraConsts.PREVIEW_ASPECT_RATIO))
                        .build();

                p.setSurfaceProvider(preview_view_.getSurfaceProvider());
                return p;

            case CameraConsts.USECASE_VIDEO_CAPTURE:

                video_capture_ = new VideoCapture.Builder()
                        .setTargetResolution((Size) Config.get(CameraConsts.VIDEO_RESOLUTION))
                        .setVideoFrameRate((Integer) Config.get(CameraConsts.VIDEO_FPS))
                        .setBitRate((Integer) Config.get(CameraConsts.VIDEO_BITRATE_MBPS) * 1048576)
                        .build();

                return video_capture_;

            case CameraConsts.USECASE_IMAGE_CAPTURE:

                image_capture_ = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                return image_capture_;

            case CameraConsts.USECASE_IMAGE_ANALYSIS:

                ImageAnalysis ia = new ImageAnalysis.Builder()
                        .setTargetResolution((Size) Config.get(CameraConsts.VIDEO_RESOLUTION))
                        .build();

                //TODO: Do Analyzer stuff here
                //ia.setAnalyzer();
                return ia;

            default:
                return null;
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    public static void updateCameraConfig() {

        boolean is_video_mode = (Boolean) Config.get(CameraConsts.VIDEO_MODE);

        int videoFps = (int) Config.get(CameraConsts.VIDEO_FPS);

        //3A
        crob_
                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AWB_LOCK,
                        (Boolean) Config.get(CameraConsts.AWB_LOCK) || is_recording_)

                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_LOCK,
                        (Boolean) Config.get(CameraConsts.AE_LOCK) || is_recording_);

        //Video-specfic settings
        if (is_video_mode) {

            boolean is_log_enabled_ = !((String) Config.get(CameraConsts.VIDEO_LOG_PROFILE)).equals("OFF");

            crob_
                    .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            new Range(videoFps, videoFps))

                    .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                            videoFps % 25 == 0 ?
                                    CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ :
                                    CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ)

                    .setCaptureRequestOption(
                            CaptureRequest.TONEMAP_MODE,
                            is_log_enabled_ ?
                                    CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE :
                                    CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)

                    .setCaptureRequestOption(
                            CaptureRequest.EDGE_MODE,
                            is_log_enabled_ ?
                                    CaptureRequest.EDGE_MODE_OFF :
                                    CaptureRequest.EDGE_MODE_HIGH_QUALITY)

                    .setCaptureRequestOption(
                            CaptureRequest.TONEMAP_CURVE,
                            ((String) (Config.get(CameraConsts.VIDEO_LOG_PROFILE))).equals("CLOG") ?
                                    CameraUtils.makeToneMapCurve(
                                            CameraUtils.LogScheme.CLOG,
                                            CameraUtils.getCameraCharacteristics(context_, is_front_facing_ ? 1 : 0)) :

                                    CameraUtils.makeToneMapCurve(
                                            CameraUtils.LogScheme.SLOG,
                                            CameraUtils.getCameraCharacteristics(context_, is_front_facing_ ? 1 : 0)));

        }
        //Photo-specfic settings
        else {
            crob_
                    .clearCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE)
                    .setCaptureRequestOption(CaptureRequest.JPEG_QUALITY, ((Integer) Config.get(CameraConsts.STILL_JPEG_QUALITY)).byteValue());
        }

        ListenableFuture<Void> cro_future = Camera2CameraControl.from(camera_.getCameraControl()).addCaptureRequestOptions(crob_.build());

        cro_future.addListener(() -> {

            try {
                cro_future.get();

                SAL.print(TAG, "CaptureRequestOptions updated.");

            } catch (Exception e) {
                SAL.print(e);
            }

        }, Executors.newSingleThreadExecutor());

    }

    public static void takePicture(EventListener listener) {

        image_capture_.takePicture(CameraIO.getImageOFO(context_), main_executor_, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull @NotNull ImageCapture.OutputFileResults outputFileResults) {
                SAL.runFileScan(context_, outputFileResults.getSavedUri());
                listener.onEventUpdated(EventListener.DataType.URI_PICTURE_SAVED, outputFileResults.getSavedUri());
            }

            @Override
            public void onError(@NonNull @NotNull ImageCaptureException exception) {
                //TODO: Finish this
            }
        });
    }

    @SuppressLint("RestrictedApi")
    public static void stopRecording() {

        video_capture_.stopRecording();

    }

    @SuppressLint({"MissingPermission","RestrictedApi"})
    public static void startRecording(EventListener listener) {

        video_capture_.startRecording(CameraIO.getVideoOFO(context_), main_executor_, new VideoCapture.OnVideoSavedCallback() {
            @Override
            public void onVideoSaved(@NonNull @NotNull VideoCapture.OutputFileResults outputFileResults) {

                listener.onEventUpdated(EventListener.DataType.URI_VIDEO_SAVED,outputFileResults.getSavedUri());
                SAL.runFileScan(context_,outputFileResults.getSavedUri());
            }

            @Override
            public void onError(int videoCaptureError, @NonNull @NotNull String message, @Nullable @org.jetbrains.annotations.Nullable Throwable cause) {
                //TODO: Finish this
            }
        });

    }


    public static void zoomByRatio(
            @FloatRange(from = 1.0, to = Float.MAX_VALUE)
            float ratio, EventListener listener)  {

        camera_.getCameraControl().setZoomRatio(ratio);

        listener.onEventUpdated(EventListener.DataType.FLOAT_CAM_FOCAL_LENGTH,ratio * default_zoom_);
    }

    public static void zoomByFocalLength(float focal_length,EventListener listener) {

        if(focal_length < default_zoom_) {
            listener.onEventFinished(false,"Queried Focal length too low.");
            return;
        }

        camera_.getCameraControl().setZoomRatio(focal_length / default_zoom_);
        listener.onEventUpdated(EventListener.DataType.FLOAT_CAM_FOCAL_LENGTH,focal_length);
    }

    public static void focusToPoint(float x, float y, boolean is_continuous, EventListener listener) {

        if(focus_action_ != null && focus_action_.isContinuous()) {
            focus_action_.updateFocusCoordinate(new float[]{x,y});
            return;
        }
        else if(focus_action_ != null) {
            focus_action_.interrupt();
        }

        focus_action_ = new FocusAction(is_continuous,
                new float[]{x,y},
                camera_.getCameraControl(),
                preview_view_,
                main_executor_,
                listener);
    }
}
