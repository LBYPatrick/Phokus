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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class FocusAction {

    private EventListener listener_;
    private boolean is_continuous_ = false,
            is_interrupted_ = false,
            is_paused_ = false;
    private Thread thread_;
    private PreviewView preview_view_ = null;
    private Executor executor_ = null;
    private CameraControl cc_ = null;
    private float [] coordinate_ = {0,0};
    final public static String MSG_BUSY = "focus_busy",
                               MSG_SUCCESS = "focus_success",
                               MSG_CANCELLED = "focus_cancelled";


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
        cc_ = cameraControl;
        listener_ = listener;

        thread_ = new Thread( () -> {

            if(!is_continuous_) focus();
            else {
                while(!is_interrupted_) {
                    while(is_paused_) SAL.sleepFor(10);
                    focus();
                }
            }
        });

        thread_.start();
    }

    public boolean isContinuous() {
        return is_continuous_;
    }
    public boolean isInterrupted() {return is_interrupted_;}

    private boolean focus() {

        AtomicBoolean is_completed = new AtomicBoolean(false);

        executor_.execute( () -> {

            MeteringPointFactory factory = preview_view_.getMeteringPointFactory();
            MeteringPoint point = factory.createPoint(coordinate_[0], coordinate_[1]);
            FocusMeteringAction action = new FocusMeteringAction.Builder(point).disableAutoCancel().build();
            ListenableFuture<FocusMeteringResult> future = cc_.startFocusAndMetering(action);

            listener_.onEventUpdated(EventListener.DataType.STRING_FOCUS_STAT, MSG_BUSY);

            future.addListener(() -> {

                try {

                    FocusMeteringResult result = ((FocusMeteringResult) (future.get()));

                    listener_.onEventUpdated(EventListener.DataType.STRING_FOCUS_STAT, MSG_SUCCESS);

                    is_completed.set(true);

                } catch (Exception e) {

                    listener_.onEventUpdated(EventListener.DataType.STRING_FOCUS_STAT, MSG_CANCELLED);


                    /**
                     * This almost always comes out as OperationCancelledException, which suggests
                     * that the current focus action has been cancelled by the system
                     * or user input (picking a new focus point BEFORE current focus action finishes)
                     */

                    SAL.print(e,false);

                } finally {
                    is_completed.set(true);
                }
            }, executor_);
        });

        while (!is_completed.get()) SAL.sleepFor(1);

        return true;
    }

    public synchronized void pause() {
        is_paused_ = true;
    }
    public synchronized void resume() {
        is_paused_ = false;
    }

    public synchronized void updateFocusCoordinate(float [] newCoordinate) {

        cc_.cancelFocusAndMetering();
        coordinate_ = newCoordinate;
    }

    public void interrupt() {

        is_interrupted_ = true;
        cc_.cancelFocusAndMetering();
    }

}
