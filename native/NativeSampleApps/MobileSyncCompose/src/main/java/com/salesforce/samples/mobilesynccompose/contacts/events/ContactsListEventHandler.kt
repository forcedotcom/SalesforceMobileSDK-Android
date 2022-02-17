package com.salesforce.samples.mobilesynccompose.contacts.events

import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

interface ContactsListEventHandler {
    fun contactClick(contact: Contact)
    fun contactCreateClick()
    fun contactDeleteClick(contact: Contact)
    fun contactEditClick(contact: Contact)
    fun searchClick()
    fun searchTermUpdated(newSearchTerm: String)
}
