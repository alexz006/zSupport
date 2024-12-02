package com.zsupport.helpers

import android.content.Context
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.zsupport.R

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

    private val supportedKeyboards = mapOf(
        "Gboard" to "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",
        "Yandex Keyboard" to "ru.yandex.androidkeyboard/com.android.inputmethod.latin.LatinIME",
        "Microsoft Swift" to "com.touchtype.swiftkey/com.touchtype.KeyboardService"
    )

    private val TAG = "KeyboardManager"

    /**
     * Проверяет, установлена ли клавиатура по пакету.
     */
    fun isKeyboardInstalled(context: Context, packageName: String): Boolean {
        Log.d(TAG, "Checking if keyboard is installed: $packageName")
        val packageManager = context.packageManager
        return try {
            packageManager.getPackageInfo(packageName.split("/")[0], 0)
            Log.d(TAG, "Keyboard $packageName is installed")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Keyboard $packageName is not installed: ${e.message}")
            false
        }
    }

    /**
     * Включает клавиатуру в настройках.
     */
    fun enableKeyboard(context: Context, inputMethod: String) {
        Log.d(TAG, "Enabling keyboard: $inputMethod")
        try {
            val enabledInputMethods = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS
            ) ?: ""
            Log.d(TAG, "Currently enabled keyboards: $enabledInputMethods")

            if (!enabledInputMethods.contains(inputMethod)) {
                val newEnabledInputMethods = if (enabledInputMethods.isEmpty()) {
                    inputMethod
                } else {
                    "$enabledInputMethods:$inputMethod"
                }

                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_INPUT_METHODS,
                    newEnabledInputMethods
                )
                Log.d(TAG, "Keyboard $inputMethod enabled")
            } else {
                Log.d(TAG, "Keyboard $inputMethod is already enabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable keyboard $inputMethod: ${e.message}", e)
        }
    }

    /**
     * Выбирает клавиатуру как основную.
     */
    fun selectKeyboard(context: Context, inputMethod: String) {
        Log.d(TAG, "Selecting keyboard: $inputMethod")
        try {
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
                inputMethod
            )
            Log.d(TAG, "Keyboard $inputMethod selected as default")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to select keyboard $inputMethod as default: ${e.message}", e)
        }
    }

    /**
     * Возвращает текущую выбранную клавиатуру.
     */
    fun getCurrentKeyboard(context: Context): String? {
        return try {
            val currentKeyboard = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            Log.d(TAG, "Current keyboard: $currentKeyboard")
            currentKeyboard
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current keyboard: ${e.message}", e)
            null
        }
    }

    /**
     * Конфигурация и выбор клавиатуры.
     */
    fun configureAndSelectKeyboard(context: Context, keyboardName: String) {
        val inputMethod = supportedKeyboards[keyboardName]
        if (inputMethod != null) {
            if (isKeyboardInstalled(context, inputMethod)) {
                enableKeyboard(context, inputMethod)
                selectKeyboard(context, inputMethod)
                Toast.makeText(context, "$keyboardName activated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "$keyboardName not installed", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "Unsupported keyboard: $keyboardName")
        }
    }

    /**
     * Показывает диалог выбора клавиатуры.
     */
    fun showKeyboardDialog(context: Context) {
        Log.d(TAG, "Showing keyboard selection dialog")
        val currentKeyboard = getCurrentKeyboard(context)
        val currentKeyboardName = supportedKeyboards.entries.find { it.value == currentKeyboard }?.key
            ?: "Unknown"

        val dialogView = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dialog_keyboard_selection, null)

        val currentInputMethodTextView = dialogView.findViewById<android.widget.TextView>(R.id.currentInputMethodTextView)
        val gboardButton = dialogView.findViewById<android.widget.Button>(R.id.gboardButton)
        val yandexButton = dialogView.findViewById<android.widget.Button>(R.id.yandexButton)
        val microsoftButton = dialogView.findViewById<android.widget.Button>(R.id.msswiftButton)

        currentInputMethodTextView.text = "Current Input Method: $currentKeyboardName"

        gboardButton.setOnClickListener {
            configureAndSelectKeyboard(context, "Gboard")
        }

        yandexButton.setOnClickListener {
            configureAndSelectKeyboard(context, "Yandex Keyboard")
        }

        microsoftButton.setOnClickListener {
            configureAndSelectKeyboard(context, "Microsoft Swift")
        }

        val builder = AlertDialog.Builder(context)
        builder.setView(dialogView)
        builder.setNegativeButton("Close", null)
        builder.show()
    }
}
