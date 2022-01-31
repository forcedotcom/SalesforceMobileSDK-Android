package com.salesforce.samples.mobilesynccompose.core.ui

import android.graphics.Rect
import androidx.compose.ui.unit.DpSize

data class LayoutRestrictions(
    val sizeRestrictions: WindowSizeRestrictions,
    val foldRestrictions: FoldRestrictions? = null,
)

data class WindowSizeRestrictions(val horiz: WindowSizeClass, val vert: WindowSizeClass)

fun DpSize.toWindowSizeRestrictions(): WindowSizeRestrictions = WindowSizeRestrictions(
    horiz = width.toWindowSizeClass(),
    vert = height.toWindowSizeClass()
)

data class FoldRestrictions(
    val foldOrientation: FoldOrientation,
    val foldState: FoldState,
    val isHingeOccluding: Boolean,
    val hingeBounds: Rect,
) {
    val spansTwoLogicalAreas = isHingeOccluding || foldState == FoldState.HalfOpen
}
