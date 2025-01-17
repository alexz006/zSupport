package com.zsupport.helpers

import android.content.Context
import android.util.Log

class PermissionsHelper {

    val TAG = "AnyAppPermissionsHelper"

    private val packagePermissionsMap = mutableMapOf(
        "com.anyapp.store" to listOf("android.permission.REQUEST_INSTALL_PACKAGES"),
        "com.anyapp.zee.store" to listOf("android.permission.REQUEST_INSTALL_PACKAGES"),
        "ru.vk.store" to listOf("android.permission.REQUEST_INSTALL_PACKAGES"),
        "air.StrelkaHUDFREE" to listOf("android.permission.SYSTEM_ALERT_WINDOW", "deviceidle whitelist"),
        "ru.yandex.yandexnavi" to listOf("android.permission.SYSTEM_ALERT_WINDOW"),
        "com.carwizard.li.youtube" to listOf("android.permission.SYSTEM_ALERT_WINDOW"),
        "com.anyapp.vkvideo" to listOf("android.permission.SYSTEM_ALERT_WINDOW")
    )

    fun applyPermissions(context: Context, packageName: String) {
        val permissions = packagePermissionsMap[packageName]
        if (permissions.isNullOrEmpty()) {
            Log.w(TAG, "No permissions configured for $packageName")
            return
        }

        permissions.forEach { permission ->
            try {
                when (permission) {
                    "android.permission.REQUEST_INSTALL_PACKAGES" -> {
                        if (!isAppOpsPermissionGranted(context, packageName, "OP_REQUEST_INSTALL_PACKAGES")) {
                            setAppOpsPermission(context, packageName, "OP_REQUEST_INSTALL_PACKAGES", "allow")
                        } else {
                            Log.i(TAG, "AppOps OP_REQUEST_INSTALL_PACKAGES already granted for $packageName")
                        }
                    }
                    "android.permission.SYSTEM_ALERT_WINDOW" -> {
                        if (!isRuntimePermissionGranted(context, packageName, permission)) {
                            grantRuntimePermission(context, packageName, permission)
                        } else {
                            Log.i(TAG, "Permission $permission already granted for $packageName")
                        }
                    }
                    "deviceidle whitelist" -> {
                        if (!isInDozeWhitelist(packageName)) {
                            addToWhitelist(packageName)
                        } else {
                            Log.i(TAG, "Package $packageName is already in Doze whitelist")
                        }
                    }
                    else -> Log.e(TAG, "Unknown permission/action: $permission for $packageName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process permission $permission for $packageName: ${e.message}", e)
            }
        }
    }

    private fun isRuntimePermissionGranted(context: Context, packageName: String, permission: String): Boolean {
        return try {
            val packageManager = context.packageManager
            packageManager.checkPermission(permission, packageName) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check runtime permission $permission for $packageName: ${e.message}", e)
            false
        }
    }

    private fun isAppOpsPermissionGranted(context: Context, packageName: String, appOp: String): Boolean {
        return try {
            val appOpsManagerClass = Class.forName("android.app.AppOpsManager")
            val checkOpMethod = appOpsManagerClass.getMethod(
                "checkOpNoThrow",
                Integer.TYPE,  // опция
                Integer.TYPE,  // UID приложения
                String::class.java // пакет
            )

            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE)
            val appUid = context.packageManager.getApplicationInfo(packageName, 0).uid
            val opCode = appOpsManagerClass.getDeclaredField(appOp).getInt(null)

            val result = checkOpMethod.invoke(appOpsManager, opCode, appUid, packageName) as Int
            result == 0 // MODE_ALLOWED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check AppOps $appOp for $packageName: ${e.message}", e)
            false
        }
    }

    private fun isInDozeWhitelist(packageName: String): Boolean {
        return try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val binder = serviceManagerClass.getDeclaredMethod("getService", String::class.java)
                .invoke(null, "deviceidle") as android.os.IBinder

            val deviceIdleControllerClass = Class.forName("android.os.IDeviceIdleController\$Stub")
            val asInterface = deviceIdleControllerClass.getMethod("asInterface", android.os.IBinder::class.java)
            val deviceIdleController = asInterface.invoke(null, binder)

            val isWhitelistedMethod = deviceIdleController.javaClass.getMethod(
                "isPowerSaveWhitelistApp", String::class.java
            )
            isWhitelistedMethod.invoke(deviceIdleController, packageName) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Doze whitelist for $packageName: ${e.message}", e)
            false
        }
    }

    private fun setAppOpsPermission(context: Context, packageName: String, appOp: String, mode: String) {
        try {
            val appOpsManagerClass = Class.forName("android.app.AppOpsManager")
            val setModeMethod = appOpsManagerClass.getMethod(
                "setMode",
                Integer.TYPE,  // опция
                Integer.TYPE,  // UID приложения
                String::class.java, // пакет
                Integer.TYPE   // режим (0=allow, 1=ignore)
            )

            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE)
            val appUid = context.packageManager.getApplicationInfo(packageName, 0).uid
            val modeValue = when (mode.lowercase()) {
                "allow" -> 0  // MODE_ALLOWED
                "ignore" -> 1  // MODE_IGNORED
                else -> throw IllegalArgumentException("Unsupported mode: $mode")
            }

            val opCode = appOpsManagerClass.getDeclaredField(appOp).getInt(null)
            setModeMethod.invoke(appOpsManager, opCode, appUid, packageName, modeValue)
            Log.i(TAG, "AppOps $appOp set to $mode for $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set AppOps $appOp for $packageName: ${e.message}", e)
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
}
