package com.salesforce.samples.mobilesynccompose.contacts.listcomponent

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject

sealed interface ContactsListUiState {
    val contacts: List<SObjectRecord<ContactObject>>
    val curSelectedContactId: String?
    val showLoadingOverlay: Boolean

    data class ViewingList(
        override val contacts: List<SObjectRecord<ContactObject>>,
        override val curSelectedContactId: String?,
        override val showLoadingOverlay: Boolean
    ) : ContactsListUiState

    data class Searching(
        override val contacts: List<SObjectRecord<ContactObject>>,
        override val curSelectedContactId: String?,
        override val showLoadingOverlay: Boolean,
        val curSearchTerm: String
    ) : ContactsListUiState
}
