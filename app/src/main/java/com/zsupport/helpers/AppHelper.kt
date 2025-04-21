package com.zsupport.helpers

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

/**
 * AppHelper - вспомогательный класс для управления приложениями на устройстве.
 * 
 * Предоставляет функциональность для:
 * - Получения списка установленных приложений
 * - Принудительной остановки приложений
 * - Очистки кэша приложений
 * - Очистки данных приложений
 */
object AppHelper {

    /**
     * Карта соответствия пакетов и требуемых разрешений
     */
    private val packagePermissionsMap = mutableMapOf(
        "com.anyapp.store" to listOf("REQUEST_INSTALL_PACKAGES"),
        "com.anyapp.zee.store" to listOf("REQUEST_INSTALL_PACKAGES"),
        "ru.vk.store" to listOf("REQUEST_INSTALL_PACKAGES"),
        "air.StrelkaHUDFREE" to listOf("android.permission.SYSTEM_ALERT_WINDOW", "deviceidle whitelist")
    )

    /**
     * Получает список запущенных приложений
     *
     * @param context Контекст приложения
     * @return Список имен пакетов запущенных приложений
     */
    fun getRunningApps(context: Context): List<String> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningTasks = activityManager.runningAppProcesses
        return runningTasks?.map { it.processName } ?: emptyList()
    }

    /**
     * Принудительно останавливает указанное приложение
     *
     * @param context Контекст приложения
     * @param packageName Имя пакета приложения для остановки
     */
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

    /**
     * Очищает кэш указанного приложения
     *
     * @param context Контекст приложения
     * @param packageName Имя пакета приложения для очистки кэша
     */
    fun clearAppCache(context: Context, packageName: String) {
        try {
            // Получение IPackageManager через рефлексию
            val pmClass = Class.forName("android.content.pm.IPackageManager")
            val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
            val asInterface = stubClass.getDeclaredMethod("asInterface", android.os.IBinder::class.java)

            val binder = Class.forName("android.os.ServiceManager")
                .getDeclaredMethod("getService", String::class.java)
                .invoke(null, "package") as android.os.IBinder

            val packageManager = asInterface.invoke(null, binder)

            // Создание IPackageDataObserver через динамический прокси
            val iPackageDataObserverClass = Class.forName("android.content.pm.IPackageDataObserver")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                iPackageDataObserverClass.classLoader,
                arrayOf(iPackageDataObserverClass)
            ) { _, method, args ->
                if (method.name == "onRemoveCompleted") {
                    Log.e("AppHelper", "Cache cleared for package: $packageName")
                }
                null
            }

            // Вызов метода deleteApplicationCacheFiles
            val deleteCacheMethod = pmClass.getDeclaredMethod(
                "deleteApplicationCacheFiles",
                String::class.java,
                iPackageDataObserverClass
            )
            deleteCacheMethod.invoke(packageManager, packageName, proxy)

            Log.e("AppHelper", "Request to clear cache sent for package: $packageName")
        } catch (e: Exception) {
            Log.e("AppHelper", "Failed to clear cache: ${e.message}", e)
        }
    }

    /**
     * Очищает данные указанного приложения
     *
     * @param context Контекст приложения
     * @param packageName Имя пакета приложения для очистки данных
     */
    fun clearAppData(context: Context, packageName: String) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            // Используем рефлексию для вызова метода clearApplicationUserData
            val clearDataMethod = activityManager.javaClass.getDeclaredMethod(
                "clearApplicationUserData",
                String::class.java,
                Class.forName("android.content.pm.IPackageDataObserver")
            )

            // Создаем IPackageDataObserver через Proxy для обратного вызова
            val observerClass = Class.forName("android.content.pm.IPackageDataObserver")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                observerClass.classLoader,
                arrayOf(observerClass)
            ) { _, method, args ->
                if (method.name == "onRemoveCompleted") {
                    val success = args?.get(1) as? Boolean ?: false
                    Log.e("AppHelper", "Data cleared callback for $packageName, success: $success")
                }
                null
            }

            // Вызываем метод очистки данных
            clearDataMethod.invoke(activityManager, packageName, proxy)
            Log.e("AppHelper", "Clearing data initiated for: $packageName")
        } catch (e: NoSuchMethodException) {
            Log.e("AppHelper", "clearApplicationUserData method not found: ${e.message}", e)
        } catch (e: ClassNotFoundException) {
            Log.e("AppHelper", "IPackageDataObserver class not found: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("AppHelper", "Failed to clear data: ${e.message}", e)
        }
    }

    /**
     * Получает список установленных пользовательских приложений
     *
     * @param context Контекст приложения
     * @return Список пар (имя пакета, название приложения)
     */
    fun getInstalledApps(context: Context): List<Pair<String, String>> {
        val packageManager = context.packageManager
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        return apps.filter { app ->
            // Исключаем системные приложения
            (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        }.map { app ->
            val appName = packageManager.getApplicationLabel(app).toString()
            app.packageName to appName
        }.filter { it.second.isNotEmpty() } // Исключаем приложения без имени
    }

}
