package com.lbynet.Phokus.utils;

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
}
