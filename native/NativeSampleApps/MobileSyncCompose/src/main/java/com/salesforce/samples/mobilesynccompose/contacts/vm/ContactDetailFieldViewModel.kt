package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.annotation.StringRes
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject

data class ContactDetailFieldViewModel(
    val fieldValue: String,
    val isInErrorState: Boolean,
    val canBeEdited: Boolean,
    @StringRes val labelRes: Int?,
    @StringRes val helperRes: Int?,
    @StringRes val placeholderRes: Int?,
    val onFieldValueChange: (newValue: String) -> ContactObject
)
