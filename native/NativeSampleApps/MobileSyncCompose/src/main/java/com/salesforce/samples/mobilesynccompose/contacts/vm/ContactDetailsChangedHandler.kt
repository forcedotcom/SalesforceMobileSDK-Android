package com.salesforce.samples.mobilesynccompose.contacts.vm

interface ContactDetailsChangedHandler {
    fun onFirstNameChanged(newValue: String)
    fun onMiddleNameChanged(newValue: String)
    fun onLastNameChanged(newValue: String)
    fun onNameChanged(newValue: String)
    fun onTitleChanged(newValue: String)
}
