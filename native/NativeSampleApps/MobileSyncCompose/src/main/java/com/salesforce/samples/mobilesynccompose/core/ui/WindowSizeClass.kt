package com.salesforce.samples.mobilesynccompose.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowSizeClass {
    Compact,
    Medium,
    Extended
}

fun Dp.toWindowSizeClass(): WindowSizeClass {
    val safeDp = this.coerceAtLeast(0.dp)
    return when {
        safeDp < 600.dp -> WindowSizeClass.Compact
        safeDp < 840.dp -> WindowSizeClass.Medium
        else -> WindowSizeClass.Extended
    }
}