package com.zsupport

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Получаем ссылки на элементы из XML
        val chineseButton = findViewById<Button>(R.id.chineseButton)
        val englishButton = findViewById<Button>(R.id.englishButton)
        val timezoneButton = findViewById<Button>(R.id.timezoneButton)
        val timezoneSpinner = findViewById<Spinner>(R.id.timezoneSpinner)
        val agreementCheckBox = findViewById<CheckBox>(R.id.checkBox)

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

        // Заполняем Spinner доступными часовыми поясами
        val timeZoneIds = TimeZone.getAvailableIDs().sorted()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeZoneIds).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        timezoneSpinner.adapter = adapter

        // Устанавливаем действие для кнопки изменения часового пояса
        timezoneButton.setOnClickListener {
            val selectedTimeZone = timezoneSpinner.selectedItem as String
            changeSystemTimeZone(selectedTimeZone)
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
}
