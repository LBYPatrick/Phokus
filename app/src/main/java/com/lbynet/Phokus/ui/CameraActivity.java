package com.lbynet.Phokus.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.TextView;

import com.lbynet.Phokus.R;
import com.lbynet.Phokus.camera.CameraConsts;
import com.lbynet.Phokus.camera.CameraCore;
import com.lbynet.Phokus.camera.CameraUtils;
import com.lbynet.Phokus.global.Config;
import com.lbynet.Phokus.global.GlobalConsts;
import com.lbynet.Phokus.template.EventListener;
import com.lbynet.Phokus.utils.MathTools;
import com.lbynet.Phokus.utils.SAL;
import com.lbynet.Phokus.utils.Timer;
import com.lbynet.Phokus.utils.UIHelper;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

public class CameraActivity extends AppCompatActivity {

    final public static String TAG = CameraActivity.class.getCanonicalName();

    private View root = null,
                 viewShutterUp = null,
                 viewShutterDown = null,
                 viewVideoShutterDown = null,
                 viewRecordRec = null;
    private TextView textAperture,
                     textFocalLength,
                     textExposure,
                     textBottomInfo;
    private CardView cardTopInfo,
                     cardBottomInfo;
    private Timer videoTimer = new Timer("Video Timer");
    private boolean isShutterBusy = false,
                    isVideoMode = false,
                    isRecording = false;
    static int [] previewCurrentDimensions = null;
    static String bottomInfo;

    final private Runnable
            rHideBottomInfo = () -> UIHelper.setViewAlpha(cardBottomInfo, 200, 0, true),
            rShowBottomInfo = () -> UIHelper.setViewAlpha(cardBottomInfo, 10, 1, true),
            rShowTopInfo = () -> UIHelper.setViewAlpha(cardTopInfo, 10, 1, true),
            rFadeTopInfo = () -> UIHelper.setViewAlpha(cardTopInfo, 50, 0.5f, true),
            rShowShutterDown = () -> UIHelper.setViewAlpha(isVideoMode ? viewVideoShutterDown :viewShutterDown, 200, 1, true),
            rHideShutterDown = () -> UIHelper.setViewAlpha(isVideoMode ? viewVideoShutterDown :viewShutterDown, 200, 0, true),
            rHideNav = () -> {
                root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            };


    private Handler animationHandler = new Handler(),
                    fullscreenHandler = new Handler();
    private float currZoomRatio = 1;

