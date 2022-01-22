package com.lbynet.phokus.ui.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.Lifecycle
import com.lbynet.phokus.R
import com.lbynet.phokus.global.Consts
import com.lbynet.phokus.hardware.RotationSensor
import com.lbynet.phokus.template.RotationListener
import com.lbynet.phokus.utils.MathTools
import com.lbynet.phokus.utils.MovingAverage
import com.lbynet.phokus.utils.SAL
import com.lbynet.phokus.utils.UIHelper
import kotlin.math.abs

class DeviceRotationView(context : Context, attrs : AttributeSet) : ConstraintLayout(context,attrs) {

    private var ivHorizBkgd : ImageView
    private var ivHorizFrgd : ImageView
    private var ivVertBkgd : ImageView
    private var ivVertFrgd : ImageView
    private var sensorRotation : RotationSensor
    private var vertViewHeight = 0
    private var vertViewWidth  = 0

    public val COLOR_OFF_ANGLE = Color.argb(255,255,165,0)
    public val COLOR_GOOD_ANGLE = Color.argb(255,0,255,0)

    init {

        val root = inflate(context, R.layout.view_device_rotation,this)

        ivHorizBkgd = root.findViewById(R.id.iv_horiz_background)
        ivHorizFrgd = root.findViewById(R.id.iv_horiz_foreground)
        ivVertBkgd = root.findViewById(R.id.iv_vert_background)
        ivVertFrgd = root.findViewById(R.id.iv_vert_foreground)

        val avgHorizontal = MovingAverage(20)
        val avgVertical = MovingAverage(20)

        sensorRotation = RotationSensor(context) { azimuth, pitch, roll ->
            run {

                Consts.EXE_THREAD_POOL.execute(Runnable {

                    avgHorizontal.put(MathTools.radianToDegrees(pitch.toDouble(), false))
                    avgVertical.put((MathTools.radianToDegrees(roll.toDouble(), false) + 90))


                    UIHelper.runLater(context, Runnable {
                        setHorizontalAngle(avgHorizontal.average.toFloat())
                        setVerticalAngle(avgVertical.average.toFloat())
                        //SAL.print("New Angle information!")
                    })
                })
            }
        }

        UIHelper.queryViewDimensions(ivVertBkgd) { width, height ->
            run {
                vertViewWidth = width
                vertViewHeight = height
            }
        }

    }

    fun onPause() {
        sensorRotation.hibernate()
    }

    fun onResume() {
        sensorRotation.resume()
    }

    fun setHorizontalAngle(angle : Float) : DeviceRotationView {

        var capAngle = MathTools.capValue(angle,-45f,45f)

        ivHorizFrgd.rotation = capAngle

        ivHorizFrgd.setColorFilter(
            if(abs(angle) > 1) COLOR_OFF_ANGLE
            else COLOR_GOOD_ANGLE)

        return this
    }

    fun setVerticalAngle(angle : Float): DeviceRotationView {

        var capAngle = MathTools.capValue(angle,-45f,45f)

        ivVertFrgd.translationY = vertViewHeight / 2 * ivVertBkgd.scaleY * (capAngle / 45)

        ivVertFrgd.setColorFilter(
            if(abs(capAngle) > 1) COLOR_OFF_ANGLE
            else COLOR_GOOD_ANGLE)

        return this
    }

}