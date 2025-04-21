package com.zsupport.helpers

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.zsupport.R

/**
 * UIHelper - вспомогательный класс для UI операций.
 * 
 * Предоставляет методы для отображения пользовательских уведомлений
 * и других UI элементов.
 */
object UIHelper {

    /**
     * Отображает кастомный Toast с заданным сообщением.
     * Использует собственный макет для стилизации уведомления.
     * 
     * @param context Контекст приложения
     * @param message Текст сообщения для отображения
     */
    fun showCustomToast(context: Context, message: String) {
        // Inflate custom toast layout
        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(R.layout.custom_toast, null)

        // Set message text
        val textView = layout.findViewById<TextView>(R.id.toastText)
        textView.text = message

        // Create and show toast
        val toast = Toast(context)
        toast.duration = Toast.LENGTH_SHORT
        toast.view = layout
        toast.show()
    }
}
