package com.lbynet.phokus.utils

import android.util.SizeF
import com.lbynet.phokus.utils.MathTools
import java.lang.StringBuilder

object MathTools {

    private val fullFrameCmosSize = Math.sqrt(Math.pow(36.0, 2.0) + Math.pow(24.0, 2.0))
        .toFloat()

    @JvmStatic
    fun getCappedFloat(value: Float, min: Float, max: Float): Float {
        var min = min
        var max = max
        if (min > max) {
            val temp = min
            min = max
            max = temp
        }
        return if (value > max) {
            max
        } else if (value < min) {
            min
        } else {
            value
        }
    }

    @JvmStatic
    fun isValueInRange(value: Int, low: Int, high: Int): Boolean {
        return if (low > high) value in high..low else value in low..high
    }

    @JvmStatic
    fun capValue(value : Float, low : Float, high : Float) : Float {

        if(value < low) return low
        else if(value > high) return high
        else return value
    }

    @JvmStatic
    fun getCropFactor(cmosDimensions: SizeF): Float {
        return fullFrameCmosSize / Math.sqrt(
            Math.pow(
                cmosDimensions.width.toDouble(),
                2.0
            ) + Math.pow((cmosDimensions.width / 3 * 2).toDouble(), 2.0)
        ).toFloat()
    }

    @JvmStatic
    fun sumOf(vararg values: Int): Int {
        var sum = 0
        for (i in values) {
            sum += i
        }
        return sum
    }

    @JvmStatic
    fun sliceTime(ms: Double): IntArray {
        return intArrayOf(
            Math.floor(ms / 1000 / 3600).toInt(),  //Hour
            Math.floor(ms / 1000 / 60 % 60).toInt(),  //Minute
            Math.floor(ms / 1000 % 60).toInt(),  //Second
            Math.floor(ms % 100).toInt() //Tens of ms
        )
    }

    /**
     * Caps radian to [0,2\pi].
     * @param rad raw radian [-\infinity,+\infinity].
     * @return capped radian.
     */
    @JvmStatic
    fun getCappedRadian(rad: Double): Double {
        val twoPi = 2 * Math.PI
        var remainder = rad % twoPi
        if (remainder < 0) remainder += twoPi
        return remainder
    }

    @JvmStatic
    fun radianToDegrees(rad: Double, isAlwaysPositive: Boolean): Double {
        return if (!isAlwaysPositive) radianToDegrees(rad) else radianToDegrees(
            getCappedRadian(rad)
        )
    }

    @JvmStatic
    fun radianToDegrees(rad: Double): Double {
        return rad * 180 / Math.PI
    }

    @JvmStatic
    fun formatTime(ms: Double, buffer: Array<String?>) {
        val time = sliceTime(ms)
        if (buffer.size < 4) return
        for (i in 0..3) {
            if (time[i] < 10) {
                buffer[i] = "0" + time[i]
            } else buffer[i] = Integer.toString(time[i])
        }
    }

    @JvmStatic
    fun parseTimeToString(elaspedTimeInMs: Double): String {
        val buffer = arrayOfNulls<String>(4)
        formatTime(elaspedTimeInMs, buffer)
        val sb = StringBuilder().append(buffer[0])
        for (i in 1..3) {
            sb.append(':').append(buffer[i])
        }
        return sb.toString()
    }
}