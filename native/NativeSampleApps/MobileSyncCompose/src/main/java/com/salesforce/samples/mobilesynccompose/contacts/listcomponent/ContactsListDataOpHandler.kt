package com.salesforce.samples.mobilesynccompose.contacts.listcomponent

interface ContactsListDataOpHandler {
    fun deleteClick(contactId: String)
    fun undeleteClick(contactId: String)
}
