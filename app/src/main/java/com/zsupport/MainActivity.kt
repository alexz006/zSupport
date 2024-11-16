package com.zsupport

import android.annotation.SuppressLint
import android.app.backup.BackupManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.zsupport.helpers.HoverUtils
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Получаем ссылки на элементы из XML
        val chineseButton = findViewById<Button>(R.id.chineseButton)
        val englishButton = findViewById<Button>(R.id.englishButton)
        val agreementCheckBox = findViewById<CheckBox>(R.id.checkBox)
        val timezoneButton = findViewById<Button>(R.id.timezoneButton)


        HoverUtils().setHover(chineseButton, englishButton, timezoneButton)

        // Начально деактивируем кнопки
        chineseButton.isEnabled = false
        englishButton.isEnabled = false
        timezoneButton.isEnabled = false

        // Настраиваем действие для чекбокса
        agreementCheckBox.setOnCheckedChangeListener { _, isChecked ->
            // Активируем или деактивируем кнопки в зависимости от состояния чекбокса
            chineseButton.isEnabled = isChecked
            englishButton.isEnabled = isChecked
            timezoneButton.isEnabled = isChecked
        }

        // Настраиваем действия для кнопок
        chineseButton.setOnClickListener { changeSystemLanguage(Locale.CHINA) }
        englishButton.setOnClickListener { changeSystemLanguage(Locale.ENGLISH) }

        val timeZoneIds = TimeZone.getAvailableIDs()
        val readableTimeZones = timeZoneIds
            .map { timeZoneId -> Pair(timeZoneId, getReadableTimeZone(timeZoneId)) } // Создаем пары (ID, читаемый формат)
            .sortedBy { TimeZone.getTimeZone(it.first).rawOffset } // Сортируем по смещению
            .map { it.second } // Оставляем только читаемый формат

        // Настраиваем адаптер
        val adapter = ArrayAdapter(this, R.layout.custom_spinner_item, readableTimeZones).apply {
            setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
        }

        val timezoneAutoComplete = findViewById<AutoCompleteTextView>(R.id.timezoneAutoComplete)
        timezoneAutoComplete.setAdapter(adapter)
        timezoneAutoComplete.threshold = 1 // Поиск начинается после ввода первого символа

        // Устанавливаем действие для кнопки изменения часового пояса
        timezoneButton.setOnClickListener {
            val selectedTimeZoneDisplay = timezoneAutoComplete.text.toString()
            val selectedTimeZoneId = timeZoneIds[readableTimeZones.indexOf(selectedTimeZoneDisplay)]
            changeSystemTimeZone(selectedTimeZoneId)
        }

    }

    private fun changeSystemLanguage(locale: Locale) {
        Log.e("MainActivity", "Changing system language to $locale")

        try {
            val amnClass = Class.forName("android.app.ActivityManagerNative")
            val amnInstance = amnClass.getMethod("getDefault").invoke(null)

            val config = amnInstance.javaClass
                .getMethod("getConfiguration")
                .invoke(amnInstance) as Configuration

            config.locale = locale
            config.javaClass.getField("userSetLocale").setBoolean(config, true)

            amnInstance.javaClass
                .getMethod("updateConfiguration", Configuration::class.java)
                .invoke(amnInstance, config)

            BackupManager.dataChanged("com.android.providers.settings")
            Log.e("MainActivity", "System language updated successfully.")

        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to change language: ${e.message}", e)
        }
    }

    private fun changeSystemTimeZone(timeZoneId: String) {
        Log.e("MainActivity", "Changing system timezone to $timeZoneId")

        try {
            val alarmManagerClass = Class.forName("android.app.AlarmManager")
            val setTimeZoneMethod = alarmManagerClass.getDeclaredMethod("setTimeZone", String::class.java)

            val alarmManager = getSystemService(ALARM_SERVICE)
            setTimeZoneMethod.invoke(alarmManager, timeZoneId)

            BackupManager.dataChanged("com.android.providers.settings")
            Log.e("MainActivity", "System timezone updated successfully to $timeZoneId")

        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to change timezone: ${e.message}", e)
        }
    }

    // Функция для преобразования часового пояса в читаемый формат
    fun getReadableTimeZone(timeZoneId: String): String {
        val timeZone = TimeZone.getTimeZone(timeZoneId)
        val offset = timeZone.rawOffset / (60 * 60 * 1000) // Смещение в часах
        val offsetMinutes = Math.abs(timeZone.rawOffset / (60 * 1000) % 60) // Минуты для неполных часов
        val offsetSign = if (offset >= 0) "+" else "-"
        val gmtOffset = if (offsetMinutes > 0) {
            "GMT$offsetSign$offset:${String.format("%02d", offsetMinutes)}"
        } else {
            "GMT$offsetSign$offset"
        }

        val cityName = timeZoneId.substringAfterLast('/') // Получаем название города/области
            .replace('_', ' ') // Преобразуем подчеркивания в пробелы

        return "$cityName ($gmtOffset)"
    }
}
