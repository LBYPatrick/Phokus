package com.lbynet.phokus.camera

import android.media.Image
import kotlin.jvm.Synchronized
import com.lbynet.phokus.camera.AnalysisResult

object AnalysisResult {

    private var prev: Image? = null
    var is_paused = false


    @Synchronized
    fun put(proxy: Image?) {
        if (is_paused) return
        prev = proxy
    }

    @Synchronized
    fun get(): Image? {
        is_paused = true
        return prev
    }

    @Synchronized
    fun unlock() {
        is_paused = false
    }
}