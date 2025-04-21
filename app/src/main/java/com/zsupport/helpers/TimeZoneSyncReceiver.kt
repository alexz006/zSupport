package com.zsupport.helpers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log

/**
 * BroadcastReceiver для синхронизации настроек часового пояса и USB режима
 * при загрузке устройства.
 * 
 * Реагирует на интент ACTION_BOOT_COMPLETED, восстанавливая сохраненные настройки
 * из SharedPreferences.
 */
class TimeZoneSyncReceiver : BroadcastReceiver() {

    private val TAG = "AnyAppSupport"

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.d(TAG, "Received boot completed intent")
        Log.d(TAG, "Context class: ${context.javaClass.name}")

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        
        // Обрабатываем настройки USB режима
        handleUsbMode(context, prefs)
        
        // Обрабатываем настройки часового пояса
        handleTimeZone(context, prefs)
    }
    
    /**
     * Обрабатывает настройки USB режима при загрузке устройства
     */
    private fun handleUsbMode(context: Context, prefs: SharedPreferences) {
        val isAutoUSBperipheral = prefs.getBoolean("auto_usb_peripheral", false)
        Log.d(TAG, "Auto USB peripheral mode: $isAutoUSBperipheral")
        
        if (!isAutoUSBperipheral) {
            return
        }
        
        // Создаем SwitchUSBHelper только если нужно установить режим peripheral
        try {
            val usbHelper = SwitchUSBHelper()
            val success = usbHelper.setUSBMode("0")
            if (success) {
                Log.d(TAG, "USB mode switched to peripheral on system boot")
            } else {
                Log.e(TAG, "Failed to switch USB mode to peripheral")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while switching USB mode on system boot", e)
        }
    }
    
    /**
     * Обрабатывает настройки часового пояса при загрузке устройства
     */
    private fun handleTimeZone(context: Context, prefs: SharedPreferences) {
        val selectedTimeZone = prefs.getString("selected_time_zone", null)
        if (selectedTimeZone.isNullOrEmpty()) {
            Log.d(TAG, "No saved timezone found in preferences")
            return
        }
        
        Log.i(TAG, "Found saved timezone in preferences: $selectedTimeZone")
        
        try {
            // Отключаем автоматическое определение часового пояса
            setAutoTimeZoneEnabled(context, false)
            Log.i(TAG, "Auto timezone detection disabled on boot")
            
            // Устанавливаем часовой пояс через Settings.Global напрямую
            val success1 = setTimeZoneDirectly(context, selectedTimeZone)
            
            // Также вызываем метод из TimeZoneHelper для полноты
            val timeZoneHelper = TimeZoneHelper()
            val success2 = timeZoneHelper.setSystemTimeZonePermanent(context, selectedTimeZone)
            
            if (success1 || success2) {
                Log.i(TAG, "Time zone synchronized to $selectedTimeZone on boot")
            } else {
                Log.e(TAG, "Failed to synchronize time zone on boot")
            }
            
            // Проверяем установленное значение
            val currentTimeZone = Settings.Global.getString(context.contentResolver, "time_zone")
            Log.i(TAG, "Current time_zone setting after change: $currentTimeZone")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to synchronize time zone: ${e.message}", e)
        }
    }
    
    /**
     * Устанавливает часовой пояс напрямую через Settings.Global
     */
    private fun setTimeZoneDirectly(context: Context, timeZoneId: String): Boolean {
        return try {
            // Прямая установка значения
            Settings.Global.putString(context.contentResolver, "time_zone", timeZoneId)
            
            // Также устанавливаем через AlarmManager для немедленного применения
            try {
                val alarmManagerClass = Class.forName("android.app.AlarmManager")
                val setTimeZoneMethod = alarmManagerClass.getDeclaredMethod("setTimeZone", String::class.java)

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE)
                setTimeZoneMethod.invoke(alarmManager, timeZoneId)
                Log.i(TAG, "Time zone set through AlarmManager")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set timezone through AlarmManager: ${e.message}", e)
            }
            
            Log.i(TAG, "System timezone set directly to $timeZoneId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set timezone directly: ${e.message}", e)
            false
        }
    }
    
    /**
     * Включает или отключает автоматическое определение часового пояса
     */
    private fun setAutoTimeZoneEnabled(context: Context, enabled: Boolean): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, Settings.Global.AUTO_TIME_ZONE, if (enabled) 1 else 0)
            Log.i(TAG, "Auto timezone detection set to ${if (enabled) "enabled" else "disabled"}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change auto timezone setting: ${e.message}", e)
            false
        }
    }
} 