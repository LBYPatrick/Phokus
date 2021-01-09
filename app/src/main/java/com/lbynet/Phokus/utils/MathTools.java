package com.lbynet.Phokus.utils;

public class MathTools {
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
}
