/*
 * Copyright (c) 2022-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
