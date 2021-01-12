package com.lbynet.Phokus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.hardware.camera2.CaptureRequest;
import android.util.Range;

import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.lbynet.Phokus.frames.EventListener;
import com.lbynet.Phokus.utils.SAL;

public class CameraControl {

    static boolean isVideoMode_ = false,
            isRecording_ = false,
            isCameraBound = false,
            isWidescreen_ = false,
            isFrontFacing_ = false,
            isWsOnly = false;

    static double minFocalLength = 0;
    static CameraSelector cs;
    static ImageCapture ic;
    static ImageAnalysis ia;
    static Preview preview;
    static Camera camera;
    static ProcessCameraProvider pcp = null;
    static PreviewView previewView_ = null;
    static Context context_;
    static ValueAnimator pvwAnimator = null,
            pvAnimator = null;

    public static void initiate(PreviewView previewView) {
        //TODO

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
        bindCamera(new EventListener() {
        });
    }

    public static void bindCamera(EventListener event) {

        isCameraBound = false;

        event.onEventBegan("Start binding camera.");

        cs = new CameraSelector.Builder()
                .requireLensFacing(isFrontFacing_ ?
                        CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();

        ImageCapture.Builder icBuilder = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY);

        if (preview == null) {
            preview = makePreview(isWidescreen_ || isVideoMode_);
            preview.setSurfaceProvider(previewView_.getSurfaceProvider());


            camera = pcp.bindToLifecycle((LifecycleOwner) context_, cs, preview);

        }

        //TODO: Add custom params for ImageCapture here

        if (ic != null) {
            pcp.unbind(ic);
        }

        new Camera2Interop.Extender<>(icBuilder)
                .setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL)
                .setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);

        ic = icBuilder.build();

        camera = pcp.bindToLifecycle((LifecycleOwner) context_, cs, ic);

        isCameraBound = true;

        event.onEventFinished(true, "Finish binding camera.");

        isWsOnly = false;
    }

    public static void toggleCameraFacing(EventListener listener) {
        isFrontFacing_ = !isFrontFacing_;
        updateCameraFacing(isFrontFacing_,listener);
    }

    public static void updateCameraFacing(boolean isFrontFacing,EventListener listener) {

        listener.onEventBegan("Updating camera facing: " + (isFrontFacing? "Front" : "Back"));

        isFrontFacing_ = isFrontFacing;

        cs = new CameraSelector.Builder()
                .requireLensFacing(isFrontFacing_ ?
                        CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();

        pcp.unbindAll();

        camera = pcp.bindToLifecycle((LifecycleOwner) context_,cs,preview,ic);

        listener.onEventFinished(true,"Finished switching camera facing");

    }

    public static void toggleWideScreen(EventListener event) {

        event.onEventBegan("Start toggling widescreen");

        if(isWidescreen_ && isVideoMode_) {
            event.onEventFinished(false,"Video mode, cannot switch widescreen mode");
            return;
        }

        isWidescreen_ = !isWidescreen_;

        Preview oldPreview = preview;

        preview = makePreview(isWidescreen_ || isVideoMode_);
        preview.setSurfaceProvider(previewView_.getSurfaceProvider());

        pcp.unbind(oldPreview);
        camera = pcp.bindToLifecycle((LifecycleOwner) context_, cs, preview);

        event.onEventFinished(true, "");
    }

    public static void setVideoMode(boolean isVideoMode, EventListener listener) {

        new Thread(() -> {
            listener.onEventBegan("");
            if (isVideoMode != isVideoMode_) {
                //TODO
            } else {
                //Do nothing
            }
            listener.onEventFinished(true, "");
        }).start();
    }

    public static void takePicture(EventListener listener) {
        //TODO
        new Thread(() -> {

        }).start();
    }

    public static void zoomByFocalLength(double mm, EventListener listener) {

        new Thread(() -> {
            listener.onEventBegan("");
            if (mm < minFocalLength) {
                listener.onEventFinished(false,
                        "Failed to zoom, reason: requested focus length is lower than native focal length.");
            }
            final double zoomRatio = mm / minFocalLength;

            //TODO: Zoom

            listener.onEventFinished(true, "Zoom by focal length successful.");

        }).start();
    }

    public static void startRecording(EventListener listener) {

        new Thread(() -> {
            listener.onEventBegan("");
            if (isRecording_) {
                listener.onEventFinished(false, "Camera is already recording");
            } else if (!isVideoMode_) {
                listener.onEventFinished(false, "Camera is not in video mode");
            }

            //TODO: Start recording

            listener.onEventFinished(true, "");
        }).start();

    }

    public static Preview makePreview(boolean isWidescreen) {

        Preview.Builder builder = new Preview.Builder()
                .setTargetAspectRatio((isWidescreen || isVideoMode_) ? AspectRatio.RATIO_16_9 : AspectRatio.RATIO_4_3);

        new Camera2Interop.Extender<>(builder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(30,60));

        return builder.build();
    }

}
