package com.lbynet.phokus.camera

import com.lbynet.phokus.utils.MathTools.getCropFactor
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.TonemapCurve
import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.camera2.internal.compat.CameraManagerCompat
import com.lbynet.phokus.utils.SAL
import android.hardware.camera2.params.RggbChannelVector
import android.util.Range
import java.lang.Exception
import java.util.ArrayDeque
import java.util.HashMap

object CameraUtils {

    val AVAIL_ZOOM_LENGTHS = floatArrayOf(28f, 35f, 50f, 70f, 85f)
    val AVAIL_VIDEO_FPS = intArrayOf(24, 25, 30, 48, 50, 60)
    
    var hmCameraChar = HashMap<Int, CameraCharacteristics>()
    var hmFocalLength = HashMap<Int, Float>()
    var hmCropFactor = HashMap<Int, Float>()
    var hmExposure = HashMap<Int, FloatArray>()
    var hmLogCurve = HashMap<String, TonemapCurve>()
    val TAG = CameraUtils::class.java.simpleName

    enum class LogScheme {CLOG,SLOG}
    @JvmStatic
    @SuppressLint("RestrictedApi")
    fun getCameraCharacteristics(context: Context?, cameraId: Int): CameraCharacteristics? {
        return if (hmCameraChar.containsKey(cameraId)) hmCameraChar[cameraId] else try {
            val cc = CameraManagerCompat
                .from(context!!)
                .unwrap()
                .getCameraCharacteristics(Integer.toString(cameraId))
            hmCameraChar[cameraId] = cc
            cc
        } catch (e: Exception) {
            SAL.print(TAG, "Failed to obtain CameraCharacteristics of cameraId $cameraId.")
            SAL.print(e)
            null
        }
    }

    @SuppressLint("RestrictedApi")
    fun getCameraCount(context: Context?): Int {
        return try {
            CameraManagerCompat.from(context!!).unwrap().cameraIdList.size
        } catch (e: Exception) {
            SAL.print(TAG, "Failed to obtain camera count.")
            SAL.print(e)
            -1
        }
    }

    fun getFocalLength(context: Context?, cameraId: Int): Float {
        if (hmFocalLength.containsKey(cameraId)) return hmFocalLength[cameraId]!!
        val cc = getCameraCharacteristics(context, cameraId)
        if (cc == null) {
            SAL.print("Failed to obtain focal length.")
            return (-1).toFloat()
        }
        val r = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!![0]
        hmFocalLength[cameraId] = r
        return r
    }

    fun getCropFactor(context: Context?, cameraId: Int): Float {
        if (hmCropFactor.containsKey(cameraId)) return hmCropFactor[cameraId]!!
        val cc = getCameraCharacteristics(context, cameraId)
        if (cc == null) {
            SAL.print("Failed to obtain crop factor.")
            return (-1).toFloat()
        }
        val r = getCropFactor(cc.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!)
        hmCropFactor[cameraId] = r
        return r
    }

    @JvmStatic
    fun get35FocalLength(context: Context?, cameraId: Int): Float {
        return getCropFactor(context, cameraId) * getFocalLength(context, cameraId)
    }

