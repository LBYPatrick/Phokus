package com.lbynet.Phokus;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.FragmentTransaction;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.slider.Slider;

import com.lbynet.Phokus.backend.CameraControl;
import com.lbynet.Phokus.backend.Constants;
import com.lbynet.Phokus.listener.BMSListener;
import com.lbynet.Phokus.listener.ColorListener;
import com.lbynet.Phokus.listener.EventListener;
import com.lbynet.Phokus.listener.RotationListener;
import com.lbynet.Phokus.ui.UIHelper;
import com.lbynet.Phokus.utils.MathTools;
import com.lbynet.Phokus.utils.SAL;
import com.lbynet.Phokus.utils.SysInfo;
import com.lbynet.Phokus.utils.Timer;

public class ViewFinderActivity extends AppCompatActivity {

    final public static String TAG = ViewFinderActivity.class.getCanonicalName();

    /**
     * Views
     */
    private View guideOverlay = null,
                 viewRecordRect = null;
    private TextView textRecordStatus = null,
                     textElapsedTime = null,
                     textExposure = null,
                     textZoomData = null,
                     textFpsData = null;
    private SensorInfoFragment fragmentSensorInfo = null;
    private FrameLayout flSensorInfoHolder = null;
    private CardView guideCard = null,
                     infoCard  = null,
                     evCard    = null;
    private PreviewView previewView = null;
    private ImageView recordIcon = null;
    private Slider  evSlider   = null;
    private OrientationEventListener oel = null;

    private boolean isGuideActionDown_ = false,
                    isRecording_ = false,
                    isInfoEnabled_ = false,
                    isEvEnabled_ = false,
                    isFirstSensorInfoAdded = true;
    private static Timer recordTimer = new Timer("Record Timer");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(R.style.AppTheme);
        setContentView(R.layout.layout_video_viewfinder);
            
        setupViews();

        requestPermissions(Constants.PERMISSIONS,1);

        SysInfo.initialize(this);

