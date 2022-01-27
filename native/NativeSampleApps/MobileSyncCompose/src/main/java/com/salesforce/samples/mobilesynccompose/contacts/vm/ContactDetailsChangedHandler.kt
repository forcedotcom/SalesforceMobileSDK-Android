package com.salesforce.samples.mobilesynccompose.contacts.vm

interface ContactDetailsChangedHandler {
    fun onNameChanged(newValue: String)
    fun onTitleChanged(newValue: String)
}
