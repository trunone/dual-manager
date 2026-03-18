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

    private fun extractSection(dump: String, startMarker: String): String {
        val startIndex = dump.indexOf(startMarker)
        if (startIndex == -1) return ""

        val contentStart = startIndex + startMarker.length
        val lines = dump.substring(contentStart).lines()
        val result = StringBuilder()

        // Find the base indentation level of the first non-empty line
        var baseIndent = -1

        for (line in lines) {
            if (line.isBlank()) continue

            val currentIndent = line.takeWhile { it.isWhitespace() }.length
            if (baseIndent == -1) {
                baseIndent = currentIndent
            } else if (currentIndent < baseIndent && line.trim().isNotEmpty()) {
                // We've reached a line with less indentation than the section's first line,
                // which means the section has ended.
                break
            }
            result.append(line).append("\n")
        }
        return result.toString()
    }

    suspend fun getSpecialPermissions(packageName: String): List<SpecialPermission> = withContext(Dispatchers.IO) {
        val specialOps = listOf(
            Triple("MANAGE_EXTERNAL_STORAGE", "All Files Access", "android.permission.MANAGE_EXTERNAL_STORAGE"),
            Triple("SYSTEM_ALERT_WINDOW", "Display Over Other Apps", "android.permission.SYSTEM_ALERT_WINDOW"),
            Triple("WRITE_SETTINGS", "Modify System Settings", "android.permission.WRITE_SETTINGS"),
            Triple("REQUEST_INSTALL_PACKAGES", "Install Unknown Apps", "android.permission.REQUEST_INSTALL_PACKAGES"),
            Triple("GET_USAGE_STATS", "Usage Access", "android.permission.PACKAGE_USAGE_STATS")
        )

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
        val requestedPermissions = extractSection(dump, "requested permissions:").lines()
            .map { it.trim().substringBefore(":") }
            .filter { it.startsWith("android.permission.") }

        val installPermissionsSection = extractSection(dump, "install permissions:")
        val user95Section = extractSection(dump, "User 95:")

        requestedPermissions.forEach { perm ->
            val isGranted = installPermissionsSection.lines().any { it.trim().startsWith("$perm: granted=true") } ||
                           user95Section.lines().any { it.trim().startsWith("$perm: granted=true") }

            val label = perm.substringAfterLast(".").replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }

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
