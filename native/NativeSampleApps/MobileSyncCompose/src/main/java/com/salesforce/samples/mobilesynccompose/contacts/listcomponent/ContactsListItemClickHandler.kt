package com.salesforce.samples.mobilesynccompose.contacts.listcomponent

interface ContactsListItemClickHandler {
    fun contactClick(contactId: String)
    fun createClick()
    fun editClick(contactId: String)
}
