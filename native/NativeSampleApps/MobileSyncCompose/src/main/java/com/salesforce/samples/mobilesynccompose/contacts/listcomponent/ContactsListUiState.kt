package com.salesforce.samples.mobilesynccompose.contacts.listcomponent

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject

data class ContactsListUiState(
    val contacts: List<SObjectRecord<ContactObject>>,
    val curSelectedContactId: String?,
    val isDoingInitialLoad: Boolean,
    val curSearchTerm: String = ""
)
