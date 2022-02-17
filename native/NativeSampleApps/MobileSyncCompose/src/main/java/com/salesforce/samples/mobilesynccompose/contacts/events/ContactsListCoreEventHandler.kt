package com.salesforce.samples.mobilesynccompose.contacts.events

import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

interface ContactsListCoreEventHandler {
    fun listContactClick(contact: Contact)
    fun listCreateClick()
    fun listDeleteClick(contact: Contact)
    fun listEditClick(contact: Contact)
}
