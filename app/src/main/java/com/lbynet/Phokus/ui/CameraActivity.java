package com.lbynet.Phokus.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

import com.lbynet.Phokus.R;
import com.lbynet.Phokus.camera.CameraConsts;
import com.lbynet.Phokus.camera.CameraCore;
import com.lbynet.Phokus.camera.CameraUtils;
import com.lbynet.Phokus.global.Config;
import com.lbynet.Phokus.global.GlobalConsts;
import com.lbynet.Phokus.utils.SAL;
import com.lbynet.Phokus.utils.UIHelper;

import org.jetbrains.annotations.NotNull;

public class CameraActivity extends AppCompatActivity {

    private View root = null;
    private TextView textAperture,
                     textFocalLength,
                     textExposure,
                     textBottomInfo;
    private CardView cardTopInfo,
                     cardBottomInfo;
    final private Runnable rHideBottomInfo = () -> {
        UIHelper.setViewAlpha(cardBottomInfo,200,0,true);
    },
    rShowBottomInfo = () -> {
        UIHelper.setViewAlpha(cardBottomInfo,50,1,true);
    },
    rShowTopInfo = () -> {
        UIHelper.setViewAlpha(cardTopInfo,50,1, true);
    },
    rFadeTopInfo = () -> {
        UIHelper.setViewAlpha(cardTopInfo,50,0.5f,true);
    };


    private Handler fullscreenHandler = new Handler(),
                    topInfoHandler = new Handler(),
                    bottomInfoHandler = new Handler();


    private PreviewView preview;

    final private Runnable rHideNav = () -> {
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    };

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
        CameraCore.start(findViewById(R.id.pv_preview));

        int camera_id = (Boolean) Config.get(CameraConsts.FRONT_FACING) ? 1 : 0;

        textAperture.setText(String.format("F/%.2f",CameraUtils.get35Aperture(this,camera_id)));
        textAperture.setTextColor(UIHelper.getColors(this,R.color.colorSecondary)[0]);

        textBottomInfo.setText("Am I a joke to you? Please tell me I am not.");

        wakeTopInfo();
        wakeBottomInfo();
    }

    private void wakeTopInfo() {

        topInfoHandler.removeCallbacks(rFadeTopInfo);
        ContextCompat.getMainExecutor(this).execute(rShowTopInfo);
        topInfoHandler.postDelayed(rFadeTopInfo,2000);
    }

    private void wakeBottomInfo() {
        bottomInfoHandler.removeCallbacks(rHideBottomInfo);
        ContextCompat.getMainExecutor(this).execute(rShowBottomInfo);
        bottomInfoHandler.postDelayed(rHideBottomInfo,4000);
    }

    @Override
    protected void onResume() {
        super.onResume();

        fullscreenHandler.removeCallbacks(rHideNav);
        fullscreenHandler.postDelayed(rHideNav,100);
    }
}