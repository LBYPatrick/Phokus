package com.lbynet.phokus.ui.widget

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
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

    private var ivToggleOn_ : ImageView
    private var ivToggleOff_ : ImageView
    private var isInitialized_ = false
    private var isToggledOn_ = true
    private var isToggleOffPersistent_ = true
    private var transitionDuration_ : Long  = 0
    private var actualClickListener: OnClickListener? = null
    private lateinit var interpolator: TimeInterpolator

    init {

        val view = inflate(context, R.layout.view_toggle,this)

        ivToggleOn_ = findViewById(R.id.iv_toggle_on)
        ivToggleOff_ = findViewById(R.id.iv_toggle_off)

        val attr = context.obtainStyledAttributes(attrs,R.styleable.ToggleView,0,0)

        try {
            ivToggleOn_.setImageDrawable(attr.getDrawable(R.styleable.ToggleView_toggleOnDrawable))
            ivToggleOff_.setImageDrawable(attr.getDrawable(R.styleable.ToggleView_toggleOffDrawable))
            transitionDuration_ = attr.getInteger(R.styleable.ToggleView_transitionDurationInMs, 150).toLong()
            isToggleOffPersistent_ = attr.getBoolean(R.styleable.ToggleView_toggleOffPersistent,true);

            setInterpolator(attr.getInteger(R.styleable.ToggleView_interpolator, UIHelper.INTRPL_LINEAR))

            val currState = attr.getBoolean(R.styleable.ToggleView_isToggledOn, false)
            setToggleState(currState)

            super.setOnClickListener {
                if(actualClickListener == null) {
                    SAL.print("Default routine");
                    setToggleState(!isToggledOn_)
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
            isInitialized_ = true
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        //super.setOnClickListener(l)
        actualClickListener = l
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        TODO("Not yet implemented")
    }

    fun setToggleState(isToggledOn: Boolean) {
        if (isToggledOn == isToggledOn_) return

        isToggledOn_ = isToggledOn

        ivToggleOn_.animate()
                .alpha(if (isToggledOn) 1f else 0f)
                .setDuration(if (isInitialized_) transitionDuration_ else 0)
                .setInterpolator(interpolator)
                .start();

        if(!isToggleOffPersistent_) {
            ivToggleOff_.animate()
                    .alpha(if (isToggledOn) 0f else 1f)
                    .setDuration(if (isInitialized_) transitionDuration_ else 0)
                    .setInterpolator(interpolator)
                    .start()
        }
    }


    fun setInterpolator(@InterpolatorType type: Int) {
        interpolator = UIHelper.getInterpolator(type)
    }


}