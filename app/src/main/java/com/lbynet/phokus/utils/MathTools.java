package com.lbynet.phokus.utils;

import android.util.SizeF;

public class MathTools {

    final public static float fullFrameCmosSize = (float)Math.sqrt(Math.pow(36,2) + Math.pow(24,2));

    public static float getCappedFloat(float value, float min, float max) {

        if(min > max) {
            float temp = min;
            min = max;
            max = temp;
        }

        if(value > max) { return max; }

        else if (value < min) { return min; }

        else { return value; }
    }

    public static boolean isValueInRange(int value, int low, int high) {
        return low > high ?
                    (value >= high && value <= low)
                :   (value >= low && value <= high);
    }

    public static float getCropFactor(SizeF cmosDimensions) {
        return fullFrameCmosSize / (float)(Math.sqrt(Math.pow(cmosDimensions.getWidth(),2) + Math.pow(cmosDimensions.getWidth() /3 * 2,2)));
    }

    public static int sumOf(int... values) {
        int sum = 0;

        for(int i : values) {
            sum += i;
        }

        return sum;
    }

    public static int [] sliceTime(double ms) {

        return new int [] {
                (int)Math.floor(ms / 1000 / 3600), //Hour
                (int)Math.floor((ms / 1000 /60) % 60), //Minute
                (int)Math.floor((ms / 1000) % 60), //Second
                (int)Math.floor(ms % 100) //Tens of ms
        };

    }

    /**
     * Caps radian to [0,2\pi].
     * @param rad raw radian [-\infinity,+\infinity].
     * @return capped radian.
     */
    public static double getCappedRadian(double rad) {

        final double twoPi = 2 * Math.PI;
        double remainder = rad % (twoPi);

        if(remainder < 0) remainder += twoPi;

        return remainder;
    }

    public static double radianToDegrees(double rad, boolean isAlwaysPositive) {

        if(!isAlwaysPositive) return radianToDegrees(rad);
        else return radianToDegrees(getCappedRadian(rad));

    }

    public static double radianToDegrees(double rad) {
        return rad * 180 / Math.PI;
    }

    public static void formatTime(double ms, String [] buffer) {
        int [] time = sliceTime(ms);

        if(buffer.length < 4) return;

        for(int i = 0; i < 4; ++i) {
            if (time[i] < 10) {
                buffer[i] = "0" + time[i];
            } else buffer[i] = Integer.toString(time[i]);
        }
    }
}
