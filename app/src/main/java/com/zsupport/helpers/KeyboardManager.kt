package com.zsupport.helpers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.util.Log

class KeyboardManager private constructor() {

    companion object {
        private var instance: KeyboardManager? = null

        fun getInstance(): KeyboardManager {
            if (instance == null) {
                instance = KeyboardManager()
            }
            return instance!!
        }
    }

    private val supportedKeyboards = listOf(
        "com.google.android.inputmethod.latin", // Gboard
        "ru.yandex.androidkeyboard"            // Yandex Keyboard
    )

    fun isKeyboardInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setKeyboard(context: Context, packageName: String) {
        if (isKeyboardInstalled(context, packageName)) {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } else {
            Log.e("KeyboardManager", "Keyboard $packageName is not installed.")
        }
    }

    fun selectKeyboard(context: Context, packageName: String) {
        if (isKeyboardInstalled(context, packageName)) {
            val inputMethodManager =
                context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val inputMethodId = getInputMethodId(context, packageName)
            if (inputMethodId != null) {
                inputMethodManager.setInputMethod(null, inputMethodId)
                Log.i("KeyboardManager", "Keyboard $packageName selected.")
            } else {
                Log.e("KeyboardManager", "Failed to get input method ID for $packageName.")
            }
        } else {
            Log.e("KeyboardManager", "Keyboard $packageName is not installed.")
        }
    }

    fun getCurrentKeyboard(context: Context): String? {
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentInputMethod = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        return currentInputMethod?.substringBefore('/')
    }

    private fun getInputMethodId(context: Context, packageName: String): String? {
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val inputMethods = inputMethodManager.enabledInputMethodList
        for (method in inputMethods) {
            if (method.packageName == packageName) {
                return method.id
            }
        }
        return null
    }
}
