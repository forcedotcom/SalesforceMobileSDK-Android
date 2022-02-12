package com.salesforce.samples.mobilesynccompose.contacts.events

import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

sealed interface ContactsActivityDataEvents {
    data class ContactListUpdates(val newContactList: List<Contact>) : ContactsActivityDataEvents
//    data class ContactDetailsSaved(val contact: Contact) : ContactsActivityDataEvents
}
