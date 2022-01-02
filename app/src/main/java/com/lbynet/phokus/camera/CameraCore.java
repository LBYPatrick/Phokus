package com.lbynet.phokus.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.core.Camera;
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
import com.lbynet.phokus.global.Config;
import com.lbynet.phokus.template.EventListener;
import com.lbynet.phokus.utils.SAL;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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
    static PreviewView preview_view_;
    static CaptureRequestOptions.Builder crob_ = new CaptureRequestOptions.Builder();

    //CameraX components
    static CameraSelector cs_;
    static Camera camera_;
    static ImageCapture image_capture_;
    static VideoCapture video_capture_;
    static ProcessCameraProvider pcp;
    static float zoom_default_ = -1,
            zoom_prev_ = -1;
    static Executor ui_executor_,
                    exec_cam_core_ = Executors.newFixedThreadPool(50);
    static int rot_minor_ = 0,
               rot_major_ = 0;
    static EventListener listener_stat_ = new EventListener() {};

    //Other internal variables
    private static boolean isRecording_ = false,
                           is_camera_bound_ = false;
    final private static ReentrantLock m_camera = new ReentrantLock();
    final private static Condition cond_camera = m_camera.newCondition();



    public static void initialize() {
        Config.loadConfig();
    }

    public static void setStatusListener_(EventListener listener) {
        listener_stat_ = listener;}

    public static void start(PreviewView previewView) {

        context_ = previewView.getContext();
        preview_view_ = previewView;
        ui_executor_ = ContextCompat.getMainExecutor(context_);

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context_);

        future.addListener(() -> {
                    try {

                        pcp = future.get();
                        bindCameraX();

                        m_camera.lock();

                        while(!is_camera_bound_) cond_camera.await();

                        SAL.print("is camera null? " + (camera_ == null? "yes" : "no"));

                        FocusAction.initialize(camera_.getCameraControl(), preview_view_, ui_executor_);

                        m_camera.unlock();

                    } catch (Exception e) {
                        SAL.print(e);
                    }
                }
                , exec_cam_core_
        );
    }

    public static void updateVideoMode() {

        m_camera.lock();

        is_camera_bound_ = false;

        boolean isVideoMode = (boolean) Config.get(Config.VIDEO_MODE),
                isChangeDetected = false;

        /**
         * See if there are usecases that needs to be un-bound
         */
        if(isVideoMode && image_capture_ != null) {
            pcp.unbind(image_capture_);
            image_capture_ = null;
            isChangeDetected = true;
        }
        else if(!isVideoMode && video_capture_ != null) {
            pcp.unbind(video_capture_);
            video_capture_ = null;
            isChangeDetected = true;
        }

        /**
         * Bind new UseCase when necessary (and notify user via statusListener_)
         */
        if(isChangeDetected) {

            listener_stat_.onEventUpdated(EventListener.DataType.VOID_CAMERA_BINDING,null);
            camera_ = pcp.bindToLifecycle((LifecycleOwner) context_, cs_, buildUseCase(isVideoMode ? USECASE_VIDEO_CAPTURE : USECASE_IMAGE_CAPTURE));

            updateCameraConfig();
        }

        detectCameraBoundState();
    }

    @SuppressLint("RestrictedApi")
    private static void bindCameraX() {

        m_camera.lock();

        is_camera_bound_ = false;

        ui_executor_.execute( ()-> {

            pcp.unbindAll();

            SAL.print(TAG, "CameraX binding...");

            boolean isFrontFacing = (boolean)Config.get(Config.FRONT_FACING);

            cs_ = new CameraSelector.Builder()
                    .requireLensFacing(isFrontFacing ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                    .build();
            boolean is_video_mode = (Boolean) Config.get(Config.VIDEO_MODE);

            //Build usecase array
            UseCase [] useCaseArray = buildUseCaseArray(
                    USECASE_PREVIEW,
                    (is_video_mode ? USECASE_VIDEO_CAPTURE : USECASE_IMAGE_CAPTURE));


            camera_ = pcp.bindToLifecycle((LifecycleOwner) context_, cs_, useCaseArray);
            zoom_default_ = CameraUtils.get35FocalLength(context_, isFrontFacing ? 1 : 0);
            zoom_prev_ = zoom_default_;

            updateCameraConfig();

        });

        detectCameraBoundState();

    }

    private static void detectCameraBoundState() {


        while(camera_ == null) SAL.sleepFor(1);

        LiveData<CameraState> state = camera_.getCameraInfo().getCameraState();

        while(state.getValue().getType() != CameraState.Type.OPEN) {
            //SAL.print("Camera is opening");
            SAL.sleepFor(1);
        }

        is_camera_bound_ = true;

        if (m_camera.isLocked()) {
            cond_camera.signalAll();
            m_camera.unlock();
        }

        listener_stat_.onEventUpdated(EventListener.DataType.VOID_CAMERA_BOUND,null);

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

                p.setSurfaceProvider(preview_view_.getSurfaceProvider());
                return p;

            case USECASE_VIDEO_CAPTURE:

                video_capture_ = new VideoCapture.Builder()
                        .setTargetResolution((Size) Config.get(Config.VIDEO_RESOLUTION))
                        .setVideoFrameRate((Integer) Config.get(Config.VIDEO_FPS))
                        .setBitRate((Integer) Config.get(Config.VIDEO_BITRATE_MBPS) * 1048576)
                        .build();

                return video_capture_;

            case USECASE_IMAGE_CAPTURE:

                image_capture_ = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetResolution(new Size(8000,6000))
                        .build();

                return image_capture_;

            case USECASE_IMAGE_ANALYSIS_BASIC:

                ImageAnalysis ia = new ImageAnalysis.Builder()
                        //.setTargetResolution((Size) Config.get(Config.VIDEO_RESOLUTION))
                        .build();

                //TODO: Do Analyzer stuff here
                ia.setAnalyzer(exec_cam_core_, image -> {
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

        }, exec_cam_core_);
    }

    public static void takePicture(EventListener listener) {

        image_capture_.setTargetRotation(rot_minor_);

        image_capture_.takePicture(CameraIO.getImageOFO(context_), Executors.newSingleThreadExecutor(), new ImageCapture.OnImageSavedCallback() {
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

        video_capture_.stopRecording();

        update3A();
    }

    @SuppressLint({"MissingPermission","RestrictedApi"})
    public static void startRecording(EventListener listener) {

        isRecording_ = true;

        video_capture_.setTargetRotation(rot_major_);

        update3A();

        video_capture_.startRecording(CameraIO.getVideoOFO(context_), Executors.newSingleThreadExecutor(), new VideoCapture.OnVideoSavedCallback() {
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

        listener.onEventUpdated(EventListener.DataType.FLOAT_CAM_FOCAL_LENGTH,ratio * zoom_default_);
    }

    public static void zoomByFocalLength(float focal_length,EventListener listener) {

        if(focal_length < zoom_default_) {
            listener.onEventFinished(false,"Queried Focal length too low.");
            return;
        }

        camera_.getCameraControl().setZoomRatio(focal_length / zoom_default_);
        listener.onEventUpdated(EventListener.DataType.FLOAT_CAM_FOCAL_LENGTH,focal_length);
    }

    public static void updateRotation(int surface_rotation) {
        rot_minor_ = surface_rotation;

        if(surface_rotation == Surface.ROTATION_90 || surface_rotation == Surface.ROTATION_270) {
            rot_major_ = surface_rotation;
        }
    }

    public static void pauseFocus() {
        FocusAction.pause();
    }

    public static void resumeFocus() {
        FocusAction.resume();
    }

    public static void resumeFreshFocus() {
        FocusAction.resumeFresh();
    }

    public static void focus(FocusAction.FocusActionRequest request) {
        FocusAction.issueRequest(request);
    }

    public static FocusAction.FocusActionRequest getLastRequest() {
        return FocusAction.getLastRequest();
    }

    public static void focus(float x,
                             float y,
                             @FocusAction.FocusType int type) {

        FocusAction.issueRequest(
                new FocusAction.FocusActionRequest(
                        type,
                        new float[]{x,y})
        );
    }

    public static void cancelFocus() {
        focus(-1,-1,FocusAction.FOCUS_AUTO);
    }

    public static boolean isFrontFacing() {
        return (Boolean) Config.get(Config.FRONT_FACING);
    }
}
