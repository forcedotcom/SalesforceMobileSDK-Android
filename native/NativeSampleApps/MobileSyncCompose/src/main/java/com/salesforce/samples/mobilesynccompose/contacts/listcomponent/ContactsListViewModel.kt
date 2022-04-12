package com.salesforce.samples.mobilesynccompose.contacts.listcomponent

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactRecord
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactsRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ContactsListViewModel
    : ContactsListSearchEventHandler,
    ContactsListItemClickHandler,
    ContactsListDataOpHandler {

    val uiState: StateFlow<ContactsListUiState>

    fun setSelectedContact(id: String?)
    fun setSearchModeEnabled(isEnabled: Boolean)
    fun setSearchTerm(newSearchTerm: String)
}

class DefaultContactsListViewModel(
    private val contactsRepo: ContactsRepo,
    private val parentScope: CoroutineScope,
    private val itemClickDelegate: ContactsListItemClickHandler?,
    private val dataOpDelegate: ContactsListDataOpHandler?,
    private val searchEventDelegate: ContactsListSearchEventHandler?
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

    override fun setSelectedContact(id: String?) = launchWithStateMutex {
        when (val curState = uiState.value) {
            is ContactsListUiState.Searching -> curState.copy(curSelectedContactId = id)
            is ContactsListUiState.ViewingList -> curState.copy(curSelectedContactId = id)
        }.also {
            mutUiState.value = it
        }
    }

    private fun onContactListUpdate(newRecords: Map<String, SObjectRecord<ContactObject>>) =
        launchWithStateMutex {
            curRecords = newRecords

            when (val curState = uiState.value) {
                is ContactsListUiState.Searching -> TODO()
                is ContactsListUiState.ViewingList -> curState.copy(contacts = curRecords.values.toList()) // TODO how should we sort this?
            }.let {
                mutUiState.value = it
            }
            // TODO handle when selected contact is no longer in the records list
        }

    override fun contactClick(contactId: String) {
        if (itemClickDelegate != null) {
            itemClickDelegate.contactClick(contactId = contactId)
            return
        }
        TODO("Not yet implemented")
    }

    override fun createClick() {
        if (itemClickDelegate != null) {
            itemClickDelegate.createClick()
            return
        }
        TODO("Not yet implemented")
    }

    override fun deleteClick(contactId: String) {
        if (dataOpDelegate != null) {
            dataOpDelegate.deleteClick(contactId = contactId)
            return
        }
        TODO("Not yet implemented")
    }

    override fun editClick(contactId: String) {
        if (itemClickDelegate != null) {
            itemClickDelegate.editClick(contactId = contactId)
            return
        }
        TODO("Not yet implemented")
    }

    override fun undeleteClick(contactId: String) {
        if (dataOpDelegate != null) {
            dataOpDelegate.undeleteClick(contactId = contactId)
            return
        }
        TODO("Not yet implemented")
    }

    override fun searchClick() {
        if (searchEventDelegate != null) {
            searchEventDelegate.searchClick()
            return
        }

        setSearchModeEnabled(isEnabled = true)
    }

    override fun exitSearchClick() {
        if (searchEventDelegate != null) {
            searchEventDelegate.exitSearchClick()
            return
        }

        setSearchModeEnabled(isEnabled = false)
    }

    override fun setSearchModeEnabled(isEnabled: Boolean) = launchWithStateMutex {
        when (val curState = uiState.value) {
            is ContactsListUiState.Searching ->
                if (isEnabled) curState
                else ContactsListUiState.ViewingList(
                    contacts = curRecords.values.toList(),
                    curSelectedContactId = curState.curSelectedContactId,
                    showLoadingOverlay = curState.showLoadingOverlay,
                )
            is ContactsListUiState.ViewingList ->
                if (isEnabled) ContactsListUiState.Searching(
                    contacts = curState.contacts,
                    curSelectedContactId = curState.curSelectedContactId,
                    showLoadingOverlay = curState.showLoadingOverlay,
                    curSearchTerm = ""
                )
                else curState
        }.also {
            mutUiState.value = it
        }
    }

    @Volatile
    private var searchJob: Job? = null

    override fun setSearchTerm(newSearchTerm: String) = launchWithStateMutex {
        when (val curState = uiState.value) {
            is ContactsListUiState.Searching -> curState.copy(curSearchTerm = newSearchTerm)
            is ContactsListUiState.ViewingList -> ContactsListUiState.Searching(
                contacts = curState.contacts,
                curSelectedContactId = curState.curSelectedContactId,
                showLoadingOverlay = curState.showLoadingOverlay,
                curSearchTerm = newSearchTerm
            )
        }.also {
            mutUiState.value = it
        }

        searchJob?.cancel()
        searchJob = launchNewSearchJob(
            searchTerm = newSearchTerm,
            contacts = curRecords.values.toList() // TODO this is not optimized. it would be cool to successively refine the search, but for now just search the entire list every time
        )
    }

    override fun onSearchTermUpdated(newSearchTerm: String) {
        if (searchEventDelegate != null) {
            searchEventDelegate.onSearchTermUpdated(newSearchTerm = newSearchTerm)
            return
        }

        setSearchTerm(newSearchTerm = newSearchTerm)
    }

    private fun launchNewSearchJob(searchTerm: String, contacts: List<ContactRecord>) =
        parentScope.launch(Dispatchers.Default) {
            val filteredResults =
                if (searchTerm.isEmpty()) {
                    contacts
                } else {
                    contacts.filter {
                        ensureActive()
                        it.sObject.fullName.contains(searchTerm, ignoreCase = true)
                    }
                }

            ensureActive()

            stateMutex.withLock {
                when (val curState = uiState.value) {
                    is ContactsListUiState.Searching -> curState.copy(contacts = filteredResults)
                    is ContactsListUiState.ViewingList -> curState // somehow no longer searching, so do nothing
                }.also {
                    mutUiState.value = it
                }
            }
        }

    private fun launchWithStateMutex(block: suspend CoroutineScope.() -> Unit) {
        parentScope.launch {
            stateMutex.withLock { this.block() }
        }
    }
}
