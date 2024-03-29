package com.lbynet.phokus.ui;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import com.lbynet.phokus.R;
import com.lbynet.phokus.camera.CameraCore;
import com.lbynet.phokus.camera.CameraUtils;
import com.lbynet.phokus.camera.FocusAction;
import com.lbynet.phokus.camera.FocusAction.FocusActionRequest;
import com.lbynet.phokus.databinding.ActivityCameraBinding;
import com.lbynet.phokus.global.Config;
import com.lbynet.phokus.global.Consts;
import com.lbynet.phokus.hardware.BatterySensor;
import com.lbynet.phokus.template.EventListener;
import com.lbynet.phokus.template.OnEventCompleteCallback;
import com.lbynet.phokus.utils.MathTools;
import com.lbynet.phokus.utils.SAL;
import com.lbynet.phokus.utils.Timer;
import com.lbynet.phokus.utils.UIHelper;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

public class CameraActivity extends AppCompatActivity {

    final public static String TAG = CameraActivity.class.getCanonicalName();
    final public static int DUR_ANIM_PREVIEW_RESIZE = 400,
                            DUR_ANIM_SHUTTER = 300,
                            DUR_ANIM_AF_OVERLAY = 300,
                            LENGTH_FOCUS_RECT = 80;

    private ActivityCameraBinding binding;
    private Timer videoTimer = new Timer("Video Timer");
    private OrientationEventListener orientationListener;
    private boolean isVideoMode = false,
            isRecording = false,
            isContinuousFocus = false,
            isFocused = false,
            isZooming = false,
            isCameraStarted = false;

    private static int[] previewDimensions = null;
    private static String bottomInfo;
    private OnEventCompleteCallback onCameraBoundCallback,
                                    onCameraBindingCallback;
    private static BatterySensor sensorBattery;
    //private static RotationSensor sensorRotation;

    private View [] controlViews;

    private static ReentrantLock mFocus = new ReentrantLock();
    private Handler animationHandler = new Handler(),
            fullscreenHandler = new Handler();

    @Retention(RetentionPolicy.SOURCE)
    @IntDef ( {
            STATE_PHOTO_IDLE,
            STATE_PHOTO_PRESS,
            STATE_PHOTO_RELEASE,
            STATE_VIDEO_IDLE,
            STATE_VIDEO_BUSY,
            STATE_VIDEO_STOP,
    })
    private @interface ShutterState { }
    final private static int STATE_PHOTO_IDLE = 0,
            STATE_PHOTO_PRESS = 1,
            STATE_PHOTO_RELEASE = 2,
            STATE_VIDEO_IDLE = 3,
            STATE_VIDEO_BUSY = 4,
            STATE_VIDEO_STOP = 5;


