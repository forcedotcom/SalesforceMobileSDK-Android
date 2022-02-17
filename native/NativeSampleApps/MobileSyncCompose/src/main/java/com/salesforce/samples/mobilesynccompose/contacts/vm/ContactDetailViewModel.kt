package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

data class ContactDetailViewModel(
    val contact: Contact,
    val firstNameVm: ContactDetailFieldViewModel,
    val lastNameVm: ContactDetailFieldViewModel,
    val titleVm: ContactDetailFieldViewModel,
) {
    val vmList = listOf(
        firstNameVm,
        lastNameVm,
        titleVm
    )

    val updatedContact: Contact by lazy {
        contact.copy(
            firstName = firstNameVm.fieldValue,
            lastName = lastNameVm.fieldValue,
            title = titleVm.fieldValue,
        )
    }

    val isModified: Boolean by lazy {
        updatedContact != contact
    }
}
