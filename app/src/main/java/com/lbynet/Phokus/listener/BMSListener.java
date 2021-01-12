package com.lbynet.Phokus.listener;

import android.content.Intent;

import com.lbynet.Phokus.utils.SAL;

/**
 * Stands for "Battery Management System Listener"
 */
public class BMSListener {

    public boolean onUpdate(Intent intent){

        SAL.print(this.getClass().getSimpleName(),"Battery info updated.");

        return true;
    }
}
