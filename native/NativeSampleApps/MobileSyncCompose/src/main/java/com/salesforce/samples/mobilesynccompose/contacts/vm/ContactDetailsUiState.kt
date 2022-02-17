package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

data class ContactDetailsUiState(
    val mode: ContactDetailsUiMode,
    val origContact: Contact,
    val firstNameVm: ContactDetailFieldViewModel,
    val lastNameVm: ContactDetailFieldViewModel,
    val titleVm: ContactDetailFieldViewModel,
    val isSaving: Boolean = false,
    val fieldToScrollTo: ContactDetailFieldViewModel? = null
) {
    val vmList = listOf(
        firstNameVm,
        lastNameVm,
        titleVm
    )

    val updatedContact: Contact by lazy {
        origContact.copy(
            firstName = firstNameVm.fieldValue,
            lastName = lastNameVm.fieldValue,
            title = titleVm.fieldValue,
        )
    }

    val isModified: Boolean by lazy {
        updatedContact != origContact
    }

    val fieldsInErrorState: List<ContactDetailFieldViewModel> by lazy {
        vmList.filter { it.isInErrorState }
    }

    val hasFieldsInErrorState: Boolean by lazy {
        fieldsInErrorState.isNotEmpty()
    }
}

enum class ContactDetailsUiMode {
    Creating,
    Editing,
    Viewing
}

fun Contact.toContactDetailsUiState(mode: ContactDetailsUiMode, isSaving: Boolean = false) =
    ContactDetailsUiState(
        mode = mode,
        origContact = this,
        firstNameVm = createFirstNameVm(),
        lastNameVm = createLastNameVm(),
        titleVm = createTitleVm(),
        isSaving = isSaving
    )
