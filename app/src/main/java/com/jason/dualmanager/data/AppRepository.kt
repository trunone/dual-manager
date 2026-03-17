package com.jason.dualmanager.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.jason.dualmanager.shizuku.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    suspend fun getMainApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val output = ShizukuHelper.executeShellCommand("pm list packages --user 0")
        if (output.startsWith("Error")) {
            return@withContext emptyList()
        }

        val mainPackageNames = output.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }

        val pm = context.packageManager
        mainPackageNames.mapNotNull { packageName ->
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                AppInfo(
                    packageName = appInfo.packageName,
                    name = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun getDualMessengerApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val output = ShizukuHelper.executeShellCommand("pm list packages --user 95")
        if (output.startsWith("Error")) {
            return@withContext emptyList()
        }

        val dualPackageNames = output.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() } // Removing trim() trailing spaces if any

        val pm = context.packageManager
        dualPackageNames.mapNotNull { packageName ->
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                AppInfo(
                    packageName = appInfo.packageName,
                    name = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            } catch (e: PackageManager.NameNotFoundException) {
                AppInfo(
                    packageName = packageName,
                    name = packageName,
                    icon = null,
                    isSystemApp = false
                )
            }
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun getSpecialPermissions(packageName: String): List<SpecialPermission> = withContext(Dispatchers.IO) {
        val specialOps = listOf(
            Triple("MANAGE_EXTERNAL_STORAGE", "All Files Access", "android.permission.MANAGE_EXTERNAL_STORAGE"),
            Triple("SYSTEM_ALERT_WINDOW", "Display Over Other Apps", "android.permission.SYSTEM_ALERT_WINDOW"),
            Triple("WRITE_SETTINGS", "Modify System Settings", "android.permission.WRITE_SETTINGS"),
            Triple("REQUEST_INSTALL_PACKAGES", "Install Unknown Apps", "android.permission.REQUEST_INSTALL_PACKAGES"),
            Triple("GET_USAGE_STATS", "Usage Access", "android.permission.PACKAGE_USAGE_STATS")
        )

        // Check which permissions are requested in manifest
        val dump = ShizukuHelper.executeShellCommand("pm dump $packageName")
        
        specialOps.mapNotNull { (op, label, perm) ->
            if (dump.contains(perm)) {
                val status = ShizukuHelper.executeShellCommand("appops get --user 95 $packageName $op")
                val isAllowed = status.contains("allow", ignoreCase = true)
                SpecialPermission(op, label, isAllowed, perm)
            } else {
                null
            }
        }
    }

    suspend fun setSpecialPermission(packageName: String, op: String, allow: Boolean): Boolean = withContext(Dispatchers.IO) {
        val mode = if (allow) "allow" else "ignore"
        val output = ShizukuHelper.executeShellCommand("appops set --user 95 $packageName $op $mode")
        !output.startsWith("Error")
    }

    suspend fun installToDualMessenger(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val output = ShizukuHelper.executeShellCommand("pm install-existing --user 95 $packageName")
        output.contains("installed", ignoreCase = true) || output.contains("Success", ignoreCase = true)
    }

    suspend fun uninstallFromDualMessenger(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val output = ShizukuHelper.executeShellCommand("pm uninstall --user 95 $packageName")
        output.contains("Success", ignoreCase = true)
    }
}
