package com.zsupport.helpers

import android.app.ActivityManager
import android.content.Context
import android.util.Log

object AppHelper {

    // Получить список запущенных приложений
    fun getRunningApps(context: Context): List<String> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningTasks = activityManager.runningAppProcesses
        return runningTasks?.map { it.processName } ?: emptyList()
    }

    // Форс-стоп приложения
    fun forceStopApp(context: Context, packageName: String) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val method = activityManager.javaClass.getMethod("forceStopPackage", String::class.java)
            method.invoke(activityManager, packageName)
            Log.e("AppHelper", "Forced stop: $packageName")
        } catch (e: Exception) {
            Log.e("AppHelper", "Failed to force stop: ${e.message}", e)
        }
    }

    // Очистить кэш приложения
    fun clearAppCache(context: Context, packageName: String) {
        try {
            val packageManager = context.packageManager
            val method = packageManager.javaClass.getMethod("deleteApplicationCacheFiles", String::class.java)
            method.invoke(packageManager, packageName)
            Log.e("AppHelper", "Cleared cache: $packageName")
        } catch (e: Exception) {
            Log.e("AppHelper", "Failed to clear cache: ${e.message}", e)
        }
    }

    // Очистить данные приложения
    fun clearAppData(context: Context, packageName: String) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val method = activityManager.javaClass.getMethod("clearApplicationUserData", String::class.java, Int::class.java)
            method.invoke(activityManager, packageName, 0)
            Log.e("AppHelper", "Cleared data: $packageName")
        } catch (e: Exception) {
            Log.e("AppHelper", "Failed to clear data: ${e.message}", e)
        }
    }
}
