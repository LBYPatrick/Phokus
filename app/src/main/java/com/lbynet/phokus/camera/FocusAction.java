package com.lbynet.phokus.camera;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.camera.core.CameraControl;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.view.PreviewView;

import com.google.common.util.concurrent.ListenableFuture;
import com.lbynet.phokus.template.FocusActionListener;
import com.lbynet.phokus.utils.SAL;

import java.lang.annotation.Retention;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * For the record, the original FocusAction was written back in August 17th, 2021, which is BEFORE I took Operating Systems
 * And here we are! I realized that there are so many concurrency issues with FocusAction so I rewrote the entire thing
 * 2021/12/19.
 */
public class FocusAction {

    @Retention(SOURCE)
    @IntDef({
         FOCUS_AUTO,
            FOCUS_SINGLE,
            FOCUS_SERVO
    })
    public @interface FocusType{}

    final public static int FOCUS_AUTO = 0,
                            FOCUS_SINGLE = 1,
                            FOCUS_SERVO = 2;

    public static class FocusActionRequest {

        public @FocusType int type = FOCUS_AUTO;
        public float [] point = {0,0};

        public FocusActionRequest() {}

        public FocusActionRequest(@FocusType int type, float [] point) {
            this.type = type;
            this.point[0] = point[0];
            this.point[1] = point[1];
        }

        public FocusActionRequest(FocusActionRequest another) {
            this(another.type,another.point);
        }

    }

    public static class FocusActionResult {
        public @FocusType int type = 0;
        public boolean isSuccess = false;
        public float [] point = {0,0};

        public FocusActionResult() {}

        public FocusActionResult(@FocusType int type, float [] point,boolean isSuccess) {
            this.type = type;
            this.isSuccess = isSuccess;
            this.point[0] = point[0];
            this.point[1] = point[1];
        }

        public FocusActionResult(FocusActionRequest request, boolean isSuccess) {
            this(request.type,request.point,isSuccess);
        }

        public FocusActionResult(FocusActionResult another) {
            this(another.type,another.point,another.isSuccess);
        }

    }

    //Java's version of pthread_mutex
    final private static ReentrantLock m_focus = new ReentrantLock(),
                                 m_request = new ReentrantLock();

    //Since Condition is tightly bound a mutex, this implicitly fulfills the concurrency commandment
    //Instead of us passing the mutexes around
    final private static Condition cond = m_focus.newCondition();

    private static CameraControl cc_;
    private static PreviewView pv_;
    private static Executor ui_executor_;
    private static Exception exception_ = null;
    private static FocusActionListener listener_ = null;
    private static Thread t_looper_ = null;

    final private static Executor exec_listener_ = Executors.newSingleThreadExecutor();


    //AtomicBoolean is thread-safe so no need to worry about concurrency for them
    private static boolean is_point_valid_ = false,
                           is_busy_ = false,
                           is_flying_change_ = false,
                           is_paused_ = false;

    private static FocusActionRequest currReq = new FocusActionRequest();

    /**
     *
     * @param cameraControl CameraControl object returned from androidx.camera.core.Camera.getCameraControl()
     * @param previewView the PreviewView Object from the frontend for translating user input into coordinates in CMOS
     * @param uiExecutor the executor for the UI Thread (focus requests are required to be sent via this thread for some reason)
     * @return 0 on success, < 0 on failure. (and no I don't like using errno)
     */
    public static int initialize(CameraControl cameraControl,
                             PreviewView previewView,
                                 Executor uiExecutor) {
        cc_ = cameraControl;
        pv_ = previewView;
        ui_executor_ = uiExecutor;

        //Gotta Appreciate the simplicity of this
        t_looper_ = new Thread(() -> {

            int r = 0;
            while(r >= 0) {
                r = exec();
                //TODO: Remove this
                //SAL.sleepFor(1);
            }
        });

        t_looper_.start();

        return 0;
    }

    public static void setListener(FocusActionListener listener) {
        listener_ = listener;
    }

    public static void issueRequest(FocusActionRequest request) {
        //m_request ensures that ONE request may be processed at a time
        m_request.lock();
        is_flying_change_ = true;

        cc_.cancelFocusAndMetering(); //This would make focus() release its lock

        m_focus.lock();

        //One of the corner cases -- what
        while(is_busy_) condWait();

        currReq = new FocusActionRequest(request);
        is_point_valid_ = true;
        is_flying_change_ = false;

        //This wakes up the looper thread(s)
        cond.signalAll();
        m_focus.unlock();

        m_request.unlock();
    }

    public static int condWait() {

        try {
            cond.await();
        } catch (InterruptedException e) {
            SAL.print(e);
            return -1;
        }

        return 0;
    }

    public static FocusActionRequest getLastRequest() {

        return new FocusActionRequest(currReq);

    }

    public static void pause() {

        boolean wasBusy = is_busy_;

        cc_.cancelFocusAndMetering(); //This would make focus() release its lock ASAP
        m_focus.lock();

        while(is_busy_) condWait();

        is_paused_ = true;
        if(wasBusy) is_point_valid_ = true; //So that as soon as we run resume(), focus() would pick up where it left off

        m_focus.unlock();
    }

    public static void resume() {
        m_focus.lock();

        is_paused_ = false;

        m_focus.unlock();
    }

    public static void resumeFresh() {
        m_focus.lock();

        is_point_valid_ = false;
        is_paused_ = false;

        m_focus.unlock();
    }


    private static int exec() {

        int r = 0;
        m_focus.lock();
        while (r == 0 && (is_paused_ || is_flying_change_ || is_busy_ || !is_point_valid_)) r = condWait();

        if(r < 0) return r;
        else {
            focus();
            return 0;
        }
    }

    //TODO: This method is unlocking prematurely, waiting for a fix
    private static void focus() {

        //Since currReq is used, we are technically in a critical section
        m_focus.lock();

        is_busy_ = true;

        if(listener_ != null)
            listener_.onFocusBusy(new FocusActionRequest(currReq));

        if(currReq.type == FOCUS_AUTO) {

            ui_executor_.execute(cc_::cancelFocusAndMetering);
            is_busy_ = false;
            is_point_valid_ = false;

            if(listener_ != null)
                listener_.onFocusEnd(new FocusActionResult(currReq,true));

            cond.signalAll();
            m_focus.unlock();

            return;
        }

        ui_executor_.execute( ()-> {

            FocusMeteringAction action = new FocusMeteringAction.Builder
                    (pv_
                            .getMeteringPointFactory()
                            .createPoint(currReq.point[0],currReq.point[1]))
                    .disableAutoCancel()
                    .build();

            ListenableFuture<FocusMeteringResult> future = cc_.startFocusAndMetering(action);

            future.addListener(() -> {

                //m_focus.lock();
                FocusMeteringResult res = null;

                //Obtain focus result
                try {
                    res = future.get();
                }
                //Focus Cancelled
                catch (Exception e) {
                    if(listener_ != null) listener_.onFocusEnd(new FocusActionResult(currReq,false));
                    //SAL.print(e);
                    exception_ = e;
                }
                //Deal with the variables in FocusAction (in a thread-safe way of course)
                finally {

                    m_focus.lock();

                    if(currReq.type != FOCUS_SERVO) is_point_valid_ = false;
                    is_busy_ = false;

                    if(listener_ != null && res != null) listener_.onFocusEnd(new FocusActionResult(currReq,res.isFocusSuccessful()));

                    //Equivalent of cond.broadcast(mutex) in Java
                    cond.signalAll();
                    m_focus.unlock();
                }

            },exec_listener_);

        });

        while(is_busy_) condWait();

        m_focus.unlock();

    }

}
