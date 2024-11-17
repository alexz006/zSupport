package com.zsupport.helpers

import android.content.Context
import android.util.Log

class PermissionsHelper {

    val TAG = "AnyAppPermissionsHelper"

    private val packagePermissionsMap = mutableMapOf(
        "com.anyapp.store" to listOf("REQUEST_INSTALL_PACKAGES"),
        "com.anyapp.zee.store" to listOf("REQUEST_INSTALL_PACKAGES"),
        "ru.vk.store" to listOf("REQUEST_INSTALL_PACKAGES"),
        "air.StrelkaHUDFREE" to listOf("android.permission.SYSTEM_ALERT_WINDOW", "deviceidle whitelist")
    )

    // Метод для выдачи прав согласно маппингу
    fun applyPermissions(context: Context, packageName: String) {
        val permissions = packagePermissionsMap[packageName]
        if (permissions.isNullOrEmpty()) {
            Log.w(TAG, "No permissions configured for $packageName")
            return
        }

        permissions.forEach { permission ->
            when (permission) {
                "REQUEST_INSTALL_PACKAGES" -> {
                    grantRuntimePermission(context, packageName, permission)
                }
                "android.permission.SYSTEM_ALERT_WINDOW" -> {
                    grantRuntimePermission(context, packageName, permission)
                }
                "deviceidle whitelist" -> {
                    addToWhitelist(packageName)
                }
                else -> Log.e(TAG, "Unknown permission/action: $permission for $packageName")
            }
        }
    }

    private fun grantRuntimePermission(context: Context, packageName: String, permission: String) {
        try {
            val packageManager = context.packageManager

            // Используем рефлексию для вызова метода grantRuntimePermission
            val packageManagerClass = packageManager.javaClass
            val grantPermissionMethod = packageManagerClass.getMethod(
                "grantRuntimePermission",
                String::class.java,
                String::class.java,
                android.os.UserHandle::class.java
            )

            val userHandle = android.os.Process.myUserHandle()

            grantPermissionMethod.invoke(packageManager, packageName, permission, userHandle)
            Log.i(TAG, "Granted permission: $permission to $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant permission $permission to $packageName: ${e.message}", e)
        }
    }


    // Добавить в whitelist Doze Mode
    private fun addToWhitelist(packageName: String) {
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val binder = serviceManagerClass.getDeclaredMethod("getService", String::class.java)
                .invoke(null, "deviceidle") as android.os.IBinder

            val deviceIdleControllerClass = Class.forName("android.os.IDeviceIdleController\$Stub")
            val asInterface = deviceIdleControllerClass.getMethod("asInterface", android.os.IBinder::class.java)
            val deviceIdleController = asInterface.invoke(null, binder)

            val whitelistMethod = deviceIdleController.javaClass.getMethod("addPowerSaveWhitelistApp", String::class.java)
            whitelistMethod.invoke(deviceIdleController, packageName)
            Log.i(TAG, "Added $packageName to Doze whitelist")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add $packageName to Doze whitelist: ${e.message}", e)
        }
    }

    // Метод для добавления нового пакета и прав
    fun addPackageWithPermissions(packageName: String, permissions: List<String>) {
        packagePermissionsMap[packageName] = permissions
        Log.i(TAG, "Added $packageName with permissions: $permissions")
    }
}