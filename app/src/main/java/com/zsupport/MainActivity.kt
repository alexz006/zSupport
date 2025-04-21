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
import com.zsupport.helpers.TimeZoneHelper
import com.zsupport.helpers.TimeZoneSyncReceiver
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import android.widget.ArrayAdapter
import com.addisonelliott.segmentedbutton.SegmentedButtonGroup
import com.zsupport.helpers.SwitchUSBHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
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
     * Scope для запуска корутин, привязанный к жизненному циклу активности
     */
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

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
        "VK Video AA" to "com.anyapp.vkvideo",
        "Back Button" to "mavie.shadowsong.bb"
    )

    /**
     * Вспомогательный объект для работы с USB режимами
     */
    private val usbHelper = SwitchUSBHelper()
    
    /**
     * Вспомогательный объект для работы с часовыми поясами
     */
    private val timeZoneHelper = TimeZoneHelper()
    
    /**
     * Флаг для отслеживания программного изменения состояния.
     * Используется AtomicBoolean для потокобезопасных операций.
     */
    private val isProgrammaticChange = AtomicBoolean(false)
    
    /**
     * Текущая позиция переключателя USB режимов
     * Инициализируется значением -1, которое не соответствует ни одному состоянию.
     * Используется AtomicInteger для потокобезопасных операций.
     */
    private val currentUSBPosition = AtomicInteger(-1)

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
        val versionTextView = findViewById<TextView>(R.id.versionTextView)

        val usbModeSwitcher = findViewById<SegmentedButtonGroup>(R.id.usbModeSwitcher)

        // Устанавливаем текст версии приложения
        val appVersion = SystemHelper.getAppVersion(this)
        versionTextView.text = "v${appVersion}"

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
            .map { timeZoneId -> Pair(timeZoneId, timeZoneHelper.getReadableTimeZone(timeZoneId)) } // Создаем пары (ID, читаемый формат)
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
        timezoneAutoComplete.hint = getString(R.string.select_timezone_hint)

        // Загружаем сохраненный часовой пояс, если он существует
        val savedTimeZone = timeZoneHelper.getTimeZoneFromPrefs(this)
        if (!savedTimeZone.isNullOrEmpty()) {
            timezoneAutoComplete.setText(timeZoneHelper.getReadableTimeZone(savedTimeZone))
            radioGroupTimezone.check(R.id.radioPermanent)
            Log.i(TAG, "Loaded saved timezone: $savedTimeZone")
        }

        // Настраиваем действие для кнопки изменения часового пояса
        timezoneButton.setOnClickListener {
            hideKeyboard()

            val selectedTimeZoneDisplay = timezoneAutoComplete.text.toString()

            // Получаем идентификатор таймзоны на основе читаемого формата
            val selectedTimeZoneId = timeZoneIds.find { id ->
                timeZoneHelper.getReadableTimeZone(id) == selectedTimeZoneDisplay
            }

            if (selectedTimeZoneId != null) {
                val isPermanent = radioGroupTimezone.checkedRadioButtonId == R.id.radioPermanent
                if (isPermanent) {
                    // Отключаем автоматическое определение часового пояса
                    timeZoneHelper.setAutoTimeZoneEnabled(this, false)
                    
                    // Применяем постоянное изменение часового пояса
                    val success = timeZoneHelper.setSystemTimeZonePermanent(this, selectedTimeZoneId)
                    
                    if (success) {
                        // Сохраняем в настройках только если успешно установили
                        timeZoneHelper.saveTimeZoneToPrefs(this, selectedTimeZoneId)
                        UIHelper.showCustomToast(this@MainActivity, "Timezone permanently set to ${selectedTimeZoneDisplay}")
                        
                        // Проверяем текущий часовой пояс для отладки
                        val currentTimeZone = Settings.Global.getString(contentResolver, "time_zone")
                        Log.i(TAG, "Current timezone setting after change: $currentTimeZone")
                    } else {
                        UIHelper.showCustomToast(this@MainActivity, getString(R.string.invalid_timezone_selected))
                    }
                } else {
                    // Временное изменение часового пояса
                    val success = timeZoneHelper.changeSystemTimeZone(this, selectedTimeZoneId)
                    if (success) {
                        UIHelper.showCustomToast(this@MainActivity, "Timezone temporarily set to ${selectedTimeZoneDisplay}")
                        timeZoneHelper.clearTimeZonePrefs(this)
                    } else {
                        UIHelper.showCustomToast(this@MainActivity, getString(R.string.invalid_timezone_selected))
                    }
                }
            } else {
                Log.e(TAG, "Selected timezone not found in available IDs.")
                UIHelper.showCustomToast(this@MainActivity, getString(R.string.invalid_timezone_selected))
            }
        }

        // Настраиваем действие для чекбокса автоопределения часового пояса
        autoDetectCheckbox.setOnCheckedChangeListener { _, isChecked ->
            val success = timeZoneHelper.setAutoTimeZoneEnabled(this, !isChecked)
            if (success) {
                if (isChecked) {
                    UIHelper.showCustomToast(this@MainActivity, "Auto timezone detection disabled")
                } else {
                    UIHelper.showCustomToast(this@MainActivity, "Auto timezone detection enabled")
                }
            }
        }

        // Проверяем и устанавливаем состояние чекбокса автоопределения
        try {
            val autoTimeZoneEnabled = Settings.Global.getInt(contentResolver, Settings.Global.AUTO_TIME_ZONE) == 1
            autoDetectCheckbox.isChecked = !autoTimeZoneEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get auto timezone setting: ${e.message}", e)
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
                UIHelper.showCustomToast(this@MainActivity, getString(R.string.cache_cleared_for, selectedAppName))
            } else {
                Log.e(TAG, "App not found: $selectedAppName")
                UIHelper.showCustomToast(this@MainActivity, getString(R.string.select_valid_app))
            }
        }

        clearDataButton.setOnClickListener {
            val selectedAppName = appSelector.text.toString()
            val selectedPackage = appPackages.find { it.second == selectedAppName }?.first

            if (selectedPackage != null) {
                AppHelper.clearAppData(this, selectedPackage)
                UIHelper.showCustomToast(this@MainActivity, getString(R.string.data_cleared_for, selectedAppName))
            } else {
                Log.e(TAG, "App not found: $selectedAppName")
                UIHelper.showCustomToast(this@MainActivity, getString(R.string.select_valid_app))
            }
        }

        forceStopButton.setOnClickListener {
            val selectedAppName = appSelector.text.toString()
            val selectedPackage = appPackages.find { it.second == selectedAppName }?.first

            if (selectedPackage != null) {
                AppHelper.forceStopApp(this, selectedPackage)
                UIHelper.showCustomToast(this@MainActivity, getString(R.string.app_stopped, selectedAppName))
            } else {
                Log.e(TAG, "App not found: $selectedAppName")
                UIHelper.showCustomToast(this@MainActivity, getString(R.string.select_valid_app))
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


        coroutineScope.launch(Dispatchers.IO) {
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
                    if (currentUSBPosition.get() != initialPosition) {
                        isProgrammaticChange.set(true)
                        usbModeSwitcher.setPosition(initialPosition, false)
                        isProgrammaticChange.set(false)
                        currentUSBPosition.set(initialPosition)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading USB mode", e)
                withContext(Dispatchers.Main) {
                    UIHelper.showCustomToast(this@MainActivity, getString(R.string.failed_read_usb_mode))

                    if (currentUSBPosition.get() != 0) {
                        isProgrammaticChange.set(true)
                        usbModeSwitcher.setPosition(0, false)
                        isProgrammaticChange.set(false)
                        currentUSBPosition.set(0)
                    }
                }
            }
        }

        usbModeSwitcher.onPositionChangedListener =
            SegmentedButtonGroup.OnPositionChangedListener { position ->
                if (isProgrammaticChange.get()) {
                    Log.i(TAG, "Ignoring programmatic position change")
                    return@OnPositionChangedListener // Игнорируем вызов
                }

                if (position == currentUSBPosition.get()) {
                    Log.i(TAG, "Position unchanged. No action needed.")
                    return@OnPositionChangedListener // Если позиция не изменилась, ничего не делаем
                }

                val newMode = if (position == 0 || position == 2) "0" else "1"

                coroutineScope.launch(Dispatchers.IO) {
                    var isSuccess = usbHelper.setUSBMode(newMode)

                    withContext(Dispatchers.Main) {
                        if (isSuccess) {
                            if (position == 2) {
                                usbHelper.setAutoPeripheralModeEnabled(this@MainActivity, true)
                            } else {
                                usbHelper.setAutoPeripheralModeEnabled(this@MainActivity, false)
                            }

                            currentUSBPosition.set(position)
                            UIHelper.showCustomToast(this@MainActivity, getString(R.string.usb_mode_set_to, usbHelper.formatUsbMode(newMode)))
                            Log.i(TAG, "USB Mode successfully set to $newMode")
                        } else {
                            UIHelper.showCustomToast(this@MainActivity, getString(R.string.failed_set_usb_mode))
                            Log.e(TAG, "Failed to set USB Mode to $newMode")

                            isProgrammaticChange.set(true)
                            usbModeSwitcher.setPosition(currentUSBPosition.get(), true) // Возвращаем в исходную позицию
                            isProgrammaticChange.set(false)
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
                statusBuilder.append(getString(R.string.app_found_rights_granted, appName) + "\n")
                Log.i(TAG, "$appName ($packageName) найден. Права выданы.")
            } else {
                //statusBuilder.append("$appName: не найден.\n")
                Log.w(TAG, "$appName ($packageName) не найден.")
            }
        }

        statusTextView.text = statusBuilder.toString().trim()
    }
    
    /**
     * Отменяем все запущенные корутины при уничтожении активности
     */
    override fun onDestroy() {
        coroutineScope.cancel() // Отменяем все корутины
        super.onDestroy()
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
