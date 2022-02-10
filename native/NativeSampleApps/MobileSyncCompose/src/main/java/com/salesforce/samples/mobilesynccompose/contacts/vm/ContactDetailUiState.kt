package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityUiEvents.ContactEdit
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityUiEvents.ContactView
import com.salesforce.samples.mobilesynccompose.contacts.vm.DetailComponentUiEvents.FieldValuesChanged
import com.salesforce.samples.mobilesynccompose.contacts.vm.DetailComponentUiEvents.SaveClick
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

sealed interface ContactDetailUiState : Iterable<ContactDetailFieldViewModel> {
    fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactDetailUiState
    fun calculateProposedTransition(event: DetailComponentUiEvents): ContactDetailUiState
    fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactDetailUiState
}

// TODO just make the list of VMs the val in the primary constructor and stop trying to be cute with iterable implementation.
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
                return copy(
                    firstNameVm = event.newObject.createFirstNameVm(),
                    lastNameVm = event.newObject.createLastNameVm(),
                    titleVm = event.newObject.createTitleVm()
                )
            }

            SaveClick -> {
                val vmToScrollTo = firstOrNull { it.isInErrorState }
                return if (vmToScrollTo != null) {
                    copy(vmToScrollTo = vmToScrollTo)
                } else {
                    this
                }
            }
        }
    }

    override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactDetailUiState =
        when (event) {
            is ContactsActivityDataEvents.ContactDetailsSaved -> {
                if (event.contact.id == originalContact.id) {
                    ViewingContact(
                        contact = event.contact,
                        firstNameVm = event.contact.createFirstNameVm(),
                        lastNameVm = event.contact.createLastNameVm(),
                        titleVm = event.contact.createTitleVm()
                    )
                } else {
                    this
                }
            }
            is ContactsActivityDataEvents.ContactListUpdates -> {
                val conflictingContact = event.newContactList.firstOrNull {
                    it.id == this.originalContact.id
                }

                if (conflictingContact == null) {
                    // updated list does not contain the contact we are editing, so ignore the event
                    this
                } else {
                    // The updated list contains the contact we are editing, but there were no upstream changes.  Ignore this event.
                    if (conflictingContact == originalContact) {
                        this
                    } else {
                        // The updated list contains the contact we are editing _and_ there were upstream changes.
                        TODO("EditingContact -> ContactListUpdates with conflicting updates. This contact=$originalContact upstream contact=$conflictingContact")
                    }
                }
            }
        }

    val updatedContact: Contact by lazy {
        originalContact.copy(
            firstName = firstNameVm.fieldValue,
            lastName = lastNameVm.fieldValue,
            title = titleVm.fieldValue
        )
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

    override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactDetailUiState =
        when (event) {
            is ContactsActivityDataEvents.ContactDetailsSaved -> TODO()
            is ContactsActivityDataEvents.ContactListUpdates -> TODO()
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
        onFieldValueChange = { newValue -> this.copy(firstName = newValue) }
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
        onFieldValueChange = { newValue -> this.copy(lastName = newValue) }
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
        onFieldValueChange = { newValue -> this.copy(title = newValue) }
    )
