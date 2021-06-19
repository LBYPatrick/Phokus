package com.lbynet.Phokus.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CaptureRequest;
import android.util.Range;
import android.util.Size;

import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCase;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.lbynet.Phokus.global.Config;
import com.lbynet.Phokus.template.EventListener;
import com.lbynet.Phokus.utils.SAL;

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

    //Other internal variables
    static boolean is_front_facing_ = false,
                   is_recording_ = false;

    public static void initialize() {
        Config.loadConfig();
    }

    public static void start(PreviewView preview_view) {

        context_ = preview_view.getContext();
        preview_view_ = preview_view;

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

        SAL.print(TAG,"CameraX binding...");

        CameraSelector cs = new CameraSelector.Builder()
                .requireLensFacing(is_front_facing_ ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();

        boolean is_video_mode = (Boolean) Config.get("VIDEO_MODE");

        camera_ = pcp.bindToLifecycle((LifecycleOwner) context_,cs,buildUseCaseArray(
                CameraConsts.USECASE_PREVIEW,
                (is_video_mode ? CameraConsts.USECASE_VIDEO_CAPTURE :
                                CameraConsts.USECASE_IMAGE_CAPTURE)
        ));

        default_zoom_ = CameraUtils.get35FocalLength(context_,is_front_facing_ ? 1 : 0);
        prev_zoom_ = default_zoom_;

        SAL.print(TAG,"CameraX bound.");
    }

    public static UseCase [] buildUseCaseArray(int... types) {

        UseCase [] r = new UseCase[types.length];

        int cnt = 0;
        while(cnt < types.length) {
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
                        .setTargetAspectRatio((Integer) Config.get("PREVIEW_ASPECT_RATIO"))
                        .build();

                p.setSurfaceProvider(preview_view_.getSurfaceProvider());
                return p;

            case CameraConsts.USECASE_VIDEO_CAPTURE:

                return new VideoCapture.Builder()
                        .setTargetResolution((Size) Config.get("VIDEO_RESOLUTION"))
                        .setVideoFrameRate((Integer) Config.get("VIDEO_FPS"))
                        .setBitRate((Integer) Config.get("VIDEO_BITRATE_MBPS") * 1048576)
                        .build();

            case CameraConsts.USECASE_IMAGE_CAPTURE:
                return new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();
            case CameraConsts.USECASE_IMAGE_ANALYSIS:

                ImageAnalysis ia = new ImageAnalysis.Builder()
                        .setTargetResolution((Size) Config.get("VIDEO_RESOLUTION"))
                        .build();

                //TODO: Do Analyzer stuff here
                //ia.setAnalyzer();
                return ia;

            default: return null;
        }
    }

    public void updateCameraConfig() {

        boolean is_video_mode = (Boolean) Config.get("VIDEO_MODE");

        int videoFps = (int)Config.get("VIDEO_FPS");

        //3A
        crob_
                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AWB_LOCK,
                        (Boolean)Config.get("AWB_LOCK") || is_recording_)

                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_LOCK,
                        (Boolean)Config.get("AE_LOCK") || is_recording_);

        //Video-specfic settings
        if(is_video_mode) {

            boolean is_log_enabled_ = !((String)Config.get("VIDEO_LOG_PROFILE")).equals("OFF");

            crob_
                    .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    new Range(videoFps,videoFps))

                    .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                            videoFps % 25 == 0 ?
                                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ:
                                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ)
                    .setCaptureRequestOption(
                            CaptureRequest.TONEMAP_MODE,
                            is_log_enabled_ ?
                                    CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE:
                                    CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
                    .setCaptureRequestOption(
                            CaptureRequest.EDGE_MODE,
                            is_log_enabled_ ?
                                    CaptureRequest.EDGE_MODE_OFF:
                                    CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                    .setCaptureRequestOption(
                            CaptureRequest.TONEMAP_CURVE,
                            ((String)(Config.get("VIDEO_LOG_PROFILE"))).equals("CLOG")?
                                    CameraUtils.makeToneMapCurve(
                                            CameraUtils.LogScheme.CLOG,
                                            CameraUtils.getCameraCharacteristics(context_,is_front_facing_ ? 1 : 0)) :

                                    CameraUtils.makeToneMapCurve(
                                            CameraUtils.LogScheme.SLOG,
                                            CameraUtils.getCameraCharacteristics(context_,is_front_facing_ ? 1 : 0)));

        }
        //Photo-specfic settings
        else {
            crob_
                    .clearCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE)
                    .setCaptureRequestOption(CaptureRequest.JPEG_QUALITY,((Integer)Config.get("STILL_JPEG_QUALITY")).byteValue());
        }
    }

    public void zoom(int targetFocalLength, EventListener listener) { }

}
