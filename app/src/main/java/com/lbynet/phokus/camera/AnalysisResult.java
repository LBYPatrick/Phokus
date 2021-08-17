package com.lbynet.phokus.camera;

import android.media.Image;

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
