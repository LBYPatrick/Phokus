package com.lbynet.Phokus.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCase;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;

import com.google.common.util.concurrent.ListenableFuture;
import com.lbynet.Phokus.global.Config;
import com.lbynet.Phokus.template.EventListener;
import com.lbynet.Phokus.utils.SAL;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static java.lang.annotation.RetentionPolicy.SOURCE;

//TODO: Finish this
@SuppressLint("UnsafeOptInUsageError")
public class CameraCore {

    @Retention(SOURCE)
    @StringDef( {
            USECASE_PREVIEW,
            USECASE_IMAGE_CAPTURE,
            USECASE_VIDEO_CAPTURE,
            USECASE_IMAGE_ANALYSIS_BASIC
    })
    private @interface UseCaseType{};

    final public static String USECASE_PREVIEW = "preview",
                               USECASE_IMAGE_CAPTURE = "image_capture",
                               USECASE_VIDEO_CAPTURE = "video_capture",
                               USECASE_IMAGE_ANALYSIS_BASIC = "image_analysis_basic";


    final public static String TAG = CameraCore.class.getCanonicalName();

    //From frontend
    static Context context_;
    static PreviewView previewView_;
    static CaptureRequestOptions.Builder crob_ = new CaptureRequestOptions.Builder();

    //CameraX components
    static CameraSelector cs_;
    static Camera camera_;
    static ImageCapture imageCapture_;
    static VideoCapture videoCapture_;
    static ProcessCameraProvider pcp;
    static HashSet<UseCase> prevUseCaseArray_ = null;
    static float defaultZoom_ = -1,
            prevZoom_ = -1;
    static Executor uiThreadExecutor_;
    static FocusAction focusAction_;
    static int rotationMinor_ = 0,
               rotationMajor_ = 0;
    static EventListener statusListener_ = new EventListener() {};

    //Other internal variables
    static boolean isRecording_ = false;

    public static void initialize() {
        Config.loadConfig();
    }

    public static void setStatusListener_(EventListener listener) {
        statusListener_ = listener;}

    public static void start(PreviewView preview_view) {

        context_ = preview_view.getContext();
        previewView_ = preview_view;
        uiThreadExecutor_ = ContextCompat.getMainExecutor(context_);

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

    public static void restart() {

    }

    @SuppressLint("RestrictedApi")
    private static void bindCameraX() {

        pcp.unbindAll();

        SAL.print(TAG, "CameraX binding...");

        boolean isFrontFacing = (boolean)Config.get(Config.FRONT_FACING);

        statusListener_.onEventUpdated(EventListener.DataType.VOID_CAMERA_BINDING,null);

        CameraSelector cs = new CameraSelector.Builder()
                .requireLensFacing(isFrontFacing ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();
        boolean is_video_mode = (Boolean) Config.get(Config.VIDEO_MODE);

        //Build usecase array
        UseCase [] useCaseArray = buildUseCaseArray(
                USECASE_PREVIEW,
                (is_video_mode ? USECASE_VIDEO_CAPTURE :
                        USECASE_IMAGE_CAPTURE));

        camera_ = pcp.bindToLifecycle((LifecycleOwner) context_, cs, useCaseArray);
        defaultZoom_ = CameraUtils.get35FocalLength(context_, isFrontFacing ? 1 : 0);
        prevZoom_ = defaultZoom_;

        updateCameraConfig();

        new Thread( ()-> {

            LiveData<CameraState> state = camera_.getCameraInfo().getCameraState();

            while(state.getValue().getType() != CameraState.Type.OPEN)  {
                SAL.print("Camera is opening");
                SAL.sleepFor(10);
            }

            statusListener_.onEventUpdated(EventListener.DataType.VOID_CAMERA_BOUND,null);

        }).start();

        SAL.print(TAG, "CameraX bound.");
    }

    public static UseCase[] buildUseCaseArray(@UseCaseType String... types) {

        UseCase[] r = new UseCase[types.length];

        int cnt = 0;
        while (cnt < types.length) {
            r[cnt] = buildUseCase(types[cnt]);
            ++cnt;
        }
        return r;
    }

    @SuppressLint("RestrictedApi")
    public static UseCase buildUseCase(@UseCaseType String type) {

        switch (type) {
            case USECASE_PREVIEW:

                Preview p = new Preview.Builder()
                        .build();

                p.setSurfaceProvider(previewView_.getSurfaceProvider());
                return p;

            case USECASE_VIDEO_CAPTURE:

                videoCapture_ = new VideoCapture.Builder()
                        .setTargetResolution((Size) Config.get(Config.VIDEO_RESOLUTION))
                        .setVideoFrameRate((Integer) Config.get(Config.VIDEO_FPS))
                        .setBitRate((Integer) Config.get(Config.VIDEO_BITRATE_MBPS) * 1048576)
                        .build();

                return videoCapture_;

            case USECASE_IMAGE_CAPTURE:

                imageCapture_ = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetResolution(new Size(8000,6000))
                        .build();

                return imageCapture_;

            case USECASE_IMAGE_ANALYSIS_BASIC:

                ImageAnalysis ia = new ImageAnalysis.Builder()
                        //.setTargetResolution((Size) Config.get(Config.VIDEO_RESOLUTION))
                        .build();

                //TODO: Do Analyzer stuff here
                ia.setAnalyzer(Executors.newSingleThreadExecutor(), image -> {
                    SAL.print("New frame came in.");
                    //AnalysisResult.put(image);
                    image.close();
                });
                return ia;

            default:
                return null;
        }
    }

    public static void update3A() {

        crob_
                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AWB_LOCK,
                        (Boolean) Config.get(Config.AWB_LOCK) || isRecording_)

                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_LOCK,
                        (Boolean) Config.get(Config.AE_LOCK) || isRecording_);

        flushCaptureRequest();

    }

