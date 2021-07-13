package com.lbynet.Phokus.camera;

import android.media.Image;

import androidx.camera.core.ImageProxy;

import com.lbynet.Phokus.utils.SAL;

public class AnalysisResult {

    private static Image prev = null;
    public static boolean is_paused = false;


    public static synchronized void put(Image proxy) {

        if(is_paused) return;

        prev = proxy;
    }

    public static synchronized Image get() {
        is_paused = true;
        return prev;
    }

    public static synchronized void unlock() {
        is_paused = false;

    }


}
