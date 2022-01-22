package com.lbynet.phokus.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.AnyThread;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
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
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;

import com.google.common.util.concurrent.ListenableFuture;
import com.lbynet.phokus.global.Config;
import com.lbynet.phokus.global.Consts;
import com.lbynet.phokus.template.EventListener;
import com.lbynet.phokus.template.OnEventCompleteCallback;
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

    public abstract static class VideoEventListener {
        @AnyThread
        public void onStart(VideoRecordEvent event) {}
        @AnyThread public void onPause(VideoRecordEvent event) {}
        @AnyThread public void onResume(VideoRecordEvent event) {}
        @AnyThread public void onStatus(VideoRecordEvent event) {}
        @AnyThread public void onFinalize(VideoRecordEvent event) {}
    }

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
    static Recording recording_active_;
    static float zoom_default_ = -1,
            zoom_prev_ = -1;
    static Executor ui_executor_;
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
                        FocusAction.initialize(camera_.getCameraControl(), preview_view_, ui_executor_);

                        m_camera.unlock();

                    } catch (Exception e) {
                        SAL.print(e);
                    }
                }
                , Consts.EXE_THREAD_POOL
        );
    }

    public static void setVideoMode(boolean isVideoMode,
                                    OnEventCompleteCallback startCallback,
                                    OnEventCompleteCallback endCallback) {

        Consts.EXE_THREAD_POOL.execute(() -> {

            if(startCallback != null) startCallback.onComplete(0, "setVideoMode(boolean,OnEventCompleteCallback)");

            setVideoMode(isVideoMode);

            //Notify on complete
            if(endCallback != null) endCallback.onComplete(is_camera_bound_? 0 : -1, "setVideoMode(boolean,OnEventCompleteCallback)");

        });

    }

    private static void setVideoMode(boolean isVideoMode) {

        boolean prev = Config.get(Config.VIDEO_MODE).equals("true");
        if(prev == isVideoMode) return;

        m_camera.lock();

        //cancelFocus();
        //while(FocusAction.isBusy() || FocusAction.getLastRequest().type != FocusAction.FOCUS_AUTO) SAL.sleepFor(1);

        FocusAction.pause();

        is_camera_bound_ = false;

        Config.set(Config.VIDEO_MODE,isVideoMode ? "true" : "false");
        UseCase useCase = buildUseCase(isVideoMode ? USECASE_VIDEO_CAPTURE : USECASE_IMAGE_CAPTURE);

        if(isVideoMode) video_capture_ = (VideoCapture) useCase;
        else image_capture_  = (ImageCapture) useCase;

        //Bind new usecase(s) and unbind old usecase(s)
        ui_executor_.execute(() -> {
            pcp.unbind(isVideoMode ? image_capture_ : video_capture_);
            camera_ = pcp.bindToLifecycle((LifecycleOwner) context_,cs_,useCase);
            detectBoundState();
        });


        while(!is_camera_bound_) condWait();

        FocusAction.resumeFresh();

        m_camera.unlock();
    }

    public static void toggleCameraFacing(OnEventCompleteCallback callback) {
        setCameraFacing(!isFrontFacing(),callback);
    }

    public static void setCameraFacing(boolean isFrontFacing,OnEventCompleteCallback callback) {

        Consts.EXE_THREAD_POOL.execute( () -> {

            m_camera.lock();

            boolean prev = isFrontFacing();
            if (prev == isFrontFacing) {
                m_camera.unlock();
                return;
            }

            Config.set(Config.FRONT_FACING, isFrontFacing ? "true" : "false");

            cancelFocus();

            bindCameraX();

            FocusAction.setNewCameraControl(camera_.getCameraControl());

            if (callback != null)
                callback.onComplete(isFrontFacing ? 1 : 0, "setCameraFacing(boolean,OnEventCompleteCallback)");

            m_camera.unlock();
        });
    }

    @SuppressLint("RestrictedApi")
    private static void bindCameraX() {

        m_camera.lock();

        is_camera_bound_ = false;

        ui_executor_.execute( ()-> {

            pcp.unbindAll();

            SAL.print(TAG, "CameraX binding...");

            cs_ = new CameraSelector.Builder()
                    .requireLensFacing(isFrontFacing() ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                    .build();
            boolean is_video_mode = Config.get(Config.VIDEO_MODE).equals("true");

            //Build usecase array
            UseCase [] useCaseArray = buildUseCaseArray(
                    USECASE_PREVIEW,
                    (is_video_mode ? USECASE_VIDEO_CAPTURE : USECASE_IMAGE_CAPTURE));

            camera_ = pcp.bindToLifecycle((LifecycleOwner) context_, cs_, useCaseArray);
            zoom_default_ = CameraUtils.get35FocalLength(context_, isFrontFacing() ? 1 : 0);
            zoom_prev_ = zoom_default_;

            detectBoundState();
            updateCameraConfig();
        });

        while(!is_camera_bound_) condWait();

        m_camera.unlock();

    }

    private static void detectBoundState() {
        Consts.EXE_THREAD_POOL.execute(CameraCore::detectBoundStateBlocking);
    }

    private static void condWait() {

        try {
            cond_camera.await();
        } catch (InterruptedException e) {
            SAL.print(e);
        }

    }

    private static void detectBoundStateBlocking() {

        while(camera_ == null) SAL.sleepFor(1);

        LiveData<CameraState> state = camera_.getCameraInfo().getCameraState();

        while(state.getValue().getType() != CameraState.Type.OPEN) {
            //SAL.print("Camera is opening");
            SAL.sleepFor(1);
        }
        m_camera.lock();
        is_camera_bound_ = true;

        cond_camera.signalAll();

        listener_stat_.onEventUpdated(EventListener.DataType.VOID_CAMERA_BOUND,null);

        m_camera.unlock();

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

        UseCase r = null;

        switch (type) {
            case USECASE_PREVIEW:
                Preview p = new Preview.Builder().build();
                p.setSurfaceProvider(preview_view_.getSurfaceProvider());
                r = p;
                break;

            case USECASE_VIDEO_CAPTURE:

                //TODO: Update this after testing
                QualitySelector qs = QualitySelector.from(Quality.UHD);

                Recorder recorder = new Recorder.Builder()
                        .setExecutor(Consts.EXE_THREAD_POOL)
                        .setQualitySelector(qs)
                        .build();

                video_capture_ =
                        /*
                        new VideoCapture.Builder()
                        .setTargetResolution((Size) Config.get(Config.VIDEO_RESOLUTION))
                        .setVideoFrameRate((Integer) Config.get(Config.VIDEO_FPS))
                        .setBitRate((Integer) Config.get(Config.VIDEO_BITRATE_MBPS) * 1048576)
                        .build();
                         */
                        VideoCapture.withOutput(recorder);

                r=video_capture_;
                break;

            case USECASE_IMAGE_CAPTURE:
                image_capture_ = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetResolution(new Size(8000,6000))
                        .build();
                r=image_capture_;
                break;

            case USECASE_IMAGE_ANALYSIS_BASIC:

                ImageAnalysis ia = new ImageAnalysis.Builder()
                        //.setTargetResolution((Size) Config.get(Config.VIDEO_RESOLUTION))
                        .build();
                //TODO: Do Analyzer stuff here
                ia.setAnalyzer(Consts.EXE_THREAD_POOL, image -> {
                    SAL.print("New frame came in.");
                    //AnalysisResult.put(image);
                    image.close();
                });
                r = ia;
                break;
            default:break;
        }
        return r;
    }

    private static void update3A() {

        m_camera.lock();

        while(!is_camera_bound_) condWait();

        crob_
                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AWB_LOCK,
                        Config.get(Config.AWB_LOCK).equals("true") || isRecording_)

                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_LOCK,
                        Config.get(Config.AE_LOCK).equals("true") || isRecording_);


        m_camera.unlock();

        flushCaptureRequest();

    }

    @SuppressLint("UnsafeOptInUsageError")
    public static void updateCameraConfig() {

        boolean isVideoMode = Config.get(Config.VIDEO_MODE).equals("true");

        int videoFps = Integer.parseInt(Config.get(Config.VIDEO_FPS));

        SAL.print("Max camera resolution: " +
                CameraUtils.getCameraCharacteristics(context_, isFrontFacing() ? 1 : 0)
                        .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE).toString());

        //3A
        crob_
                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AWB_LOCK,
                        Config.get(Config.AWB_LOCK).equals("true")|| isRecording_)

                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_LOCK,
                        Config.get(Config.AE_LOCK).equals("true") || isRecording_);

        //Video-specfic settings
        if (isVideoMode) {

            boolean isLogCurveEnabled = !(Config.get(Config.VIDEO_LOG_PROFILE).equals("false"));

            crob_
                    //Recording FPS
                    .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            new Range(videoFps, videoFps))

                    //NTSC/PAL but for AE
                    .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                            videoFps % 25 == 0 ?
                                    CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ :
                                    CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ)

                    //Tonemap Mode (i.e. whether to enable custom contrast curve, which can be set to a log curve for HDR video recording)
                    .setCaptureRequestOption(
                            CaptureRequest.TONEMAP_MODE,
                            isLogCurveEnabled ?
                                    CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE :
                                    CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)

                    //Post-effect sharpening (Good for high-res still image but nothing else)
                    .setCaptureRequestOption(
                            CaptureRequest.EDGE_MODE,
                            isLogCurveEnabled
                                    //|| isVideoMode
                                    ?
                                    CaptureRequest.EDGE_MODE_OFF :
                                    CaptureRequest.EDGE_MODE_HIGH_QUALITY)

                    //TODO: Uncomment this as soon as Google states that this works
                    /**
                     * This is not working as of 2021.07.13
                    .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    )
                     */

                    //Contrast Curve (CLOG or SLOG)
                    .setCaptureRequestOption(
                            CaptureRequest.TONEMAP_CURVE,
                            Config.get(Config.VIDEO_LOG_PROFILE).equals("CLOG") ?
                                    CameraUtils.makeToneMapCurve(
                                            CameraUtils.LogScheme.CLOG,
                                            CameraUtils.getCameraCharacteristics(context_, isFrontFacing() ? 1 : 0)) :

                                    CameraUtils.makeToneMapCurve(
                                            CameraUtils.LogScheme.SLOG,
                                            CameraUtils.getCameraCharacteristics(context_, isFrontFacing() ? 1 : 0)));

        }
        //Photo-specfic settings
        else {
            crob_
                    .clearCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE)
                    .clearCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE)
                    .clearCaptureRequestOption(CaptureRequest.EDGE_MODE)
                    //TODO: Uncomment this as soon as Google states that this works
                    //.clearCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE)
                    .setCaptureRequestOption(CaptureRequest.JPEG_QUALITY, ((Integer)Integer.parseInt(Config.get(Config.STILL_JPEG_QUALITY))).byteValue());
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

        }, Consts.EXE_THREAD_POOL);
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
    public static void stopRecording(OnEventCompleteCallback callback) {

        Consts.EXE_THREAD_POOL.execute(()-> {
            m_camera.lock();

            recording_active_.stop();

            while(isRecording_) condWait();

            if(callback != null) callback.onComplete(isRecording_ ? -1 : 0,"stopRecording(OnEventCompleteCallback)");

            update3A();

            m_camera.unlock();
        });
    }

    @SuppressLint({"MissingPermission","RestrictedApi"})
    public static void startRecording(VideoEventListener listener) {

        Consts.EXE_THREAD_POOL.execute( () -> {

            m_camera.lock();

            video_capture_.setTargetRotation(rot_major_);


            recording_active_ =
                    ((Recorder) video_capture_.getOutput())
                            .prepareRecording(context_, CameraIO.getVideoMso(context_)) //PendingRecording is built here
                            .withAudioEnabled()
                            .start(Consts.EXE_THREAD_POOL, videoRecordEvent -> { //PendingRecording is configured here and returns a Recording

                                m_camera.lock();
                                //This is REALLY UGLY
                                if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                                    isRecording_ = true;
                                    update3A();
                                    listener.onStart(videoRecordEvent);
                                }

                                else if (videoRecordEvent instanceof VideoRecordEvent.Pause) {
                                    isRecording_ = false;
                                    listener.onPause(videoRecordEvent);
                                }
                                else if (videoRecordEvent instanceof VideoRecordEvent.Resume) {
                                    isRecording_ = true;
                                    listener.onResume(videoRecordEvent);
                                }
                                else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                                    isRecording_ = false;
                                    listener.onFinalize(videoRecordEvent);
                                }
                                //TODO: Figure out what this event is for
                                else if (videoRecordEvent instanceof VideoRecordEvent.Status) {
                                    listener.onStatus(videoRecordEvent);
                                }
                                cond_camera.signalAll();

                                m_camera.unlock();

                            });

            m_camera.unlock();
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

    public static void onPause() {
        pauseFocus();
    }

    public static void onResume() {
        resumeFocus();
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
        return Config.get(Config.FRONT_FACING).equals("true");
    }

}
