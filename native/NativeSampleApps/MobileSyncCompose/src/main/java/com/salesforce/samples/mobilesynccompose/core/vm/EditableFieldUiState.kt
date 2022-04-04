package com.salesforce.samples.mobilesynccompose.core.vm

/**
 * UI state for a general editable field rendered in a form.
 */
interface EditableFieldUiState : FieldUiState {
    val isEnabled: Boolean
    val onValueChange: (newValue: String) -> Unit
}
