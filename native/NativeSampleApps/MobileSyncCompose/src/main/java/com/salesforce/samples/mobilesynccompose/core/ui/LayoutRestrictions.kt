package com.salesforce.samples.mobilesynccompose.core.ui

import android.graphics.Rect
import androidx.compose.ui.unit.DpSize

/**
 * An encapsulation of all the information needed to make both high- and low-level UI layout decisions.
 * Using [sizeRestrictions] allows the activity content to decide what high-level screen
 * configuration to use, and [foldRestrictions] allows individual UI elements to layout around the
 * device's fold.
 *
 * @param sizeRestrictions Property containing information about overall width and height of the window.
 * @param foldRestrictions Property containing information about the device's fold state and hinge position.
 */
data class LayoutRestrictions(
    val sizeRestrictions: WindowSizeRestrictions,
    val foldRestrictions: FoldRestrictions? = null,
)

/**
 * A simple model representation of what size class the app's window is.
 */
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
