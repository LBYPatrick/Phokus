package com.lbynet.phokus.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
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
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.lbynet.phokus.R;
import com.lbynet.phokus.camera.CameraCore;
import com.lbynet.phokus.camera.CameraUtils;
import com.lbynet.phokus.camera.FocusAction;
import com.lbynet.phokus.global.Config;
import com.lbynet.phokus.global.Consts;
import com.lbynet.phokus.global.SysInfo;
import com.lbynet.phokus.template.BatteryListener;
import com.lbynet.phokus.template.EventListener;
import com.lbynet.phokus.template.RotationListener;
import com.lbynet.phokus.ui.widget.ToggleView;
import com.lbynet.phokus.utils.MathTools;
import com.lbynet.phokus.utils.SAL;
import com.lbynet.phokus.utils.Timer;
import com.lbynet.phokus.utils.UIHelper;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

public class CameraActivity extends AppCompatActivity {

    final public static String TAG = CameraActivity.class.getCanonicalName();

    private View root,
            viewRecordRect,
            viewCaptureRect,
            viewFocusRect,
            viewPreviewMask;
    private ImageView ivShutterBase,
                      ivShutterPhoto,
                      ivShutterVideoIdle,
                      ivShutterVideoBusy,
                      ivChevLeft,
                      ivChevRight;
    private Button buttonCaptureMode,
            buttonFocusCancel,
            buttonWhiteBalance,
            buttonExposure;
    private ToggleView toggleFocusFreq;
    private TextView textAperture,
            textFocalLength,
            textExposure,
            textBottomInfo;
    private CardView cardTopInfo,
            cardBottomInfo;
    private FloatingActionButton fabSwitchSide = null;
    private Timer videoTimer = new Timer("Video Timer");
    private OrientationEventListener orientationListener;
    private boolean isVideoMode = false,
            isRecording = false,
            isContinuousFocus = false,
            isFocused = false,
            isZooming = false;

    static int[] previewDimensions = null;
    static String bottomInfo;

    final private Runnable
            rHideBottomInfo = () -> UIHelper.setViewAlpha(cardBottomInfo, 200, 0, true),
            rShowBottomInfo = () -> UIHelper.setViewAlpha(cardBottomInfo, 10, 1, true),
            rShowTopInfo = () -> UIHelper.setViewAlpha(cardTopInfo, 10, 1, true),
            rFadeTopInfo = () -> UIHelper.setViewAlpha(cardTopInfo, 50, 0.5f, true),
            rOnShutterPressed = () -> {

                SAL.simulatePress(this, false);

                if (isVideoMode) {

                    ivShutterVideoIdle.animate()
                            .scaleX(0)
                            .scaleY(0)
                            .setDuration(500)
                            .setInterpolator(new OvershootInterpolator())
                            .start();

                    //UIHelper.setViewAlpha(ivShutterVideoIdle, 50, 1, true);
                } else {
                    ivShutterPhoto.animate()
                            .scaleX(0.95f)
                            .scaleY(0.95f)
                            .alpha(0.5f)
                            .setDuration(50)
                            .start();

                }

            },
    rOnShutterReleased = () -> {

        SAL.simulatePress(this, true);

        if (isVideoMode) {
            ivShutterVideoIdle.animate()
                    .scaleX(1)
                    .scaleY(1)
                    .setInterpolator(new OvershootInterpolator())
                    .setDuration(300)
                    .start();
        } else {
            /**
             * release shutter button -- make it big again
             */
            ivShutterPhoto.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(50)
                    .start();

            /**
             * Fade capture rectangle out
             */
            viewCaptureRect.animate()
                    .alpha(0)
                    .setDuration(1000)
                    .start();
        }
    },
            rHideNav = () -> {
                root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            };


