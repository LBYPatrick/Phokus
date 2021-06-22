package com.lbynet.Phokus.deprecated.listener;

import com.lbynet.Phokus.utils.SAL;

import java.util.Arrays;

public class RotationListener {

    final public static String TAG = RotationListener.class.getCanonicalName();


    public boolean onUpdate(float [] data) {

        SAL.print(TAG, "New rotation data received: " + Arrays.toString(data));
        return true;
    }
}