    fun getAperture(context: Context?, cameraId: Int): Float {
        return getCameraCharacteristics(context, cameraId)!!
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)!![0]
    }

    @JvmStatic
    fun get35Aperture(context: Context?, cameraId: Int): Float {
        return getCropFactor(context, cameraId) * getAperture(context, cameraId)
    }

    @JvmStatic
    fun makeToneMapCurve(cc: CameraCharacteristics): TonemapCurve? {
        return makeToneMapCurve(LogScheme.CLOG, cc)
    }

    @JvmStatic
    fun makeToneMapCurve(scheme: LogScheme, cc: CameraCharacteristics): TonemapCurve? {
        val pts = cc.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS)
        return makeToneMapCurve(scheme, pts ?: 2)
    }

    //TODO: Finish this
    @JvmStatic
    fun makeToneMapCurve(scheme: LogScheme, points: Int): TonemapCurve? {

        val key = scheme.toString() + "_" + Integer.toString(points)
        if (hmLogCurve.containsKey(key)) return hmLogCurve[key]

        //草，植树问题
        val incr = 1 / (points - 2).toDouble()

        val ptArray = FloatArray(points * 2)
        var x = 0f

        ptArray[0] = 0f
        ptArray[1] = 0f
        x += incr.toFloat()
        var i = 2
        while (i < ptArray.size) {
            if (x >= 1) x = 1f
            ptArray[i] = x

            //Equation: y = a * tanh(bx), where a = 0.9, b = exp
            //ptArray[i+1] = (float)(0.9 * Math.tanh((x * (double)exp)));
            /**
             * Sony S-LOG: y = (0.432699*log10(x+0.037584)+0.616596)+0.03
             * (x {0,10}, y {0,1000}, 14 bit to 10 bit)
             *
             * Source: http://www.theodoropoulos.info/attachments/076_on%20S-Log.pdf
             *
             * Canon Log: y = (0.529136 * log10(10.1596x + 1) + 0.0730597);
             * (x {-0.045, 8.001}, y {-0.0685, 1.087}, 14 bit to 8 bit)
             *
             * Source: https://support.usa.canon.com/resources/sites/CANON/content/live/ARTICLES/170000/ART170247/en_US/White_Paper_Clog_optoelectronic.pdf
             *
             * Since we want y {0,1] and x {0,1} and the output is in 8 bit,
             * modification to these formulas is necessary.
             */
            ptArray[i + 1] = x
            when (scheme) {
                LogScheme.CLOG -> ptArray[i + 1] = (1 / 1.0865 * (0.529136 * Math.log10(10.1596 * 8 * x + 1) + 0.0730597)).toFloat()

                LogScheme.SLOG -> ptArray[i + 1] = (1 / 1.08 * (0.432699 * Math.log10(10 * x + 0.037584) + 0.616596 + 0.03)).toFloat()
            }


            //Cap ptArray[i+1] to 1 if the LOG value is greater than 1
            if (ptArray[i + 1] > 1) ptArray[i + 1] = 1f
            x += incr.toFloat()
            i += 2
        }

        //SAL.print("Final X: " + x +"\n");
        //SAL.print("Array: " + Arrays.toString(ptArray) + "\n");
        val curve = TonemapCurve(ptArray, ptArray, ptArray)
        hmLogCurve[key] = curve
        return curve
    }

    @JvmStatic
    fun getEvInfo(context: Context?, cameraId: Int): FloatArray? {
        if (hmExposure.containsKey(cameraId)) return hmExposure[cameraId]
        val cc = getCameraCharacteristics(context, cameraId)
        val r = FloatArray(3)
        r[0] = cc!!.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)!!.toFloat()
        val range = cc.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) as Range<Int>
        r[1] = range.lower * r[0]
        r[2] = range.upper * r[0]
        hmExposure[cameraId] = r
        return r
    }

    @JvmStatic
    fun getRggbVectorWithTemp(tempInKeivin: Int): RggbChannelVector {

        val tempIndex = (tempInKeivin / 100).toFloat()
        var red: Float
        var green: Float
        var blue: Float

        //Calculate red
        if (tempIndex <= 66) red = 255f else {
            red = tempIndex - 60
            red = (329.698727446 * Math.pow(red.toDouble(), -0.1332047592)).toFloat()
            if (red < 0) red = 0f
            if (red > 255) red = 255f
        }


        //Calculate green
        if (tempIndex <= 66) {
            green = tempIndex
            green = (99.4708025861 * Math.log(green.toDouble()) - 161.1195681661).toFloat()
            if (green < 0) green = 0f
            if (green > 255) green = 255f
        } else {
            green = tempIndex - 60
            green = (288.1221695283 * Math.pow(green.toDouble(), -0.0755148492)).toFloat()
            if (green < 0) green = 0f
            if (green > 255) green = 255f
        }

        //calculate blue
        if (tempIndex >= 66) blue = 255f else if (tempIndex <= 19) blue = 0f else {
            blue = tempIndex - 10
            blue = (138.5177312231 * Math.log(blue.toDouble()) - 305.0447927307).toFloat()
            if (blue < 0) blue = 0f
            if (blue > 255) blue = 255f
        }
        return RggbChannelVector(red / 255 * 2, green / 255, green / 255, blue / 255 * 2)
    }




}