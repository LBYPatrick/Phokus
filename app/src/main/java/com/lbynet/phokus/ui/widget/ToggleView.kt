package com.lbynet.phokus.ui.widget

import android.animation.TimeInterpolator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.lbynet.phokus.R
import com.lbynet.phokus.utils.SAL
import com.lbynet.phokus.utils.UIHelper
import com.lbynet.phokus.utils.UIHelper.InterpolatorType

class ToggleView(context: Context, attrs : AttributeSet) : ConstraintLayout(context,attrs),View.OnTouchListener {

    private var ivToggleOn : ImageView
    private var ivToggleOff : ImageView
    private var isInitialized = false
    private var isToggledOn = true
    private var isToggleOffPersistent = true
    private var transitionDuration : Long  = 0
    private var actualClickListener: OnClickListener? = null
    private lateinit var interpolator: TimeInterpolator

    init {

        /**
         * Why would I use view.findViewById(int)? and not findViewById(int) ?
         * Because our "smart" IDE thinks that I could call findViewById(int) WITHOUT inflating the view...
         * This would force the IDE to interpret my code THE RIGHT WAY.
         * -- LBYPatrick, 08/24/2021
         */
        val root = inflate(context, R.layout.view_toggle,this)

        ivToggleOn = root.findViewById(R.id.iv_toggle_on)
        ivToggleOff = root.findViewById(R.id.iv_toggle_off)

        val attr = context.obtainStyledAttributes(attrs,R.styleable.ToggleView,0,0)

        try {
            ivToggleOn.setImageDrawable(attr.getDrawable(R.styleable.ToggleView_toggleOnDrawable))
            ivToggleOff.setImageDrawable(attr.getDrawable(R.styleable.ToggleView_toggleOffDrawable))
            transitionDuration = attr.getInteger(R.styleable.ToggleView_transitionDurationInMs, 150).toLong()
            isToggleOffPersistent = attr.getBoolean(R.styleable.ToggleView_toggleOffPersistent,true);


            setInterpolator(attr.getInteger(R.styleable.ToggleView_interpolator, UIHelper.INTRPL_LINEAR))

            val currState = attr.getBoolean(R.styleable.ToggleView_isToggledOn, false)
            setToggleState(currState)

            super.setOnClickListener {
                if(actualClickListener == null) {
                    SAL.print("Default routine");
                    setToggleState(!isToggledOn)
                }
                else {
                    SAL.print("custom routine");
                        actualClickListener?.onClick(it)
                }
            }

        } catch (e : Exception) {
            SAL.print(e)
        } finally {
            attr.recycle()
            isInitialized = true
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        //super.setOnClickListener(l)
        actualClickListener = l
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        TODO("Not yet implemented")
    }

    fun setTint(color : Int) {
        ivToggleOn.setColorFilter(color)
        ivToggleOff.setColorFilter(color)
    }

    fun setToggleState(isToggledOn: Boolean) {
        if (isToggledOn == this.isToggledOn) return

        this.isToggledOn = isToggledOn

        ivToggleOn.animate()
                .alpha(if (isToggledOn) 1f else 0f)
                .setDuration(if (isInitialized) transitionDuration else 0)
                .setInterpolator(interpolator)
                .start()

        if(!isToggleOffPersistent) {
            ivToggleOff.animate()
                    .alpha(if (isToggledOn) 0f else 1f)
                    .setDuration(if (isInitialized) transitionDuration else 0)
                    .setInterpolator(interpolator)
                    .start()
        }
    }


    fun setInterpolator(@InterpolatorType type: Int) {
        interpolator = UIHelper.getInterpolator(type)
    }


}