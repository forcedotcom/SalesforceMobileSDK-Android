package com.salesforce.samples.mobilesynccompose.contacts.listcomponent

interface ContactsListSearchEventHandler {
    fun searchClick()
    fun exitSearchClick()
    fun onSearchTermUpdated(newSearchTerm: String)
}
