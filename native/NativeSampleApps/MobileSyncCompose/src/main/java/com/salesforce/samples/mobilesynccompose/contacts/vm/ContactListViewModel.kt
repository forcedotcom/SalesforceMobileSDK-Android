package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactListUiState.*
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ContactListViewModel {
    val uiState: StateFlow<ContactListUiState>
    fun onContactSelected(contact: ContactObject)
    fun onSearchTermUpdate(newSearch: String)
    fun enterSearchMode()
    fun exitSearchMode()
}

sealed interface ContactListUiState {
    val contacts: List<ContactObject>

    object Loading : ContactListUiState {
        override val contacts: List<ContactObject> = emptyList()
    }

    data class ViewList(
        override val contacts: List<ContactObject>
    ) : ContactListUiState

    data class Search(
        override val contacts: List<ContactObject>,
        val searchTerm: String
    ) : ContactListUiState
}

class DefaultContactListViewModel(
    contactUpdates: Flow<List<ContactObject>>,
    parentScope: CoroutineScope,
    private val onContactSelectedDelegate: (ContactObject) -> Unit
) : ContactListViewModel {

    private val contactsMutex = Mutex()

    private val mutUiState: MutableStateFlow<ContactListUiState> = MutableStateFlow(Loading)
    override val uiState: StateFlow<ContactListUiState> get() = mutUiState

    init {
        parentScope.launch {
            contactUpdates.collect {
                val newState = contactsMutex.withLock {
                    when (val curState = mutUiState.value) {
                        Loading -> ViewList(it)
                        is Search -> TODO("Contacts updated in search mode")
                        is ViewList -> curState.copy(contacts = it)
                    }
                }
                mutUiState.emit(newState)
            }
        }
    }

    override fun onContactSelected(contact: ContactObject) {
        onContactSelectedDelegate(contact)
    }

    override fun onSearchTermUpdate(newSearch: String) {
        TODO("onSearchTermUpdate")
    }

    override fun enterSearchMode() {
        TODO("enterSearchMode")
    }

    override fun exitSearchMode() {
        TODO("exitSearchMode")
    }
}
