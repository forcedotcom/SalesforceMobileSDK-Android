package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityUiEvents.ContactEdit
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityUiEvents.ContactView
import com.salesforce.samples.mobilesynccompose.contacts.vm.DetailComponentUiEvents.FieldValuesChanged
import com.salesforce.samples.mobilesynccompose.contacts.vm.DetailComponentUiEvents.SaveClick
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

sealed interface ContactDetailUiState {
    val vmList: List<ContactDetailFieldViewModel>
    // TODO These methods should be suspending and use Dispatchers.Default withContext
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
    val savingContact: Boolean,
    val vmToScrollTo: ContactDetailFieldViewModel? = null,
) : ContactDetailUiState {
    override val vmList = listOf(
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
                val vmToScrollTo = fieldsInErrorState.firstOrNull()
                return if (vmToScrollTo != null) {
                    copy(vmToScrollTo = vmToScrollTo)
                } else {
                    copy(savingContact = true)
                }
            }
        }
    }

    override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactDetailUiState =
        when (event) {
//            is ContactsActivityDataEvents.ContactDetailsSaved -> {
//                if (event.contact.id == originalContact.id) {
//                    ViewingContact(
//                        contact = event.contact,
//                        firstNameVm = event.contact.createFirstNameVm(),
//                        lastNameVm = event.contact.createLastNameVm(),
//                        titleVm = event.contact.createTitleVm()
//                    )
//                } else {
//                    this
//                }
//            }
            is ContactsActivityDataEvents.ContactListUpdates -> {
                val matchingContact = event.newContactList.firstOrNull {
                    it.id == this.originalContact.id
                }

                when {
                    matchingContact == null -> this // updated list does not contain this contact, so ignore
                    savingContact -> ViewingContact(matchingContact) // we were saving and then received an updated contact with the same ID which means the save was successful
                    matchingContact == originalContact -> this // updated contact is same as current edit state, so ignore
                    else -> TODO("EditingContact -> ContactListUpdates with conflicting updates. This contact=$originalContact upstream contact=$matchingContact")
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

    val fieldsInErrorState: List<ContactDetailFieldViewModel> by lazy {
        vmList.filter { it.isInErrorState }
    }

    val hasFieldsInErrorState: Boolean by lazy { fieldsInErrorState.isNotEmpty() }
}

data class ViewingContact(
    val contact: Contact,
    val firstNameVm: ContactDetailFieldViewModel,
    val lastNameVm: ContactDetailFieldViewModel,
    val titleVm: ContactDetailFieldViewModel,
) : ContactDetailUiState {
    override val vmList = listOf(
        firstNameVm,
        lastNameVm,
        titleVm
    )

    constructor(contact: Contact) : this(
        contact = contact,
        firstNameVm = contact.createFirstNameVm(),
        lastNameVm = contact.createLastNameVm(),
        titleVm = contact.createTitleVm()
    )

    override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactDetailUiState =
        when (event) {
            is ContactEdit -> EditingContact(
                originalContact = contact,
                firstNameVm = firstNameVm,
                lastNameVm = lastNameVm,
                titleVm = titleVm,
                savingContact = false
            )

            is ContactView -> ViewingContact(contact = event.contact)

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
//            is ContactsActivityDataEvents.ContactDetailsSaved -> {
//                if (event.contact.id == this.contact.id) {
//                    ViewingContact(event.contact)
//                } else {
//                    this
//                }
//            }
            is ContactsActivityDataEvents.ContactListUpdates -> {
                val updatedContact = event.newContactList.firstOrNull { it.id == this.contact.id }
                if (updatedContact != null) {
                    ViewingContact(updatedContact)
                } else {
                    this
                }
            }
        }
}

object NoContactSelected : ContactDetailUiState {
    override val vmList = emptyList<ContactDetailFieldViewModel>()
    override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactDetailUiState =
        when (event) {
            is ContactEdit -> EditingContact(
                originalContact = event.contact,
                firstNameVm = event.contact.createFirstNameVm(),
                lastNameVm = event.contact.createLastNameVm(),
                titleVm = event.contact.createTitleVm(),
                savingContact = false
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
//            is ContactsActivityDataEvents.ContactDetailsSaved,
            is ContactsActivityDataEvents.ContactListUpdates -> this
        }
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
