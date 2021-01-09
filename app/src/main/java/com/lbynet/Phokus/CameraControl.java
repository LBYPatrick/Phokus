package com.lbynet.Phokus;

import android.content.Context;
import android.hardware.camera2.CaptureRequest;

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
                   isWidescreen =  false,
                   isFrontFacing = false;

    static double minFocalLength = 0;
    static CameraSelector cs;
    static ImageCapture ic;
    static ImageAnalysis ia;
    static Preview preview;
    static Camera camera;
    static ProcessCameraProvider pcp = null;
    static PreviewView previewView_ = null,
                       previewViewW_ = null;
    static Context context_;

    public static void initiate(PreviewView previewView, PreviewView previewViewWide) {
        //TODO

        context_ = previewView.getContext();

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context_);
        //Bind to lifecycle
        future.addListener(() -> {
            try {
                pcp = future.get();
                previewView_ = previewView;
                previewViewW_ = previewViewWide;
                bindCamera();

            } catch (Exception e) {
                SAL.print(e);
            }
        }, ContextCompat.getMainExecutor(context_));
    }

    public static void bindCamera() {

        isCameraBound = false;

        preview = new Preview.Builder()
                .setTargetAspectRatio(

                        //AspectRatio.RATIO_16_9
                        (isWidescreen || isVideoMode_) ? AspectRatio.RATIO_16_9 : AspectRatio.RATIO_4_3
        ).build();

        cs = new CameraSelector.Builder()
                .requireLensFacing(isFrontFacing?
                        CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();

        ImageCapture.Builder icBuilder = new ImageCapture.Builder();

        //TODO: Add custom params for ImageCapture here

        new Camera2Interop.Extender<>(icBuilder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

        ic = icBuilder.build();

        pcp.unbindAll();

        camera = pcp.bindToLifecycle((LifecycleOwner) context_,cs,preview, ic);

        preview.setSurfaceProvider(previewView_.getSurfaceProvider());

        isCameraBound = true;

    }

    public static void setVideoMode(boolean isVideoMode, EventListener listener) {

        new Thread( () -> {
            listener.onEventBegan("");
            if (isVideoMode != isVideoMode_) {
                //TODO
            } else {
                //Do nothing
            }
            listener.onEvenFinished(true, "");
        }).start();
    }

    public static void takePicture(EventListener listener) {
        //TODO
        new Thread(() -> {

        }).start();
    }

    public static void zoomByFocalLength(double mm, EventListener listener) {

        new Thread( () -> {
            listener.onEventBegan("");
            if (mm < minFocalLength) {
                listener.onEvenFinished(false,
                        "Failed to zoom, reason: requested focus length is lower than native focal length.");
            }
            final double zoomRatio = mm / minFocalLength;

            //TODO: Zoom

            listener.onEvenFinished(true, "Zoom by focal length successful.");

        }).start();
    }

    public static void startRecording(EventListener listener) {

        new Thread( () -> {
            listener.onEventBegan("");
            if (isRecording_) {
                listener.onEvenFinished(false, "Camera is already recording");
            } else if (!isVideoMode_) {
                listener.onEvenFinished(false, "Camera is not in video mode");
            }

            //TODO: Start recording

            listener.onEvenFinished(true, "");
        }).start();

    }

}
