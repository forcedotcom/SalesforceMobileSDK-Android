package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.annotation.StringRes
import com.salesforce.samples.mobilesynccompose.R.string.*
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

fun Contact.createFirstNameVm(): ContactDetailFieldViewModel =
    ContactDetailFieldViewModel(
        fieldValue = firstName,
        isInErrorState = false,
        canBeEdited = true,
        labelRes = label_contact_first_name,
        helperRes = null,
        placeholderRes = label_contact_first_name,
        onFieldValueChange = { newValue -> this.copy(firstName = newValue) }
    )

fun Contact.createLastNameVm(): ContactDetailFieldViewModel {
    val isError = lastName.isBlank()
    val help = if (isError) help_cannot_be_blank else null

    return ContactDetailFieldViewModel(
        fieldValue = lastName,
        isInErrorState = isError,
        canBeEdited = true,
        labelRes = label_contact_last_name,
        helperRes = help,
        placeholderRes = label_contact_last_name,
        onFieldValueChange = { newValue -> this.copy(lastName = newValue) }
    )
}

fun Contact.createTitleVm(): ContactDetailFieldViewModel =
    ContactDetailFieldViewModel(
        fieldValue = title,
        isInErrorState = false, // cannot be in error state
        canBeEdited = true,
        labelRes = label_contact_title,
        helperRes = null,
        placeholderRes = label_contact_title,
        onFieldValueChange = { newValue -> this.copy(title = newValue) }
    )
