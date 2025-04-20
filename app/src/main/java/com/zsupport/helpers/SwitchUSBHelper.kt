package com.zsupport.helpers

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class SwitchUSBHelper {

    private val TAG = "AnyAppSwitchUSBHelper"

    fun getUSBMode(): String {
        Log.d(TAG, "getUSBMode called")
        return getPropValue("persist.usb.mode")
    }

    fun setUSBMode(mode: String): Boolean {
        Log.d(TAG, "setUSBMode called with mode $mode")
        return setPropValue("persist.usb.mode", mode)
    }

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

    fun formatUsbMode(mode: String): String {
        return when (mode) {
            "1" -> "host"
            "0" -> "peripheral"
            else -> "unknown"
        }
    }
}
