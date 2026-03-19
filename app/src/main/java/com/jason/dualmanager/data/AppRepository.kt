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
            .map { it.removePrefix("package:").trim() }

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

    private fun extractSection(dump: String, startTag: String): String {
        val lines = dump.lines()
        val startIndex = lines.indexOfFirst { it.trim().startsWith(startTag) }
        if (startIndex == -1) return ""

        val baseIndent = lines[startIndex].takeWhile { it.isWhitespace() }.length
        val sectionContent = mutableListOf<String>()
        sectionContent.add(lines[startIndex])

        for (i in startIndex + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val currentIndent = line.takeWhile { it.isWhitespace() }.length
            if (currentIndent <= baseIndent) break
            sectionContent.add(line)
        }

        return sectionContent.joinToString("\n")
    }

    suspend fun getAppPermissions(packageName: String): List<AppPermission> = withContext(Dispatchers.IO) {
        val specialOps = listOf(
            Triple("MANAGE_EXTERNAL_STORAGE", "All Files Access", "android.permission.MANAGE_EXTERNAL_STORAGE"),
            Triple("SYSTEM_ALERT_WINDOW", "Display Over Other Apps", "android.permission.SYSTEM_ALERT_WINDOW"),
            Triple("WRITE_SETTINGS", "Modify System Settings", "android.permission.WRITE_SETTINGS"),
            Triple("REQUEST_INSTALL_PACKAGES", "Install Unknown Apps", "android.permission.REQUEST_INSTALL_PACKAGES"),
            Triple("GET_USAGE_STATS", "Usage Access", "android.permission.PACKAGE_USAGE_STATS")
        )

        val dump = ShizukuHelper.executeShellCommand(context, "pm dump $packageName")
        
        val permissions = mutableListOf<AppPermission>()

        // Runtime Permissions
        val user95Section = extractSection(dump, "User 95:")
        val runtimePermissionsSection = extractSection(user95Section, "runtime permissions:")

        val installPermissionsSection = extractSection(dump, "install permissions:")

        val requestedPermissions = dump.lines()
            .dropWhile { !it.contains("requested permissions:") }
            .drop(1)
            .takeWhile { it.startsWith("  ") }
            .map { it.trim() }

        requestedPermissions.forEach { perm ->
            if (specialOps.any { it.third == perm }) return@forEach

            val isGranted = runtimePermissionsSection.lines().any { it.contains(perm) && it.contains("granted=true") } ||
                    installPermissionsSection.lines().any { it.contains(perm) && it.contains("granted=true") }

            val label = perm.substringAfterLast(".")
            permissions.add(AppPermission(perm, label, isGranted, false, perm))
        }

        // Special AppOps
        specialOps.forEach { (op, label, perm) ->
            if (dump.contains(perm)) {
                val status = ShizukuHelper.executeShellCommand(context, "appops get --user 95 $packageName $op")
                val isAllowed = status.contains("allow", ignoreCase = true)
                permissions.add(AppPermission(op, label, isAllowed, true, perm))
            }
        }

        permissions.sortedBy { it.label.lowercase() }
    }

    suspend fun setAppPermission(packageName: String, permission: AppPermission, allow: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (permission.isAppOp) {
            val mode = if (allow) "allow" else "ignore"
            val output = ShizukuHelper.executeShellCommand(context, "appops set --user 95 $packageName ${permission.name} $mode")
            !output.startsWith("Error")
        } else {
            val action = if (allow) "grant" else "revoke"
            val output = ShizukuHelper.executeShellCommand(context, "pm $action --user 95 $packageName ${permission.name}")
            !output.startsWith("Error")
        }
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
}
