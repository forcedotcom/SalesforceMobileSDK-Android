package com.salesforce.samples.mobilesynccompose.contacts.state

import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityDataEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityUiEvents.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListUiEvents.SearchClick
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListUiEvents.SearchTermUpdated
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

sealed interface ContactsListUiState {
    val contacts: List<Contact>
    fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactsListUiState
    fun calculateProposedTransition(event: ContactsListUiEvents): ContactsListUiState
    fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactsListUiState

    object Loading : ContactsListUiState {
        override val contacts: List<Contact> = emptyList()
        override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactsListUiState =
            when (event) {
                ContactCreate,
                is ContactDelete,
                is ContactEdit,
                is ContactView -> this
            }

        override fun calculateProposedTransition(event: ContactsListUiEvents): ContactsListUiState =
            when (event) {
                SearchClick -> this
                is SearchTermUpdated -> this
                ContactsListUiEvents.ListNavBack -> TODO()
                ContactsListUiEvents.ListNavUp -> TODO()
            }

        override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactsListUiState =
            when (event) {
                is ContactsActivityDataEvents.ContactListUpdates -> ViewingList(event.newContactList)
            }
    }

    data class ViewingList(
        override val contacts: List<Contact>
    ) : ContactsListUiState {
        override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactsListUiState =
            when (event) {
                ContactCreate,
                is ContactDelete,
                is ContactEdit,
                is ContactView -> this
            }

        override fun calculateProposedTransition(event: ContactsListUiEvents): ContactsListUiState =
            when (event) {
                SearchClick,
                is SearchTermUpdated,
                ContactsListUiEvents.ListNavBack,
                ContactsListUiEvents.ListNavUp -> this
            }

        override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactsListUiState =
            when (event) {
                is ContactsActivityDataEvents.ContactListUpdates -> this.copy(contacts = event.newContactList)
            }
    }

    data class Searching(
        override val contacts: List<Contact>,
        val searchTerm: String
    ) : ContactsListUiState {
        override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactsListUiState =
            when (event) {
                ContactCreate,
                is ContactDelete,
                is ContactEdit,
                is ContactView -> this
            }

        override fun calculateProposedTransition(event: ContactsListUiEvents): ContactsListUiState =
            when (event) {
                SearchClick,
                is SearchTermUpdated,
                ContactsListUiEvents.ListNavBack,
                ContactsListUiEvents.ListNavUp -> this
            }

        override fun calculateProposedTransition(event: ContactsActivityDataEvents): ContactsListUiState =
            when (event) {
                is ContactsActivityDataEvents.ContactListUpdates -> TODO()
            }
    }
}
