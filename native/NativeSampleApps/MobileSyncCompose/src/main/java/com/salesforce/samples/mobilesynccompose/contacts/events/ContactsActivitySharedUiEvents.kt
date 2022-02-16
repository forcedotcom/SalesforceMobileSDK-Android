package com.salesforce.samples.mobilesynccompose.contacts.events

sealed interface ContactsActivitySharedUiEvents {
    object SyncClick : ContactsActivitySharedUiEvents
    object LogoutClick : ContactsActivitySharedUiEvents
    object SwitchUserClick : ContactsActivitySharedUiEvents
    object InspectDbClick : ContactsActivitySharedUiEvents
}
