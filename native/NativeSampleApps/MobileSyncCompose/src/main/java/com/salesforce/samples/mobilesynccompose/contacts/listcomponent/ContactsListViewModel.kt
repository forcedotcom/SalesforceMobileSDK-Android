package com.salesforce.samples.mobilesynccompose.contacts.listcomponent

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import kotlinx.coroutines.flow.StateFlow

interface ContactsListUiEventHandler {
    fun contactClick(contactId: String)
    fun createClick()
    fun deleteClick(contactId: String)
    fun editClick(contactId: String)
    fun undeleteClick(contactId: String)

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
        abstract val curSelectedContactId: String?

        data class ViewingList(
            override val contacts: List<SObjectRecord<ContactObject>>,
            override val curSelectedContactId: String?
        ) : Loaded()

        data class Searching(
            override val contacts: List<SObjectRecord<ContactObject>>,
            override val curSelectedContactId: String?
        ) : Loaded()
    }
}

class DefaultContactsListViewModel() : ContactsListViewModel {
    override val uiState: StateFlow<ContactsListUiState>
        get() = TODO("Not yet implemented")

    override fun contactClick(contactId: String) {
        TODO("Not yet implemented")
    }

    override fun createClick() {
        TODO("Not yet implemented")
    }

    override fun deleteClick(contactId: String) {
        TODO("Not yet implemented")
    }

    override fun editClick(contactId: String) {
        TODO("Not yet implemented")
    }

    override fun undeleteClick(contactId: String) {
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
