package com.lbynet.Phokus;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import com.lbynet.Phokus.ui.UIHelper;

public class TestActivity extends AppCompatActivity {

    private View guideOverlay = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);
        setContentView(R.layout.layout_video_viewfinder);
        
        findViewById(R.id.btn_guide).setOnClickListener(this::onGuideClicked);

        guideOverlay = findViewById(R.id.v_guide_overlay);

    }

    boolean onGuideClicked(View v) {

        boolean isVisibile = guideOverlay.getAlpha() != 0;

        UIHelper.setViewAlpha(guideOverlay,100,isVisibile ? 0 : 1);

        return true;

    }
}