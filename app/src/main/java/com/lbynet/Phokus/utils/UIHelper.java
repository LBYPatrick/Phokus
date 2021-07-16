package com.lbynet.Phokus.utils;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Interpolator;
import android.graphics.Point;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.lbynet.Phokus.deprecated.listener.ColorListener;

import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class UIHelper {

    final public static String TAG = UIHelper.class.getSimpleName();

    static Point screenDimensions_ = null;
    static DelayedAnimation animation = null;
    static Executor toastExecutor_ = null;
    static CardView toastView_ = null;
    static HashMap<View,ValueAnimator> animatorMap = new HashMap<>();
    static HashMap<View, Boolean> hapticViewMap = new HashMap<>();
    static HashMap<Integer, ColorStateList> cslMap = new HashMap<>();

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

    public static void setViewAlpha(View view, int durationInMs, float targetAlpha) {
        setViewAlpha(view,durationInMs,targetAlpha,true);
    }

    @Deprecated
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

    public static void setViewAlpha(View v, long durationInMs, float targetAlpha, boolean isNonLinear) {

        v.post( () -> {
            v.animate()
                    .alpha(targetAlpha)
                    .setDuration(durationInMs)
                    .setInterpolator(isNonLinear ? new AccelerateDecelerateInterpolator() : new LinearInterpolator())
                    .start();
        });
    }

    @Deprecated
    public static ValueAnimator setViewAlphaOld(View view, int durationInMs, float targetAlpha, boolean isNonLinear) {

        if(animatorMap.get(view) != null) animatorMap.get(view).cancel();

        ValueAnimator a = getAlphaAnimator(view,durationInMs,targetAlpha,isNonLinear);

        ContextCompat.getMainExecutor(view.getContext()).execute(a::start);

        animatorMap.put(view,a);

        return a;

    }

    public static void resizeView(View view,
                                  int [] oldDimensions,
                                  int [] newDimensions,
                                  int durationInMs,
                                  boolean isNonLinear) {

        ValueAnimator h = ValueAnimator.ofFloat(oldDimensions[1],newDimensions[1]).setDuration(durationInMs),
                      w = ValueAnimator.ofFloat(oldDimensions[0],newDimensions[0]).setDuration(durationInMs);

        if(isNonLinear) {
            h.setInterpolator(new DecelerateInterpolator());
            w.setInterpolator(new DecelerateInterpolator());
        }


        h.addUpdateListener(animation -> {

            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.height = Math.round((Float)animation.getAnimatedValue());
            view.setLayoutParams(params);

        });


        w.addUpdateListener(animation -> {

            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.width = Math.round((Float)animation.getAnimatedValue());
            view.setLayoutParams(params);

        });

        ContextCompat.getMainExecutor(view.getContext()).execute(()->{
            h.start();
            w.start();
        });
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

    public static int [] getViewDimensions(View view) {

        AtomicInteger width = new AtomicInteger(-1),
                      height = new AtomicInteger(-1);

        view.post( () -> {
            view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

            width.set(view.getWidth());
            height.set(view.getHeight());

        });

        while(width.get() == -1 || height.get() == -1) SAL.sleepFor(1);

        int [] res = new int [] {width.get(),height.get()};

        return res;

    }

    public static String getString(Context context, int resid) {
        return context.getString(resid);
    }

    public static int getSurfaceOrientation(int degrees) {

        if(degrees <= 45) { return Surface.ROTATION_0; }
        else if(degrees <= 135) { return Surface.ROTATION_270; }
        else if(degrees <= 225) { return Surface.ROTATION_180; }
        else if(degrees <= 315) { return Surface.ROTATION_90; }
        else { return Surface.ROTATION_0; }
    }

    public static void runLater(Context context, Runnable r) {
        ContextCompat.getMainExecutor(context).execute(r);
    }

    public static ColorStateList makeCSLwithID(Context context, int resId) {

        if(cslMap.containsKey(resId)) return cslMap.get(resId);

        ColorStateList entry = ContextCompat.getColorStateList(context,resId);

        cslMap.put(resId,entry);

        return entry;

    }

    /**
     * Generate a box-blurred image based on the input
     * Credits to Patrick Favre: https://stackoverflow.com/a/23119957
     * @param context Application context.
     * @param image Input image (And the output will go to it too!)
     */
    public static void blurImage(Context context,Bitmap image) {

        RenderScript rs = RenderScript.create(context);

        final Allocation input = Allocation.createCubemapFromBitmap(rs,image),
                         output = Allocation.createTyped(rs,input.getType());
        final ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));

        script.setRadius(8f);
        script.setInput(input);
        script.forEach(output);
        output.copyTo(image);
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
}
