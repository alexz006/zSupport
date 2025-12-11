package com.zsupport.helpers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
     * ЛОГИКА:
     * - читаем сохранённый ID зоны из SharedPreferences;
     * - если есть — вызываем каноничный метод TimeZoneHelper.setSystemTimeZonePermanent();
     * - без дополнительных фолбеков и дублей через Settings/AlarmManager.
     */
    private fun handleTimeZone(context: Context, prefs: SharedPreferences) {
        val selectedTimeZone = prefs.getString("selected_time_zone", null)
        if (selectedTimeZone.isNullOrEmpty()) {
            Log.d(TAG, "No saved timezone found in preferences")
            return
        }

        Log.i(TAG, "Found saved timezone in preferences: $selectedTimeZone")

        try {
            val timeZoneHelper = TimeZoneHelper()
            val success = timeZoneHelper.setSystemTimeZonePermanent(context, selectedTimeZone)

            if (success) {
                val currentTimeZone = timeZoneHelper.getCurrentSystemTimeZoneId(context)
                Log.i(TAG, "Time zone synchronized to $currentTimeZone on boot")
            } else {
                Log.e(TAG, "Failed to synchronize time zone on boot")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to synchronize time zone: ${e.message}", e)
        }
    }
}
