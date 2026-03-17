package com.jason.dualmanager.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuHelper {

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

    fun requestShizukuPermission(requestCode: Int) {
        if (isShizukuAvailable() && !isShizukuPermissionGranted()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    fun executeShellCommand(command: String): String {
        if (!isShizukuPermissionGranted()) {
            return "Error: Shizuku permission not granted."
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
            "Error: ${e.message}"
        }
    }
}
