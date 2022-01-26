package com.salesforce.samples.mobilesynccompose.core.ui

import android.graphics.Rect

data class LayoutRestrictions(
    val sizeRestrictions: WindowSizeRestrictions,
    val foldRestrictions: FoldRestrictions? = null,
)

data class WindowSizeRestrictions(val horiz: WindowSizeClass, val vert: WindowSizeClass)

data class FoldRestrictions(
    val foldOrientation: FoldOrientation,
    val foldState: FoldState,
    val isHingeOccluding: Boolean,
    val hingeBounds: Rect,
) {
    val spansTwoLogicalAreas = isHingeOccluding || foldState == FoldState.HalfOpen
}
