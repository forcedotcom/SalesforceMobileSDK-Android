package com.salesforce.samples.mobilesynccompose.contacts.state

import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactDetailUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactDetailUiEvents.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityDataEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityUiEvents.ContactEdit
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityUiEvents.ContactView
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailFieldViewModel
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

sealed interface ContactDetailUiState {
    val vmList: List<ContactDetailFieldViewModel>

    // TODO These methods should be suspending and use Dispatchers.Default withContext
    fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactDetailUiState
    fun calculateProposedTransition(event: ContactDetailUiEvents): ContactDetailUiState
    fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactDetailUiState
}

sealed class EditMode : ContactDetailUiState {

    abstract val originalContact: Contact
    abstract val firstNameVm: ContactDetailFieldViewModel
    abstract val lastNameVm: ContactDetailFieldViewModel
    abstract val titleVm: ContactDetailFieldViewModel

    override val vmList by lazy {
        listOf(
            firstNameVm,
            lastNameVm,
            titleVm,
        )
    }

    val updatedContact: Contact by lazy {
        originalContact.copy(
            firstName = firstNameVm.fieldValue,
            lastName = lastNameVm.fieldValue,
            title = titleVm.fieldValue
        )
    }
    val isModified: Boolean by lazy { updatedContact != originalContact }

    val fieldsInErrorState: List<ContactDetailFieldViewModel> by lazy {
        vmList.filter { it.isInErrorState }
    }

    val hasFieldsInErrorState: Boolean by lazy { fieldsInErrorState.isNotEmpty() }

    data class EditingContact(
        override val originalContact: Contact,
        override val firstNameVm: ContactDetailFieldViewModel,
        override val lastNameVm: ContactDetailFieldViewModel,
        override val titleVm: ContactDetailFieldViewModel,
        val vmToScrollTo: ContactDetailFieldViewModel? = null,
    ) : EditMode() {
        override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactDetailUiState =
            when (event) {
                ContactsActivityUiEvents.ContactCreate,
                is ContactsActivityUiEvents.ContactDelete,
                is ContactEdit,
                is ContactView,
                ContactsActivityUiEvents.InspectDbClick,
                ContactsActivityUiEvents.LogoutClick,
//            ContactsActivityUiEvents.NavBack,
//            ContactsActivityUiEvents.NavUp,
                ContactsActivityUiEvents.SwitchUserClick,
                ContactsActivityUiEvents.SyncClick -> this
            }

        override fun calculateProposedTransition(event: ContactDetailUiEvents): ContactDetailUiState =
            when (event) {
                is FieldValuesChanged -> {
                    copy(
                        firstNameVm = event.newObject.createFirstNameVm(),
                        lastNameVm = event.newObject.createLastNameVm(),
                        titleVm = event.newObject.createTitleVm()
                    )
                }

                SaveClick -> {
                    val vmToScrollTo = fieldsInErrorState.firstOrNull()

                    if (vmToScrollTo != null) {
                        copy(vmToScrollTo = vmToScrollTo)
                    } else {
                        Saving(originalContact, firstNameVm, lastNameVm, titleVm)
                    }
                }

                DetailNavBack,
                DetailNavUp -> {
                    ViewingContact(contact = originalContact)
//                    if (!isModified) {
//                        ViewingContact(contact = originalContact)
//                    } else {
//                        DiscardChanges(originalContact, firstNameVm, lastNameVm, titleVm)
//                    }
                }
            }

        override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactDetailUiState =
            when (event) {
                is ContactsActivityDataEvents.ContactListUpdates -> {
                    val matchingContact = event.newContactList.firstOrNull {
                        it.id == this.originalContact.id
                    }

                    when (matchingContact) {
                        null -> this // updated list does not contain this contact, so ignore
                        //                        savingContact -> ViewingContact(matchingContact) // we were saving and then received an updated contact with the same ID which means the save was successful
                        originalContact -> this // updated contact is same as current edit state, so ignore
                        else -> TODO("EditingContact -> ContactListUpdates with conflicting updates. This contact=$originalContact upstream contact=$matchingContact")
                    }
                }
            }
    }