        oel = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                CameraControl.updateRotation(UIHelper.getSurfaceOrientation(orientation), new EventListener() {});
            }
        };
    }

    public void setupViews() {
        guideCard = findViewById(R.id.card_guide);
        infoCard = findViewById(R.id.card_info);

        recordIcon = findViewById(R.id.data_recording_icon);
        guideOverlay = findViewById(R.id.v_guide_overlay);

        evCard = findViewById(R.id.card_ev);
        evSlider = findViewById(R.id.slider_ev);

        previewView = findViewById(R.id.pv_preview);

        textRecordStatus = findViewById(R.id.data_recording_text);
        textElapsedTime = findViewById(R.id.tv_elapsed_time);
        textExposure = findViewById(R.id.tv_ev_text);
        textZoomData = findViewById(R.id.data_zoom);
        textFpsData = findViewById(R.id.data_fps);

        textZoomData.setOnTouchListener(this::onZoomTouched);
        findViewById(R.id.hint_zoom).setOnTouchListener(this::onZoomTouched);

        textFpsData.setOnTouchListener(this::onFpsTouched);
        findViewById(R.id.hint_fps).setOnTouchListener(this::onFpsTouched);

        guideCard.setOnTouchListener(this::onGuideTouched);
        infoCard.setOnTouchListener(this::onInfoTouched);
        textRecordStatus.setOnTouchListener(this::onRecordTouched);
        recordIcon.setOnTouchListener(this::onRecordTouched);
        evCard.setOnTouchListener(this::onEvTouched);
        evSlider.addOnChangeListener(this::onEvBarChanged);

        viewRecordRect = findViewById(R.id.v_record_rect);

        fragmentSensorInfo = new SensorInfoFragment();
        flSensorInfoHolder = findViewById(R.id.fl_sensor_info);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        for (int i = 0; i < grantResults.length; ++i) {

            boolean isGranted = (grantResults[i] == android.content.pm.PackageManager.PERMISSION_GRANTED);
            SAL.print(TAG,"Permission: " + permissions[i] + "\tGrant status: " + isGranted);

            if (!isGranted) {
                SAL.print(TAG, "Failed to obtain necessary permissions, please try again.");
                finish();
                return;
            }
        }

        onPermissionGranted();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SysInfo.onResume();
        oel.enable(); 
    }

    @Override
    protected void onPause() {
        super.onPause();
        SysInfo.onPause();
        oel.disable();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    void onPermissionGranted() {
        CameraControl.setVideoMode(true);
        CameraControl.initialize(previewView);

        float [] evConfig = CameraControl.getEVConfig();

        evSlider.setValueFrom(evConfig[1]);
        evSlider.setValueTo(evConfig[2]);

        new Thread( () -> {

            while(CameraControl.getLastFocalLength() == 0) SAL.sleepFor(10);

            runOnUiThread( () -> {
                textFpsData.setText(String.format("%d", CameraControl.getVideoFps()));
                textZoomData.setText(String.format("%.1fmm", CameraControl.getLastFocalLength()));
            });
        }).start();
    }

    boolean onGuideTouched(View v, MotionEvent event) {

        if(!UIHelper.hapticFeedback(v,event)) return false;

        boolean isVisibile = guideOverlay.getAlpha() != 0;

        //Update card color
        UIHelper.updateCardColor(guideCard, !isVisibile);

        //Show/hide overlay
        UIHelper.setViewAlpha(guideOverlay,100,isVisibile ? 0 : 1);

        return true;
    }

    boolean onRecordTouched(View v, MotionEvent event) {

        //Do haptic feedback (iOS-like!)
        if(!UIHelper.hapticFeedback(textRecordStatus,event)) return false;

        isRecording_ = !isRecording_;

        int [] colors = UIHelper.getColors(this, R.color.record_inactive,R.color.record_active);
        //Update data_record_icon's color
        UIHelper.getColorAnimator(new ColorListener() {
            @Override
            public void onColorUpdated(int newColor) {
                ImageViewCompat.setImageTintList(recordIcon,ColorStateList.valueOf(newColor));
            }
        }, 100, true, (isRecording_ ? colors : new int[]{colors[1], colors[0]})).start();

        //Update data_record_text's text
        ((TextView) textRecordStatus)
                .setText(isRecording_ ?
                          R.string.data_recording_busy
                        : R.string.data_recording_idle);


        final boolean[] isRecordStatusUpdated = {false};

        //Start recording -- wait until CameraControl actually starts recording -- monitor via EventListener
        CameraControl.toggleRecording(new EventListener() {
            @Override
            public boolean onEventUpdated(String extra) {

                if(extra.equals("START") || extra.equals("STOP")) isRecordStatusUpdated[0] = true;

                return super.onEventUpdated(extra);
            }
        });

        if(isRecording_) {

            new Thread( () -> {
                String [] time = new String[4];

                while(!isRecordStatusUpdated[0]) SAL.sleepFor(1);

                recordTimer.start();

                UIHelper.setViewAlpha(viewRecordRect,100,1);

                while(recordTimer.isBusy()) {
                    SAL.sleepFor(1000 / 100);

                    UIHelper.runLater(this,() -> {
                        MathTools.formatTime(recordTimer.getElaspedTimeInMs(),time);
                        textElapsedTime.setText(time[0] + ":" + time[1] + ":" + time[2] + ":" + time[3]);
                    });
                }

                UIHelper.runLater(this,() -> {
                    MathTools.formatTime(recordTimer.getElaspedTimeInMs(),time);
                    textElapsedTime.setText(time[0] + ":" + time[1] + ":" + time[2] + ":" + time[3]);
                });
            }).start();
        } else {
            //Stop Recording
            new Thread(() -> {

                String [] time = new String[4];
                while(!isRecordStatusUpdated[0]) SAL.sleepFor(1);

                UIHelper.setViewAlpha(viewRecordRect,100,0);

                recordTimer.stop();
                //TODO: Get last filename and stuff, whatever happens after recording stopped, put it here
            }).start();
        }

        return true;
    }

    boolean onInfoTouched(View v, MotionEvent e) {

        if(!UIHelper.hapticFeedback(infoCard,e)) return false;

        isInfoEnabled_ = !isInfoEnabled_;

        UIHelper.updateCardColor(infoCard,isInfoEnabled_);

        if(isFirstSensorInfoAdded && isInfoEnabled_) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.fl_sensor_info,fragmentSensorInfo)
                    .commit();
            isFirstSensorInfoAdded = false;
        }
        else if(isInfoEnabled_) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .show(fragmentSensorInfo)
                    .commit();
        }
        else {
            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .hide(fragmentSensorInfo)
                    .commit();
        }

        return true;
    }

    boolean onEvTouched(View v, MotionEvent e) {

        if(!UIHelper.hapticFeedback(evCard,e)) return false;

        isEvEnabled_ = !isEvEnabled_;

        UIHelper.updateCardColor(evCard,isEvEnabled_);

        UIHelper.setViewAlpha(evSlider,100,isEvEnabled_ ? 1 : 0, true);
        return true;
    }

    boolean onEvBarChanged(@NonNull Slider slider, float value, boolean fromUser) {
        CameraControl.updateEV(value);
        textExposure.setText(String.format("%.2f EV",value));
        return true;
    }

    boolean onZoomTouched(View v, MotionEvent e) {
        if(!UIHelper.hapticFeedback(textZoomData,e)) return false;

        CameraControl.toggleZoom(new EventListener() {
            @Override
            public boolean onEventUpdated(DataType dataType, Object data) {

                if(dataType == DataType.CAM_FOCAL_LENGTH)
                    textZoomData.setText(String.format("%.1fmm",((Float)data).floatValue()));

                return super.onEventUpdated(dataType, data);
            }
        }
        );

        return true;
    }

    boolean onFpsTouched(View v, MotionEvent e) {
        if(!UIHelper.hapticFeedback(textFpsData,e)) return false;

        CameraControl.toggleVideoFps(new EventListener() {
                                     @Override
                                     public boolean onEventUpdated(DataType dataType, Object data) {

                                         if(dataType == DataType.VIDEO_FPS)
                                             textFpsData.setText(String.format("%d",((Integer)data)));

                                         return super.onEventUpdated(dataType, data);
                                     }
                                 }
        );

        return true;
    }

}