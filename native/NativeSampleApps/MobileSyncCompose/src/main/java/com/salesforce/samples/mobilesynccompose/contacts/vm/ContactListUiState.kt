package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityUiEvents.*
import com.salesforce.samples.mobilesynccompose.contacts.vm.ListComponentUiEvents.*
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject

sealed interface ContactListUiState {
    val contacts: List<ContactObject>
    fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactListUiState
    fun calculateProposedTransition(event: ListComponentUiEvents): ContactListUiState

    object Loading : ContactListUiState {
        override val contacts: List<ContactObject> = emptyList()
        override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactListUiState =
            when (event) {
                ContactCreate -> TODO("Loading -> ContactCreate")
                is ContactDelete -> TODO("Loading -> ContactDelete")
                is ContactEdit -> TODO("Loading -> ContactEdit")
                is ContactView -> TODO("Loading -> ContactView")
                is ContactListUpdates -> ViewList(event.newContactList)
                InspectDb -> TODO("Loading -> InspectDb")
                Logout -> TODO("Loading -> Logout")
                NavBack -> TODO("Loading -> NavBack")
                NavUp -> TODO("Loading -> NavUp")
                SwitchUser -> TODO("Loading -> SwitchUser")
                Sync -> this
            }

        override fun calculateProposedTransition(event: ListComponentUiEvents): ContactListUiState =
            when (event) {
                SearchClick -> TODO()
                is SearchTermUpdated -> TODO()
            }
    }

    data class ViewList(
        override val contacts: List<ContactObject>
    ) : ContactListUiState {
        override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactListUiState =
            when (event) {
                ContactCreate -> TODO()
                is ContactDelete -> TODO()
                is ContactEdit -> TODO()
                is ContactListUpdates -> this.copy(contacts = event.newContactList)
                InspectDb -> TODO()
                Logout -> TODO()
                NavBack -> TODO()
                NavUp -> TODO()
                SwitchUser -> TODO()
                Sync -> TODO()
                else -> this
            }

        override fun calculateProposedTransition(event: ListComponentUiEvents): ContactListUiState =
            when (event) {
                SearchClick -> TODO()
                is SearchTermUpdated -> TODO()
            }
    }

    data class Search(
        override val contacts: List<ContactObject>,
        val searchTerm: String
    ) : ContactListUiState {
        override fun calculateProposedTransition(event: ContactsActivityUiEvents): ContactListUiState =
            when (event) {
                ContactCreate -> TODO()
                is ContactDelete -> TODO()
                is ContactEdit -> TODO()
                is ContactView -> TODO()
                is ContactListUpdates -> TODO()
                InspectDb -> TODO()
                Logout -> TODO()
                NavBack -> TODO()
                NavUp -> TODO()
                SwitchUser -> TODO()
                Sync -> TODO()
            }

        override fun calculateProposedTransition(event: ListComponentUiEvents): ContactListUiState =
            when (event) {
                SearchClick -> TODO()
                is SearchTermUpdated -> TODO()
            }
    }
}
