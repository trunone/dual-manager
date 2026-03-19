package com.jason.dualmanager.data

data class AppPermission(
    val name: String,
    val label: String,
    val isAllowed: Boolean,
    val isAppOp: Boolean,
    val manifestPermission: String
)
