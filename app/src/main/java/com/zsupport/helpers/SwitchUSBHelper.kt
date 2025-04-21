package com.zsupport.helpers

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * SwitchUSBHelper - вспомогательный класс для управления режимами работы USB.
 * 
 * Предоставляет методы для получения и установки режима работы USB порта устройства
 * через системные свойства (properties).
 */
class SwitchUSBHelper {

    private val TAG = "AnyAppSwitchUSBHelper"

    /**
     * Получает текущий режим работы USB.
     * 
     * @return Строковое значение режима USB из системного свойства persist.usb.mode
     */
    fun getUSBMode(): String {
        Log.d(TAG, "getUSBMode called")
        return getPropValue("persist.usb.mode")
    }

    /**
     * Устанавливает режим работы USB.
     * 
     * @param mode Строковое значение режима USB для установки
     * @return true в случае успешной установки, false в случае ошибки
     */
    fun setUSBMode(mode: String): Boolean {
        Log.d(TAG, "setUSBMode called with mode $mode")
        return setPropValue("persist.usb.mode", mode)
    }

    /**
     * Получает значение системного свойства.
     * 
     * @param propName Имя системного свойства для чтения
     * @return Значение системного свойства или "Unknown" в случае пустого значения или ошибки
     */
    private fun getPropValue(propName: String): String {
        var value = "Unknown"
        try {
            Log.d(TAG, "Reading property $propName")
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", propName))
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                value = reader.readLine() ?: "Unknown"
            }
            process.waitFor()
            Log.d(TAG, "Read property $propName: $value")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading property $propName", e)
        }
        Log.d(TAG, "Returning value: $value")
        return value.ifEmpty { "Unknown" }
    }

    /**
     * Устанавливает значение системного свойства.
     * 
     * @param propName Имя системного свойства для установки
     * @param value Новое значение для системного свойства
     * @return true в случае успешной установки, false в случае ошибки
     */
    private fun setPropValue(propName: String, value: String): Boolean {
        return try {
            Log.d(TAG, "Setting property $propName to $value")
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/setprop", propName, value))
            process.waitFor()
            val exitCode = process.exitValue()

            if (exitCode != 0) {
                val errorStream = process.errorStream.bufferedReader().use { it.readText() }
                Log.e(TAG, "setPropValue failed: Error output: $errorStream")
            }

            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error setting property $propName to $value", e)
            false
        }
    }

    /**
     * Форматирует значение режима USB в человекочитаемый формат.
     * 
     * @param mode Строковое представление режима USB
     * @return Человекочитаемое строковое описание режима: "host", "peripheral" или "unknown"
     */
    fun formatUsbMode(mode: String): String {
        return when (mode) {
            "1" -> "host"
            "0" -> "peripheral"
            else -> "unknown"
        }
    }
}
