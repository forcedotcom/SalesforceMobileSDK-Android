package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityUiEvents.*
import com.salesforce.samples.mobilesynccompose.contacts.vm.DetailComponentUiEvents.FieldValuesChanged
import com.salesforce.samples.mobilesynccompose.contacts.vm.DetailComponentUiEvents.SaveClick
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import com.salesforce.samples.mobilesynccompose.model.contacts.toEditContactUiState
import com.salesforce.samples.mobilesynccompose.model.contacts.toViewContactUiState

sealed interface ContactDetailUiState : Iterable<ContactDetailFieldViewModel> {
    fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactDetailUiState
    fun calculateProposedTransition(event: DetailComponentUiEvents): ContactDetailUiState
}

data class EditingContact(
    val originalContactObj: ContactObject,
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
            ContactCreate -> TODO("EditingContact -> ContactCreate")
            is ContactDelete -> TODO("EditingContact -> ContactDelete")
            is ContactEdit -> TODO("EditingContact -> ContactEdit")
            is ContactView -> TODO("EditingContact -> ContactView")
            NavBack -> TODO("EditingContact -> NavBack")
            NavUp -> TODO("EditingContact -> NavUp")
            else -> this
        }

    override fun calculateProposedTransition(event: DetailComponentUiEvents): ContactDetailUiState =
        when (event) {
            is FieldValuesChanged -> {
                event.newObject.toEditContactUiState()
            }
            SaveClick -> TODO("EditingContact -> SaveClick")
        }

    override fun iterator(): Iterator<ContactDetailFieldViewModel> = vmList.iterator()

    fun toViewContactState() = ViewingContact(originalContactObj, firstNameVm, lastNameVm, titleVm)
}

data class ViewingContact(
    val contactObject: ContactObject,
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
            ContactCreate -> TODO("ViewingContact -> ContactCreate")
            is ContactDelete -> TODO("ViewingContact -> ContactDelete")
            is ContactEdit -> event.contact.toEditContactUiState()
            is ContactView -> event.contact.toViewContactUiState()
            else -> this
        }

    override fun calculateProposedTransition(event: DetailComponentUiEvents): ContactDetailUiState =
        when (event) {
            is FieldValuesChanged -> TODO("ViewingContact -> FieldValuesChanged")
            SaveClick -> TODO("ViewingContact -> SaveClick")
        }

    override fun iterator(): Iterator<ContactDetailFieldViewModel> = vmList.iterator()

    fun toEditContactState() = EditingContact(contactObject, firstNameVm, lastNameVm, titleVm)
}

object NoContactSelected : ContactDetailUiState {
    private val vmList = emptyList<ContactDetailFieldViewModel>()
    override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactDetailUiState =
        when (event) {
            ContactCreate -> TODO("NoContactSelected -> ContactCreate")
            is ContactDelete -> TODO("NoContactSelected -> ContactDelete")
            is ContactEdit -> event.contact.toEditContactUiState()
            is ContactView -> event.contact.toViewContactUiState()
            InspectDb -> TODO("NoContactSelected -> InspectDb")
            Logout -> TODO("NoContactSelected -> Logout")
            NavBack -> TODO("NoContactSelected -> NavBack")
            NavUp -> TODO("NoContactSelected -> NavUp")
            SwitchUser -> TODO("NoContactSelected -> SwitchUser")
            else -> this
        }

    override fun calculateProposedTransition(event: DetailComponentUiEvents): ContactDetailUiState =
        when (event) {
            is FieldValuesChanged -> TODO("NoContactSelected -> FieldValuesChanged")
            SaveClick -> TODO("NoContactSelected -> SaveClick")
        }

    override fun iterator(): Iterator<ContactDetailFieldViewModel> = vmList.iterator()
}
