package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityUiEvents.ContactEdit
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityUiEvents.ContactView
import com.salesforce.samples.mobilesynccompose.contacts.vm.DetailComponentUiEvents.FieldValuesChanged
import com.salesforce.samples.mobilesynccompose.contacts.vm.DetailComponentUiEvents.SaveClick
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact
import com.salesforce.samples.mobilesynccompose.model.contacts.toEditContactUiState

sealed interface ContactDetailUiState : Iterable<ContactDetailFieldViewModel> {
    fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactDetailUiState
    fun calculateProposedTransition(event: DetailComponentUiEvents): ContactDetailUiState
    fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactDetailUiState
}

data class EditingContact(
    val originalContact: Contact,
    val firstNameVm: ContactDetailFieldViewModel,
    val lastNameVm: ContactDetailFieldViewModel,
    val titleVm: ContactDetailFieldViewModel,
    val vmToScrollTo: ContactDetailFieldViewModel? = null
) : ContactDetailUiState {
    private val vmList = listOf(
        firstNameVm,
        lastNameVm,
        titleVm,
    )

    override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactDetailUiState =
        when (event) {
            ContactsActivityUiEvents.ContactCreate,
            is ContactsActivityUiEvents.ContactDelete,
            is ContactEdit,
//            is ContactsActivityUiEvents.ContactListUpdates,
            is ContactView,
            ContactsActivityUiEvents.InspectDbClick,
            ContactsActivityUiEvents.LogoutClick,
            ContactsActivityUiEvents.NavBack,
            ContactsActivityUiEvents.NavUp,
            ContactsActivityUiEvents.SwitchUserClick,
            ContactsActivityUiEvents.SyncClick -> this
        }

    override fun calculateProposedTransition(event: DetailComponentUiEvents): ContactDetailUiState {
        when (event) {
            is FieldValuesChanged -> {
                return event.newObject.toEditContactUiState(originalContact)
            }
            SaveClick -> {
                for (vm in this) {
                    if (vm.isInErrorState) {
                        return this.copy(vmToScrollTo = vm)
                    }
                }
                return ViewingContact(
                    contact = originalContact.copy(
                        firstName = firstNameVm.fieldValue,
                        lastName = lastNameVm.fieldValue,
                        title = titleVm.fieldValue
                    ),
                    firstNameVm = firstNameVm,
                    lastNameVm = lastNameVm,
                    titleVm = titleVm
                )
            }
        }
    }

    override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactDetailUiState =
        when (event) {
            is ContactsActivityDataEvents.ContactDetailsSaved -> ViewingContact(
                contact = event.contact,
                firstNameVm = event.contact.createFirstNameVm(),
                lastNameVm = event.contact.createLastNameVm(),
                titleVm = event.contact.createTitleVm()
            )
            is ContactsActivityDataEvents.ContactListUpdates -> {
                if ()
                val conflictingContact = event.newContactList.firstOrNull {
                    it.id == this.originalContact.id
                }

                if (conflictingContact == null) {
                    // updated list does not contain this
                    this
                } else {
                    if (conflictingContact == originalContact) {
                        this
                    } else {
                        TODO("EditingContact -> ContactListUpdates with conflicting updates. This contact=$originalContact upstream contact=$conflictingContact")
                    }
                }
            }
        }

    override fun iterator(): Iterator<ContactDetailFieldViewModel> = vmList.iterator()
}

