package com.zsupport

import android.annotation.SuppressLint
import android.app.backup.BackupManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
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
 *
 * gradlew assembleRelease -Dorg.gradle.java.home="C:\Program Files\Android\Android Studio\jbr"
 *
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

        val agreementLayout = findViewById<LinearLayout>(R.id.agreementLayout)
        val mainContentLayout = findViewById<LinearLayout>(R.id.mainContentLayout)

        // Получаем ссылки на элементы из XML
        val chineseButton = findViewById<Button>(R.id.chineseButton)
        val englishButton = findViewById<Button>(R.id.englishButton)
        val agreementCheckBox = findViewById<CheckBox>(R.id.checkBox)
        val timezoneButton = findViewById<Button>(R.id.timezoneButton)
        val autoDetectCheckbox = findViewById<CheckBox>(R.id.autoDetectTimezoneCheckbox)

        val appSelector = findViewById<AutoCompleteTextView>(R.id.appSelector)
        val clearCacheButton = findViewById<Button>(R.id.clearCacheButton)
        val clearDataButton = findViewById<Button>(R.id.clearDataButton)
        val forceStopButton = findViewById<Button>(R.id.forceStopButton)
        val versionTextView = findViewById<TextView>(R.id.versionTextView)

        val usbModeSwitcher = findViewById<SegmentedButtonGroup>(R.id.usbModeSwitcher)

        // Меню
        val menuLocaleButton = findViewById<Button>(R.id.menuLocaleButton)
        val menuUsbButton = findViewById<Button>(R.id.menuUsbButton)
        val menuTimezoneButton = findViewById<Button>(R.id.menuTimezoneButton)
        val menuAppsButton = findViewById<Button>(R.id.menuAppsButton)
        val menuKeyboardButton = findViewById<Button>(R.id.menuKeyboardButton)
        val menuOtherButton = findViewById<Button>(R.id.menuOtherButton)

        // Секции контента
        val sectionLocale = findViewById<View>(R.id.sectionLocale)
        val sectionUsb = findViewById<View>(R.id.sectionUsb)
        val sectionTimezone = findViewById<View>(R.id.sectionTimezone)
        val sectionApps = findViewById<View>(R.id.sectionApps)
        val sectionKeyboard = findViewById<View>(R.id.sectionKeyboard)
        val sectionOther = findViewById<View>(R.id.sectionOther)

        // Кнопка в разделе "Other"
        val selectLauncherButton = findViewById<Button>(R.id.selectLauncherButton)

        // Элементы управления клавиатурой в секции Keyboard
        val currentInputMethodTextView = findViewById<TextView>(R.id.currentInputMethodTextView)
        val gboardButton = findViewById<Button>(R.id.gboardButton)
        val yandexButton = findViewById<Button>(R.id.yandexButton)
        val microsoftButton = findViewById<Button>(R.id.msswiftButton)
        val testInputEditText = findViewById<EditText>(R.id.testInputEditText)

        val keyboardManager = KeyboardManager.getInstance()

        // Локальная функция для обновления UI статуса клавиатуры
        fun updateKeyboardUI() {
            val currentId = keyboardManager.getCurrentKeyboard(this)
            val currentName = when (currentId) {
                "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME" ->
                    "Gboard"
                "ru.yandex.androidkeyboard/com.android.inputmethod.latin.LatinIME" ->
                    "Yandex Keyboard"
                "com.touchtype.swiftkey/com.touchtype.KeyboardService" ->
                    "Microsoft Swift"
                else -> "Unknown"
            }
            currentInputMethodTextView.text = getString(R.string.current_input_method, currentName)
            val buttons = mapOf(
                "Gboard" to gboardButton,
                "Yandex Keyboard" to yandexButton,
                "Microsoft Swift" to microsoftButton
            )
            buttons.forEach { (name, button) ->
                if (name == currentName) {
                    button.setBackgroundResource(R.drawable.custom_button_selected_background)
                } else {
                    button.setBackgroundResource(R.drawable.custom_button_background)
                }
            }
        }
        // Обработчики кнопок выбора клавиатуры
        gboardButton.setOnClickListener {
            keyboardManager.configureAndSelectKeyboard(this, "Gboard")
            updateKeyboardUI()
        }
        yandexButton.setOnClickListener {
            keyboardManager.configureAndSelectKeyboard(this, "Yandex Keyboard")
            updateKeyboardUI()
        }
        microsoftButton.setOnClickListener {
            keyboardManager.configureAndSelectKeyboard(this, "Microsoft Swift")
            updateKeyboardUI()
        }
        // Стартовое обновление UI (когда откроется секция Keyboard, всё уже будет корректно)
        updateKeyboardUI()

        // Устанавливаем текст версии приложения
        //val appVersion = SystemHelper.getAppVersion(this)
        //versionTextView.text = "v${appVersion}"
        val appVersion = SystemHelper.getAppVersion(this)
        val osVersion = try {
            Settings.Global.getString(contentResolver, "b22_current_version")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read b22_current_version: ${e.message}", e)
            null
        }
        versionTextView.text = if (!osVersion.isNullOrEmpty()) {
            "v$appVersion | OS: $osVersion"
        } else {
            "v$appVersion"
        }

        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Настраиваем эффект при наведении для кнопок
        // Настраиваем эффект при наведении для кнопок
        HoverUtils().setHover(
            chineseButton,
            englishButton,
            timezoneButton,
            clearCacheButton,
            clearDataButton,
            forceStopButton,
            selectLauncherButton
        )

        // Начально деактивируем кнопки
        chineseButton.isEnabled = false
        englishButton.isEnabled = false
        timezoneButton.isEnabled = false
        clearCacheButton.isEnabled = false
        clearDataButton.isEnabled = false
        forceStopButton.isEnabled = false

        selectLauncherButton.setOnClickListener {
            val pm = packageManager

            // Базовый HOME-интент
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }

            // Пытаемся узнать текущий дефолтный лаунчер
            val resolveInfo = try {
                pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve default home activity: ${e.message}", e)
                null
            }

            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                val homePackage = resolveInfo.activityInfo.packageName
                Log.i(TAG, "Current default launcher package: $homePackage")

                var cleared = false
                try {
                    // Работает только для системных/привилегированных приложений
                    pm.clearPackagePreferredActivities(homePackage)
                    cleared = true
                    Log.i(TAG, "Preferred activities for $homePackage cleared")
                } catch (se: SecurityException) {
                    Log.w(TAG, "No permission to clear preferred activities: ${se.message}", se)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear preferred activities: ${e.message}", e)
                }

                if (!cleared) {
                    // Открываем настройки лаунчера, чтобы пользователь мог сам сбросить "по умолчанию"
                    try {
                        val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$homePackage")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(settingsIntent)

                        UIHelper.showCustomToast(
                            this@MainActivity,
                            "В настройках лаунчера сбросьте «по умолчанию», затем вернитесь и снова нажмите кнопку."
                        )
                        return@setOnClickListener
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open launcher app settings: ${e.message}", e)
                    }
                }
            } else {
                Log.w(TAG, "No default home activity resolved")
            }

            // Теперь отправляем обычный HOME-интент.
            // Если дефолт сброшен — система покажет стандартный выбор лаунчера.
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start HOME intent: ${e.message}", e)
                UIHelper.showCustomToast(
                    this@MainActivity,
                    "Не удалось открыть выбор лаунчера на этой системе"
                )
            }
        }
        // Настраиваем действие для чекбокса
        agreementCheckBox.setOnCheckedChangeListener { _, isChecked ->
            // Активируем или деактивируем кнопки в зависимости от состояния чекбокса
            updateButtonState(chineseButton, isChecked)
            updateButtonState(englishButton, isChecked)
            updateButtonState(timezoneButton, isChecked)
            updateButtonState(clearCacheButton, isChecked)
            updateButtonState(clearDataButton, isChecked)
            updateButtonState(forceStopButton, isChecked)

            // пока нет согласия — интерфейс скрыт
            if (isChecked) {
                agreementLayout.visibility = View.GONE
                mainContentLayout.visibility = View.VISIBLE
            } else {
                agreementLayout.visibility = View.VISIBLE
                mainContentLayout.visibility = View.GONE
            }
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

        // Формируем список часовых поясов в формате, как в питоне: GMT-12..GMT+12
        val gmtTimeZones = (-12..12).map { i ->
            val hours = if (i >= 0) "$i" else String.format("%d", i) // без ведущего нуля
            "GMT" + (if (i >= 0) "+" else "") + hours
        }

        // Логируем список для отладки
        Log.i(TAG, "GMT timezones: $gmtTimeZones")

        // Адаптер для выпадающего списка
        val adapter = ArrayAdapter(this, R.layout.custom_spinner_item, gmtTimeZones)

        // Настраиваем поле выбора часового пояса как селект (без ввода текста)
        val timezoneAutoComplete = findViewById<AutoCompleteTextView>(R.id.timezoneAutoComplete)
        timezoneAutoComplete.setAdapter(adapter)
        timezoneAutoComplete.threshold = Int.MAX_VALUE // отключаем поиск по вводу

        // Делаем поле «только выбор из списка»
        timezoneAutoComplete.keyListener = null
        timezoneAutoComplete.isFocusable = false
        timezoneAutoComplete.isCursorVisible = false
        timezoneAutoComplete.hint = getString(R.string.select_timezone_hint)

        timezoneAutoComplete.setOnClickListener {
            timezoneAutoComplete.showDropDown()
        }
        timezoneAutoComplete.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                timezoneAutoComplete.showDropDown()
            }
        }

        // Определяем текущий системный часовой пояс через helper (учитывает persist.sys.timezone)
        val currentSystemTimeZoneId = timeZoneHelper.getCurrentSystemTimeZoneId(this)

        val currentSystemTz = TimeZone.getTimeZone(currentSystemTimeZoneId)
        val currentOffsetHours = currentSystemTz.rawOffset / (60 * 60 * 1000)
        val currentGmtTz = "GMT" + (if (currentOffsetHours >= 0) "+" else "") + currentOffsetHours.toString()

        // Загружаем сохранённый часовой пояс (если есть)
        val savedTimeZone = timeZoneHelper.getTimeZoneFromPrefs(this)

        // Определяем, какой ID показывать по умолчанию:
        // 1) если сохранённый есть — используем его, при необходимости конвертируем в GMT±H
        // 2) иначе — текущий системный в формате GMT±H
        val initialTimeZoneId = when {
            !savedTimeZone.isNullOrEmpty() -> {
                if (savedTimeZone.startsWith("GMT") && gmtTimeZones.contains(savedTimeZone)) {
                    savedTimeZone
                } else {
                    // Старый формат (например, Europe/Berlin) конвертируем в GMT±H
                    val tz = TimeZone.getTimeZone(savedTimeZone)
                    val h = tz.rawOffset / (60 * 60 * 1000)
                    "GMT" + (if (h >= 0) "+" else "") + h.toString()
                }
            }
            gmtTimeZones.contains(currentGmtTz) -> currentGmtTz
            else -> "GMT+0"
        }

        // Устанавливаем выбранный пункт без запуска фильтрации
        timezoneAutoComplete.setText(initialTimeZoneId, false)

        // Настраиваем действие для кнопки изменения часового пояса
        timezoneButton.setOnClickListener {
            hideKeyboard()

            val selectedTimeZoneDisplay = timezoneAutoComplete.text.toString().trim()

            // Проверяем, что пользователь выбрал один из GMT±H
            val selectedTimeZoneId = if (gmtTimeZones.contains(selectedTimeZoneDisplay)) {
                selectedTimeZoneDisplay
            } else {
                null
            }

            if (selectedTimeZoneId == null) {
                UIHelper.showCustomToast(this@MainActivity, getString(R.string.invalid_timezone_selected))
                return@setOnClickListener
            }

            // Всегда постоянная установка
            val success = timeZoneHelper.setSystemTimeZonePermanent(this, selectedTimeZoneId)

            if (success) {
                UIHelper.showCustomToast(
                    this@MainActivity,
                    "Timezone successfully set to $selectedTimeZoneDisplay.\nReboot is required for changes to take full effect."
                )
            } else {
                UIHelper.showCustomToast(
                    this@MainActivity,
                    getString(R.string.invalid_timezone_selected)
                )
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

        val allSections = listOf(sectionLocale, sectionUsb, sectionTimezone, sectionApps, sectionKeyboard, sectionOther)

        // По умолчанию показываем секцию локали
        showSection(sectionLocale, allSections)
        menuLocaleButton.setOnClickListener {
            showSection(sectionLocale, allSections)
        }
        menuUsbButton.setOnClickListener {
            showSection(sectionUsb, allSections)
        }
        menuTimezoneButton.setOnClickListener {
            showSection(sectionTimezone, allSections)
        }
        menuAppsButton.setOnClickListener {
            showSection(sectionApps, allSections)
        }
        menuKeyboardButton.setOnClickListener {
            showSection(sectionKeyboard, allSections)
        }
        menuOtherButton.setOnClickListener {
            showSection(sectionOther, allSections)
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

        // Сохраняем выбранный язык для применения после перезагрузки устройства
        try {
            val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("preferred_language", locale.language) // ТОЛЬКО language, без country
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save preferred language: ${e.message}", e)
        }

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

    private fun showSection(active: View, all: List<View>) {
        all.forEach { it.visibility = if (it == active) View.VISIBLE else View.GONE }
    }

    // Класс для сохранения настроек
    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_PREFERRED_LANGUAGE = "preferred_language"

        // Вызывается ресивером при BOOT_COMPLETED
        fun applySavedLanguage(context: Context) {
            try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lang = prefs.getString(KEY_PREFERRED_LANGUAGE, null)

                if (lang.isNullOrEmpty()) {
                    Log.i("AnyAppSupport", "No saved language, skipping applySavedLanguage")
                    return
                }

                val locale = Locale(lang)
                Log.i("AnyAppSupport", "Applying saved language on boot: $locale")

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
                    Log.i("AnyAppSupport", "System language reapplied from prefs on boot.")
                } catch (e: Exception) {
                    Log.e("AnyAppSupport", "Failed to apply saved language on boot: ${e.message}", e)
                }
            } catch (e: Exception) {
                Log.e("AnyAppSupport", "applySavedLanguage error: ${e.message}", e)
            }
        }
    }

}
