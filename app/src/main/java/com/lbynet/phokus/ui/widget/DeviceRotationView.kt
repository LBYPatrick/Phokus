package com.lbynet.phokus.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.lbynet.phokus.R

class DeviceRotationView(context : Context, attrs : AttributeSet) : ConstraintLayout(context,attrs) {

    private var ivHorizBkgd : ImageView
    private var ivHorizFrgd : ImageView
    private var ivVertBkgd : ImageView
    private var ivVertFrgd : ImageView

    init {

        val root = inflate(context, R.layout.view_device_rotation,this)

        ivHorizBkgd = root.findViewById(R.id.iv_horiz_background)
        ivHorizFrgd = root.findViewById(R.id.iv_horiz_foreground)
        ivVertBkgd = root.findViewById(R.id.iv_vert_background)
        ivVertFrgd = root.findViewById(R.id.iv_vert_foreground)


    }


    /**
     * @return angle of device rotation in degrees (which is between 0 and 360 degrees)
     */
    fun getAngleInDegrees() {

        //TODO: Finish this
    }

}