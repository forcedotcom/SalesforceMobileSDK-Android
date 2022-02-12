package com.salesforce.samples.mobilesynccompose.contacts.events

import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

sealed interface ContactsActivityUiEvents {
    object ContactCreate : ContactsActivityUiEvents
    data class ContactDelete(val contact: Contact) : ContactsActivityUiEvents
    data class ContactEdit(val contact: Contact) : ContactsActivityUiEvents
    data class ContactView(val contact: Contact) : ContactsActivityUiEvents
    object NavBack : ContactsActivityUiEvents
    object NavUp : ContactsActivityUiEvents
    object SyncClick : ContactsActivityUiEvents
    object LogoutClick : ContactsActivityUiEvents
    object SwitchUserClick : ContactsActivityUiEvents
    object InspectDbClick : ContactsActivityUiEvents
}
