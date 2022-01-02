package com.lbynet.phokus.ui;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import android.widget.ImageView;

import com.lbynet.phokus.R;
import com.lbynet.phokus.camera.CameraCore;
import com.lbynet.phokus.camera.CameraUtils;
import com.lbynet.phokus.camera.FocusAction;
import com.lbynet.phokus.camera.FocusAction.FocusActionRequest;
import com.lbynet.phokus.databinding.ActivityCameraBinding;
import com.lbynet.phokus.global.Config;
import com.lbynet.phokus.global.Consts;
import com.lbynet.phokus.hardware.BatterySensor;
import com.lbynet.phokus.hardware.RotationSensor;
import com.lbynet.phokus.template.EventListener;
import com.lbynet.phokus.template.FocusActionListener;
import com.lbynet.phokus.template.RotationListener;
import com.lbynet.phokus.utils.MathTools;
import com.lbynet.phokus.utils.SAL;
import com.lbynet.phokus.utils.Timer;
import com.lbynet.phokus.utils.UIHelper;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;

public class CameraActivity extends AppCompatActivity {

    final public static String TAG = CameraActivity.class.getCanonicalName();
    final public static int DUR_ANIM_PREVIEW_RESIZE = 400,
                            DUR_ANIM_SHUTTER = 300;

    private ActivityCameraBinding binding;
    private Timer videoTimer = new Timer("Video Timer");
    private OrientationEventListener orientationListener;
    private boolean isVideoMode = false,
            isRecording = false,
            isContinuousFocus = false,
            isFocused = false,
            isZooming = false;

    static int[] previewDimensions = null;
    static String bottomInfo;
    static BatterySensor batterySensor;
    static RotationSensor rotationSensor;

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

    final private static int STATE_PHOTO_IDLE = 0, STATE_PHOTO_PRESS = 1, STATE_PHOTO_RELEASE = 2, STATE_VIDEO_IDLE = 3, STATE_VIDEO_BUSY = 4,STATE_VIDEO_STOP = 5;


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


    private Handler animationHandler = new Handler(),
            fullscreenHandler = new Handler();

