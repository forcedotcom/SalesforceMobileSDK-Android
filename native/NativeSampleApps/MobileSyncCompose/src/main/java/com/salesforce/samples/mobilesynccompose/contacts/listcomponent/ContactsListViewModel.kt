package com.salesforce.samples.mobilesynccompose.contacts.listcomponent

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactRecord
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

class DefaultContactsListViewModel(
    private val contactsRepo: ContactsRepo,
    private val parentScope: CoroutineScope
) : ContactsListViewModel {
    private val stateMutex = Mutex()

    private val mutUiState = MutableStateFlow<ContactsListUiState>(
        ContactsListUiState.ViewingList(
            contacts = emptyList(),
            curSelectedContactId = null,
            showLoadingOverlay = true
        )
    )
    override val uiState: StateFlow<ContactsListUiState> get() = mutUiState

    @Volatile
    private lateinit var curRecords: Map<String, ContactRecord>

    init {
        parentScope.launch(Dispatchers.Default) {
            contactsRepo.recordsById.collect {
                onContactListUpdate(it)
            }
        }
    }

    private fun onContactListUpdate(newRecords: Map<String, SObjectRecord<ContactObject>>) =
        launchWithStateMutex {
            curRecords = newRecords

            when (val curState = uiState.value) {
                is ContactsListUiState.Searching -> TODO()
                is ContactsListUiState.ViewingList -> curState.copy(contacts = curRecords.values.toList())
            }.let {
                mutUiState.value = it
            }
            // TODO handle when selected contact is no longer in the records list
        }

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

    override fun searchClick() = launchWithStateMutex {
        val viewingState = uiState.value as? ContactsListUiState.ViewingList
            ?: return@launchWithStateMutex

        mutUiState.value = ContactsListUiState.Searching(
            contacts = viewingState.contacts,
            curSelectedContactId = viewingState.curSelectedContactId,
            showLoadingOverlay = viewingState.showLoadingOverlay,
            curSearchTerm = ""
        )
    }

    override fun exitSearchClick() = launchWithStateMutex {
        val searchingState = uiState.value as? ContactsListUiState.Searching
            ?: return@launchWithStateMutex

        mutUiState.value = ContactsListUiState.ViewingList(
            contacts = curRecords.values.toList(), // TODO how should we sort this?
            curSelectedContactId = searchingState.curSelectedContactId,
            showLoadingOverlay = searchingState.showLoadingOverlay
        )
    }

    override fun onSearchTermUpdated(newSearchTerm: String) {
        TODO("Not yet implemented")
    }

    private fun launchWithStateMutex(block: suspend CoroutineScope.() -> Unit) {
        parentScope.launch {
            stateMutex.withLock { this.block() }
        }
    }
}
