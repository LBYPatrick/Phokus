package com.lbynet.Phokus;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ImageViewCompat;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
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

public class TestActivity extends AppCompatActivity {

    final public static String TAG = TestActivity.class.getCanonicalName();

    /**
     * Views
     */
    private View guideOverlay = null;
    private TextView recordText = null,
                     elapsedTimeText = null;
    private CardView guideCard = null,
                     infoCard  = null,
                     evCard    = null;
    private PreviewView previewView = null;
    private ImageView recordIcon = null;
    private Slider  evSlider   = null;

    private boolean isGuideActionDown_ = false,
                    isRecording_ = false,
                    isInfoEnabled_ = false,
                    isEvEnabled_ = false;
    private static Timer recordTimer = new Timer("Record Timer");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(R.style.AppTheme);
        setContentView(R.layout.layout_video_viewfinder);
            
        guideCard = findViewById(R.id.card_guide);
        guideCard.setOnTouchListener(this::onGuideTouched);

        infoCard = findViewById(R.id.card_info);
        infoCard.setOnTouchListener(this::onInfoTouched);


        recordText = findViewById(R.id.data_recording_text);
        recordIcon = findViewById(R.id.data_recording_icon);

        recordText.setOnTouchListener(this::onRecordTouched);
        recordIcon.setOnTouchListener(this::onRecordTouched);

        guideOverlay = findViewById(R.id.v_guide_overlay);
        elapsedTimeText = findViewById(R.id.tv_record_time);

        evCard = findViewById(R.id.card_ev);
        evSlider = findViewById(R.id.slider_ev);
        evCard.setOnTouchListener(this::onEvTouched);
        evSlider.addOnChangeListener(this::onEvBarChanged);

        previewView = findViewById(R.id.pv_preview);

        requestPermissions(Constants.PERMISSIONS,1);

        SysInfo.initialize(this);

        SysInfo.addListener(new BMSListener() {
            @Override
            public boolean onUpdate(Intent intent) {
                return super.onUpdate(intent);
            }
        });

        SysInfo.addListener(new RotationListener() {
            @Override
            public boolean onUpdate(float[] data) {
                return super.onUpdate(data);
            }
        });
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        SysInfo.onPause();
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
        if(!UIHelper.hapticFeedback(recordText,event)) return false;

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
        ((TextView)recordText)
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

                while(recordTimer.isBusy()) {
                    SAL.sleepFor(1000 / 100);

                    UIHelper.runLater(this,() -> {
                        MathTools.formatTime(recordTimer.getElaspedTimeInMs(),time);
                        elapsedTimeText.setText(time[0] + ":" + time[1] + ":" + time[2] + ":" + time[3]);
                    });
                }

                UIHelper.runLater(this,() -> {
                    MathTools.formatTime(recordTimer.getElaspedTimeInMs(),time);

                    elapsedTimeText.setText(time[0] + ":" + time[1] + ":" + time[2] + ":" + time[3]);
                });
            }).start();
        } else {
            //Stop Recording
            new Thread(() -> {

                String [] time = new String[4];
                while(!isRecordStatusUpdated[0]) SAL.sleepFor(1);

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
        //TODO: Finish this
        return true;
    }
}