data class ViewingContact(
    val contact: Contact,
    val firstNameVm: ContactDetailFieldViewModel,
    val lastNameVm: ContactDetailFieldViewModel,
    val titleVm: ContactDetailFieldViewModel,
) : ContactDetailUiState {
    private val vmList = listOf(
        firstNameVm,
        lastNameVm,
        titleVm
    )

    override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactDetailUiState =
        when (event) {
            is ContactEdit -> EditingContact(
                originalContact = contact,
                firstNameVm = firstNameVm,
                lastNameVm = lastNameVm,
                titleVm = titleVm
            )

            is ContactView -> ViewingContact(
                contact = event.contact,
                firstNameVm = event.contact.createFirstNameVm(),
                lastNameVm = event.contact.createLastNameVm(),
                titleVm = event.contact.createTitleVm()
            )

            ContactsActivityUiEvents.ContactCreate,
            is ContactsActivityUiEvents.ContactDelete,
//            is ContactsActivityUiEvents.ContactListUpdates,
            ContactsActivityUiEvents.InspectDbClick,
            ContactsActivityUiEvents.LogoutClick,
            ContactsActivityUiEvents.NavBack,
            ContactsActivityUiEvents.NavUp,
            ContactsActivityUiEvents.SwitchUserClick,
            ContactsActivityUiEvents.SyncClick -> this
        }

    override fun calculateProposedTransition(event: DetailComponentUiEvents): ContactDetailUiState =
        when (event) {
            is FieldValuesChanged,
            SaveClick -> this
        }

    override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactDetailUiState {
        TODO("Not yet implemented")
    }

    override fun iterator(): Iterator<ContactDetailFieldViewModel> = vmList.iterator()

    fun toEditContactState() = EditingContact(contact, firstNameVm, lastNameVm, titleVm)
}

object NoContactSelected : ContactDetailUiState {
    private val vmList = emptyList<ContactDetailFieldViewModel>()
    override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactDetailUiState =
        when (event) {
            is ContactEdit -> EditingContact(
                originalContact = event.contact,
                firstNameVm = event.contact.createFirstNameVm(),
                lastNameVm = event.contact.createLastNameVm(),
                titleVm = event.contact.createTitleVm(),
            )
            is ContactView -> ViewingContact(
                contact = event.contact,
                firstNameVm = event.contact.createFirstNameVm(),
                lastNameVm = event.contact.createLastNameVm(),
                titleVm = event.contact.createTitleVm()
            )
            ContactsActivityUiEvents.ContactCreate,
            is ContactsActivityUiEvents.ContactDelete,
//            is ContactsActivityUiEvents.ContactListUpdates,
            ContactsActivityUiEvents.InspectDbClick,
            ContactsActivityUiEvents.LogoutClick,
            ContactsActivityUiEvents.NavBack,
            ContactsActivityUiEvents.NavUp,
            ContactsActivityUiEvents.SwitchUserClick,
            ContactsActivityUiEvents.SyncClick -> this
        }

    override fun calculateProposedTransition(event: DetailComponentUiEvents): ContactDetailUiState =
        when (event) {
            is FieldValuesChanged,
            SaveClick -> this
        }

    override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactDetailUiState {
        TODO("Not yet implemented")
    }

    override fun iterator(): Iterator<ContactDetailFieldViewModel> = vmList.iterator()
}

private fun Contact.createFirstNameVm(): ContactDetailFieldViewModel =
    ContactDetailFieldViewModel(
        fieldValue = firstName,
        isInErrorState = false,
        canBeEdited = true,
        labelRes = R.string.label_contact_first_name,
        helperRes = null,
        placeholderRes = R.string.label_contact_first_name,
        onFieldValueChange = { newValue -> this.copy(firstName = newValue, locallyUpdated = true) }
    )

private fun Contact.createLastNameVm(): ContactDetailFieldViewModel {
    val isError = lastName.isBlank()
    val help = if (isError) R.string.help_cannot_be_blank else null

    return ContactDetailFieldViewModel(
        fieldValue = lastName,
        isInErrorState = isError,
        canBeEdited = true,
        labelRes = R.string.label_contact_last_name,
        helperRes = help,
        placeholderRes = R.string.label_contact_last_name,
        onFieldValueChange = { newValue -> this.copy(lastName = newValue, locallyUpdated = true) }
    )
}

private fun Contact.createTitleVm(): ContactDetailFieldViewModel =
    ContactDetailFieldViewModel(
        fieldValue = title,
        isInErrorState = false, // cannot be in error state
        canBeEdited = true,
        labelRes = R.string.label_contact_title,
        helperRes = null,
        placeholderRes = R.string.label_contact_title,
        onFieldValueChange = { newValue -> this.copy(title = newValue, locallyUpdated = true) }
    )
