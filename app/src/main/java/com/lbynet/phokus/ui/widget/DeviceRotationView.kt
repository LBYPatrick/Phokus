package com.lbynet.phokus.ui.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.lbynet.phokus.R
import com.lbynet.phokus.template.OnDimensionInfoReadyCallback
import com.lbynet.phokus.utils.MathTools
import com.lbynet.phokus.utils.UIHelper
import kotlin.math.abs

class DeviceRotationView(context : Context, attrs : AttributeSet) : ConstraintLayout(context,attrs) {

    private var ivHorizBkgd : ImageView
    private var ivHorizFrgd : ImageView
    private var ivVertBkgd : ImageView
    private var ivVertFrgd : ImageView
    private var vertViewHeight = 0
    private var vertViewWidth  = 0

    public val COLOR_OFF_ANGLE = Color.argb(255,255,165,0)
    public val COLOR_GOOD_ANGLE = Color.argb(255,0,255,0)

    /* Which stands for "Average Horizontal Angle" */
    private var avgHorizAngle : Float = 0f
    /* Which stands for "Average Vertical Angle" */
    private var avgVertAngle : Float = 0f

    init {

        val root = inflate(context, R.layout.view_device_rotation,this)

        ivHorizBkgd = root.findViewById(R.id.iv_horiz_background)
        ivHorizFrgd = root.findViewById(R.id.iv_horiz_foreground)
        ivVertBkgd = root.findViewById(R.id.iv_vert_background)
        ivVertFrgd = root.findViewById(R.id.iv_vert_foreground)

        UIHelper.queryViewDimensions(ivVertBkgd) { width, height ->
            run {
                vertViewWidth = width
                vertViewHeight = height
            }
        }

    }

    fun setHorizontalAngle(angle : Float) : DeviceRotationView {

        avgHorizAngle = avgHorizAngle * 0.9f + angle * 0.1f

        avgHorizAngle = MathTools.capValue(avgHorizAngle,-45f,45f)

        ivHorizFrgd.rotation = avgHorizAngle

        ivHorizFrgd.setColorFilter(
            if(abs(avgHorizAngle) > 1) COLOR_OFF_ANGLE
            else COLOR_GOOD_ANGLE)

        return this
    }

    fun setVerticalAngle(angle : Float): DeviceRotationView {

        avgVertAngle = avgVertAngle * 0.9f + angle * 0.1f

        avgVertAngle = MathTools.capValue(avgVertAngle,-45f,45f)

        ivVertFrgd.translationY = vertViewHeight / 2 * ivVertBkgd.scaleY * (avgVertAngle / 45)

        ivVertFrgd.setColorFilter(
            if(abs(avgVertAngle) > 1) COLOR_OFF_ANGLE
            else COLOR_GOOD_ANGLE)

        return this
    }

}