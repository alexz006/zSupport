package com.zsupport.helpers

import android.app.backup.BackupManager
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import java.util.TimeZone

/**
 * TimeZoneHelper - вспомогательный класс для работы с часовыми поясами.
 *
 * Предоставляет методы для получения, форматирования и изменения часовых поясов,
 * а также для сохранения выбранных настроек.
 */
class TimeZoneHelper {
    private val TAG = "TimeZoneHelper"
    private val PREFS_NAME = "app_prefs"
    private val TIMEZONE_KEY = "selected_time_zone"

    /**
     * Преобразует идентификатор часового пояса в человекочитаемый формат
     *
     * @param timeZoneId Идентификатор часового пояса
     * @return Строка с названием города и смещением GMT
     */
    fun getReadableTimeZone(timeZoneId: String): String {
        val timeZone = TimeZone.getTimeZone(timeZoneId)
        val offset = timeZone.rawOffset / (60 * 60 * 1000) // Смещение в часах
        val offsetMinutes = Math.abs(timeZone.rawOffset / (60 * 1000) % 60) // Минуты для неполных часов
        val offsetSign = if (offset >= 0) "+" else "-"
        val gmtOffset = if (offsetMinutes > 0) {
            "GMT$offsetSign${Math.abs(offset)}:${String.format("%02d", offsetMinutes)}"
        } else {
            "GMT$offsetSign${Math.abs(offset)}"
        }

        val cityName = timeZoneId.substringAfterLast('/') // Получаем название города/области
            .replace('_', ' ') // Преобразуем подчеркивания в пробелы

        return "$cityName ($gmtOffset)"
    }

    /**
     * Временно изменяет системный часовой пояс
     *
     * @param context Контекст приложения
     * @param timeZoneId Идентификатор часового пояса
     * @return true если операция выполнена успешно, false в противном случае
     */
    fun changeSystemTimeZone(context: Context, timeZoneId: String): Boolean {
        Log.i(TAG, "Changing system timezone to $timeZoneId")

        return try {
            val alarmManagerClass = Class.forName("android.app.AlarmManager")
            val setTimeZoneMethod = alarmManagerClass.getDeclaredMethod("setTimeZone", String::class.java)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE)
            setTimeZoneMethod.invoke(alarmManager, timeZoneId)

            BackupManager.dataChanged("com.android.providers.settings")
            Log.i(TAG, "System timezone updated successfully to $timeZoneId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change timezone: ${e.message}", e)
            false
        }
    }

    /**
     * Устанавливает системный часовой пояс постоянно
     *
     * @param context Контекст приложения
     * @param timeZoneId Идентификатор часового пояса
     * @return true если операция выполнена успешно, false в противном случае
     */
    fun setSystemTimeZonePermanent(context: Context, timeZoneId: String): Boolean {
        Log.i(TAG, "Setting permanent system timezone to $timeZoneId")
        
        // Шаг 1: Отключаем автоматическое определение часового пояса
        setAutoTimeZoneEnabled(context, false)
        
        try {
            // Шаг 2: Устанавливаем часовой пояс напрямую через Settings.Global
            Settings.Global.putString(context.contentResolver, "time_zone", timeZoneId)
            
            // Шаг 3: Также используем метод через рефлексию для совместимости со старыми устройствами
            try {
                val settingsGlobalClass = Class.forName("android.provider.Settings\$Global")
                val putStringMethod = settingsGlobalClass.getDeclaredMethod(
                    "putString",
                    android.content.ContentResolver::class.java,
                    String::class.java,
                    String::class.java
                )
                putStringMethod.invoke(null, context.contentResolver, "time_zone", timeZoneId)
            } catch (e: Exception) {
                Log.w(TAG, "Using reflection method failed, continuing with direct method: ${e.message}")
            }

            // Шаг 4: Активируем изменения через AlarmManager
            val success = changeSystemTimeZone(context, timeZoneId)
            
            // Шаг 5: Проверяем, что установка произошла корректно
            val currentTimeZone = Settings.Global.getString(context.contentResolver, "time_zone")
            Log.i(TAG, "Current timezone setting after change: $currentTimeZone")
            
            // Шаг 6: Уведомляем систему об изменении настроек
            BackupManager.dataChanged("com.android.providers.settings")
            
            Log.i(TAG, "System timezone set permanently to $timeZoneId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set timezone permanently: ${e.message}", e)
            return false
        }
    }

    /**
     * Включает или отключает автоматическое определение часового пояса
     *
     * @param context Контекст приложения
     * @param enabled true для включения, false для отключения
     * @return true если операция выполнена успешно, false в противном случае
     */
    fun setAutoTimeZoneEnabled(context: Context, enabled: Boolean): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, Settings.Global.AUTO_TIME_ZONE, if (enabled) 1 else 0)
            Log.i(TAG, "Auto timezone detection set to ${if (enabled) "enabled" else "disabled"}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change auto timezone setting: ${e.message}", e)
            false
        }
    }

    /**
     * Сохраняет выбранный часовой пояс в SharedPreferences
     *
     * @param context Контекст приложения
     * @param timeZoneId Идентификатор часового пояса
     */
    fun saveTimeZoneToPrefs(context: Context, timeZoneId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(TIMEZONE_KEY, timeZoneId).apply()
        Log.i(TAG, "TimeZone saved to prefs: $timeZoneId")
    }

    /**
     * Получает сохраненный часовой пояс из SharedPreferences
     *
     * @param context Контекст приложения
     * @return Идентификатор часового пояса или null, если ничего не сохранено
     */
    fun getTimeZoneFromPrefs(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(TIMEZONE_KEY, null)
    }

    /**
     * Удаляет сохраненный часовой пояс из SharedPreferences
     *
     * @param context Контекст приложения
     */
    fun clearTimeZonePrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(TIMEZONE_KEY).apply()
        Log.i(TAG, "TimeZone preferences cleared")
    }
} 