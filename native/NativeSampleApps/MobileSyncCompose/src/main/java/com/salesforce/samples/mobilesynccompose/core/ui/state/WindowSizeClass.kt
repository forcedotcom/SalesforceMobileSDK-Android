package com.salesforce.samples.mobilesynccompose.core.ui.state

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

enum class WindowSizeClass {
    Compact,
    Medium,
    Expanded
}

data class WindowSizeClasses(val horiz: WindowSizeClass, val vert: WindowSizeClass)

fun DpSize.toWindowSizeClasses() = WindowSizeClasses(
    horiz = width.toWindowSizeClass(),
    vert = height.toWindowSizeClass()
)

fun Dp.toWindowSizeClass(): WindowSizeClass {
    val safeDp = this.coerceAtLeast(0.dp)
    return when {
        safeDp < WINDOW_SIZE_COMPACT_CUTOFF_DP.dp -> WindowSizeClass.Compact
        safeDp < WINDOW_SIZE_MEDIUM_CUTOFF_DP.dp -> WindowSizeClass.Medium
        else -> WindowSizeClass.Expanded
    }
}

const val WINDOW_SIZE_COMPACT_CUTOFF_DP = 600
const val WINDOW_SIZE_MEDIUM_CUTOFF_DP = 840
