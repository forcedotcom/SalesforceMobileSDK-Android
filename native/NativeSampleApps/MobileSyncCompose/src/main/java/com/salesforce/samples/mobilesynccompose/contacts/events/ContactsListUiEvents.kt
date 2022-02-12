package com.salesforce.samples.mobilesynccompose.contacts.events

sealed interface ContactsListUiEvents {
    object SearchClick : ContactsListUiEvents
    data class SearchTermUpdated(val newSearchTerm: String) : ContactsListUiEvents
}
