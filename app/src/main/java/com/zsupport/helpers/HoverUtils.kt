package com.zsupport.helpers

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * HoverUtils - вспомогательный класс для добавления эффектов наведения (hover) к UI элементам.
 * 
 * Предоставляет функционал для создания анимированных эффектов при взаимодействии пользователя
 * с элементами интерфейса.
 */
class HoverUtils {

    /**
     * Создаем Handler с явным указанием основного потока для предотвращения утечек и соответствия
     * рекомендациям Android для новых API.
     */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Применяет эффект анимации при нажатии и отпускании к указанным View.
     * При нажатии (ACTION_DOWN) элемент немного уменьшается, создавая эффект нажатия.
     * При отпускании (ACTION_UP, ACTION_CANCEL) элемент возвращается к исходному размеру.
     * 
     * @param views Варарг объектов View, к которым будет применен эффект
     */
    @SuppressLint("ClickableViewAccessibility")
    fun setHover(vararg views : View) {
        for (obj in views) {
            obj.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        view.animate().scaleX(0.98f).scaleY(0.98f).setInterpolator(DecelerateInterpolator(1f)).setDuration(80)
                            .start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        mainHandler.postDelayed({
                            view.animate().scaleX(1f).scaleY(1f).setInterpolator(AccelerateInterpolator()).alpha(1f).start()
                        }, 80)
                    }
                }
                return@OnTouchListener false
            })
        }
    }
}