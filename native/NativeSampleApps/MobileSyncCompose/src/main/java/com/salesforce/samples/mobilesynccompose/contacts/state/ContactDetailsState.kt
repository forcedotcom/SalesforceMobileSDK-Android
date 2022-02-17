package com.salesforce.samples.mobilesynccompose.contacts.state

import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailViewModel

sealed interface ContactDetailsState

@JvmInline
value class Viewing(val details: ContactDetailViewModel)

@JvmInline
value class Editing(val details: ContactDetailViewModel)

@JvmInline
value class Creating(val details: ContactDetailViewModel)
