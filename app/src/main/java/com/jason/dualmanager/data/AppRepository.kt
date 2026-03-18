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
        
        val result = mutableListOf<SpecialPermission>()

        // Add special AppOps permissions
        specialOps.forEach { (op, label, perm) ->
            if (dump.contains(perm)) {
                val status = ShizukuHelper.executeShellCommand("appops get --user 95 $packageName $op")
                val isAllowed = status.contains("allow", ignoreCase = true)
                result.add(SpecialPermission(op, label, isAllowed, perm, isAppOp = true))
            }
        }

        // Add runtime permissions
        val requestedPermissionsSection = dump.substringAfter("requested permissions:", "").substringBefore("install permissions:")
        val runtimePermissionsSection = dump.substringAfter("User 95:", "").substringBefore("User")

        requestedPermissionsSection.lines().map { it.trim() }.filter { it.startsWith("android.permission.") }.forEach { perm ->
            val isGranted = runtimePermissionsSection.contains("$perm: granted=true")
            val label = perm.substringAfterLast(".").replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }

            // Only add if not already added as an AppOp (some overlap)
            if (result.none { it.manifestPermission == perm }) {
                result.add(SpecialPermission(perm, label, isGranted, perm, isAppOp = false))
            }
        }

        result.sortedBy { it.label }
    }

    suspend fun setSpecialPermission(packageName: String, op: String, allow: Boolean, isAppOp: Boolean): Boolean = withContext(Dispatchers.IO) {
        val command = if (isAppOp) {
            val mode = if (allow) "allow" else "ignore"
            "appops set --user 95 $packageName $op $mode"
        } else {
            val action = if (allow) "grant" else "revoke"
            "pm $action --user 95 $packageName $op"
        }
        val output = ShizukuHelper.executeShellCommand(command)
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
