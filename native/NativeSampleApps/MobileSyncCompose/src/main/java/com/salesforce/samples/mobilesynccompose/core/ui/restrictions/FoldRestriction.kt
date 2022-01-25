package com.salesforce.samples.mobilesynccompose.core.ui.restrictions

import android.graphics.Rect
import com.salesforce.samples.mobilesynccompose.core.ui.FoldOrientation
import com.salesforce.samples.mobilesynccompose.core.ui.FoldState

interface FoldRestriction {
    val hingeBounds: Rect
    val isHingeOccluding: Boolean
    val foldOrientation: FoldOrientation
    val foldState: FoldState
}