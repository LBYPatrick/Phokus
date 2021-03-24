package com.lbynet.Phokus;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.os.Bundle;
import android.view.View;

import com.lbynet.Phokus.utils.SAL;
import com.lbynet.Phokus.utils.SysInfo;

public class MainActivity extends AppCompatActivity {

    final static String TAG = MainActivity.class.getSimpleName();
    MainFragment mainFrag_ = null;

    boolean requirePermission = true;

    final public static String [] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main);

        requestPermissions(PERMISSIONS,1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SAL.print("onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        SAL.print("onPause");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        for (int i = 0; i < grantResults.length; ++i) {

            boolean isGranted = (grantResults[i] == android.content.pm.PackageManager.PERMISSION_GRANTED);
            SAL.print(TAG,"Permission: " + permissions[i] + "\tGrant status: " + isGranted);

            if (!isGranted) {
                SAL.print(TAG, "Failed to obtain necessary permissions, please try again.");
                finish();
                return;
            }
        }

        onPermissionGranted();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    public void onPermissionGranted() {

        if(mainFrag_ == null) {

            SysInfo.initialize(this);

            mainFrag_ = new MainFragment();

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fl_fragment_placeholder, mainFrag_)
                    //.addToBackStack("DEFAULT_STACK")
                    .commit();
        }

    }
}