    final private FocusActionListener listener_focus_ = new FocusActionListener() {
        @Override
        public void onFocusEnd(FocusAction.FocusActionResult res) {

            //SAL.print(String.format("Focus succeeded!\n type: %d",res.type));

            runOnUiThread( ()-> {
                //Routine for FOCUS_AUTO
                if (res.type == FocusAction.FOCUS_AUTO) {
                    binding.btnCancelFocus.setClickable(false);

                    binding.btnCancelFocus.animate()
                            .alpha(0)
                            .setDuration(50)
                            .start();

                    showAfOverlay();

                    UIHelper.setViewAlpha(50, 0, binding.vFocusRect);
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
            });
        }

        @Override
        public void onFocusBusy(FocusActionRequest req) {

            //SAL.print(String.format("Focus is busy...\n type: %d, isFocused: %s",req.type,isFocused ? "True" : "False"));

            if (isFocused || req.type == FocusAction.FOCUS_AUTO) return;

            runOnUiThread( ()-> {

                UIHelper.setViewAlpha(50, 0.5f, binding.vFocusRect);

                ImageView btn = binding.btnCancelFocus;

                btn.post(() -> {
                    btn.setClickable(true);
                    btn.animate()
                            .alpha(1)
                            .setDuration(200)
                            .start();
                });

                /**
                 * Make focus rectangle grey when AF is busy
                 */
                binding.vFocusRect.setForegroundTintList(UIHelper.makeCSLwithID(requireContext(), R.color.colorPrimaryDark));
            });

        }
    };

    private float currZoomRatio = 1;

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
    private ScaleGestureDetector pToZDetector = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        /**
         * Activity Setup
         */
        super.onCreate(savedInstanceState);
        binding = ActivityCameraBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());
        bindViews();
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
        if (allPermissionsGood())
            startCamera();
        else
            ActivityCompat.requestPermissions(this, Consts.PERMISSIONS, Consts.PERM_REQUEST_CODE);

    }

    public void bindViews() {

        pToZDetector = new ScaleGestureDetector(requireContext(), pToZListener);

        binding.ivShutterPhotoBase.setOnTouchListener(this::onShutterTouched);
        binding.btnCaptureMode.setOnClickListener(this::toggleVideoMode);

        binding.btnCancelFocus.setOnClickListener(this::cancelFocus);
        //binding.btnFocusCancel.setOnClickListener(this::cancelFocus);
        binding.toggleFocusFreq.setOnClickListener(this::toggleFocusFreqMode);
        binding.fabSwitchSide.setOnClickListener(this::toggleCameraFacing);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull @NotNull String[] permissions,
                                           @NonNull @NotNull int[] grantResults) {

        if (requestCode == Consts.PERM_REQUEST_CODE && allPermissionsGood()) startCamera();
        else {
            UIHelper.printSystemToast(this, "Not all permissions were granted.", false);
            finish();
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public boolean allPermissionsGood() {
        for (String p : Consts.PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_DENIED)
                return false;
        }
        return true;
    }

    @SuppressLint("DefaultLocale")
    public void startCamera() {

        SAL.print("Starting camera");
        CameraCore.initialize();

        View [] viewArray = {binding.btnCaptureMode,binding.fabSwitchSide};

        CameraCore.setStatusListener_(new EventListener() {
            @Override
            public boolean onEventUpdated(DataType dataType, Object extra) {
                switch (dataType) {
                    case VOID_CAMERA_BINDING:
                        runOnUiThread(() -> lockViews(viewArray));
                        break;
                    case VOID_CAMERA_BOUND:
                        runOnUiThread(() -> unlockViews(viewArray));
                        break;
                    default:
                        break;
                }

                return super.onEventUpdated(dataType, extra);
            }
        });

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
                SAL.sleepFor(150);
                UIHelper.queryViewDimensions(binding.cvPreviewWrapper, (width, height) -> {
                    previewDimensions = new int[]{width,height};
                });
            }).start();
        });

        showAfOverlay();



        batterySensor = new BatterySensor(this, batteryIntent -> {
            SAL.print("Battery Level: " + batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1));
        });


        rotationSensor = new RotationSensor(this,
                (RotationListener) (azimuth, pitch, roll) -> {
            runOnUiThread( () -> {
                binding.drvRotation
                        .setHorizontalAngle((float) MathTools.radianToDegrees(pitch, false))
                        .setVerticalAngle((float) MathTools.radianToDegrees(roll,false) + 90);


            });

            //SAL.print("Angle: " + MathTools.radianToDegrees(pitch,false));

        });

    }

    // Credits to Saurabh Thorat:
    // https://stackoverflow.com/questions/63202209/camerax-how-to-add-pinch-to-zoom-and-tap-to-focus-onclicklistener-and-ontouchl
    public boolean onPreviewTouched(View v, MotionEvent event) {

        //Pinch-to-zoom
        float x = event.getX(),
                y = event.getY();

        if(event.getPointerCount() > 1) pToZDetector.onTouchEvent(event);
        //Tap-to-focus
        else if (event.getAction() == MotionEvent.ACTION_DOWN && !isZooming) {

            if(binding.btnCancelFocus.getAlpha() == 0) hideAfOverlay();

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) binding.vFocusRect.getLayoutParams();
            params.setMargins((int) x - 80, (int) y - 80, 0, 0);
            binding.vFocusRect.setLayoutParams(params);

            isFocused = false;

            CameraCore.focus(
                    new FocusActionRequest(
                            isContinuousFocus ? FocusAction.FOCUS_SERVO : FocusAction.FOCUS_SINGLE,
                            new float [] {x,y}
                            )
            );
        }

        return true;
    }

    @SuppressLint("DefaultLocale")
    public void resetLensInfo() {
        int camera_id = (Boolean) Config.get(Config.FRONT_FACING) ? 1 : 0;

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

    public boolean onShutterTouched(View v, MotionEvent event) {

        if (event.getAction() != MotionEvent.ACTION_DOWN) return false;

        requireExecutor().execute(rOnShutterPressed);

        if (isVideoMode && !isRecording) {

            isRecording = true;

            CameraCore.startRecording(new EventListener() {
                @Override
                public boolean onEventUpdated(DataType dataType, Object data) {

                    /**
                     * This event would only be called when the video is saved.
                     */
                    if (dataType != DataType.URI_VIDEO_SAVED) return false;
                    requireExecutor().execute(rOnShutterReleased);

                    return super.onEventUpdated(dataType, data);
                }
            });
            startVideoTimer();

        } else if (isVideoMode) {
            CameraCore.stopRecording();
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
        if (isVideoMode && isRecording) {
            CameraCore.stopRecording();
            stopVideoTimer();
            isRecording = false;
        }

        isVideoMode = !isVideoMode;

        /**
         * Cancel focus point(Because FocusAction will go NUTS when the camera is not active)
         * Maybe I could figure out a way such that FocusAction runs smarter
         * without major performance penalty.
         */

        cancelFocus(null);

        Config.set(Config.VIDEO_MODE, isVideoMode);

        /* Communicate with the backend */
        animationHandler.postDelayed(CameraCore::updateVideoMode,  0);

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

        final boolean isAfOverlayVisible = binding.ivAfOverlay.getAlpha() != 0;

        UIHelper.setViewAlphas(
                100,
                1,
                showGroup);

        UIHelper.setViewAlphas(100,0,hideGroup);
        if(isAfOverlayVisible) hideAfOverlay();

        updateShutterState(isVideoMode ? STATE_VIDEO_IDLE : STATE_PHOTO_IDLE);

        animationHandler.postDelayed(() -> {

            updateButtonColors();

            UIHelper.setViewAlphas(200,0,showGroup);
            UIHelper.setViewAlphas(200,1,hideGroup);

            if(isAfOverlayVisible) showAfOverlay();

        }, DUR_ANIM_PREVIEW_RESIZE);


        return true;
    }

    private void showAfOverlay() {

        if(binding.ivAfOverlay.getAlpha() != 0) return;

        binding.ivAfOverlay.setScaleX(1.1f);
        binding.ivAfOverlay.setScaleY(1.1f);

        binding.ivAfOverlay.animate()
                .scaleY(1f)
                .scaleX(1f)
                .setDuration(300)
                .alpha(1)
                .setInterpolator(UIHelper.getInterpolator(UIHelper.INTRPL_DECEL))
                .start();
    }

    private void hideAfOverlay() {

        if(binding.ivAfOverlay.getAlpha() != 1) return;

        binding.ivAfOverlay.animate()
                .scaleY(1.1f)
                .scaleX(1.1f)
                .setDuration(300)
                .alpha(0)
                .setInterpolator(UIHelper.getInterpolator(UIHelper.INTRPL_ACCEL))
                .start();

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

    private void stopVideoTimer() {

        videoTimer.stop();

    }

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

        /* Update global config */
        boolean curr = (Boolean) Config.get(Config.FRONT_FACING);
        Config.set(Config.FRONT_FACING, !curr);

        /* Rotate the switch side FAB */
        binding.fabSwitchSide.animate()
                .rotationBy(curr ? -180.0f : 180.0f)
                .setInterpolator(new OvershootInterpolator())
                .setDuration(500)
                .start();

        /* Cancel focus */
        cancelFocus(null);

        /* Restart CameraCore */
        CameraCore.start(binding.preview);

        /* Reset lens info displayed in the top info card (aperture/focal length) */
        resetLensInfo();

        return true;
    }

    private boolean toggleFocusFreqMode(View view) {

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

        return true;
    }


    private void lockViews(View... views) {

        for (View v : views) v.setClickable(false);
    }

    private void unlockViews(View... views) {

        for (View v : views) v.setClickable(true);
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

        batterySensor.resume();
        rotationSensor.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        orientationListener.disable();

        batterySensor.hibernate();
        rotationSensor.hibernate();
    }
}