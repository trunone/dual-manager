package io.github.trunone.dual_manager.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.github.trunone.dual_manager.shizuku.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val prefs by lazy { context.getSharedPreferences("dual_manager_prefs", Context.MODE_PRIVATE) }
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

    fun extractSection(lines: List<String>, sectionHeader: String): List<String> {
        val headerIndex = lines.indexOfFirst { it.trim().startsWith(sectionHeader) }
        if (headerIndex == -1) return emptyList()

        val headerIndent = lines[headerIndex].takeWhile { it.isWhitespace() }.length
        val result = mutableListOf<String>()

        for (i in headerIndex + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val indent = line.takeWhile { it.isWhitespace() }.length
            if (indent > headerIndent) {
                result.add(line)
            } else {
                break
            }
        }
        return result
    }

    private fun getRequestedPermissions(packageName: String, dumpLines: List<String>): Set<String> {
        val pmPermissions = try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            packageInfo.requestedPermissions?.toSet()
        } catch (e: Exception) {
            null
        }

        if (!pmPermissions.isNullOrEmpty()) {
            return pmPermissions
        }

        val permRegex = Regex("^[a-zA-Z0-9._]+$")
        val requestedSection = extractSection(dumpLines, "requested permissions:")
        return requestedSection
            .map { it.trim().substringBefore(":") }
            .filter { permRegex.matches(it) }
            .toSet()
    }

    private fun isDangerousPermission(perm: String): Boolean {
        return try {
            val info = context.packageManager.getPermissionInfo(perm, 0)
            val protection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.protection
            } else {
                @Suppress("DEPRECATION")
                info.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE
            }
            protection == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getSpecialPermissions(packageName: String): List<SpecialPermission> = withContext(Dispatchers.IO) {
        val specialOps = listOf(
            Triple("MANAGE_EXTERNAL_STORAGE", "All Files Access", "android.permission.MANAGE_EXTERNAL_STORAGE"),
            Triple("SYSTEM_ALERT_WINDOW", "Display Over Other Apps", "android.permission.SYSTEM_ALERT_WINDOW"),
            Triple("WRITE_SETTINGS", "Modify System Settings", "android.permission.WRITE_SETTINGS"),
            Triple("REQUEST_INSTALL_PACKAGES", "Install Unknown Apps", "android.permission.REQUEST_INSTALL_PACKAGES"),
            Triple("GET_USAGE_STATS", "Usage Access", "android.permission.PACKAGE_USAGE_STATS")
        )

        val dumpOutput = try {
            ShizukuHelper.executeShellCommand(context, "pm dump $packageName")
        } catch (e: Exception) {
            ""
        }
        val dumpLines = dumpOutput.lines()
        val requestedPermissions = getRequestedPermissions(packageName, dumpLines)

        specialOps.mapNotNull { (op, label, perm) ->
            if (requestedPermissions.contains(perm)) {
                val status = try {
                    ShizukuHelper.executeShellCommand(context, "appops get --user 95 $packageName $op")
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
                val isAllowed = !status.startsWith("Error") &&
                        status.contains("allow", ignoreCase = true) &&
                        !status.contains("ignore", ignoreCase = true) &&
                        !status.contains("deny", ignoreCase = true)
                SpecialPermission(
                    op = op,
                    label = label,
                    isAllowed = isAllowed,
                    manifestPermission = perm,
                    isStandard = false
                )
            } else {
                null
            }
        }
    }

    suspend fun setSpecialPermission(packageName: String, op: String, allow: Boolean): Boolean = withContext(Dispatchers.IO) {
        val mode = if (allow) "allow" else "ignore"
        val output = ShizukuHelper.executeShellCommand(context, "appops set --user 95 $packageName $op $mode")
        !output.startsWith("Error") && !output.contains("Exception", ignoreCase = true)
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
        val dumpOutput = try {
            ShizukuHelper.executeShellCommand(context, "pm dump $packageName")
        } catch (e: Exception) {
            ""
        }
        val dumpLines = dumpOutput.lines()
        val requestedPermissions = getRequestedPermissions(packageName, dumpLines)

        val dangerousPermissions = requestedPermissions.filter { isDangerousPermission(it) }

        if (dangerousPermissions.isEmpty()) return@withContext emptyList()

        val user95Lines = extractSection(dumpLines, "User 95:")
        val user95RuntimeLines = extractSection(user95Lines, "runtime permissions:")
        val installPermissionsLines = extractSection(dumpLines, "install permissions:")

        dangerousPermissions.map { perm ->
            var isAllowed = false

            val runtimeMatch = user95RuntimeLines.find { line ->
                line.trim().split(":").firstOrNull()?.trim() == perm
            }

            if (runtimeMatch != null) {
                isAllowed = runtimeMatch.contains("granted=true")
            } else {
                val installMatch = installPermissionsLines.find { line ->
                    line.trim().split(":").firstOrNull()?.trim() == perm
                }
                if (installMatch != null) {
                    isAllowed = installMatch.contains("granted=true")
                }
            }

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
        !output.startsWith("Error") && !output.contains("Exception", ignoreCase = true) && !output.contains("is not a changeable", ignoreCase = true)
    }
}
