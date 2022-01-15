package com.lbynet.phokus.template;

import androidx.camera.video.VideoRecordEvent;

public abstract class VideoEventListener {
    public void onStart(VideoRecordEvent event) {}
    public void onPause(VideoRecordEvent event) {}
    public void onResume(VideoRecordEvent event) {}
    public void onStatus(VideoRecordEvent event) {}
    public void onFinalize(VideoRecordEvent event) {}
}