    final private Runnable
            rHideBottomInfo = () -> UIHelper.setViewAlpha(binding.cvBottomInfo, 200, 0, true),
            rShowBottomInfo = () -> UIHelper.setViewAlpha(binding.cvBottomInfo, 10, 1, true),
            rShowTopInfo = () -> UIHelper.setViewAlpha(binding.cvTopInfo, 10, 1, true),
            rFadeTopInfo = () -> UIHelper.setViewAlpha(binding.cvTopInfo, 200, 0.5f, true),
            rOnShutterPressed = () -> {

                SAL.simulatePress(this, false);

                updateShutterState(isVideoMode ? STATE_VIDEO_BUSY : STATE_PHOTO_PRESS);

                if(isVideoMode) UIHelper.setViewAlpha(200, 1, binding.vRecordRect);
                else UIHelper.setViewAlpha(50,1,binding.vCaptureRect);

            },
    rOnShutterReleased = () -> {

        SAL.simulatePress(this, true);

        updateShutterState(isVideoMode ? STATE_VIDEO_STOP : STATE_PHOTO_RELEASE);

        if(isVideoMode) UIHelper.setViewAlpha(200, 0, binding.vRecordRect);
        else UIHelper.setViewAlpha(1000,0,binding.vCaptureRect);

    },
            rHideNav = () -> {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        );
            };

    final private FocusAction.FocusEventListener listener_focus_ = new FocusAction.FocusEventListener() {
        @Override
        public void onFocusEnd(FocusAction.FocusActionResult res) {

            runOnUiThread( ()-> {
                mFocus.lock();
                //Routine for FOCUS_AUTO
                if (res.type == FocusAction.FOCUS_AUTO) {
                    binding.btnCancelFocus.setClickable(false);

                    binding.btnCancelFocus.animate()
                            .alpha(0)
                            .setDuration(50)
                            .start();

                    showAfOverlay();

                    UIHelper.setViewAlpha(0, 0, binding.vFocusRect);
                }
                //Otherwise, make focus rectangle green/blue(depending on the AF mode) when AF Single/Servo succeeds
                else {
                    UIHelper.setViewAlpha(50, 1, binding.vFocusRect);

                    binding.vFocusRect.setForegroundTintList(
                            UIHelper.makeCSLwithID(requireContext(),
                                    (isContinuousFocus ? R.color.colorFocusContinuous : R.color.colorFocusOneShot)
                            )
                    );
                    isFocused = true;
                }

                mFocus.unlock();
            });
        }

        @Override
        public void onFocusBusy(FocusActionRequest req) {

            //SAL.print(String.format("Focus is busy...\n type: %d, isFocused: %s",req.type,isFocused ? "True" : "False"));

            if (isFocused || req.type == FocusAction.FOCUS_AUTO) return;

            runOnUiThread( ()-> {

                mFocus.lock();

                UIHelper.setViewAlpha(50, 0.5f, binding.vFocusRect);
                //ImageView btn = binding.btnCancelFocus;

                binding.btnCancelFocus.setClickable(true);
                binding.btnCancelFocus.animate()
                        .alpha(1)
                        .setDuration(200)
                        .start();
                /**
                 * Make focus rectangle grey when AF is busy
                 */
                binding.vFocusRect.setForegroundTintList(UIHelper.makeCSLwithID(requireContext(), R.color.colorPrimaryDark));

                mFocus.unlock();

            });

        }
    };

    private float currZoomRatio = 1;

    @Deprecated
    final private ScaleGestureDetector.SimpleOnScaleGestureListener pToZListener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            isZooming = true;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {


            float factor = detector.getScaleFactor();

            if (factor < 1) factor = -(1 / factor);
            else factor -= 1;

            float delta = factor * 0.1f;

            currZoomRatio += delta;

            if (currZoomRatio < 1) currZoomRatio = 1;
            else if (currZoomRatio > 5) currZoomRatio = 5;

            wakeTopPanel();

            CameraCore.zoomByRatio(currZoomRatio, new EventListener() {
                @SuppressLint("DefaultLocale")
                @Override
                public boolean onEventUpdated(DataType dataType, Object data) {

                    if (dataType != DataType.FLOAT_CAM_FOCAL_LENGTH) return false;

                    UIHelper.runLater(requireContext(), () -> {

                        binding.tvFocalLength.setText(String.format("%.2fmm", (Float) data));
                        updateInfo(String.format("Scale factor: %.2fx", currZoomRatio));

                        int[] colors = UIHelper.getColors(requireContext(), R.color.colorText, R.color.colorPrimary);
                        binding.tvFocalLength.setTextColor(currZoomRatio == 1 ? colors[0] : colors[1]);

                    });

                    return super.onEventUpdated(dataType, data);
                }
            });

            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            isZooming = false;
        }
    };
    //private ScaleGestureDetector pToZDetector = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        /**
         * Activity Setup
         */
        super.onCreate(savedInstanceState);
        binding = ActivityCameraBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());
        initUiListeners();
        SAL.setActivity(this);

        /**
         * Configure rotation listener
         */
        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                CameraCore.updateRotation(UIHelper.getSurfaceOrientation(orientation));
            }
        };

        /**
         * Grant permission
         */
        if (isAllPermissonGood())
            startCamera();
        else
            ActivityCompat.requestPermissions(this, Consts.PERMISSIONS, Consts.PERM_REQUEST_CODE);

    }

    public void initUiListeners() {

        //pToZDetector = new ScaleGestureDetector(requireContext(), pToZListener);

        binding.ivShutterPhotoBase.setOnTouchListener(this::onShutterTouched);
        binding.btnCaptureMode.setOnClickListener(this::toggleVideoMode);
        binding.btnCancelFocus.setOnClickListener(this::cancelFocus);
        //binding.btnFocusCancel.setOnClickListener(this::cancelFocus);
        binding.toggleFocusFreq.setOnClickListener(this::toggleFocusFreqMode);
        binding.fabSwitchSide.setOnClickListener(this::toggleCameraFacing);
        binding.vFocusGuardLeft.setOnTouchListener((view,event) -> true);
        binding.vFocusGuardRight.setOnTouchListener((view,event) -> true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull @NotNull String[] permissions,
                                           @NonNull @NotNull int[] grantResults) {

        if (requestCode == Consts.PERM_REQUEST_CODE && isAllPermissonGood()) startCamera();
        else {
            UIHelper.printSystemToast(this, "Not all permissions were granted.", false);
            finish();
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public boolean isAllPermissonGood() {
        for (String p : Consts.PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_DENIED)
                return false;
        }
        return true;
    }

    @SuppressLint("DefaultLocale")
    public void startCamera() {

        if(isCameraStarted) return;
        isCameraStarted = true;

        SAL.print("Starting camera");
        CameraCore.initialize();

        //TODO: Add more to this when we have more camera control buttons
        controlViews = new View[]{binding.btnCaptureMode,binding.fabSwitchSide};

        onCameraBoundCallback = (int res, String extra) -> {
            if(res < 0) SAL.print(SAL.MsgType.ERROR,TAG,"Camera usecase failed to bind BEFORE a " + extra + " call!");
            else {
                CameraCore.resumeFocus();
                unlockViews(controlViews);
            }
        };

        onCameraBindingCallback = (int res, String extra) -> {
            if(res < 0) SAL.print(SAL.MsgType.ERROR,TAG,"Camera usecase failed to bind AFTER a " + extra + " call!");
            else {
                CameraCore.pauseFocus();
                lockViews(controlViews);
            }
        };

        CameraCore.start(binding.preview);
        FocusAction.setListener(listener_focus_);
        resetLensInfo();

        /**
         * Configure preview params for handling user interactions (MUST be this late rather than at bindViews())
         * DEBUGGED
         */
        binding.preview.setOnTouchListener(this::onPreviewTouched);

        binding.preview.post(() -> {
            new Thread(() -> {
                //SAL.sleepFor(150);
                UIHelper.queryViewDimensions(binding.cvPreviewWrapper, (width, height) -> {
                    previewDimensions = new int[]{width,height};
                });
            }).start();
        });

        sensorBattery = new BatterySensor(this, batteryIntent -> {
            SAL.print("Battery Level: " + batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1));
        });

    }

    // Credits to Saurabh Thorat:
    // https://stackoverflow.com/questions/63202209/camerax-how-to-add-pinch-to-zoom-and-tap-to-focus-onclicklistener-and-ontouchl
    public boolean onPreviewTouched(View v, MotionEvent event) {

       //mFocus.lock();

        //Pinch-to-zoom
        float x = event.getX(),
                y = event.getY();

        //if(event.getPointerCount() > 1) pToZDetector.onTouchEvent(event);
            //Tap-to-focus
        if (event.getAction() == MotionEvent.ACTION_DOWN
                //&& !isZooming
        ) {

            if(binding.btnCancelFocus.getAlpha() == 0) hideAfOverlay();

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) binding.vFocusRect.getLayoutParams();
            params.setMargins((int) x - LENGTH_FOCUS_RECT, (int) y - LENGTH_FOCUS_RECT, 0, 0);
            binding.vFocusRect.setLayoutParams(params);

            isFocused = false;

            CameraCore.focus(
                    new FocusActionRequest(
                            isContinuousFocus ? FocusAction.FOCUS_SERVO : FocusAction.FOCUS_SINGLE,
                            new float [] {x + ((float)LENGTH_FOCUS_RECT) / 2,y + ((float)LENGTH_FOCUS_RECT) / 2}
                    )
            );
        }

        //mFocus.unlock();

        return true;
    }

    @SuppressLint("DefaultLocale")
    public void resetLensInfo(boolean isFrontFacing) {

        int camera_id = isFrontFacing ? 1 : 0;

        binding.tvAperture.setText(String.format("F/%.2f", CameraUtils.get35Aperture(this, camera_id)));
        binding.tvAperture.setTextColor(UIHelper.getColors(this, R.color.colorSecondary)[0]);

        binding.tvFocalLength.setText(
                String.format("%.2fmm",
                        CameraUtils.get35FocalLength(
                                requireContext(),
                                CameraCore.isFrontFacing() ? 1 : 0)));

        currZoomRatio = 1;

        binding.tvFocalLength.setTextColor(UIHelper.getColors(requireContext(), R.color.colorText)[0]);

        wakeTopPanel();
    }

    @SuppressLint("DefaultLocale")
    public void resetLensInfo() {
        resetLensInfo(Config.get(Config.FRONT_FACING).equals("true"));
    }

    public boolean onShutterTouched(View v, MotionEvent event) {

        if (event.getAction() != MotionEvent.ACTION_DOWN) return false;

        requireExecutor().execute(rOnShutterPressed);

        if (isVideoMode && !isRecording) {

            isRecording = true;

            CameraCore.startRecording(new CameraCore.VideoEventListener() {
                @Override
                public void onStart(VideoRecordEvent event) {
                    lockViews(binding.btnCaptureMode);
                    UIHelper.setViewAlpha(100,0,binding.btnCaptureMode);
                    startVideoTimer();
                }
                @Override
                public void onFinalize(VideoRecordEvent event) {
                    unlockViews(binding.btnCaptureMode);
                    UIHelper.setViewAlpha(100,1,binding.btnCaptureMode);
                    requireExecutor().execute(rOnShutterReleased);
                }
            });

        } else if (isVideoMode) {
            // Callback may be set to null since we have filled out onFinalize(VideoRecordEvent),
            // which is called at exactly the same time for the same reason
            CameraCore.stopRecording(null);
            stopVideoTimer();
            isRecording = false;
        } else {

            //CameraCore.pauseFocus();

            animationHandler.postDelayed(rOnShutterReleased, 100);
            CameraCore.takePicture(new EventListener() {
                @Override
                public boolean onEventUpdated(DataType dataType, Object data) {

                    //CameraCore.resumeFocus();

                    if (dataType != DataType.URI_PICTURE_SAVED) return false;
                    runOnUiThread(() -> updateInfo("Picture saved!"));

                    return super.onEventUpdated(dataType, data);
                }
            });
        }

        return true;
    }

    private void updatePreviewSize() {

        SAL.print("Attempting to update preview size.");

        new Thread(() -> {
            while(previewDimensions == null) SAL.sleepFor(10);

            final int targetWidth = isVideoMode ? (previewDimensions[0] * 4 / 3) : (previewDimensions[0] * 3 / 4);

            UIHelper.resizeView(binding.cvPreviewWrapper,
                    previewDimensions,
                    new int[]{targetWidth, previewDimensions[1]},
                    DUR_ANIM_PREVIEW_RESIZE,
                    UIHelper.INTRPL_DECEL);

            previewDimensions[0] = targetWidth;

        }).start();

    }

    public boolean toggleVideoMode(View view) {

        //Terminate current video recording session if there is one
        //There shall not be!
        /*
        if (isVideoMode && isRecording) {
            CameraCore.stopRecording(null);
            stopVideoTimer();
            isRecording = false;
        }
         */
        mFocus.lock();

        isVideoMode = !isVideoMode;

        //Disable the FocusAction listener since it may screw up our animations
        FocusAction.setListener(null);

        cancelFocus(null);

        UIHelper.setViewAlphas(0,0,binding.vFocusRect,binding.btnCancelFocus);
        hideAfOverlay();

        //SAL.sleepFor(100);

        CameraCore.setVideoMode(isVideoMode, onCameraBindingCallback,(res,extra) -> {
            unlockViews(controlViews);
        });

        /* Visual stuff */
        updatePreviewSize();
        resetLensInfo();

        binding.ivChevTl.setRotation(isVideoMode ? 0 : 180);
        binding.ivChevBl.setRotation(isVideoMode ? 0 : 180);
        binding.ivChevTr.setRotation(isVideoMode ? 0 : 180);
        binding.ivChevBr.setRotation(isVideoMode ? 0 : 180);

        final View [] showGroup = {
                binding.ivChevTl,
                binding.ivChevBl,
                binding.ivChevTr,
                binding.ivChevBr,
                binding.vPreviewMask};

        final View [] hideGroup = {
                binding.toggleExposureMenu,
                binding.toggleFocusFreq,
                binding.btnCaptureMode,
                binding.btnAwb,
                binding.fabSwitchSide
        };

        //final boolean isAfOverlayVisible = binding.ivAfOverlay.getAlpha() != 0;

        UIHelper.setViewAlphas(
                100,
                1,
                showGroup);

        UIHelper.setViewAlphas(100,0,hideGroup);
        //hideAfOverlay();

        updateShutterState(isVideoMode ? STATE_VIDEO_IDLE : STATE_PHOTO_IDLE);

        animationHandler.postDelayed(() -> {

            updateButtonColors();

            UIHelper.setViewAlphas(200,0,showGroup);
            UIHelper.setViewAlphas(200,1,hideGroup);

            showAfOverlay();
            FocusAction.setListener(listener_focus_);

        }, DUR_ANIM_PREVIEW_RESIZE);



        mFocus.unlock();

        return true;
    }

    private void showAfOverlay() {

        //if(binding.ivAfOverlay.getAlpha() != 0) return;

        runOnUiThread( ()-> {
            binding.ivAfOverlay.setScaleX(1.1f);
            binding.ivAfOverlay.setScaleY(1.1f);

            binding.ivAfOverlay.animate()
                    .scaleY(1f)
                    .scaleX(1f)
                    .setDuration(DUR_ANIM_AF_OVERLAY)
                    .alpha(1)
                    .setInterpolator(UIHelper.getInterpolator(UIHelper.INTRPL_DECEL))
                    .start();
        });
    }

    @AnyThread
    private void hideAfOverlay() {

        //if(binding.ivAfOverlay.getAlpha() != 1) return;

        runOnUiThread( ()-> {

            binding.ivAfOverlay.animate()
                    .scaleY(1.1f)
                    .scaleX(1.1f)
                    .setDuration(DUR_ANIM_AF_OVERLAY)
                    .alpha(0)
                    .setInterpolator(UIHelper.getInterpolator(UIHelper.INTRPL_ACCEL))
                    .start();
        });

    }

    private void updateShutterState(@ShutterState int state) {

        switch (state) {
            case STATE_PHOTO_IDLE:

                binding.ivShutterPhotoBase.animate()
                        .scaleX(1)
                        .scaleY(1)
                        .setInterpolator(new DecelerateInterpolator())
                        .setDuration(DUR_ANIM_SHUTTER)
                        .start();

                binding.ivShutterWhiteCenter.animate()
                        .scaleX(0.85f)
                        .scaleY(0.85f)
                        .setInterpolator(new AccelerateInterpolator())
                        .setDuration(DUR_ANIM_SHUTTER)
                        .start();

                binding.ivShutterVideoIdle.animate()
                    .alpha(0)
                    .setDuration(DUR_ANIM_SHUTTER)
                    .start();

                break;

            case STATE_VIDEO_IDLE:

                binding.ivShutterPhotoBase.animate()
                        .scaleX(0.5f)
                        .scaleY(0.5f)
                        .setDuration(DUR_ANIM_SHUTTER)
                        .setInterpolator(new AccelerateInterpolator())
                        .start();

                binding.ivShutterWhiteCenter.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setInterpolator(new DecelerateInterpolator())
                        .setDuration(DUR_ANIM_SHUTTER * 2)
                        .start();

                binding.ivShutterVideoIdle.animate()
                        .alpha(1)
                        .setDuration(DUR_ANIM_SHUTTER)
                        .start();

                binding.ivShutterVideoBusy.animate()
                        .alpha(0)
                        .setDuration(DUR_ANIM_SHUTTER)
                        .start();

                break;

            case STATE_VIDEO_BUSY:
                binding.ivShutterVideoIdle.animate()
                        .scaleX(0)
                        .scaleY(0)
                        .setDuration(500)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                binding.ivShutterVideoBusy.animate()
                        .alpha(1)
                        .setDuration(0)
                        .start();

                break;

            case STATE_VIDEO_STOP:

                binding.ivShutterVideoIdle.animate()
                        .scaleX(0.85f)
                        .scaleY(0.85f)
                        .setDuration(500)
                        .setInterpolator(new OvershootInterpolator())
                        .start();

                binding.ivShutterVideoBusy.animate()
                        .alpha(0)
                        .setDuration(DUR_ANIM_SHUTTER)
                        .start();

                break;

            case STATE_PHOTO_PRESS:

                binding.ivShutterWhiteCenter.animate()
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .alpha(0.6f)
                        .setDuration(50)
                        .start();
                break;

            case STATE_PHOTO_RELEASE:
                binding.ivShutterWhiteCenter.animate()
                        .scaleX(0.85f)
                        .scaleY(0.85f)
                        .alpha(1)
                        .setDuration(50)
                        .start();

        }

    }

    private Context requireContext() {
        return this;
    }

    private Executor requireExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    private void startVideoTimer() {

        new Thread(() -> {

            videoTimer.start();

            String[] time = new String[4];

            wakeInfoPanel();

            while (videoTimer.isBusy()) {

                /*Limit timer refresh rate to be roughly 10fps*/
                while (videoTimer.isBusy() && videoTimer.getElaspedTimeInMs() % 8 != 0) { SAL.sleepFor(1); }

                updateInfo(MathTools.parseTimeToString(videoTimer.getElaspedTimeInMs()));
            }

            /*Extra run after loop*/
            updateInfo(MathTools.parseTimeToString(videoTimer.getElaspedTimeInMs()));

        }).start();
    }

    private void stopVideoTimer() { videoTimer.stop(); }

    private void wakeTopPanel() {

        animationHandler.removeCallbacks(rFadeTopInfo);
        ContextCompat.getMainExecutor(this).execute(rShowTopInfo);
        animationHandler.postDelayed(rFadeTopInfo, 2000);
    }

    private void updateInfo(String newInfo) {

        bottomInfo = newInfo;

        ContextCompat.getMainExecutor(this).execute(() -> binding.tvBottomInfo.setText(newInfo));
        wakeInfoPanel();

    }

    private void wakeInfoPanel() {
        animationHandler.removeCallbacks(rHideBottomInfo);
        ContextCompat.getMainExecutor(this).execute(rShowBottomInfo);
        animationHandler.postDelayed(rHideBottomInfo, 4000);
    }


    private boolean cancelFocus(View view) {

        CameraCore.cancelFocus();

        return true;

    }

    private boolean toggleCameraFacing(View view) {

        mFocus.lock();

        boolean isFrontFacing = !(CameraCore.isFrontFacing());

        lockViews(controlViews);
        //Disable the FocusAction listener since it may screw up our animations
        FocusAction.setListener(null);
        cancelFocus(null);

        UIHelper.setViewAlphas(0,0,binding.vFocusRect,binding.btnCancelFocus);
        hideAfOverlay();
        animationHandler.postDelayed(this::showAfOverlay,DUR_ANIM_AF_OVERLAY);
        
        CameraCore.toggleCameraFacing((res,extra) -> {
            unlockViews(controlViews);
            SAL.print(TAG,"Camera facing toggled!");

            FocusAction.setListener(listener_focus_);
        });

        /* Rotate the switch side FAB */
        binding.fabSwitchSide.animate()
                .rotationBy(isFrontFacing ? -180.0f : 180.0f)
                .setInterpolator(new OvershootInterpolator())
                .setDuration(500)
                .start();

        /* Reset lens info displayed in the top info card (aperture/focal length) */
        resetLensInfo(isFrontFacing);

        mFocus.unlock();

        return true;
    }

    private boolean toggleFocusFreqMode(View view) {

        mFocus.lock();

        isContinuousFocus = !isContinuousFocus;

        /**
         * If the current focus point is active (i.e. picked and not cancelled by the user),
         * Restart focus with new focus mode.
         */
        if(binding.vFocusRect.getAlpha() != 0) {

            FocusActionRequest req = CameraCore.getLastRequest();
            req.type = isContinuousFocus ? FocusAction.FOCUS_SERVO : FocusAction.FOCUS_SINGLE;
            isFocused = false;

            CameraCore.focus(req);
        }

        /* Toggle */
        binding.toggleFocusFreq.setToggleState(isContinuousFocus);

        updateInfo(
                /* Base format string ("Current focus mode: %s") */
                String.format(getString(R.string.fmt_focus_mode),

                /* Focus mode ("SERVO" or "ONE SHOT") */
                getString(isContinuousFocus ?
                R.string.activity_camera_focus_continuous
                : R.string.activity_camera_focus_one_shot))

        );

        mFocus.unlock();

        return true;
    }

    @AnyThread
    private void lockViews(View... views) {

        for (View v : views)
            runOnUiThread(() -> v.setClickable(false));

    }

    @AnyThread
    private void unlockViews(View... views) {

        for (View v : views)
            runOnUiThread(() -> v.setClickable(true));
    }

    private void updateButtonColors() {

        final ColorStateList targetState =
                UIHelper.makeCSLwithID(
                        this,
                        isVideoMode ? R.color.colorCameraButton_169 : R.color.colorCameraButton_43);

        runOnUiThread(() -> {
            binding.btnCaptureMode.setBackgroundTintList(targetState);
            binding.btnAwb.setBackgroundTintList(targetState);
            binding.fabSwitchSide.setBackgroundTintList(targetState);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        fullscreenHandler.removeCallbacks(rHideNav);
        fullscreenHandler.postDelayed(rHideNav, 100);

        orientationListener.enable();

        sensorBattery.resume();
        binding.drvRotation.onResume();
        //sensorRotation.resume();

        CameraCore.onResume();

        if (!isAllPermissonGood())
            ActivityCompat.requestPermissions(this, Consts.PERMISSIONS, Consts.PERM_REQUEST_CODE);

    }

    @Override
    protected void onPause() {
        super.onPause();

        orientationListener.disable();

        sensorBattery.hibernate();
        binding.drvRotation.onPause();

        CameraCore.onPause();
    }
}