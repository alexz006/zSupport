package com.zsupport.helpers

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log
import com.zsupport.R

/**
 * SystemHelper - вспомогательный класс для выполнения системных операций,
 * таких как перезагрузка устройства.
 */
object SystemHelper {

    private const val TAG = "SystemHelper"

    /**
     * Показывает диалог для подтверждения перезагрузки устройства.
     * 
     * @param context Контекст приложения
     */
    fun showRebootDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.reboot_device_title))
            .setMessage(context.getString(R.string.reboot_device_message))
            .setPositiveButton(context.getString(R.string.yes)) { _, _ ->
                initiateReboot(context)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    /**
     * Инициирует безопасную перезагрузку устройства.
     * Отправляет системный интент ACTION_SHUTDOWN для уведомления других приложений
     * о предстоящей перезагрузке, а затем выполняет саму перезагрузку.
     * 
     * @param context Контекст приложения
     */
    private fun initiateReboot(context: Context) {
        try {
            // Отправляем широковещательный интент для уведомления приложений
            val shutdownIntent = Intent(Intent.ACTION_SHUTDOWN)
            shutdownIntent.flags = Intent.FLAG_RECEIVER_FOREGROUND
            context.sendBroadcast(shutdownIntent)

            // Задержка перед перезагрузкой для обработки системных уведомлений
            Thread.sleep(2000)

            // Выполняем перезагрузку
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.reboot(null)

            Log.e(TAG, "Device is rebooting...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reboot: ${e.message}", e)
        }
    }

    /**
     * Получает версию приложения из манифеста.
     * 
     * @param context Контекст приложения
     * @return Строка с версией приложения или "Unknown" в случае ошибки
     */
    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Не удалось получить версию приложения: ${e.message}", e)
            "Unknown"
        }
    }
}
