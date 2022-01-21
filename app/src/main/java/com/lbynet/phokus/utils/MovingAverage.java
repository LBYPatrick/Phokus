package com.lbynet.phokus.utils;

import androidx.annotation.AnyThread;

import java.util.concurrent.locks.ReentrantLock;

public class MovingAverage {

    ReentrantLock mutex_;
    final double [] arr_;
    double avg_ = 0;
    int sz_arr_ = 0,
        index_ = 0;
    boolean is_full = false;


    public MovingAverage(int size) {
        sz_arr_ = size;
        arr_ = new double[sz_arr_];
        mutex_ = new ReentrantLock();
    }

    @AnyThread
    public void put(double value) {
        mutex_.lock();

        if(!is_full)
            avg_ = (avg_ * index_ + value) / (index_ + 1);
        else
            avg_ = (avg_ * sz_arr_ - arr_[index_] + value) / sz_arr_;

        arr_[index_] = value;
        index_ = (index_ + 1) % sz_arr_;

        if(index_ == 0) is_full = true;

        mutex_.unlock();
    }

    @AnyThread
    public double getAverage() {
        mutex_.lock();
        double avg = avg_;
        mutex_.unlock();
        return avg;
    }

}
