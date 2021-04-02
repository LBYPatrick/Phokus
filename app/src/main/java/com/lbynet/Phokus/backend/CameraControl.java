package com.lbynet.Phokus.backend;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.Range;
import android.util.Size;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExposureState;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.lbynet.Phokus.listener.EventListener;
import com.lbynet.Phokus.listener.LumaListener;
import com.lbynet.Phokus.ui.UIHelper;
import com.lbynet.Phokus.utils.CameraUtils;
import com.lbynet.Phokus.utils.MathTools;
import com.lbynet.Phokus.utils.SAL;
import com.lbynet.Phokus.utils.Timer;

import java.nio.ByteBuffer;

@SuppressLint("RestrictedApi")
public class CameraControl {

    final static String TAG = CameraControl.class.getCanonicalName();

    final static float[] AVAIL_ZOOM_LENGTHS = {28, 35, 50, 70, 85};
    final static int[] AVAIL_VIDEO_FPS = {24, 25, 30, 48, 50, 60};

    private static boolean isFilming_ = false,
            isWidescreen_ = false,
            isFrontFacing_ = false,
            isFocusBusy_ = false,
            isVideoStbEnabled_ = true,
            isLogEnabled_ = false,
            isVideoMode_ = false,
            isAELock_ = false,
            isAWBLock_ = false;

    private static float minFocalLength_ = 0,
            lastZoomFocalLength_ = 0;
    private static int majorRotation_ = 0,
            minorRotation_ = 0,
            fpsIndex = 4,
            videoFps_ = AVAIL_VIDEO_FPS[fpsIndex],
            zoomIndex = 0;
    private static CameraSelector cs;
    private static ImageCapture ic;
    private static ImageAnalysis ia;
    private static VideoCapture vc;
    private static Preview preview;
    private static Camera camera;
    private static ProcessCameraProvider pcp = null;
    private static PreviewView previewView_ = null;
    private static Context context_;
    private static Timer iaTimer = new Timer("ImageAnalysis Timer");
    private static int[] lumaBucket_ = {0, 0, 0, 0, 0, 0};
    private static LumaListener lumaListener_ = new LumaListener() {};

    private static CaptureRequestOptions.Builder cro = new CaptureRequestOptions.Builder();

    public static void initialize(PreviewView previewView) {
        context_ = previewView.getContext();

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context_);