    private Handler animationHandler = new Handler(),
            fullscreenHandler = new Handler();
    private EventListener focusListener = new EventListener() {
        @Override
        public boolean onEventUpdated(DataType dataType, Object data) {

            switch ((String) data) {

                case FocusAction.MSG_BUSY:

                    if (isFocused) break;

                    UIHelper.setViewAlpha(viewFocusRect, 50, 0.5f);

                    buttonFocusCancel.post(() -> {
                        buttonFocusCancel.setClickable(true);
                        buttonFocusCancel.animate()
                                .alpha(1)
                                .setDuration(200)
                                .start();
                    });

                    /**
                     * Make focus rectangle grey when AF is busy
                     */
                    viewFocusRect.setForegroundTintList(UIHelper.makeCSLwithID(requireContext(), R.color.colorPrimaryDark));
                    break;

                case FocusAction.MSG_SUCCESS:
                    /**
                     * Make focus rectangle green/blue(depending on the AF mode) when AF succeeds.
                     */
                    UIHelper.setViewAlpha(viewFocusRect, 50, 1);
                    viewFocusRect.setForegroundTintList(UIHelper.makeCSLwithID(requireContext(), (isContinuousFocus ? R.color.colorFocusContinuous : R.color.colorFocusOneShot)));
                    isFocused = true;
            }

            return super.onEventUpdated(dataType, data);
        }
    };
    private float currZoomRatio = 1;

    private PreviewView preview;
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

            wakeTopInfo();

