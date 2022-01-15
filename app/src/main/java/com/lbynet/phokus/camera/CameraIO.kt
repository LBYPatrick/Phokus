package com.lbynet.phokus.camera

import androidx.camera.core.VideoCapture
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.lbynet.phokus.camera.CameraIO
import androidx.camera.core.ImageCapture
import androidx.camera.video.MediaStoreOutputOptions
import java.lang.StringBuilder
import java.text.SimpleDateFormat

object CameraIO {
    private var num_simultaneous_images_ = 0
    private var lastImageTime: Long = -1

    @Deprecated("This was required by the legacy VideoCapture and hence is no longer needed")
    fun getVideoOFO(context: Context, filename: String): VideoCapture.OutputFileOptions {

        val extension = filename.substring(filename.lastIndexOf('.') + 1, filename.length)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.TITLE, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/$extension")
        }

        return VideoCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()
    }

    @JvmStatic
    fun getVideoMso(context : Context, filename: String): MediaStoreOutputOptions {

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME,filename)
        }

        return MediaStoreOutputOptions.Builder(context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()
    }

    @JvmStatic
    fun getVideoMso(context : Context): MediaStoreOutputOptions {
        return getVideoMso(context, videoFilename)
    }

    @JvmStatic
    fun getVideoOFO(context: Context): VideoCapture.OutputFileOptions {
        return getVideoOFO(context, videoFilename)
    }

    //Prefix
    //Time
    //Suffix (extension)
    val videoFilename: String
        get() = StringBuilder()
            .append("VID_") //Prefix
            .append(SimpleDateFormat("yyyyLLdd_HHmmss").format(System.currentTimeMillis())) //Time
            .append(".mp4") //Suffix (extension)
            .toString()
    @JvmStatic
    val photoFilename: String
        get() {
            val timestamp = System.currentTimeMillis()
            val time =
                SimpleDateFormat("yyyyLLdd_HHmmss").format(timestamp)
            val sb =
                StringBuilder().append("IMG_").append(time)
            if (timestamp == lastImageTime) {
                num_simultaneous_images_ += 1
                sb.append("_" + Integer.toString(num_simultaneous_images_))
            } else {
                num_simultaneous_images_ = 0
            }
            lastImageTime = timestamp
            return sb.append(".jpg").toString()
        }

    @JvmStatic
    fun getImageOFO(context: Context, filename: String): ImageCapture.OutputFileOptions {
        val values = ContentValues()
        val extension = filename.substring(filename.lastIndexOf('.') + 1)

        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        values.put(MediaStore.MediaColumns.TITLE, filename)
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/$extension")

        return ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()
    }

    @JvmStatic
    fun getImageOFO(context: Context): ImageCapture.OutputFileOptions {
        return getImageOFO(context, photoFilename)
    }
}