        //Bind to lifecycle
        future.addListener(() -> {
            try {
                pcp = future.get();
                previewView_ = previewView;
                bindCamera();

            } catch (Exception e) {
                SAL.print(e);
            }
        }, ContextCompat.getMainExecutor(context_));
    }

    public static void bindCamera() {
        bindCamera(new EventListener() {});
    }

    @SuppressLint("RestrictedApi")
    public static void bindCamera(EventListener listener) {

        listener.onEventBegan("Start binding camera.");

        pcp.unbindAll();
        /**
         * CameraSelector instance
         */
        cs = new CameraSelector.Builder()
                .requireLensFacing(isFrontFacing_ ?
                        CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();

        camera = pcp.bindToLifecycle((LifecycleOwner) context_,cs);

        preview = makePreview();
        preview.setSurfaceProvider(previewView_.getSurfaceProvider());

        if(isVideoMode_) vc = makeVideoCapture();
        else ic = makeImageCapture();

        //Let's not worry about imageAnalysis for now

        camera = pcp.bindToLifecycle((LifecycleOwner) context_,cs,preview, (isVideoMode_ ? vc : ic));

        new Thread( () -> {
            SAL.sleepFor(5000);
            updateAllCameraConfig();
            UIHelper.runLater(context_, CameraControl::flushCaptureRequest);
        }).start();

        listener.onEventFinished(true, "Finish binding camera.");
    }

    public static void analyzeCurrentFrame(@NonNull ImageProxy ip) {
        //TODO: Finish this

        new Thread(() -> {
            /**
             * Get YUV planes.
             * Plane 0: "Y" plane -- luma plane
             * Plane 1: "Cb" plane -- blue projection plane
             * Plane 2: "Cr" plane -- red projection plane
             */
            ImageProxy.PlaneProxy lumaPlane = ip.getPlanes()[0],
                    bPlane = ip.getPlanes()[1],
                    rPlane = ip.getPlanes()[2];

            ByteBuffer bb = lumaPlane.getBuffer();

            int[] bucket = {0, 0, 0, 0, 0, 0};

            int max = Integer.MIN_VALUE,
                    min = Integer.MAX_VALUE;

            while (bb.remaining() != 0) {

                int temp = bb.get() + 128; //0-255

                if (temp > max) max = temp;
                if (temp < min) min = temp;

                if (temp == 0) bucket[0] += 1;
                else if (temp == 255) bucket[5] += 1;
                else if (MathTools.isValueInRange(temp, 1, 64)) bucket[1] += 1;
                else if (MathTools.isValueInRange(temp, 65, 128)) bucket[2] += 1;
                else if (MathTools.isValueInRange(temp, 129, 192)) bucket[3] += 1;
                else if (MathTools.isValueInRange(temp, 193, 254)) bucket[4] += 1;
            }

            if (Math.abs(max - min) > 20) {
                for (int i = 0; i < 6; ++i) {
                    lumaBucket_[i] += bucket[i];
                }
            }

            if (iaTimer.getElaspedTimeInMs() > 1000) {

                lumaListener_.onDataUpdate(lumaBucket_.clone());

                for (int i = 0; i < lumaBucket_.length; ++i) {
                    lumaBucket_[i] = 0;
                }

                iaTimer.start();
            }
            /*
            SAL.print("Luma: " + Arrays.toString(bucket) + " min: " + min +" max: " + max);
            SAL.print("Size: " + lumaPlane.getRowStride() + " x " + (bucket[0] + bucket[1] + bucket[2] + bucket[3]) / lumaPlane.getRowStride());
            */
            /**
             * Close imageProxy (NEVER close the underlying Image instance directly as noted by Google)
             */
            ip.close();

        }).start();
    }

    public static void toggleCameraFacing(EventListener listener) {
        isFrontFacing_ = !isFrontFacing_;
        updateCameraFacing(isFrontFacing_, listener);
    }

    public static void updateCameraFacing(boolean isFrontFacing, EventListener listener) {

        new Thread(() -> {

            listener.onEventBegan("Updating camera facing: " + (isFrontFacing ? "Front" : "Back"));

            isFrontFacing_ = isFrontFacing;

            //Reset zoom parameters
            lastZoomFocalLength_ = CameraUtils.get35FocalLength(context_,isFrontFacing_? 1 : 0);
            zoomIndex = 0;

            cs = new CameraSelector.Builder()
                    .requireLensFacing(isFrontFacing_ ?
                            CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                    .build();

            runLater(() -> {
                pcp.unbindAll();
                camera = pcp.bindToLifecycle((LifecycleOwner) context_, cs, preview, (isVideoMode_ ? vc : ic));
                listener.onEventFinished(true, "Finished switching camera facing");
            });
        }).start();
    }

    public static float getEquivalentFocalLength(int cameraId) {
        return CameraUtils.get35FocalLength(context_, cameraId);
    }

    public static void runLater(Runnable r) {
        ContextCompat.getMainExecutor(context_).execute(r);
    }

    public static void toggleWidescreen(EventListener listener) {

        listener.onEventBegan("Start toggling widescreen");
        isWidescreen_ = !isWidescreen_;
        updateWidescreen(listener);
    }

    public static void updateWidescreen(EventListener listener) {

        listener.onEventUpdated("Updating widescreen");

        new Thread(() -> {
            runLater(() -> {
                Preview oldPreview = preview;
                preview = makePreview();
                preview.setSurfaceProvider(previewView_.getSurfaceProvider());
                pcp.unbind(oldPreview);

                camera = pcp.bindToLifecycle((LifecycleOwner) context_, cs, preview);

                listener.onEventFinished(true,"Preview widescreen status is now " +isWidescreen_ + ".");
            });
        }).start();
    }

    public static void takePicture(EventListener listener) {
        //TODO
        new Thread(() -> {

            listener.onEventBegan("Start taking picture");

            ic.setTargetRotation(minorRotation_);

            final String filename = CameraUtils.getPhotoFilename();

            ic.takePicture(CameraUtils.getImageOFO(context_,filename),ContextCompat.getMainExecutor(context_), new ImageCapture.OnImageSavedCallback() {

                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        SAL.runFileScan(context_,outputFileResults.getSavedUri());
                        listener.onEventFinished(true,"Image saved, filename: " + filename);
                        return;
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {

                    listener.onEventFinished(false, "Error: " + exception.getLocalizedMessage());
                }
            });

        }).start();
    }

    public static void toggleZoom(EventListener listener) {

        listener.onEventBegan("Start zooming...");

        if (minFocalLength_ > AVAIL_ZOOM_LENGTHS[AVAIL_ZOOM_LENGTHS.length - 1]) {
            listener.onEventFinished(false, "No available zoom focal length.");
            return;
        }

        float zoomLength = 0;

        if (zoomIndex != -1) {

            while (minFocalLength_ > AVAIL_ZOOM_LENGTHS[zoomIndex]) {
                ++zoomIndex;
            }

            zoomLength = AVAIL_ZOOM_LENGTHS[zoomIndex];

            if (zoomIndex == AVAIL_ZOOM_LENGTHS.length - 1) {
                zoomIndex = -1;
            } else {
                ++zoomIndex;
            }
        } else {
            zoomLength = minFocalLength_;
            ++zoomIndex;
        }

        listener.onEventUpdated(Float.toString(zoomLength));

        zoomByFocalLength(zoomLength, new EventListener() {
            @Override
            public boolean onEventFinished(boolean isSuccess, String extra) {
                listener.onEventFinished(true, "Zoom done");
                return super.onEventFinished(isSuccess, extra);
            }
        });

    }

    public static void zoomByFocalLength(float mm, EventListener listener) {

        listener.onEventBegan("Zooming from " + lastZoomFocalLength_ + "mm to " + mm + "mm.");

        //TODO: Currently this snippet only works for rear-facing camera, make it also work for front
        new Thread(() -> {
            listener.onEventBegan("");
            if (mm < minFocalLength_) {
                listener.onEventFinished(false,
                        "Failed to zoom, reason: requested focus length is lower than native focal length.");
            }
            final float zoomRatio = mm / minFocalLength_;

            androidx.camera.core.CameraControl cc = camera.getCameraControl();

            ValueAnimator animator = ValueAnimator.ofFloat(lastZoomFocalLength_ / minFocalLength_, zoomRatio);

            final int duration = 100;

            animator.setDuration(duration);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float value = (float) valueAnimator.getAnimatedValue();

                    cc.setZoomRatio(value);

                    if (value == zoomRatio) {
                        listener.onEventFinished(true, "Zoom by focal length successful.");
                    }
                }
            });

            lastZoomFocalLength_ = mm;

            runLater(animator::start);

        }).start();
    }

    @SuppressLint("RestrictedApi")
    public static void toggleRecording(EventListener listener) {

        new Thread(() -> {

            isFilming_ = !isFilming_;

            listener.onEventBegan("");

            if (!isWidescreen_) updateWidescreen(listener);

            update3A();
            flushCaptureRequest();

            if (isFilming_) {

                runLater(() -> {

                    vc.setTargetRotation(majorRotation_);

                    vc.startRecording(CameraUtils.getVideoOFO(context_,CameraUtils.getVideoFilename())
                            , ContextCompat.getMainExecutor(context_), new VideoCapture.OnVideoSavedCallback() {

                                @Override
                                public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                                    SAL.runFileScan(context_,outputFileResults.getSavedUri());
                                    SAL.print("Video saved.");

                                }

                                @Override
                                public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                                    SAL.print("Error occured. cause: " + message);
                                }
                            });

                    listener.onEventUpdated("START");
                });
            } else {
                vc.stopRecording();
                listener.onEventUpdated("STOP");
            }

            listener.onEventFinished(true, "");
        }).start();

    }

    public static int getVideoFps() {
        return videoFps_;
    }

    public static void toggleVideoFps(EventListener listener) {

        new Thread(() -> {
            videoFps_ = AVAIL_VIDEO_FPS[fpsIndex];

            listener.onEventBegan("Changing video framerate to " + videoFps_ + "fps...");

            if (fpsIndex == AVAIL_VIDEO_FPS.length - 1) {
                fpsIndex = 0;
            } else ++fpsIndex;


            pcp.unbind(vc);
            vc = makeVideoCapture();
            camera = pcp.bindToLifecycle((LifecycleOwner) context_,cs,vc);

            listener.onEventFinished(true, "Video framerate changed to " + videoFps_ + "fps.");

        }).start();
    }

    private static String getOutputPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/Camera";
    }

    public static void updateRotation(int rotation, EventListener listener) {

        //listener.onEventBegan("Updating rotation: " + rotation);

        minorRotation_ = rotation;

        if (rotation == 1 || rotation == 3) { //90 and 270 degrees, important for video recording (neglecting portrait rotations)
            majorRotation_ = rotation;
        }

        //listener.onEventFinished(true, "Finished updating rotation.");
    }

    //Helper methods
    public static Preview makePreview() {

        Preview.Builder builder = new Preview.Builder()
                //.setTargetResolution(new Size(3840, (isWidescreen_ || isVideoMode_)? 2160 : (3840 * 3 / 4));
                .setTargetAspectRatio((isWidescreen_ || isVideoMode_) ? AspectRatio.RATIO_16_9 : AspectRatio.RATIO_4_3);

        //new Camera2Interop.Extender<>(builder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(30,60));

        return builder.build();
    }

    public static VideoCapture makeVideoCapture() {

        VideoCapture.Builder builder = new VideoCapture.Builder()
                .setVideoFrameRate(videoFps_)
                .setBitRate(100 * 1024 * 1024) //100 Mbps
                .setTargetResolution(new Size(1920, 1080));

        VideoCapture r = builder.build();

        return r;
    }

    public static ImageCapture makeImageCapture() {

        ImageCapture.Builder icBuilder = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY);

        return icBuilder.build();
    }

    public static void focusToPoint(float x, float y, EventListener listener) {
        new Thread(() -> {

            isFocusBusy_ = true;

            listener.onEventBegan("Start focusing...");

            runLater(() -> {
                //Start focusing
                MeteringPointFactory factory = previewView_.getMeteringPointFactory();
                MeteringPoint point = factory.createPoint(x, y);
                FocusMeteringAction action = new FocusMeteringAction.Builder(point)
                .disableAutoCancel()
                .build();

                ListenableFuture<FocusMeteringResult> focusResult = camera.getCameraControl().startFocusAndMetering(action);

                focusResult.addListener(() -> {
                    try {
                        focusResult.get();

                        listener.onEventUpdated("Focused");

                        isFocusBusy_ = false;

                    } catch (Exception e) {
                        SAL.print(e);
                        SAL.print("Focus cancelled");
                        listener.onEventFinished(false, "Focus cancelled");
                    }
                }, ContextCompat.getMainExecutor(context_));
            });

        }).start();
    }

    public static ImageAnalysis makeImageAnalysis() {
        ImageAnalysis.Builder builder = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);

        ImageAnalysis temp = builder.build();

        temp.setAnalyzer(ContextCompat.getMainExecutor(context_), CameraControl::analyzeCurrentFrame);

        return temp;
    }

    private static void updateAllCameraConfig() {
        updateVideoSettings();
        updatePhotoSettings();
    }

    private static void updateVideoSettings() {

        update3A();
        updateLogMode();

        cro.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                isVideoStbEnabled_ ? CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                : CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);

    }

    private static void updatePhotoSettings() {

        cro.setCaptureRequestOption(CaptureRequest.JPEG_QUALITY,Integer.valueOf(100).byteValue());

    }

    private static void updateLogMode() {

        cro.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE,
                isLogEnabled_ ? CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE
                        : CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
                .setCaptureRequestOption(CaptureRequest.EDGE_MODE,
                        isLogEnabled_ ? CaptureRequest.EDGE_MODE_OFF
                                : CaptureRequest.EDGE_MODE_HIGH_QUALITY);

        if(isLogEnabled_) {
            cro.setCaptureRequestOption(CaptureRequest.TONEMAP_CURVE, CameraUtils.makeToneMapCurve(
                            CameraUtils.LogScheme.CLOG, CameraUtils.getCameraCharacteristics(context_, isFrontFacing() ? 1 : 0)));
        }
    }

    private static void update3A() {


        cro.setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,videoFps_ % 25 == 0 ?
                                        CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ:
                                        CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ);

        cro.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range(isFilming_ ? videoFps_ : 0,videoFps_));

        cro.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, isAWBLock_ || isFilming_)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, isAELock_ || isFilming_)
                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        isVideoMode_ ?
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                            : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

    }

    //"Experimental" cannot stop me from using it lol
    @SuppressLint("UnsafeExperimentalUsageError")
    private static void flushCaptureRequest() {
        runLater( ()-> {
            try {
                Camera2CameraControl.from(camera.getCameraControl()).addCaptureRequestOptions(cro.build()).get();

                //Reset CaptureRequestOption
                cro = new CaptureRequestOptions.Builder();
                SAL.print("Finish flushing");

            } catch (Exception e) {
                SAL.print(e);
            }
        });
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    public static void updateEV(double ev) {

        float [] evInfo = CameraUtils.getEvInfo(context_,isFrontFacing_ ? 1 : 0);

        if(ev < evInfo[1] || ev > evInfo[2]) {
            SAL.print(TAG,"EV " + ev + " is out of range.");
        }

        int index = (int)Math.round((ev / evInfo[0]));

        camera.getCameraControl().setExposureCompensationIndex(index);

    }

    public static void setVideoMode(boolean isVideoMode) {
        isVideoMode_ = isVideoMode;
    }

    public static void setLumaListener(LumaListener listener) {
        lumaListener_ = listener;
    }

    public static boolean isFrontFacing() {
        return isFrontFacing_;
    }

    public static boolean isWidescreen() {
        return isWidescreen_;
    }

    public static boolean isManualWideScreen() {
        return isWidescreen_;
    }

    public static boolean isFilming() {
        return isFilming_;
    }

}
