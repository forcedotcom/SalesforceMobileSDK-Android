package com.salesforce.samples.mobilesynccompose.contacts.listcomponent

interface ContactsListItemClickHandler {
    fun contactClick(contactId: String)
    fun createClick()
    fun deleteClick(contactId: String)
    fun editClick(contactId: String)
    fun undeleteClick(contactId: String)
}
