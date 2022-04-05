package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectCombinedId
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import kotlinx.coroutines.flow.StateFlow

interface ContactsListUiEventHandler {
    fun contactClick(contactId: SObjectCombinedId)
    fun createClick()
    fun deleteClick(contactId: SObjectCombinedId)
    fun editClick(contactId: SObjectCombinedId)
    fun undeleteClick(contactId: SObjectCombinedId)

    fun searchClick()
    fun exitSearchClick()
    fun onSearchTermUpdated(newSearchTerm: String)
}

interface ContactsListViewModel : ContactsListUiEventHandler {
    val uiState: StateFlow<ContactsListUiState>
}

sealed interface ContactsListUiState {
    object InitialLoad : ContactsListUiState

    sealed class Loaded : ContactsListUiState {
        abstract val contacts: List<SObjectRecord<ContactObject>>
        abstract val curSelectedContactId: SObjectCombinedId?

        data class ViewingList(
            override val contacts: List<SObjectRecord<ContactObject>>,
            override val curSelectedContactId: SObjectCombinedId?
        ) : Loaded()

        data class Searching(
            override val contacts: List<SObjectRecord<ContactObject>>,
            override val curSelectedContactId: SObjectCombinedId?
        ) : Loaded()
    }
}

class DefaultContactsListViewModel() : ContactsListViewModel {
    override val uiState: StateFlow<ContactsListUiState>
        get() = TODO("Not yet implemented")

    override fun contactClick(contactId: SObjectCombinedId) {
        TODO("Not yet implemented")
    }

    override fun createClick() {
        TODO("Not yet implemented")
    }

    override fun deleteClick(contactId: SObjectCombinedId) {
        TODO("Not yet implemented")
    }

    override fun editClick(contactId: SObjectCombinedId) {
        TODO("Not yet implemented")
    }

    override fun undeleteClick(contactId: SObjectCombinedId) {
        TODO("Not yet implemented")
    }

    override fun searchClick() {
        TODO("Not yet implemented")
    }

    override fun exitSearchClick() {
        TODO("Not yet implemented")
    }

    override fun onSearchTermUpdated(newSearchTerm: String) {
        TODO("Not yet implemented")
    }
}
