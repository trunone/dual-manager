package io.github.trunone.dual_manager

import android.content.Context
import android.content.ContextWrapper
import io.github.trunone.dual_manager.data.AppRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRepositoryTest {

    private val dummyContext: Context = object : ContextWrapper(null) {}
    private val repository = AppRepository(dummyContext)

    @Test
    fun testExtractSection_user95SectionIsolated() {
        val dumpText = """
Packages:
  Package [com.example.app] (123456):
    userId=10123
    install permissions:
      android.permission.INTERNET: granted=true
    User 0:
      installed=true
      runtime permissions:
        android.permission.CAMERA: granted=true, flags=[ USER_SET ]
    User 95:
      installed=true
      runtime permissions:
        android.permission.CAMERA: granted=true, flags=[ USER_SET ]
        android.permission.RECORD_AUDIO: granted=false, flags=[ USER_SET ]
    User 96:
      installed=true
        """.trimIndent()

        val lines = dumpText.lines()
        val user95Lines = repository.extractSection(lines, "User 95:")

        assertEquals(4, user95Lines.size)
        assertTrue(user95Lines.any { it.contains("android.permission.CAMERA: granted=true") })
        assertTrue(user95Lines.any { it.contains("android.permission.RECORD_AUDIO: granted=false") })
        assertTrue(user95Lines.none { it.contains("User 0:") })
        assertTrue(user95Lines.none { it.contains("User 96:") })
    }

    @Test
    fun testExtractSection_nestedRuntimePermissions() {
        val dumpText = """
    User 95:
      installed=true
      runtime permissions:
        android.permission.CAMERA: granted=true, flags=[ USER_SET ]
        android.permission.RECORD_AUDIO: granted=false, flags=[ USER_SET ]
    User 0:
      installed=true
        """.trimIndent()

        val lines = dumpText.lines()
        val user95Lines = repository.extractSection(lines, "User 95:")
        val runtimeLines = repository.extractSection(user95Lines, "runtime permissions:")

        assertEquals(2, runtimeLines.size)
        assertTrue(runtimeLines[0].contains("android.permission.CAMERA: granted=true"))
        assertTrue(runtimeLines[1].contains("android.permission.RECORD_AUDIO: granted=false"))
    }

    @Test
    fun testExtractSection_notFoundReturnsEmpty() {
        val dumpText = """
Packages:
  Package [com.example.app]:
    User 0:
      installed=true
        """.trimIndent()

        val lines = dumpText.lines()
        val user95Lines = repository.extractSection(lines, "User 95:")
        assertTrue(user95Lines.isEmpty())
    }

    @Test
    fun testExtractSection_installPermissions() {
        val dumpText = """
Packages:
  Package [com.example.app]:
    install permissions:
      android.permission.INTERNET: granted=true
      android.permission.ACCESS_NETWORK_STATE: granted=true
    User 95:
      installed=true
        """.trimIndent()

        val lines = dumpText.lines()
        val installLines = repository.extractSection(lines, "install permissions:")

        assertEquals(2, installLines.size)
        assertTrue(installLines.any { it.contains("android.permission.INTERNET: granted=true") })
    }
}
