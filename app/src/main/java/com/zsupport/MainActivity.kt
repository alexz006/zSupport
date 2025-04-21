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
import com.zsupport.helpers.KeyboardManager
import com.zsupport.helpers.PermissionsHelper
import com.zsupport.helpers.SystemHelper
import java.util.Locale
import java.util.TimeZone
import android.widget.ArrayAdapter
import com.addisonelliott.segmentedbutton.SegmentedButtonGroup
import com.zsupport.helpers.SwitchUSBHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.zsupport.helpers.UIHelper

/**
 * MainActivity - основной класс приложения, реализующий управление системными настройками
 * автомобильного головного устройства.
 *
 * Основные возможности:
 * - Изменение языка системы (китайский/английский)
 * - Управление часовым поясом (временно/постоянно)
 * - Управление приложениями (очистка кэша/данных, принудительная остановка)
 * - Переключение режимов USB (Host/Peripheral/Auto)
 * - Доступ к выбору клавиатуры
 */
class MainActivity : AppCompatActivity() {
    
    val TAG = "AnyAppSupport"

    /**
     * Карта известных приложений с сопоставлением их названий и пакетов
     */
    private val appNamesToPackages = mapOf(
        "Антирадар HUD Speed" to "air.StrelkaHUDFREE",
        "AnyApp Store" to "com.anyapp.store",
        "AnyApp Zee Store" to "com.anyapp.zee.store",
        "VK Store" to "ru.vk.store",
        "Яндекс Навигатор" to "ru.yandex.yandexnavi",
        "YTube AA" to "com.carwizard.li.youtube",
        "VK Video AA" to "com.anyapp.vkvideo"
    )

    /**
     * Вспомогательный объект для работы с USB режимами
     */
    private val usbHelper = SwitchUSBHelper()
    
    /**
     * Флаг для отслеживания программного изменения состояния
     */
    private var isProgrammaticChange = false
    
    /**
     * Текущая позиция переключателя USB режимов
     * Инициализируется значением -1, которое не соответствует ни одному состоянию
     */
    private var currentUSBPosition: Int = -1

    /**
     * Инициализация активности
     * Настраивает UI элементы и обработчики событий
     */
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

        val appSelector = findViewById<AutoCompleteTextView>(R.id.appSelector)
        val clearCacheButton = findViewById<Button>(R.id.clearCacheButton)
        val clearDataButton = findViewById<Button>(R.id.clearDataButton)
        val forceStopButton = findViewById<Button>(R.id.forceStopButton)
        val keyboardSelectButton = findViewById<ImageButton>(R.id.keyboardButton)

        val usbModeSwitcher = findViewById<SegmentedButtonGroup>(R.id.usbModeSwitcher)

        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Настраиваем эффект при наведении для кнопок
        HoverUtils().setHover(chineseButton, englishButton, timezoneButton, clearCacheButton, clearDataButton, forceStopButton, keyboardSelectButton)

        // Начально деактивируем кнопки
        chineseButton.isEnabled = false
        englishButton.isEnabled = false
        timezoneButton.isEnabled = false
        clearCacheButton.isEnabled = false
        clearDataButton.isEnabled = false
        forceStopButton.isEnabled = false