    data class Saving(
        override val originalContact: Contact,
        override val firstNameVm: ContactDetailFieldViewModel,
        override val lastNameVm: ContactDetailFieldViewModel,
        override val titleVm: ContactDetailFieldViewModel,
    ) : EditMode() {
        override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactDetailUiState =
            this // ignore event

        override fun calculateProposedTransition(event: ContactDetailUiEvents): ContactDetailUiState =
            when (event) {
                DetailNavBack -> TODO()
                DetailNavUp -> TODO()
                is FieldValuesChanged -> TODO()
                SaveClick -> TODO()
            }

        override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactDetailUiState =
            when (event) {
                is ContactsActivityDataEvents.ContactListUpdates -> {
                    val upstreamContact = event.newContactList.firstOrNull {
                        it.id == this.originalContact.id
                    }

                    if (upstreamContact != null) ViewingContact(upstreamContact) else this
                }
            }
    }

    data class DiscardChanges(
        override val originalContact: Contact,
        override val firstNameVm: ContactDetailFieldViewModel,
        override val lastNameVm: ContactDetailFieldViewModel,
        override val titleVm: ContactDetailFieldViewModel,
    ) : EditMode() {
        override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactDetailUiState =
            this // ignore event

        override fun calculateProposedTransition(event: ContactDetailUiEvents): ContactDetailUiState =
            when (event) {
                DetailNavBack,
                DetailNavUp -> EditingContact(originalContact, firstNameVm, lastNameVm, titleVm)
                is FieldValuesChanged -> this
                SaveClick -> Saving(originalContact, firstNameVm, lastNameVm, titleVm)
            }

        override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactDetailUiState =
            this
    }
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
            is ContactEdit -> EditMode.EditingContact(
                originalContact = contact,
                firstNameVm = firstNameVm,
                lastNameVm = lastNameVm,
                titleVm = titleVm,
            )

            is ContactView -> ViewingContact(contact = event.contact)

            ContactsActivityUiEvents.ContactCreate,
            is ContactsActivityUiEvents.ContactDelete,
            ContactsActivityUiEvents.InspectDbClick,
            ContactsActivityUiEvents.LogoutClick,
//            ContactsActivityUiEvents.NavBack,
//            ContactsActivityUiEvents.NavUp,
            ContactsActivityUiEvents.SwitchUserClick,
            ContactsActivityUiEvents.SyncClick -> this
        }

    override fun calculateProposedTransition(event: ContactDetailUiEvents): ContactDetailUiState =
        when (event) {
            is FieldValuesChanged,
            SaveClick -> this
            DetailNavBack,
            DetailNavUp -> NoContactSelected
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
            is ContactEdit -> EditMode.EditingContact(
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
//            ContactsActivityUiEvents.NavBack,
//            ContactsActivityUiEvents.NavUp,
            ContactsActivityUiEvents.SwitchUserClick,
            ContactsActivityUiEvents.SyncClick -> this
        }

    override fun calculateProposedTransition(event: ContactDetailUiEvents): ContactDetailUiState =
        when (event) {
            is FieldValuesChanged,
            SaveClick,
            DetailNavBack,
            DetailNavUp -> this
        }

    override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactDetailUiState =
        when (event) {
//            is ContactsActivityDataEvents.ContactDetailsSaved,
            is ContactsActivityDataEvents.ContactListUpdates -> this
        }
}

fun Contact.createFirstNameVm(): ContactDetailFieldViewModel =
    ContactDetailFieldViewModel(
        fieldValue = firstName,
        isInErrorState = false,
        canBeEdited = true,
        labelRes = R.string.label_contact_first_name,
        helperRes = null,
        placeholderRes = R.string.label_contact_first_name,
        onFieldValueChange = { newValue -> this.copy(firstName = newValue) }
    )

fun Contact.createLastNameVm(): ContactDetailFieldViewModel {
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

fun Contact.createTitleVm(): ContactDetailFieldViewModel =
    ContactDetailFieldViewModel(
        fieldValue = title,
        isInErrorState = false, // cannot be in error state
        canBeEdited = true,
        labelRes = R.string.label_contact_title,
        helperRes = null,
        placeholderRes = R.string.label_contact_title,
        onFieldValueChange = { newValue -> this.copy(title = newValue) }
    )
