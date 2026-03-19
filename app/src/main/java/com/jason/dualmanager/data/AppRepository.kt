package com.jason.dualmanager.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.jason.dualmanager.shizuku.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("dual_manager_prefs", Context.MODE_PRIVATE)
    private val HISTORY_KEY = "cloned_apps_history"

    private fun getClonedHistory(): Set<String> {
        return prefs.getStringSet(HISTORY_KEY, emptySet()) ?: emptySet()
    }

    private fun addToHistory(packageName: String) {
        val history = getClonedHistory().toMutableSet()
        if (history.add(packageName)) {
            prefs.edit().putStringSet(HISTORY_KEY, history).apply()
        }
    }

    private fun removeFromHistory(packageName: String) {
        val history = getClonedHistory().toMutableSet()
        if (history.remove(packageName)) {
            prefs.edit().putStringSet(HISTORY_KEY, history).apply()
        }
    }

    suspend fun recoverClonedApps(): List<String> = withContext(Dispatchers.IO) {
        val history = getClonedHistory()
        val failedApps = mutableListOf<String>()
        history.forEach { packageName ->
            try {
                val success = installToDualMessenger(packageName)
                if (!success) {
                    failedApps.add(packageName)
                }
            } catch (e: Exception) {
                failedApps.add(packageName)
            }
        }
        failedApps
    }

    suspend fun getClonedHistoryApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val history = getClonedHistory()
        val pm = context.packageManager
        history.mapNotNull { packageName ->
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

    suspend fun getMainApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val output = ShizukuHelper.executeShellCommand(context, "pm list packages --user 0")
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
        val output = ShizukuHelper.executeShellCommand(context, "pm list packages --user 95")
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
        val dump = ShizukuHelper.executeShellCommand(context, "pm dump $packageName")
        
        specialOps.mapNotNull { (op, label, perm) ->
            if (dump.contains(perm)) {
                val status = ShizukuHelper.executeShellCommand(context, "appops get --user 95 $packageName $op")
                val isAllowed = status.contains("allow", ignoreCase = true)
                SpecialPermission(op, label, isAllowed, perm)
            } else {
                null
            }
        }
    }

    suspend fun setSpecialPermission(packageName: String, op: String, allow: Boolean): Boolean = withContext(Dispatchers.IO) {
        val mode = if (allow) "allow" else "ignore"
        val output = ShizukuHelper.executeShellCommand(context, "appops set --user 95 $packageName $op $mode")
        !output.startsWith("Error")
    }

    suspend fun installToDualMessenger(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val output = ShizukuHelper.executeShellCommand(context, "pm install-existing --user 95 $packageName")
        val success = output.contains("installed", ignoreCase = true) || output.contains("Success", ignoreCase = true)
        if (success) {
            addToHistory(packageName)
        }
        success
    }

    suspend fun uninstallFromDualMessenger(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val output = ShizukuHelper.executeShellCommand(context, "pm uninstall --user 95 $packageName")
        val success = output.contains("Success", ignoreCase = true)
        if (success) {
            removeFromHistory(packageName)
        }
        success
    }

    suspend fun getStandardPermissions(packageName: String): List<SpecialPermission> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val requestedPermissions = try {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }

        val dangerousPermissions = requestedPermissions.filter { perm ->
            try {
                val permInfo = pm.getPermissionInfo(perm, 0)
                (permInfo.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE) == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
            } catch (e: Exception) {
                false
            }
        }

        if (dangerousPermissions.isEmpty()) return@withContext emptyList()

        val dump = ShizukuHelper.executeShellCommand(context, "dumpsys package $packageName")
        val user95Index = dump.indexOf("User 95:")
        val user95Dump = if (user95Index != -1) dump.substring(user95Index) else ""

        dangerousPermissions.map { perm ->
            val grantedIndex = user95Dump.indexOf("$perm: granted=true")
            val isAllowed = grantedIndex != -1

            val label = perm.substringAfterLast(".")
                .replace("_", " ")
                .lowercase()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            SpecialPermission(
                op = perm,
                label = label,
                isAllowed = isAllowed,
                manifestPermission = perm,
                isStandard = true
            )
        }
    }

    suspend fun setStandardPermission(packageName: String, permission: String, allow: Boolean): Boolean = withContext(Dispatchers.IO) {
        val action = if (allow) "grant" else "revoke"
        val output = ShizukuHelper.executeShellCommand(context, "pm $action --user 95 $packageName $permission")
        !output.startsWith("Error")
    }
}
