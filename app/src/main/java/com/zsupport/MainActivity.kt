package com.zsupport

import android.annotation.SuppressLint
import android.app.backup.BackupManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.zsupport.helpers.AppHelper
import com.zsupport.helpers.HoverUtils
import com.zsupport.helpers.SystemHelper
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
        val autoDetectCheckbox = findViewById<CheckBox>(R.id.autoDetectTimezoneCheckbox)
        val radioGroupTimezone = findViewById<RadioGroup>(R.id.radioGroupTimezone)
        val rebootButton = findViewById<Button>(R.id.rebootButton)

        val appSelector = findViewById<AutoCompleteTextView>(R.id.appSelector)
        val clearCacheButton = findViewById<Button>(R.id.clearCacheButton)
        val clearDataButton = findViewById<Button>(R.id.clearDataButton)
        val forceStopButton = findViewById<Button>(R.id.forceStopButton)


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
        chineseButton.setOnClickListener {
            changeSystemLanguage(Locale.CHINA)
            hideKeyboard()
        }
        englishButton.setOnClickListener {
            changeSystemLanguage(Locale.ENGLISH)
            hideKeyboard()
        }

        val timeZoneIds = TimeZone.getAvailableIDs()
        val readableTimeZones = timeZoneIds
            .map { timeZoneId -> Pair(timeZoneId, getReadableTimeZone(timeZoneId)) } // Создаем пары (ID, читаемый формат)
            .sortedBy { TimeZone.getTimeZone(it.first).rawOffset } // Сортируем по смещению
            .map { it.second } // Оставляем только читаемый формат

        val mutableTimeZoneIds = readableTimeZones.toMutableList() // Создаём изменяемую копию списка

        val adapter = object : ArrayAdapter<String>(this, R.layout.custom_spinner_item, mutableTimeZoneIds) {
            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val filteredList = if (constraint.isNullOrEmpty()) {
                            readableTimeZones
                        } else {
                            readableTimeZones.filter { it.contains(constraint, ignoreCase = true) }
                        }

                        return FilterResults().apply {
                            values = filteredList
                        }
                    }


                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        val filteredList = results?.values as? List<String> ?: emptyList()
                        clear() // Очищаем текущий список адаптера
                        addAll(filteredList) // Добавляем отфильтрованные значения
                        notifyDataSetChanged()
                    }
                }
            }
        }


        val timezoneAutoComplete = findViewById<AutoCompleteTextView>(R.id.timezoneAutoComplete)
        timezoneAutoComplete.setAdapter(adapter)
        timezoneAutoComplete.threshold = 1 // Поиск начинается после ввода первого символа

        val savedTimeZone = getTimeZoneFromPrefs()
        if (!savedTimeZone.isNullOrEmpty()) {
            timezoneAutoComplete.setText(getReadableTimeZone(savedTimeZone))
            radioGroupTimezone.check(R.id.radioPermanent)
            Log.e("MainActivity", "Loaded saved timezone: $savedTimeZone")
        }

        timezoneButton.setOnClickListener {

            hideKeyboard()

            val selectedTimeZoneDisplay = timezoneAutoComplete.text.toString()

            // Получаем идентификатор таймзоны на основе читаемого формата
            val selectedTimeZoneId = timeZoneIds.find { id ->
                getReadableTimeZone(id) == selectedTimeZoneDisplay
            }

            if (selectedTimeZoneId != null) {
                val isPermanent = radioGroupTimezone.checkedRadioButtonId == R.id.radioPermanent
                if (isPermanent) {
                    setSystemTimeZonePermanent(selectedTimeZoneId)
                    saveTimeZoneToPrefs(selectedTimeZoneId)
                } else {
                    changeSystemTimeZone(selectedTimeZoneId)
                    clearTimeZonePrefs()
                }
            } else {
                Log.e("MainActivity", "Selected timezone not found in available IDs.")
                Toast.makeText(this, "Invalid timezone selected", Toast.LENGTH_SHORT).show()
            }
        }

        autoDetectCheckbox.setOnCheckedChangeListener { _, isChecked ->
            setAutoTimeZoneEnabled(!isChecked)
        }

        findViewById<FrameLayout>(R.id.rootLayout).setOnTouchListener { _, _ ->
            hideKeyboard()
            false
        }

        rebootButton.setOnClickListener {
            SystemHelper.showRebootDialog(this)
        }


        //////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////
        /////////////////// Apps processing /////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////

        // Получение списка приложений
        val appPackages = AppHelper.getInstalledApps(this) // Список приложений
        val appNames = appPackages.map { it.second } // Имена приложений

        // Настройка адаптера для Spinner
        val appAdapter = ArrayAdapter(this, R.layout.custom_spinner_item, appNames)
        appSelector.setAdapter(appAdapter)
        appSelector.threshold = 1

        // Обработка нажатий кнопок
        clearCacheButton.setOnClickListener {
            val selectedAppName = appSelector.text.toString()
            val selectedPackage = appPackages.find { it.second == selectedAppName }?.first

            if (selectedPackage != null) {
                AppHelper.clearAppCache(this, selectedPackage)
                Toast.makeText(this, "Cache cleared for $selectedAppName", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("MainActivity", "App not found: $selectedAppName")
                Toast.makeText(this, "Please select a valid app", Toast.LENGTH_SHORT).show()
            }
        }

        clearDataButton.setOnClickListener {
            val selectedAppName = appSelector.text.toString()
            val selectedPackage = appPackages.find { it.second == selectedAppName }?.first

            if (selectedPackage != null) {
                AppHelper.clearAppData(this, selectedPackage)
                Toast.makeText(this, "Data cleared for $selectedAppName", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("MainActivity", "App not found: $selectedAppName")
                Toast.makeText(this, "Please select a valid app", Toast.LENGTH_SHORT).show()
            }
        }

        forceStopButton.setOnClickListener {
            val selectedAppName = appSelector.text.toString()
            val selectedPackage = appPackages.find { it.second == selectedAppName }?.first

            if (selectedPackage != null) {
                AppHelper.forceStopApp(this, selectedPackage)
                Toast.makeText(this, "App stopped: $selectedAppName", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("MainActivity", "App not found: $selectedAppName")
                Toast.makeText(this, "Please select a valid app", Toast.LENGTH_SHORT).show()
            }
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

    private fun setSystemTimeZonePermanent(timeZoneId: String) {
        Log.e("MainActivity", "Setting permanent system timezone to $timeZoneId")
        try {
            // Используем рефлексию для доступа к Settings.Global
            val settingsGlobalClass = Class.forName("android.provider.Settings\$Global")
            val putStringMethod = settingsGlobalClass.getDeclaredMethod(
                "putString",
                android.content.ContentResolver::class.java,
                String::class.java,
                String::class.java
            )

            // Вызываем метод putString через рефлексию
            putStringMethod.invoke(null, contentResolver, "time_zone", timeZoneId)

            // Уведомляем систему об изменении времени
            val amnClass = Class.forName("android.app.AlarmManager")
            val amnInstance = getSystemService(ALARM_SERVICE)
            val setTimeZoneMethod = amnClass.getDeclaredMethod("setTimeZone", String::class.java)
            setTimeZoneMethod.invoke(amnInstance, timeZoneId)

            Log.e("MainActivity", "System timezone set permanently to $timeZoneId")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to set timezone permanently: ${e.message}", e)
        }
    }


    private fun setAutoTimeZoneEnabled(enabled: Boolean) {
        try {
            Settings.Global.putInt(contentResolver, Settings.Global.AUTO_TIME_ZONE, if (enabled) 1 else 0)
            Log.e("MainActivity", "Auto timezone detection set to ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to change auto timezone setting: ${e.message}", e)
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

    private fun saveTimeZoneToPrefs(timeZoneId: String) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putString("selected_time_zone", timeZoneId).apply()
        Log.e("MainActivity", "TimeZone saved to prefs: $timeZoneId")
    }

    private fun getTimeZoneFromPrefs(): String? {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return prefs.getString("selected_time_zone", null)
    }

    private fun clearTimeZonePrefs() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().remove("selected_time_zone").apply()
    }

    class TimeZoneSyncReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val selectedTimeZone = prefs.getString("selected_time_zone", null)

                if (!selectedTimeZone.isNullOrEmpty()) {
                    try {
                        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                        val setTimeZoneMethod = android.app.AlarmManager::class.java.getDeclaredMethod("setTimeZone", String::class.java)
                        setTimeZoneMethod.invoke(alarmManager, selectedTimeZone)

                        Log.e("TimeZoneSyncReceiver", "Time zone synchronized to $selectedTimeZone")
                    } catch (e: Exception) {
                        Log.e("TimeZoneSyncReceiver", "Failed to synchronize time zone: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }


}
