package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityUiEvents.*
import com.salesforce.samples.mobilesynccompose.contacts.vm.ListComponentUiEvents.SearchClick
import com.salesforce.samples.mobilesynccompose.contacts.vm.ListComponentUiEvents.SearchTermUpdated
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

sealed interface ContactsListUiState {
    val contacts: List<Contact>
    fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactsListUiState
    fun calculateProposedTransition(event: ListComponentUiEvents): ContactsListUiState
    fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactsListUiState

    object Loading : ContactsListUiState {
        override val contacts: List<Contact> = emptyList()
        override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactsListUiState =
            when (event) {
                ContactCreate,
                is ContactDelete,
                is ContactEdit,
                is ContactView,
                InspectDbClick,
                LogoutClick,
                NavBack,
                NavUp,
                SwitchUserClick,
                SyncClick -> this
            }

        override fun calculateProposedTransition(event: ListComponentUiEvents): ContactsListUiState =
            when (event) {
                SearchClick -> this
                is SearchTermUpdated -> this
            }

        override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactsListUiState =
            when (event) {
//                is ContactsActivityDataEvents.ContactDetailsSaved -> this
                is ContactsActivityDataEvents.ContactListUpdates -> ViewList(event.newContactList)
            }
    }

    data class ViewList(
        override val contacts: List<Contact>
    ) : ContactsListUiState {
        override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactsListUiState =
            when (event) {
                ContactCreate,
                is ContactDelete,
                is ContactEdit,
                is ContactView,
                InspectDbClick,
                LogoutClick,
                NavBack,
                NavUp,
                SwitchUserClick,
                SyncClick -> this
            }

        override fun calculateProposedTransition(event: ListComponentUiEvents): ContactsListUiState =
            when (event) {
                SearchClick,
                is SearchTermUpdated -> this
            }

        override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactsListUiState =
            when (event) {
//                is ContactsActivityDataEvents.ContactDetailsSaved -> ViewList(
//                    this.contacts.map { if (it.id == event.contact.id) event.contact else it }
//                )
                is ContactsActivityDataEvents.ContactListUpdates -> this.copy(contacts = event.newContactList)
            }
    }

    data class Search(
        override val contacts: List<Contact>,
        val searchTerm: String
    ) : ContactsListUiState {
        override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactsListUiState =
            when (event) {
                ContactCreate,
                is ContactDelete,
                is ContactEdit,
                is ContactView,
                InspectDbClick,
                LogoutClick,
                NavBack,
                NavUp,
                SwitchUserClick,
                SyncClick -> this
            }

        override fun calculateProposedTransition(event: ListComponentUiEvents): ContactsListUiState =
            when (event) {
                SearchClick,
                is SearchTermUpdated -> this
            }

        override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactsListUiState =
            when (event) {
//                is ContactsActivityDataEvents.ContactDetailsSaved -> TODO()
                is ContactsActivityDataEvents.ContactListUpdates -> TODO()
            }
    }
}
