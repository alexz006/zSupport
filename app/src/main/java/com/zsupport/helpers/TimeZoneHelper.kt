package com.zsupport.helpers

import android.app.backup.BackupManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.os.SystemClock
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
     * Читает текущую системную таймзону с учётом persist.sys.timezone.
     */
    fun getCurrentSystemTimeZoneId(context: Context): String {
        // 1) пробуем persist.sys.timezone
        try {
            val spClass = Class.forName("android.os.SystemProperties")
            val getMethod = spClass.getDeclaredMethod("get", String::class.java, String::class.java)
            getMethod.isAccessible = true
            val propTz = getMethod.invoke(null, "persist.sys.timezone", "") as String
            if (!propTz.isNullOrEmpty()) {
                Log.i(TAG, "Current timezone from persist.sys.timezone: $propTz")
                return propTz
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read persist.sys.timezone: ${e.message}", e)
        }

        // 2) пробуем Settings.Global.time_zone
        try {
            val globalTz = Settings.Global.getString(context.contentResolver, "time_zone")
            if (!globalTz.isNullOrEmpty()) {
                Log.i(TAG, "Current timezone from Settings.Global.time_zone: $globalTz")
                return globalTz
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Settings.Global.time_zone: ${e.message}", e)
        }

        // 3) дефолт Java
        val def = TimeZone.getDefault().id
        Log.i(TAG, "Using TimeZone.getDefault(): $def")
        return def
    }

    /**
     * Устанавливает persist.sys.timezone через SystemProperties.set()
     * (аналог adb shell setprop persist.sys.timezone <tz>).
     */
    private fun setPersistSysTimezone(timeZoneId: String): Boolean {
        return try {
            val spClass = Class.forName("android.os.SystemProperties")
            val setMethod = spClass.getDeclaredMethod("set", String::class.java, String::class.java)
            setMethod.isAccessible = true
            setMethod.invoke(null, "persist.sys.timezone", timeZoneId)
            Log.i(TAG, "persist.sys.timezone set to $timeZoneId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set persist.sys.timezone: ${e.message}", e)
            false
        }
    }

    /**
     * Шлёт broadcast, аналогичный:
     * adb shell am broadcast -a android.intent.action.TIMEZONE_CHANGED --es time-zone <tz>
     */
    private fun sendTimeZoneChangedBroadcast(context: Context, timeZoneId: String) {
        try {
            val intent = Intent(Intent.ACTION_TIMEZONE_CHANGED)
            intent.putExtra("time-zone", timeZoneId)
            context.sendBroadcast(intent)
            Log.i(TAG, "Sent ACTION_TIMEZONE_CHANGED with time-zone=$timeZoneId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ACTION_TIMEZONE_CHANGED: ${e.message}", e)
        }
    }

    /**
     * Временно изменяет системный часовой пояс.
     *
     * ЛОГИКА:
     * 1) Сохраняем текущее UTC-время (System.currentTimeMillis()).
     * 2) Делаем setprop + broadcast.
     * 3) Возвращаем SystemClock к сохранённому UTC.
     *
     * Таким образом, текущий момент остаётся тем же,
     * меняется только интерпретация времени в новой зоне.
     */
    fun changeSystemTimeZone(context: Context, timeZoneId: String): Boolean {
        Log.i(TAG, "Changing system timezone TEMPORARY to $timeZoneId")

        // Сохраняем «как есть» текущее UTC до любых изменений
        val beforeUtc = System.currentTimeMillis()
        Log.i(TAG, "UTC before timezone change: $beforeUtc")

        return try {
            // 1) setprop persist.sys.timezone
            val propOk = setPersistSysTimezone(timeZoneId)

            // 2) шлём broadcast
            sendTimeZoneChangedBroadcast(context, timeZoneId)

            // 3) уведомляем систему о смене настроек
            BackupManager.dataChanged("com.android.providers.settings")

            // 4) ВОЗВРАЩАЕМ обратно UTC — чтобы система не «накручивала» часы.
            try {
                val ok = SystemClock.setCurrentTimeMillis(beforeUtc)
                Log.i(TAG, "SystemClock.setCurrentTimeMillis(back to $beforeUtc) = $ok")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore UTC after timezone change: ${e.message}", e)
            }

            val finalTz = getCurrentSystemTimeZoneId(context)
            Log.i(TAG, "System timezone after TEMP change: $finalTz")

            propOk
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change timezone: ${e.message}", e)
            false
        }
    }

    /**
     * Устанавливает системный часовой пояс постоянно.
     *
     * ЛОГИКА:
     * 1) Отключаем авто-таймзону.
     * 2) Сохраняем выбранный ID в Settings.Global (для совместимости) и префы.
     * 3) Вызываем changeSystemTimeZone() — он уже делает setprop + broadcast + фиксацию времени.
     */
    fun setSystemTimeZonePermanent(context: Context, timeZoneId: String): Boolean {
        Log.i(TAG, "Setting PERMANENT system timezone to $timeZoneId")

        // 1) отключаем автоматическое определение
        setAutoTimeZoneEnabled(context, false)

        return try {
            // 2) сохраняем в Settings.Global для «официальной» настройки
            try {
                Settings.Global.putString(context.contentResolver, "time_zone", timeZoneId)
                Log.i(TAG, "Settings.Global.time_zone set to $timeZoneId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set Settings.Global.time_zone: ${e.message}")
            }

            // 3) сам переход зоны + фиксация времени
            val success = changeSystemTimeZone(context, timeZoneId)

            if (success) {
                // Сохраняем в префы выбранный ID
                saveTimeZoneToPrefs(context, timeZoneId)
            }

            val currentTimeZone = getCurrentSystemTimeZoneId(context)
            Log.i(TAG, "System timezone after PERMANENT change: $currentTimeZone")

            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set timezone permanently: ${e.message}", e)
            false
        }
    }

    /**
     * Включает или отключает автоматическое определение часового пояса.
     */
    fun setAutoTimeZoneEnabled(context: Context, enabled: Boolean): Boolean {
        return try {
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.AUTO_TIME_ZONE,
                if (enabled) 1 else 0
            )
            Log.i(TAG, "Auto timezone detection set to ${if (enabled) "enabled" else "disabled"}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change auto timezone setting: ${e.message}", e)
            false
        }
    }

    /**
     * Сохраняет выбранный часовой пояс в SharedPreferences.
     */
    fun saveTimeZoneToPrefs(context: Context, timeZoneId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(TIMEZONE_KEY, timeZoneId).apply()
        Log.i(TAG, "TimeZone saved to prefs: $timeZoneId")
    }

    /**
     * Получает сохраненный часовой пояс из SharedPreferences.
     */
    fun getTimeZoneFromPrefs(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(TIMEZONE_KEY, null)
    }
}
