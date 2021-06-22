package com.lbynet.Phokus.camera;

import androidx.camera.core.CameraControl;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.view.PreviewView;

import com.google.common.util.concurrent.ListenableFuture;
import com.lbynet.Phokus.template.EventListener;
import com.lbynet.Phokus.utils.SAL;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class FocusAction {

    EventListener listener_;
    boolean is_continuous_ = false,
            is_interrupted_ = false;
    Thread thread_;
    PreviewView preview_view_ = null;
    Executor executor_ = null;
    CameraControl cc_ = null;
    float [] coordinate_ = {0,0};


    public FocusAction(boolean isContinuous,
                       float[] coordinate,
                       CameraControl cameraControl,
                       PreviewView previewView,
                       Executor executor,
                       EventListener listener) {

        is_continuous_ = isContinuous;
        coordinate_ =  coordinate;
        preview_view_ = previewView;
        executor_ = executor;

        thread_ = new Thread( () -> {

            if(!is_continuous_) focus();
            else {
                while(!is_interrupted_) { focus(); }
            }
        });

        thread_.start();
    }

    public boolean isContinuous() {
        return is_continuous_;
    }

    private boolean focus() {
        MeteringPointFactory factory = preview_view_.getMeteringPointFactory();
        MeteringPoint point = factory.createPoint(coordinate_[0], coordinate_[1]);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point).disableAutoCancel().build();
        ListenableFuture<FocusMeteringResult> future = cc_.startFocusAndMetering(action);

        AtomicBoolean is_completed = new AtomicBoolean(false);

        future.addListener(() -> {

            try {

                FocusMeteringResult result = ((FocusMeteringResult)(future.get()));

                is_completed.set(true);

            } catch (Exception e) {
                SAL.print(e);

            } finally {
                is_completed.set(true);
            }
        },executor_);

        while(!is_completed.get()) SAL.sleepFor(1);

        return true;
    }


    public synchronized void updateFocusCoordinate(float [] newCoordinate) {
        coordinate_ = newCoordinate;
    }

    public void interrupt() {

        is_interrupted_ = true;
        cc_.cancelFocusAndMetering();
    }

}
