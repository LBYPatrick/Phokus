package com.lbynet.Phokus;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ImageViewCompat;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.lbynet.Phokus.listener.ColorListener;
import com.lbynet.Phokus.ui.UIHelper;
import com.lbynet.Phokus.utils.SAL;

public class TestActivity extends AppCompatActivity {

    private View guideOverlay = null,
                 recordText = null;
    private ImageView recordIcon = null;
    private boolean isGuideActionDown_ = false,
                    isRecording_ = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);
        setContentView(R.layout.layout_video_viewfinder);
            
        findViewById(R.id.btn_guide).setOnTouchListener(this::onGuideTouched);

        recordText = findViewById(R.id.data_recording_text);
        recordIcon = findViewById(R.id.data_recording_icon);

        recordText.setOnTouchListener(this::onRecordTouched);
        recordIcon.setOnTouchListener(this::onRecordTouched);

        guideOverlay = findViewById(R.id.v_guide_overlay);

    }

    @Override
    protected void onStart() {
        super.onStart();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    boolean onGuideTouched(View v, MotionEvent event) {

        if(!UIHelper.hapticFeedback(v,event)) return false;

        boolean isVisibile = guideOverlay.getAlpha() != 0;

        UIHelper.setViewAlpha(guideOverlay,100,isVisibile ? 0 : 1);

        return true;
    }

    boolean onRecordTouched(View v, MotionEvent event) {

        if(!UIHelper.hapticFeedback(recordText,event)) return false;

        isRecording_ = !isRecording_;

        int [] colors = UIHelper.getColors(this, R.color.record_inactive,R.color.record_active);

        UIHelper.getColorAnimator(new ColorListener() {
            @Override
            public void onColorUpdated(int newColor) {
                ImageViewCompat.setImageTintList(recordIcon,ColorStateList.valueOf(newColor));
            }
        }, 100, true, (isRecording_ ? colors : new int[]{colors[1], colors[0]})).start();

        ((TextView)recordText)
                .setText(isRecording_ ?
                          R.string.data_recording_busy
                        : R.string.data_recording_idle);

        return true;

    }
}