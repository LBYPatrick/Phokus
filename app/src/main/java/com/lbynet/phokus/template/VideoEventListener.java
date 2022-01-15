package com.lbynet.phokus.template;

import androidx.annotation.AnyThread;
import androidx.camera.video.VideoRecordEvent;

public abstract class VideoEventListener {
    @AnyThread public void onStart(VideoRecordEvent event) {}
    @AnyThread public void onPause(VideoRecordEvent event) {}
    @AnyThread public void onResume(VideoRecordEvent event) {}
    @AnyThread public void onStatus(VideoRecordEvent event) {}
    @AnyThread public void onFinalize(VideoRecordEvent event) {}
}
