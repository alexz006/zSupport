package com.zsupport

import android.app.backup.BackupManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val chineseButton = Button(this).apply {
            text = "CHINESE"
            setOnClickListener { changeSystemLanguage(Locale.CHINA) }
        }

        val englishButton = Button(this).apply {
            text = "ENGLISH"
            setOnClickListener { changeSystemLanguage(Locale.ENGLISH) }
        }

        // Создаем Spinner для выбора часового пояса
        val timezoneSpinner = Spinner(this)
        val timeZoneIds = TimeZone.getAvailableIDs().sorted() // Список всех доступных тайм-зон
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeZoneIds).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        timezoneSpinner.adapter = adapter

        val timezoneButton = Button(this).apply {
            text = "Set Timezone"
            setOnClickListener {
                val selectedTimeZone = timezoneSpinner.selectedItem as String
                changeSystemTimeZone(selectedTimeZone)
            }
        }

        layout.addView(chineseButton)
        layout.addView(englishButton)
        layout.addView(timezoneSpinner)
        layout.addView(timezoneButton)
        setContentView(layout)
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
