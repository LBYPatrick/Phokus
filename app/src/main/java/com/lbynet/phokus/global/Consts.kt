package com.lbynet.phokus.global

import android.Manifest
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class Consts {

    companion object {

        @kotlin.jvm.JvmField
        val PERMISSIONS : Array<String> = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )

        const val PERM_REQUEST_CODE : Int = 114514

        @kotlin.jvm.JvmField
        val EXE_THREAD_POOL : Executor = Executors.newCachedThreadPool()
    }
}