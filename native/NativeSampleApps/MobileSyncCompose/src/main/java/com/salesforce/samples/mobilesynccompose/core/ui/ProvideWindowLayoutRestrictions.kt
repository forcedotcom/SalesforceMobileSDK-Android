package com.salesforce.samples.mobilesynccompose.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.salesforce.samples.mobilesynccompose.core.ui.restrictions.WindowLayoutRestriction
import com.salesforce.samples.mobilesynccompose.core.ui.restrictions.WindowLayoutRestriction.SimpleRestriction

val LocalWindowLayoutRestrictions = compositionLocalOf<WindowLayoutRestriction> {
    error("LocalWindowLayoutRestrictions does not have a provider.")
}

@Composable
fun ProvideWindowLayoutRestrictions(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalWindowLayoutRestrictions provides SimpleRestriction(
            WindowSizeClassInfo(
                horiz = WindowSizeClass.Compact,
                vert = WindowSizeClass.Medium
            )
        ),
        content = content
    )
}