package com.salesforce.samples.mobilesynccompose.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource

@Composable
@ReadOnlyComposable
fun safeStringResource(@StringRes id: Int?): String =
    if (id != null) {
        stringResource(id = id)
    } else {
        ""
    }
