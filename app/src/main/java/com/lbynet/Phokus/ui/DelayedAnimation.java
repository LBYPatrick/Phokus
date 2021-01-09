package com.lbynet.Phokus.ui;

import android.animation.Animator;

import com.lbynet.Phokus.utils.SAL;
import com.lbynet.Phokus.utils.Timer;

import java.util.concurrent.Executor;

public class DelayedAnimation {

    private int delayInMs_;
    private Animator animator_;
    private Executor executor_;
    private boolean isInterrupted = false;

    public DelayedAnimation(int delayInMs, Animator animator, Executor executor) {
        animator_ = animator;
        delayInMs_ = delayInMs;
        executor_ = executor;
    }

    public void start() {

        new Thread( () -> {
            Timer timer = new Timer("DelayedAnimation Timer");

            while(timer.getElaspedTimeInMs() < delayInMs_ && !isInterrupted) {
                SAL.sleepFor(10);
            }

            if(isInterrupted) {
                return;
            }

            executor_.execute(animator_::start);
        }).start();
    }

    public void cancel() {

        isInterrupted = true;

        if(animator_.isRunning()) {
            executor_.execute(animator_::cancel);
        }
    }

    public Animator getAnimator() {
        return animator_;
    }


}
