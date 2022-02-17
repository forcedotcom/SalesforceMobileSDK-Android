package com.salesforce.samples.mobilesynccompose.contacts.events

import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

interface ContactEditModeEventHandler : ContactDetailsCoreEventHandler {
    fun onDetailsUpdated(newContact: Contact)
    fun saveClick()
}
