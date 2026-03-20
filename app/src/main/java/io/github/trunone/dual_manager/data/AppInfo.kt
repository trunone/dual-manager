package io.github.trunone.dual_manager.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable? = null,
    val isSystemApp: Boolean = false
)
