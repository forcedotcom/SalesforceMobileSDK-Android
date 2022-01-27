package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.annotation.StringRes

data class ContactDetailFieldViewModel(
    val fieldValue: String,
    val isInErrorState: Boolean,
    @StringRes val helperRes: Int?,
)
