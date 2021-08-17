package com.lbynet.phokus.camera;

import android.content.ContentValues;
import android.content.Context;
import android.provider.MediaStore;

import androidx.camera.core.ImageCapture;
import androidx.camera.core.VideoCapture;

import java.text.SimpleDateFormat;

public class CameraIO {

    private static int num_simultaneous_images_ = 0;
    private static long lastImageTime = -1;

    public static VideoCapture.OutputFileOptions getVideoOFO(Context context, String filename) {

        ContentValues values = new ContentValues();

        final String extension = filename.substring(filename.lastIndexOf('.') + 1,filename.length());

        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        values.put(MediaStore.MediaColumns.TITLE,filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "video/" + extension);

        return new VideoCapture.OutputFileOptions.Builder(context.getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values).build();

    }

    public static VideoCapture.OutputFileOptions getVideoOFO(Context context) {
        return getVideoOFO(context, getVideoFilename());
    }

    public static String getVideoFilename()  {

        return new StringBuilder()
                .append("VID_") //Prefix
                .append(new SimpleDateFormat("yyyyLLdd_HHmmss").format(System.currentTimeMillis())) //Time
                .append(".mp4") //Suffix (extension)
                .toString();
    }

    public static String getPhotoFilename() {

        long timestamp = System.currentTimeMillis();

        final String time = new SimpleDateFormat("yyyyLLdd_HHmmss").format(timestamp);
        StringBuilder sb = new StringBuilder().append("IMG_").append(time);

        if(timestamp == lastImageTime) {
            num_simultaneous_images_ += 1;
            sb.append("_" + Integer.toString(num_simultaneous_images_));
        }
        else { num_simultaneous_images_ = 0; }

        lastImageTime = timestamp;
        String output = sb.append(".jpg").toString();

        return output;
    }

    public static ImageCapture.OutputFileOptions getImageOFO(Context context, String filename) {

        ContentValues values = new ContentValues();

        final String extension = filename.substring(filename.lastIndexOf('.') + 1);

        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        values.put(MediaStore.MediaColumns.TITLE,filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/" + extension);

        return new ImageCapture.OutputFileOptions.Builder(context.getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values).build();
    }

    public static ImageCapture.OutputFileOptions getImageOFO(Context context) {
        return getImageOFO(context,getPhotoFilename());
    }

}
