package com.salesforce.samples.mobilesynccompose.contacts.detailscomponent

sealed class ContactDetailsException : Exception()
data class HasUnsavedChangesException(override val message: String? = null) :
    ContactDetailsException()

data class DataOperationActiveException(override val message: String? = null) :
    ContactDetailsException()
