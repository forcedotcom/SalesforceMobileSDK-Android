package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.annotation.StringRes
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

data class ContactDetailFieldViewModel(
    val fieldValue: String,
    val isInErrorState: Boolean,
    val canBeEdited: Boolean,
    @StringRes val labelRes: Int?,
    @StringRes val helperRes: Int?,
    @StringRes val placeholderRes: Int?,
    val onFieldValueChange: (newValue: String) -> Contact,
    val maxLines: UInt = 1u
)
