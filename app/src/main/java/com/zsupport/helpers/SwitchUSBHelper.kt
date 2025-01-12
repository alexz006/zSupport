package com.zsupport.helpers

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class SwitchUSBHelper {

    private val TAG = "SwitchUSBHelper"

    fun getUSBMode(): String {
        return getPropValue("persist.usb.mode")
    }

    fun setUSBMode(mode: String): Boolean {
        return setPropValue("persist.usb.mode", mode)
    }

    private fun getPropValue(propName: String): String {
        var value = "Unknown"
        try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", propName))
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                value = reader.readLine() ?: "Unknown"
            }
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading property $propName", e)
        }
        return value.ifEmpty { "Unknown" }
    }

    private fun setPropValue(propName: String, value: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/setprop", propName, value))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error setting property $propName to $value", e)
            false
        }
    }
}
