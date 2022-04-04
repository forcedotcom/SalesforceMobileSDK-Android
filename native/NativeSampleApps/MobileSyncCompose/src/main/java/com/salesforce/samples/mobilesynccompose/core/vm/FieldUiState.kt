package com.salesforce.samples.mobilesynccompose.core.vm

import androidx.annotation.StringRes

/**
 * Non-editable, general UI state for any field rendered in a form.
 */
interface FieldUiState {
    val fieldValue: String?
    val isInErrorState: Boolean
    val labelRes: Int? @StringRes get
    val helperRes: Int? @StringRes get
    val placeholderRes: Int? @StringRes get
}
