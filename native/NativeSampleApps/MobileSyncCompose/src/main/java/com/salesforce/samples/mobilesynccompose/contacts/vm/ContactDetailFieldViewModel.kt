package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.annotation.StringRes
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

/**
 * A ViewModel for a single Contact detail text field (e.g. the "first name" field).
 * It holds the entire state of the text field and handles content change events, encapsulating
 * business logic for creating updated [Contact] objects when the corresponding field value changes.
 */
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

// TODO there is a flaw in this system of capturing the Contact reference in this callback. Using a
//  stale object can lead to inconsistent state and at bare minimum we should change this interface
//  to emit the contact ID and which field changed. This means more concretely typing the fields so
//  that the activity VM knows which field changed.
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
