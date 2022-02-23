package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

data class ContactsActivityListUiState(
    val contacts: List<Contact>,
    val searchTerm: String?,
    val isSaving: Boolean = false
)
