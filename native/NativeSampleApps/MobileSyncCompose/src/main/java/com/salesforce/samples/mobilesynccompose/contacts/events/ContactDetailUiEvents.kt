package com.salesforce.samples.mobilesynccompose.contacts.events

import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

sealed interface ContactDetailUiEvents {
    object SaveClick : ContactDetailUiEvents
    data class FieldValuesChanged(val newObject: Contact) : ContactDetailUiEvents
}
