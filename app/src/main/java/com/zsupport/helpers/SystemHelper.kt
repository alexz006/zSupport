package com.zsupport.helpers

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

object SystemHelper {

    private const val TAG = "SystemHelper"

    /**
     * Показывает диалог для подтверждения перезагрузки.
     */
    fun showRebootDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Reboot Device")
            .setMessage("Are you sure you want to reboot your device?")
            .setPositiveButton("Yes") { _, _ ->
                initiateReboot(context)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Инициирует безопасную перезагрузку с уведомлением.
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
}