            CameraCore.zoomByRatio(currZoomRatio, new EventListener() {
                @SuppressLint("DefaultLocale")
                @Override
                public boolean onEventUpdated(DataType dataType, Object data) {

                    if (dataType != DataType.FLOAT_CAM_FOCAL_LENGTH) return false;

                    UIHelper.runLater(requireContext(), () -> {

                        textFocalLength.setText(String.format("%.2fmm", (Float) data));
                        updateBottomInfo(String.format("Scale factor: %.2fx", currZoomRatio));

                        int[] colors = UIHelper.getColors(requireContext(), R.color.colorText, R.color.colorPrimary);
                        textFocalLength.setTextColor(currZoomRatio == 1 ? colors[0] : colors[1]);

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
        setContentView(R.layout.activity_camera);
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

        root = findViewById(R.id.cl_camera);
        preview = findViewById(R.id.pv_preview);
        textAperture = findViewById(R.id.tv_aperture);
        textBottomInfo = findViewById(R.id.tv_bottom_info);
        textExposure = findViewById(R.id.tv_exposure);
        textFocalLength = findViewById(R.id.tv_focal_length);
        cardTopInfo = findViewById(R.id.cv_top_info);
        cardBottomInfo = findViewById(R.id.cv_bottom_info);
        ivShutterBase = findViewById(R.id.iv_shutter_base);
        ivShutterPhoto = findViewById(R.id.v_shutter_photo);
        ivShutterVideoIdle = findViewById(R.id.iv_shutter_video_idle);
        ivShutterVideoBusy = findViewById(R.id.iv_shutter_video_busy);
        ivChevLeft = findViewById(R.id.iv_preview_chev_left);
        ivChevRight = findViewById(R.id.iv_preview_chev_right);

        viewRecordRect = findViewById(R.id.v_record_rect);
        viewFocusRect = findViewById(R.id.v_focus_rect);
        viewCaptureRect = findViewById(R.id.v_capture_rect);

        pToZDetector = new ScaleGestureDetector(requireContext(), pToZListener);
        ivShutterBase.setOnTouchListener(this::onShutterTouched);

        buttonCaptureMode = findViewById(R.id.btn_capture_mode);
        buttonFocusCancel = findViewById(R.id.btn_focus_cancel);
        toggleFocusFreq = findViewById(R.id.toggle_focus_freq);
        buttonExposure = findViewById(R.id.btn_exposure);
        buttonWhiteBalance = findViewById(R.id.btn_awb);
        fabSwitchSide = findViewById(R.id.fab_switch_side);

        buttonCaptureMode.setOnClickListener(this::toggleVideoMode);
        buttonFocusCancel.setOnClickListener(this::cancelFocus);
        toggleFocusFreq.setOnClickListener(this::toggleFocusFreqMode);
        fabSwitchSide.setOnClickListener(this::toggleCameraFacing);
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

        CameraCore.setStatusListener_(new EventListener() {
            @Override
            public boolean onEventUpdated(DataType dataType, Object extra) {
                switch (dataType) {
                    case VOID_CAMERA_BINDING:
                        runOnUiThread(() -> {
                            lockButtons(buttonCaptureMode,
                                    fabSwitchSide);
                        });
                        break;
                    case VOID_CAMERA_BOUND:
                        runOnUiThread(() -> {
                            unlockButtons(buttonCaptureMode,
                                    fabSwitchSide);
                        });
                        break;
                    default:
                        break;
                }

                return super.onEventUpdated(dataType, extra);
            }
        });

        CameraCore.start(preview);
        resetLensInfo();

        /**
         * Configure preview params for handling user interactions (MUST be this late rather than at bindViews())
         * DEBUGGED
         */
        preview.setOnTouchListener(this::onPreviewTouched);

        preview.post(() -> {
            new Thread(() -> {
                SAL.sleepFor(150);
                UIHelper.queryViewDimensions(preview, new EventListener() {
                    @Override
                    public boolean onEventUpdated(DataType dataType, Object extra) {
                        previewDimensions = (int[]) extra;
                        return true;
                    }
                });
            }).start();
        });

        SysInfo.initialize(this);

        SysInfo.addListener(new BatteryListener() {
            @Override
            public void onDataAvailable(Intent batteryIntent) {
                SAL.print("Battery Level: " + batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1));
            }
        });

        SysInfo.addListener(new RotationListener() {
            @Override
            public void onDataAvailable(float azimuth, float pitch, float roll) {

                runOnUiThread( () -> viewFocusRect.setRotation((float)MathTools.radianToDegrees(pitch,true)));
                //SAL.print(azimuth + ", " + pitch + ", " + roll);
            }
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

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) viewFocusRect.getLayoutParams();
            params.setMargins((int) x - 50, (int) y - 50, 0, 0);
            viewFocusRect.setLayoutParams(params);

            isFocused = false;

            CameraCore.focusToPoint(x, y, isContinuousFocus, focusListener);
        }

        return true;
    }

    @SuppressLint("DefaultLocale")
    public void resetLensInfo() {
        int camera_id = (Boolean) Config.get(Config.FRONT_FACING) ? 1 : 0;

        textAperture.setText(String.format("F/%.2f", CameraUtils.get35Aperture(this, camera_id)));
        textAperture.setTextColor(UIHelper.getColors(this, R.color.colorSecondary)[0]);

        textFocalLength.setText(
                String.format("%.2fmm",
                        CameraUtils.get35FocalLength(
                                requireContext(),
                                CameraCore.isFrontFacing() ? 1 : 0)));

        currZoomRatio = 1;

        textFocalLength.setTextColor(UIHelper.getColors(requireContext(), R.color.colorText)[0]);

        wakeTopInfo();
    }

    public boolean onShutterTouched(View v, MotionEvent event) {

        if (event.getAction() != MotionEvent.ACTION_DOWN) return false;

        requireExecutor().execute(rOnShutterPressed);

        if (isVideoMode && !isRecording) {

            isRecording = true;

            UIHelper.setViewAlpha(viewRecordRect, 200, 1);

            CameraCore.startRecording(new EventListener() {
                @Override
                public boolean onEventUpdated(DataType dataType, Object data) {

                    /**
                     * This event would only be called when the video is saved.
                     */
                    if (dataType != DataType.URI_VIDEO_SAVED) return false;

                    requireExecutor().execute(rOnShutterReleased);

                    UIHelper.setViewAlpha(viewRecordRect, 200, 0);

                    return super.onEventUpdated(dataType, data);
                }
            });

            startVideoTimer();
        } else if (isVideoMode) {
            CameraCore.stopRecording();
            stopVideoTimer();
            isRecording = false;
        } else {

            CameraCore.pauseFocus();

            viewCaptureRect.animate()
                    .alpha(1)
                    .setDuration(10)
                    .start();

            animationHandler.postDelayed(rOnShutterReleased, 100);

            CameraCore.takePicture(new EventListener() {
                @Override
                public boolean onEventUpdated(DataType dataType, Object data) {

                    CameraCore.resumeFocus();

                    if (dataType != DataType.URI_PICTURE_SAVED) return false;
                    runOnUiThread(() -> updateBottomInfo("Picture saved!"));

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

            UIHelper.resizeView(preview,
                    previewDimensions,
                    new int[]{targetWidth, previewDimensions[1]},
                    200,
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

        updatePreviewSize();
        updateButtonColors();
        resetLensInfo();

        animationHandler.postDelayed(CameraCore::updateVideoMode, 200);

        /**
         * Update shutter button color
         */
        int [] shutterBaseColors = UIHelper.getColors(this,R.color.colorShutterBasePhoto,R.color.colorShutterBaseVideo);

        UIHelper.setImageViewTint(ivShutterBase,
                100,
                isVideoMode ? shutterBaseColors[0] : shutterBaseColors[1],
                isVideoMode ? shutterBaseColors[1] : shutterBaseColors[0],
                UIHelper.INTRPL_LINEAR);

        UIHelper.setViewAlpha(ivShutterVideoIdle,100,isVideoMode? 1.0f:0);
        UIHelper.setViewAlpha(ivShutterVideoBusy,isVideoMode ? 100 : 0,isVideoMode? 1.0f:0);
        UIHelper.setViewAlpha(ivShutterPhoto,100,isVideoMode ? 0 : 1.0f);

        return true;
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

            while (videoTimer.isBusy()) {

                //Limit timer refresh rate to be roughly 10fps
                while (videoTimer.isBusy() && videoTimer.getElaspedTimeInMs() % 100 != 0) {
                    SAL.sleepFor(1);
                }

                MathTools.formatTime(videoTimer.getElaspedTimeInMs(), time);
                StringBuilder sb = new StringBuilder().append(time[0]);

                for (int i = 1; i < 3; ++i) {
                    sb.append(':').append(time[i]);
                }

                updateBottomInfo(sb.toString());
            }

            //Extra run after loop
            MathTools.formatTime(videoTimer.getElaspedTimeInMs(), time);
            StringBuilder sb = new StringBuilder().append(time[0]);

            for (int i = 1; i < 3; ++i) {
                sb.append(':').append(time[i]);
            }

            updateBottomInfo(sb.toString());

        }).start();
    }

    private void stopVideoTimer() {

        videoTimer.stop();

    }

    private void wakeTopInfo() {

        animationHandler.removeCallbacks(rFadeTopInfo);
        ContextCompat.getMainExecutor(this).execute(rShowTopInfo);
        animationHandler.postDelayed(rFadeTopInfo, 2000);
    }

    private void updateBottomInfo(String newInfo) {

        bottomInfo = newInfo;

        ContextCompat.getMainExecutor(this).execute(() -> textBottomInfo.setText(newInfo));
        wakeBottomInfo();

    }

    private void wakeBottomInfo() {
        animationHandler.removeCallbacks(rHideBottomInfo);
        ContextCompat.getMainExecutor(this).execute(rShowBottomInfo);
        animationHandler.postDelayed(rHideBottomInfo, 4000);
    }

    private boolean cancelFocus(View view) {

        CameraCore.interruptFocus();

        buttonFocusCancel.setClickable(false);

        buttonFocusCancel.animate()
                .alpha(0)
                .setDuration(50)
                .start();

        UIHelper.setViewAlpha(viewFocusRect, 50, 0);

        return true;

    }

    private boolean toggleCameraFacing(View view) {

        boolean curr = (Boolean) Config.get(Config.FRONT_FACING);

        Config.set(Config.FRONT_FACING, !curr);

        //fabSwitchSide.setClickable(false);
        //fabSwitchSide.setEnabled(false);

        fabSwitchSide.animate()
                .rotationBy(curr ? -180.0f : 180.0f)
                .setInterpolator(new OvershootInterpolator())
                .setDuration(500)
                .start();

        cancelFocus(null);
        CameraCore.start(preview);
        resetLensInfo();

        return true;
    }

    private boolean toggleFocusFreqMode(View view) {

        isContinuousFocus = !isContinuousFocus;
        cancelFocus(null);

        String focusModeText = getString(isContinuousFocus ?
                R.string.activity_camera_focus_continuous
                : R.string.activity_camera_focus_one_shot);

        /**
         * Toggle
         */
        toggleFocusFreq.setToggleState(isContinuousFocus);

        updateBottomInfo(String.format(getString(R.string.fmt_focus_mode), focusModeText));

        return true;
    }


    private void lockButtons(View... buttons) {

        for (View b : buttons) b.setClickable(false);
    }

    private void unlockButtons(View... buttons) {

        for (View b : buttons) b.setClickable(true);
    }

    private void updateButtonColors() {

        final ColorStateList targetState =
                UIHelper.makeCSLwithID(
                        this,
                        isVideoMode ? R.color.colorCameraButton_169 : R.color.colorCameraButton_43);

        root.post(() -> {
            buttonCaptureMode.setBackgroundTintList(targetState);
            buttonExposure.setBackgroundTintList(targetState);
            buttonWhiteBalance.setBackgroundTintList(targetState);
            fabSwitchSide.setBackgroundTintList(targetState);
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        
        fullscreenHandler.removeCallbacks(rHideNav);
        fullscreenHandler.postDelayed(rHideNav, 100);
        orientationListener.enable();

        SysInfo.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        orientationListener.disable();
        SysInfo.onPause();
    }
}