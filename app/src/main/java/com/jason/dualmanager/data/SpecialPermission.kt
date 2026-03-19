package com.jason.dualmanager.data

data class SpecialPermission(
    val op: String,
    val label: String,
    val isAllowed: Boolean,
    val manifestPermission: String,
    val isStandard: Boolean = false
)
