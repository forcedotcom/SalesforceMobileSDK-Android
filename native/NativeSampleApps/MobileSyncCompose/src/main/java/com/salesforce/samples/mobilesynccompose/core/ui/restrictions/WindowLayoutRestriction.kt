package com.salesforce.samples.mobilesynccompose.core.ui.restrictions

import android.graphics.Rect
import com.salesforce.samples.mobilesynccompose.core.ui.FoldOrientation
import com.salesforce.samples.mobilesynccompose.core.ui.FoldState
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeClassInfo

sealed interface WindowLayoutRestriction {
    data class SimpleRestriction(
        override val windowSizeClassInfo: WindowSizeClassInfo
    ) : WindowLayoutRestriction,
        WindowSizeRestriction

    data class FoldingRestriction(
        override val windowSizeClassInfo: WindowSizeClassInfo,
        override val foldOrientation: FoldOrientation,
        override val foldState: FoldState,
        override val isHingeOccluding: Boolean,
        override val hingeBounds: Rect,
    ) : WindowLayoutRestriction,
        FoldRestriction,
        WindowSizeRestriction {

        val spansTwoLogicalAreas = isHingeOccluding || foldState == FoldState.HalfOpen
    }
}
