package com.lbynet.Phokus;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.os.Bundle;

import com.lbynet.Phokus.utils.SAL;

public class MainActivity extends AppCompatActivity {

    final static String TAG = MainActivity.class.getSimpleName();

    final public static String [] PERMISSIONS = {
            Manifest.permission.CAMERA,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main);

        requestPermissions(PERMISSIONS,1);
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

        onCreated();
    }

    public void onCreated() {

        MainFragment mainFrag = new MainFragment();

        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.fl_fragment_placeholder,mainFrag)
                .addToBackStack("DEFAULT_STACK")
                .commit();

    }
}