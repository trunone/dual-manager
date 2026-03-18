package com.jason.dualmanager.shizuku

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

sealed class ShizukuStatus {
    object NotInstalled : ShizukuStatus()
    object NotRunning : ShizukuStatus()
    object PermissionDenied : ShizukuStatus()
    object Ready : ShizukuStatus()
}

class ShizukuException(val status: ShizukuStatus, message: String) : Exception(message)

object ShizukuHelper {

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isShizukuAvailable(): Boolean {
        return Shizuku.pingBinder()
    }

    fun isShizukuPermissionGranted(): Boolean {
        return if (isShizukuAvailable()) {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    fun getShizukuStatus(context: Context): ShizukuStatus {
        return when {
            !isShizukuInstalled(context) -> ShizukuStatus.NotInstalled
            !isShizukuAvailable() -> ShizukuStatus.NotRunning
            !isShizukuPermissionGranted() -> ShizukuStatus.PermissionDenied
            else -> ShizukuStatus.Ready
        }
    }

    fun requestShizukuPermission(requestCode: Int) {
        if (isShizukuAvailable() && !isShizukuPermissionGranted()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    fun executeShellCommand(context: Context, command: String): String {
        val status = getShizukuStatus(context)
        if (status != ShizukuStatus.Ready) {
            val message = when (status) {
                ShizukuStatus.NotInstalled -> "Shizuku is not installed. Please install Shizuku app."
                ShizukuStatus.NotRunning -> "Shizuku service is not running. Please start Shizuku."
                ShizukuStatus.PermissionDenied -> "Shizuku permission not granted. Please allow Dual Manager in Shizuku."
                else -> "Shizuku is not ready."
            }
            throw ShizukuException(status, message)
        }

        return try {
            val method = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val session = method.invoke(null, arrayOf("sh", "-c", command), null, null) as java.lang.Process

            val reader = BufferedReader(InputStreamReader(session.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            session.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            throw ShizukuException(ShizukuStatus.Ready, "Error executing command: ${e.message}")
        }
    }
}