    private PreviewView preview;
    final private ScaleGestureDetector.SimpleOnScaleGestureListener pToZListener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {

            float factor = detector.getScaleFactor();

            if(factor < 1) factor = - (1 / factor);
            else factor -= 1;

            float delta =  factor * 0.1f;

            currZoomRatio += delta;

            if(currZoomRatio < 1) currZoomRatio = 1;
            else if(currZoomRatio > 5) currZoomRatio = 5;

            wakeTopInfo();

            CameraCore.zoomByRatio(currZoomRatio, new EventListener() {
                @SuppressLint("DefaultLocale")
                @Override
                public boolean onEventUpdated(DataType dataType, Object data) {

                    if(dataType != DataType.FLOAT_CAM_FOCAL_LENGTH) return false;

                     UIHelper.runLater(requireContext(),() -> {

                        textFocalLength.setText(String.format("%.2fmm",(Float)data));

                        updateBottomInfo(String.format("Scale factor: %.2fx",currZoomRatio));

                        int [] colors = UIHelper.getColors(requireContext(),R.color.colorText,R.color.colorPrimary);
                        textFocalLength.setTextColor(currZoomRatio == 1 ? colors[0] : colors[1]);

                    });

                    return super.onEventUpdated(dataType, data);
                }
            });

            return super.onScale(detector);
        }
    };
    private ScaleGestureDetector pToZDetector = null;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        root = findViewById(R.id.cl_camera);
        textAperture = findViewById(R.id.tv_aperture);
        textBottomInfo = findViewById(R.id.tv_bottom_info);
        textExposure = findViewById(R.id.tv_exposure);
        textFocalLength = findViewById(R.id.tv_focal_length);
        cardTopInfo = findViewById(R.id.cv_top_info);
        cardBottomInfo = findViewById(R.id.cv_bottom_info);
        preview = findViewById(R.id.pv_preview);
        viewShutterUp = findViewById(R.id.v_shutter_up);
        viewShutterDown = findViewById(R.id.v_shutter_down);
        viewVideoShutterDown = findViewById(R.id.v_shutter_down_video);
        viewRecordRec = findViewById(R.id.v_record_rec);

        pToZDetector =  new ScaleGestureDetector(requireContext(),pToZListener);
        viewShutterUp.setOnTouchListener(this::onShutterTouched);


        findViewById(R.id.btn_dummy).setOnClickListener(this::toggleVideoMode);

        if(allPermissionsGood())
            startCamera();
        else
            ActivityCompat.requestPermissions(this,GlobalConsts.PERMISSIONS,GlobalConsts.PERM_REQUEST_CODE);


        SAL.print("CameraActivity","Attempted to hide navigation bar.");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull @NotNull String[] permissions,
                                           @NonNull @NotNull int[] grantResults) {

        if(requestCode == GlobalConsts.PERM_REQUEST_CODE) {

            if(allPermissionsGood()) startCamera();

        } else {
            UIHelper.printSystemToast(this,"Not all permissions were granted.",false);
            finish();
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    public boolean allPermissionsGood() {
        for(String p : GlobalConsts.PERMISSIONS) {
            if(ContextCompat.checkSelfPermission(this,p) == PackageManager.PERMISSION_DENIED) return false;
        }
        return true;
    }

    public void startCamera() {

        SAL.print("Starting camera");
        CameraCore.initialize();
        CameraCore.start(preview);

        int camera_id = (Boolean) Config.get(CameraConsts.FRONT_FACING) ? 1 : 0;

        textAperture.setText(String.format("F/%.2f",CameraUtils.get35Aperture(this,camera_id)));
        textAperture.setTextColor(UIHelper.getColors(this,R.color.colorSecondary)[0]);

        //updateBottomInfo("Am I a joke to you? Please tell me I am not.");

        preview.setOnTouchListener(this::onPreviewTouched);

        //Get preview default dimensions

        new Thread( () -> {
            previewCurrentDimensions = UIHelper.getViewDimensions(preview);
        }).start();

        wakeTopInfo();
        wakeBottomInfo();
    }

    // Credits to Saurabh Thorat:
    // https://stackoverflow.com/questions/63202209/camerax-how-to-add-pinch-to-zoom-and-tap-to-focus-onclicklistener-and-ontouchl
    public boolean onPreviewTouched(View v, MotionEvent event) {

        pToZDetector.onTouchEvent(event);

        if(event.getAction() == MotionEvent.ACTION_DOWN) {

            CameraCore.focusToPoint(event.getX(), event.getY(), false, new EventListener() {
                @Override
                public boolean onEventUpdated(DataType dataType, Object data) {

                    //if(dataType != DataType.STRING_FOCUS_STAT) return false;

                    return super.onEventUpdated(dataType, data);
                }
            });

        }

        return true;
    }

    public boolean onShutterTouched(View v, MotionEvent event) {

        if(event.getAction() != MotionEvent.ACTION_DOWN) return false;

        if (!isVideoMode && isShutterBusy) return false;
        else if(!isVideoMode) isShutterBusy = true;

        requireExecutor().execute(rShowShutterDown);

        if(isVideoMode && !isRecording) {
            isRecording = true;

            UIHelper.setViewAlpha(viewRecordRec,200,1);

            CameraCore.startRecording(new EventListener() {
                @Override
                public boolean onEventUpdated(DataType dataType, Object data) {

                    if(dataType != DataType.URI_VIDEO_SAVED) return false;

                    //runOnUiThread(() -> updateBottomInfo("Video saved!"));
                    requireExecutor().execute(rHideShutterDown);

                    UIHelper.setViewAlpha(viewRecordRec,200,0);

                    return super.onEventUpdated(dataType, data);
                }
            });

            startVideoTimer();
        }
        else if(isVideoMode) {
            CameraCore.stopRecording();
            stopVideoTimer();
            isRecording = false;
        }
        else {
            CameraCore.takePicture(new EventListener() {
                @Override
                public boolean onEventUpdated(DataType dataType, Object data) {

                    if (dataType != DataType.URI_PICTURE_SAVED) return false;

                    runOnUiThread(() -> updateBottomInfo("Picture saved!"));
                    requireExecutor().execute(rHideShutterDown);

                    isShutterBusy = false;

                    return super.onEventUpdated(dataType, data);
                }
            });
        }

        return true;
    }

    public boolean toggleVideoMode(View view) {

        isVideoMode = !isVideoMode;

        Config.set(CameraConsts.VIDEO_MODE,isVideoMode);
        Config.set(CameraConsts.PREVIEW_ASPECT_RATIO,isVideoMode ? AspectRatio.RATIO_16_9 : AspectRatio.RATIO_4_3);

        int targetWidth = (int)(previewCurrentDimensions[1] * (isVideoMode ? (16.0/9.0) : (4.0/3.0)));

        UIHelper.resizeView(preview,
                previewCurrentDimensions,
                new int [] {targetWidth,previewCurrentDimensions[1]},
                200,
                true);

        previewCurrentDimensions[0] = targetWidth;

        CameraCore.start(preview);

        return true;
    }

    private Context requireContext() {
        return this;
    }

    private Executor requireExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    private void startVideoTimer() {

        new Thread( () -> {

            videoTimer.start();

            String [] time = new String[4];

            while(videoTimer.isBusy()) {

                SAL.sleepFor(100);

                MathTools.formatTime(videoTimer.getElaspedTimeInMs(),time);
                StringBuilder sb = new StringBuilder().append(time[0]);

                for(int i = 1; i < 3; ++i) { sb.append(':').append(time[i]);}

                updateBottomInfo(sb.toString());
            }

            //Extra run after loop
            MathTools.formatTime(videoTimer.getElaspedTimeInMs(),time);
            StringBuilder sb = new StringBuilder().append(time[0]);

            for(int i = 1; i < 3; ++i) { sb.append(':').append(time[i]);}

            updateBottomInfo(sb.toString());

        }).start();
    }

    private void stopVideoTimer() {

        videoTimer.stop();

    }

    private void wakeTopInfo() {

        animationHandler.removeCallbacks(rFadeTopInfo);
        ContextCompat.getMainExecutor(this).execute(rShowTopInfo);
        animationHandler.postDelayed(rFadeTopInfo,2000);
    }

    private void updateBottomInfo(String newInfo) {

        bottomInfo = newInfo;

        ContextCompat.getMainExecutor(this).execute(() -> textBottomInfo.setText(newInfo));
        wakeBottomInfo();

    }

    private void wakeBottomInfo() {
        animationHandler.removeCallbacks(rHideBottomInfo);
        ContextCompat.getMainExecutor(this).execute(rShowBottomInfo);
        animationHandler.postDelayed(rHideBottomInfo,4000);
    }

    @Override
    protected void onResume() {
        super.onResume();

        fullscreenHandler.removeCallbacks(rHideNav);
        fullscreenHandler.postDelayed(rHideNav,100);
    }
}