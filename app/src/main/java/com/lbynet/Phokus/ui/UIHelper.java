package com.lbynet.Phokus.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Point;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.annotation.IntRange;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.lbynet.Phokus.R;
import com.lbynet.Phokus.listener.ColorListener;
import com.lbynet.Phokus.utils.MathTools;
import com.lbynet.Phokus.utils.SAL;

import java.util.HashMap;
import java.util.concurrent.Executor;

public class UIHelper {

    final public static String TAG = UIHelper.class.getSimpleName();

    static Point screenDimensions_ = null;
    static DelayedAnimation animation = null;
    static Executor toastExecutor_ = null;
    static CardView toastView_ = null;
    static HashMap<View, Boolean> hapticViewMap = new HashMap<>();

    public static void printSystemToast(Activity activity, String msg, boolean isLongTime) {
        activity.runOnUiThread( () -> {
            Toast.makeText(activity, msg, isLongTime ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        });
    }

    public static Point getScreenDimensions(Context context) {

        if(screenDimensions_ == null) {
            screenDimensions_ = new Point();
            ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealSize(screenDimensions_);
        }

        return screenDimensions_;
    }

    public static ValueAnimator setViewAlpha(View view, int durationInMs, float targetAlpha) {
        return setViewAlpha(view,durationInMs,targetAlpha,true);
    }

    public static ValueAnimator getAlphaAnimator(View view, int durationInMs, float targetAlpha, boolean isNonLinear) {
        final float oldAlpha = view.getAlpha();
        final float newAlpha = MathTools.getCappedFloat(targetAlpha,0,1);

        if(targetAlpha != newAlpha) {
            SAL.print(TAG,"setViewAlpha: targetAlpha is out of range [0,1] because your input is "
                    + targetAlpha
                    + ". it has been capped to "
                    + newAlpha + ".");
        }

        Executor executor = ContextCompat.getMainExecutor(view.getContext());

        ValueAnimator animator = ValueAnimator.ofFloat(view.getAlpha(), newAlpha);

        if(targetAlpha == view.getAlpha()) { animator.setDuration(0); }
        else { animator.setDuration(durationInMs); }

        if((oldAlpha == 0 || view.getVisibility() != View.VISIBLE) && newAlpha != 0) {
            executor.execute(() -> {
                view.setVisibility(View.VISIBLE);
            });
        }

        animator.addUpdateListener(animation -> {

                    float value = (float) animation.getAnimatedValue();

                    executor.execute(() -> {
                        view.setAlpha(value);

                        if(newAlpha == 0 && value == 0) {
                            executor.execute(() -> {
                                view.setVisibility(View.INVISIBLE);
                            });
                        }
                    });
                }
        );

        if (isNonLinear) { animator.setInterpolator(new AccelerateDecelerateInterpolator()); }

        return animator;
    }

    public static ValueAnimator setViewAlpha(View view, int durationInMs, float targetAlpha, boolean isNonLinear) {

        ValueAnimator a = getAlphaAnimator(view,durationInMs,targetAlpha,isNonLinear);

        ContextCompat.getMainExecutor(view.getContext()).execute(a::start);

        return a;

    }

    public static int [] getColors(Context context, int... resIDs) {
        int [] r = new int [resIDs.length];

        for(int i = 0; i < resIDs.length; ++i) {
            r[i] = ContextCompat.getColor(context,resIDs[i]);
        }

        return r;
    }

    public static ValueAnimator getColorAnimator(ColorListener listener,
                                                 int durationInMs,
                                                 boolean isNonLinear,
                                                 int... colors
                                                 ) {

        ValueAnimator animator = ValueAnimator.ofArgb(colors);
        animator.setDuration(durationInMs);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                listener.onColorUpdated((int)valueAnimator.getAnimatedValue());
            }
        });

        if(isNonLinear) { animator.setInterpolator(new DecelerateInterpolator()); }

        return animator;
    }

    public static String getString(Context context, int resid) {
        return context.getString(resid);
    }

    public static int getSurfaceOrientation(int degrees) {

        if(degrees <= 45) { return Surface.ROTATION_0; }
        else if(degrees > 45 && degrees <= 135) { return Surface.ROTATION_270; }
        else if(degrees > 135 && degrees <= 225) { return Surface.ROTATION_180; }
        else if(degrees > 225 && degrees <= 315) { return Surface.ROTATION_90; }
        else { return Surface.ROTATION_0; }
    }

    public static void runLater(Context context, Runnable r) {
        ContextCompat.getMainExecutor(context).execute(r);
    }

    public static ColorStateList makeCSLwithID(Context context, int resId) {

        return ContextCompat.getColorStateList(context,resId);

    }

    public static boolean hapticFeedback(View view, MotionEvent e) {

        final int action = e.getActionMasked();
        final boolean isDown = action == MotionEvent.ACTION_DOWN;

        if((hapticViewMap.containsKey(view) && hapticViewMap.get(view)) && isDown) { return true; }
        else {
            SAL.simulatePress(view.getContext(),!isDown);
            hapticViewMap.put(view,isDown);
            return isDown;
        }
    }

    public static void updateCardColor(CardView card, boolean isEnabled) {
        int [] colors = UIHelper.getColors(card.getContext(), R.color.card_inactive,R.color.card_active);

        //Update data_record_icon's color
        UIHelper.getColorAnimator(new ColorListener() {
            @Override
            public void onColorUpdated(int newColor) {
                card.setCardBackgroundColor(newColor);
            }
        }, 100, true, (isEnabled ? colors : new int[]{colors[1], colors[0]})).start();
    }

}
