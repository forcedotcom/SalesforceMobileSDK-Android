package com.salesforce.samples.mobilesynccompose.contacts.events

interface ContactsSearchEventHandler : ContactsListCoreEventHandler {
    fun exitSearch()
    fun searchTermUpdated(newSearchTerm: String)
}
