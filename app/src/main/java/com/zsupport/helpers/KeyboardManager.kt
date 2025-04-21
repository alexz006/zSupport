package com.zsupport.helpers

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.zsupport.R

/**
 * KeyboardManager - класс для управления клавиатурами на устройстве.
 * 
 * Реализует паттерн Singleton для обеспечения единой точки доступа к функциональности
 * управления клавиатурами. Позволяет проверять наличие, включать и выбирать различные
 * клавиатуры в системе.
 */
class KeyboardManager private constructor() {

    companion object {
        private var instance: KeyboardManager? = null
        
        /**
         * Возвращает единственный экземпляр класса KeyboardManager (Singleton).
         * 
         * @return Экземпляр KeyboardManager
         */
        fun getInstance(): KeyboardManager {
            if (instance == null) {
                instance = KeyboardManager()
            }
            return instance!!
        }
    }

    /**
     * Карта поддерживаемых клавиатур с сопоставлением имен и их идентификаторов
     */
    private val supportedKeyboards = mapOf(
        "Gboard" to "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",
        "Yandex Keyboard" to "ru.yandex.androidkeyboard/com.android.inputmethod.latin.LatinIME",
        "Microsoft Swift" to "com.touchtype.swiftkey/com.touchtype.KeyboardService"
    )

    private val TAG = "KeyboardManager"

    /**
     * Проверяет, установлена ли клавиатура по идентификатору пакета.
     * 
     * @param context Контекст приложения
     * @param packageName Идентификатор пакета клавиатуры
     * @return true если клавиатура установлена, false в противном случае
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
     * Включает указанную клавиатуру в настройках системы.
     * Добавляет клавиатуру в список разрешенных методов ввода.
     * 
     * @param context Контекст приложения
     * @param inputMethod Идентификатор метода ввода (клавиатуры)
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
     * Устанавливает указанную клавиатуру как метод ввода по умолчанию.
     * 
     * @param context Контекст приложения
     * @param inputMethod Идентификатор метода ввода (клавиатуры)
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
     * Возвращает идентификатор клавиатуры, выбранной в данный момент как метод ввода по умолчанию.
     * 
     * @param context Контекст приложения
     * @return Идентификатор текущего метода ввода или null в случае ошибки
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
     * Выполняет проверку наличия, включение и выбор указанной клавиатуры.
     * 
     * @param context Контекст приложения
     * @param keyboardName Название клавиатуры из списка поддерживаемых
     */
    fun configureAndSelectKeyboard(context: Context, keyboardName: String) {
        val inputMethod = supportedKeyboards[keyboardName]
        if (inputMethod != null) {
            if (isKeyboardInstalled(context, inputMethod)) {
                enableKeyboard(context, inputMethod)
                selectKeyboard(context, inputMethod)
                UIHelper.showCustomToast(context, "$keyboardName activated")
            } else {
                UIHelper.showCustomToast(context, "$keyboardName not installed")
            }
        } else {
            Log.e(TAG, "Unsupported keyboard: $keyboardName")
        }
    }

    /**
     * Отображает диалоговое окно выбора клавиатуры с возможностью смены текущего метода ввода.
     * 
     * @param context Контекст приложения
     */
    fun showKeyboardDialog(context: Context) {
        Log.d(TAG, "Showing keyboard selection dialog")

        val dialogBuilder = AlertDialog.Builder(context)
        val dialogView = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dialog_keyboard_selection, null)

        val currentInputMethodTextView = dialogView.findViewById<android.widget.TextView>(R.id.currentInputMethodTextView)
        val gboardButton = dialogView.findViewById<android.widget.Button>(R.id.gboardButton)
        val yandexButton = dialogView.findViewById<android.widget.Button>(R.id.yandexButton)
        val microsoftButton = dialogView.findViewById<android.widget.Button>(R.id.msswiftButton)

        /**
         * Обновляет UI диалога в соответствии с текущим выбранным методом ввода.
         * Выделяет кнопку активной клавиатуры.
         */
        fun updateUI() {
            val currentKeyboard = getCurrentKeyboard(context)
            val currentKeyboardName = supportedKeyboards.entries.find { it.value == currentKeyboard }?.key ?: "Unknown"
            currentInputMethodTextView.text = "Current Input Method: $currentKeyboardName"

            val buttons = mapOf(
                "Gboard" to gboardButton,
                "Yandex Keyboard" to yandexButton,
                "Microsoft Swift" to microsoftButton
            )

            buttons.forEach { (name, button) ->
                if (name == currentKeyboardName) {
                    button.setBackgroundResource(R.drawable.custom_button_selected_background)
                } else {
                    button.setBackgroundResource(R.drawable.custom_button_background)
                }
            }
        }

        // Обновляем UI перед отображением диалога
        updateUI()

        // Устанавливаем слушатели кнопок
        gboardButton.setOnClickListener {
            configureAndSelectKeyboard(context, "Gboard")
            updateUI() // Обновляем UI после изменения
        }

        yandexButton.setOnClickListener {
            configureAndSelectKeyboard(context, "Yandex Keyboard")
            updateUI()
        }

        microsoftButton.setOnClickListener {
            configureAndSelectKeyboard(context, "Microsoft Swift")
            updateUI()
        }

        dialogBuilder.setView(dialogView)
        dialogBuilder.setNegativeButton("Close", null)

        val dialog = dialogBuilder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }
}
