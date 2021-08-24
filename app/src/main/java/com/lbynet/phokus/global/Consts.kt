package com.lbynet.phokus.global

import android.Manifest

class Consts {

    companion object {

        @kotlin.jvm.JvmField
        val PERMISSIONS : Array<String> = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )

        const val PERM_REQUEST_CODE : Int = 114514
    }
}