        // Настраиваем действие для чекбокса
        agreementCheckBox.setOnCheckedChangeListener { _, isChecked ->
            // Активируем или деактивируем кнопки в зависимости от состояния чекбокса
            updateButtonState(chineseButton, isChecked)
            updateButtonState(englishButton, isChecked)
            updateButtonState(timezoneButton, isChecked)
            updateButtonState(clearCacheButton, isChecked)
            updateButtonState(clearDataButton, isChecked)
            updateButtonState(forceStopButton, isChecked)
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

        // Получаем список всех доступных часовых поясов
        val timeZoneIds = TimeZone.getAvailableIDs()

        Log.i(TAG, "Timezone IDs: ${timeZoneIds.size}")
        Log.i(TAG, "Timezone IDs: ${timeZoneIds}")

        // Преобразуем ID часовых поясов в читаемый формат
        val readableTimeZones = timeZoneIds
            .map { timeZoneId -> Pair(timeZoneId, getReadableTimeZone(timeZoneId)) } // Создаем пары (ID, читаемый формат)
            .sortedBy { TimeZone.getTimeZone(it.first).rawOffset } // Сортируем по смещению
            .map { it.second } // Оставляем только читаемый формат

        val mutableTimeZoneIds = readableTimeZones.toMutableList() // Создаём изменяемую копию списка

        // Создаем адаптер для выпадающего списка с фильтрацией
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

        // Настраиваем поле выбора часового пояса
        val timezoneAutoComplete = findViewById<AutoCompleteTextView>(R.id.timezoneAutoComplete)
        timezoneAutoComplete.setAdapter(adapter)
        timezoneAutoComplete.threshold = 1 // Поиск начинается после ввода первого символа

        // Загружаем сохраненный часовой пояс, если он существует
        val savedTimeZone = getTimeZoneFromPrefs()
        if (!savedTimeZone.isNullOrEmpty()) {
            timezoneAutoComplete.setText(getReadableTimeZone(savedTimeZone))
            radioGroupTimezone.check(R.id.radioPermanent)
            Log.i(TAG, "Loaded saved timezone: $savedTimeZone")
        }

        // Настраиваем действие для кнопки изменения часового пояса
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
                Log.e(TAG, "Selected timezone not found in available IDs.")
                UIHelper.showCustomToast(this@MainActivity, "Invalid timezone selected")
            }
        }

        // Настраиваем действие для чекбокса автоопределения часового пояса
        autoDetectCheckbox.setOnCheckedChangeListener { _, isChecked ->
            setAutoTimeZoneEnabled(!isChecked)
        }

        // Скрываем клавиатуру при нажатии на корневой элемент
        findViewById<FrameLayout>(R.id.rootLayout).setOnTouchListener { _, _ ->
            hideKeyboard()
            false
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
                UIHelper.showCustomToast(this@MainActivity, "Cache cleared for $selectedAppName")
            } else {
                Log.e(TAG, "App not found: $selectedAppName")
                UIHelper.showCustomToast(this@MainActivity, "Please select a valid app")
            }
        }

        clearDataButton.setOnClickListener {
            val selectedAppName = appSelector.text.toString()
            val selectedPackage = appPackages.find { it.second == selectedAppName }?.first

            if (selectedPackage != null) {
                AppHelper.clearAppData(this, selectedPackage)
                UIHelper.showCustomToast(this@MainActivity, "Data cleared for $selectedAppName")
            } else {
                Log.e(TAG, "App not found: $selectedAppName")
                UIHelper.showCustomToast(this@MainActivity, "Please select a valid app")
            }
        }

        forceStopButton.setOnClickListener {
            val selectedAppName = appSelector.text.toString()
            val selectedPackage = appPackages.find { it.second == selectedAppName }?.first

            if (selectedPackage != null) {
                AppHelper.forceStopApp(this, selectedPackage)
                UIHelper.showCustomToast(this@MainActivity, "App stopped: $selectedAppName")
            } else {
                Log.e(TAG, "App not found: $selectedAppName")
                UIHelper.showCustomToast(this@MainActivity, "Please select a valid app")
            }
        }

        keyboardSelectButton.setOnClickListener {
            KeyboardManager.getInstance().showKeyboardDialog(this)
        }

        //////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////
        /////////////////// USB processing /////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////


        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUSBMode = usbHelper.getUSBMode()
                Log.i(TAG, "Current USB mode: ${usbHelper.formatUsbMode(currentUSBMode)}")

                val isAutoUSBperipheral = prefs.getBoolean("auto_usb_peripheral", false)

                withContext(Dispatchers.Main) {
                    var initialPosition = when (currentUSBMode) {
                        "0" -> 0
                        "1" -> 1
                        else -> {
                            Log.w(TAG, "Unknown USB mode: $currentUSBMode. Setting default to Peripheral.")
                            0
                        }
                    }

                    if (isAutoUSBperipheral) {
                        Log.d(TAG, "Auto USB peripheral mode enabled")
                        initialPosition = 2
                    }


                    // Установка позиции только если она отличается от текущей
                    if (currentUSBPosition != initialPosition) {
                        isProgrammaticChange = true
                        usbModeSwitcher.setPosition(initialPosition, false)
                        isProgrammaticChange = false
                        currentUSBPosition = initialPosition
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading USB mode", e)
                withContext(Dispatchers.Main) {
                    UIHelper.showCustomToast(this@MainActivity, "Failed to read USB mode")

                    if (currentUSBPosition != 0) {
                        isProgrammaticChange = true
                        usbModeSwitcher.setPosition(0, false)
                        isProgrammaticChange = false
                        currentUSBPosition = 0
                    }
                }
            }
        }

        usbModeSwitcher.onPositionChangedListener =
            SegmentedButtonGroup.OnPositionChangedListener { position ->
                if (isProgrammaticChange) {
                    Log.i(TAG, "Ignoring programmatic position change")
                    return@OnPositionChangedListener // Игнорируем вызов
                }

                if (position == currentUSBPosition) {
                    Log.i(TAG, "Position unchanged. No action needed.")
                    return@OnPositionChangedListener // Если позиция не изменилась, ничего не делаем
                }

                val newMode = if (position == 0 || position == 2) "0" else "1"

                CoroutineScope(Dispatchers.IO).launch {
                    var isSuccess = usbHelper.setUSBMode(newMode)

                    //isSuccess = true // Remove this line after implementing the actual functionality

                    withContext(Dispatchers.Main) {
                        if (isSuccess) {

                            if (position == 2) {
                                prefs.edit().putBoolean("auto_usb_peripheral", true).apply()
                            } else {
                                prefs.edit().putBoolean("auto_usb_peripheral", false).apply()
                            }

                            currentUSBPosition = position
                            UIHelper.showCustomToast(this@MainActivity, "USB Mode set to ${usbHelper.formatUsbMode(newMode)}")
                            Log.i(TAG, "USB Mode successfully set to $newMode")
                        } else {
                            UIHelper.showCustomToast(this@MainActivity, "Failed to set USB Mode")
                            Log.e(TAG, "Failed to set USB Mode to $newMode")

                            isProgrammaticChange = true
                            usbModeSwitcher.setPosition(currentUSBPosition, true) // Возвращаем в исходную позицию
                            isProgrammaticChange = false
                        }
                    }
                }
            }
    }

    override fun onResume() {
        super.onResume()

        val statusTextView = findViewById<TextView>(R.id.statusTextView)
        val permissionsHelper = PermissionsHelper()

        val installedApps = AppHelper.getInstalledApps(this).map { it.first }

        //Log.i(TAG, "Installed apps: $installedApps")

        val statusBuilder = StringBuilder()
        appNamesToPackages.forEach { (appName, packageName) ->
            if (installedApps.contains(packageName)) {
                permissionsHelper.applyPermissions(this, packageName)
                statusBuilder.append("$appName: найден. Права: выданы.\n")
                Log.i(TAG, "$appName ($packageName) найден. Права выданы.")
            } else {
                //statusBuilder.append("$appName: не найден.\n")
                Log.w(TAG, "$appName ($packageName) не найден.")
            }
        }

        statusTextView.text = statusBuilder.toString().trim()
    }


    private fun changeSystemLanguage(locale: Locale) {
        Log.i(TAG, "Changing system language to $locale")

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
            Log.i(TAG, "System language updated successfully.")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to change language: ${e.message}", e)
        }
    }

    private fun changeSystemTimeZone(timeZoneId: String) {
        Log.i(TAG, "Changing system timezone to $timeZoneId")

        try {
            val alarmManagerClass = Class.forName("android.app.AlarmManager")
            val setTimeZoneMethod = alarmManagerClass.getDeclaredMethod("setTimeZone", String::class.java)

            val alarmManager = getSystemService(ALARM_SERVICE)
            setTimeZoneMethod.invoke(alarmManager, timeZoneId)

            BackupManager.dataChanged("com.android.providers.settings")
            Log.i(TAG, "System timezone updated successfully to $timeZoneId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to change timezone: ${e.message}", e)
        }
    }

    private fun setSystemTimeZonePermanent(timeZoneId: String) {
        Log.i(TAG, "Setting permanent system timezone to $timeZoneId")
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

            Log.i(TAG, "System timezone set permanently to $timeZoneId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set timezone permanently: ${e.message}", e)
        }
    }


    private fun setAutoTimeZoneEnabled(enabled: Boolean) {
        try {
            Settings.Global.putInt(contentResolver, Settings.Global.AUTO_TIME_ZONE, if (enabled) 1 else 0)
            Log.i(TAG, "Auto timezone detection set to ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change auto timezone setting: ${e.message}", e)
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

        //Log.i(TAG, "CityName: $cityName GMT: $gmtOffset")

        return "$cityName ($gmtOffset)"
    }

    private fun saveTimeZoneToPrefs(timeZoneId: String) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putString("selected_time_zone", timeZoneId).apply()
        Log.i(TAG, "TimeZone saved to prefs: $timeZoneId")
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

        val TAG = "AnyAppSupport"

        override fun onReceive(context: Context, intent: Intent?) {

            Log.d(TAG, "Context class: ${context.javaClass.name}")

            if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val selectedTimeZone = prefs.getString("selected_time_zone", null)
                val isAutoUSBperitheral = prefs.getBoolean("auto_usb_peripheral", false)

                Log.d(TAG, "Auto USB peripheral mode: $isAutoUSBperitheral.")

                if (isAutoUSBperitheral) {
                    try {
                        val usbHelper = SwitchUSBHelper()
                        usbHelper.setUSBMode("0")
                        Log.d(TAG, "USB mode switched to peripheral on system boot.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to switch USB mode on system boot: ${e.message}", e)
                    }
                }

                if (!selectedTimeZone.isNullOrEmpty()) {
                    try {
                        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                        val setTimeZoneMethod = android.app.AlarmManager::class.java.getDeclaredMethod("setTimeZone", String::class.java)
                        setTimeZoneMethod.invoke(alarmManager, selectedTimeZone)

                        Log.i(TAG, "Time zone synchronized to $selectedTimeZone")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to synchronize time zone: ${e.message}", e)
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


    @SuppressLint("UseCompatLoadingForDrawables")
    private fun updateButtonState(button: Button, isEnabled: Boolean) {
        button.isEnabled = isEnabled
        val style = if (isEnabled) R.style.CustomButtonStyle else R.style.CustomOFFButtonStyle
        button.setTextAppearance(this, style)
        button.background = getDrawable(if (isEnabled) R.drawable.custom_button_background else R.drawable.custom_button_off_background)
    }



}