    @SuppressLint("UnsafeOptInUsageError")
    public static void updateCameraConfig() {

        boolean is_video_mode = (Boolean) Config.get(Config.VIDEO_MODE),
                isFrontFacing = (Boolean) Config.get(Config.FRONT_FACING);

        int videoFps = (int) Config.get(Config.VIDEO_FPS);

        SAL.print("Max camera resolution: " +
                CameraUtils.getCameraCharacteristics(context_, isFrontFacing ? 1 : 0)
                        .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE).toString());

        //3A
        crob_
                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AWB_LOCK,
                        (Boolean) Config.get(Config.AWB_LOCK) || isRecording_)

                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_LOCK,
                        (Boolean) Config.get(Config.AE_LOCK) || isRecording_);

        //Video-specfic settings
        if (is_video_mode) {

            boolean is_log_enabled_ = !((String) Config.get(Config.VIDEO_LOG_PROFILE)).equals("OFF");

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
                            is_log_enabled_ || is_video_mode ?
                                    CaptureRequest.EDGE_MODE_OFF :
                                    CaptureRequest.EDGE_MODE_HIGH_QUALITY)

                    //TODO: Uncomment this as soon as Google states that this works
                    /**
                     * This is not working as of 2021.07.13
                    .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    )
                     */

                    .setCaptureRequestOption(
                            CaptureRequest.TONEMAP_CURVE,
                            ((String) (Config.get(Config.VIDEO_LOG_PROFILE))).equals("CLOG") ?
                                    CameraUtils.makeToneMapCurve(
                                            CameraUtils.LogScheme.CLOG,
                                            CameraUtils.getCameraCharacteristics(context_, isFrontFacing ? 1 : 0)) :

                                    CameraUtils.makeToneMapCurve(
                                            CameraUtils.LogScheme.SLOG,
                                            CameraUtils.getCameraCharacteristics(context_, isFrontFacing ? 1 : 0)));

        }
        //Photo-specfic settings
        else {
            crob_
                    .clearCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE)
                    .clearCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE)
                    .clearCaptureRequestOption(CaptureRequest.EDGE_MODE)
                    //TODO: Uncomment this as soon as Google states that this works
                    //.clearCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE)
                    .setCaptureRequestOption(CaptureRequest.JPEG_QUALITY, ((Integer) Config.get(Config.STILL_JPEG_QUALITY)).byteValue());
        }
        flushCaptureRequest();
    }


    private static void flushCaptureRequest() {

        ListenableFuture<Void> cro_future = Camera2CameraControl
                .from(camera_.getCameraControl())
                .addCaptureRequestOptions(crob_.build());

        cro_future.addListener(() -> {

            try {
                cro_future.get();

                SAL.print(TAG, "CaptureRequestOptions updated.");

            } catch (Exception e) {
                SAL.print(e,false);
            }

        }, Executors.newSingleThreadExecutor());
    }

    public static void takePicture(EventListener listener) {

        imageCapture_.setTargetRotation(rotationMinor_);

        imageCapture_.takePicture(CameraIO.getImageOFO(context_), Executors.newSingleThreadExecutor(), new ImageCapture.OnImageSavedCallback() {
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

        isRecording_ = false;

        videoCapture_.stopRecording();

        update3A();
    }

    @SuppressLint({"MissingPermission","RestrictedApi"})
    public static void startRecording(EventListener listener) {

        isRecording_ = true;

        videoCapture_.setTargetRotation(rotationMajor_);

        update3A();

        videoCapture_.startRecording(CameraIO.getVideoOFO(context_), Executors.newSingleThreadExecutor(), new VideoCapture.OnVideoSavedCallback() {
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

        listener.onEventUpdated(EventListener.DataType.FLOAT_CAM_FOCAL_LENGTH,ratio * defaultZoom_);
    }

    public static void zoomByFocalLength(float focal_length,EventListener listener) {

        if(focal_length < defaultZoom_) {
            listener.onEventFinished(false,"Queried Focal length too low.");
            return;
        }

        camera_.getCameraControl().setZoomRatio(focal_length / defaultZoom_);
        listener.onEventUpdated(EventListener.DataType.FLOAT_CAM_FOCAL_LENGTH,focal_length);
    }

    public static void updateRotation(int surface_rotation) {

        rotationMinor_ = surface_rotation;

        if(surface_rotation == Surface.ROTATION_90 || surface_rotation == Surface.ROTATION_270) {
            rotationMajor_ = surface_rotation;
        }
    }

    public static void interruptFocus() {

        if(focusAction_ != null) focusAction_.interrupt();

    }

    public static void pauseFocus() {
        if(focusAction_ != null) focusAction_.pause();
    }

    public static void resumeFocus() {
        if(focusAction_ != null) focusAction_.resume();
    }

    public static void focusToPoint(float x, float y, boolean is_continuous, EventListener listener) {

        if(focusAction_ != null && focusAction_.isContinuous() && !focusAction_.isInterrupted()) {
            focusAction_.updateFocusCoordinate(new float[]{x,y});
            return;
        }
        else if(focusAction_ != null) {
            focusAction_.interrupt();
        }

        focusAction_ = new FocusAction(is_continuous,
                new float[]{x,y},
                camera_.getCameraControl(),
                previewView_,
                uiThreadExecutor_,
                listener);
    }

    public static boolean isFrontFacing() {
        return (Boolean) Config.get(Config.FRONT_FACING);
    }
}
