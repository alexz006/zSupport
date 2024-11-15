package com.zsupport.helpers

import android.annotation.SuppressLint
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

class HoverUtils{

    @SuppressLint("ClickableViewAccessibility")
    fun setHover(vararg views : View){
        for (obj in views) {
            obj.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        view.animate().scaleX(0.98f).scaleY(0.98f).setInterpolator(DecelerateInterpolator(1f)).setDuration(80)
                            .start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        Handler().postDelayed({
                            view.animate().scaleX(1f).scaleY(1f).setInterpolator(AccelerateInterpolator()).alpha(1f).start()
                        }, 80)
                    }
                }
                return@OnTouchListener false
            })
        }
    }

}