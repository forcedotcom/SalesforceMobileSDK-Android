package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactEditModeEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactViewModeEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsSearchEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactDetailsState
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface ContactsActivityViewModel :
    ContactsListEventHandler,
    ContactsSearchEventHandler,
    ContactEditModeEventHandler,
    ContactViewModeEventHandler {

    val uiState: StateFlow<ContactsActivityUiState>
    fun sync(syncDownOnly: Boolean = false)
}

data class ContactsActivityUiState(
    val contacts: List<Contact>,
    val detailsState: ContactDetailsState?,
    val searchTerm: String?,
    val isSyncing: Boolean,
    val showDiscardChanges: Boolean
)

class DefaultContactsActivityViewModel(
    private val contactsRepo: ContactsRepo
) : ViewModel(), ContactsActivityViewModel {

    private val eventMutex = Mutex()
    private val mutUiState = MutableStateFlow(
        ContactsActivityUiState(
            contacts = emptyList(),
            detailsState = null,
            searchTerm = null,
            isSyncing = false,
            showDiscardChanges = false
        )
    )
    override val uiState: StateFlow<ContactsActivityUiState> get() = mutUiState

    init {
        viewModelScope.launch {
            contactsRepo.contactUpdates.collect {
                onContactListUpdate(it)
            }
        }
    }

    private fun onContactListUpdate(newList: List<Contact>) {
        viewModelScope.launch {
            eventMutex.withLock {
                val curState = uiState.value
                if (curState.detailsState != null) {
                    TODO("Contact list updates while viewing contact")
                } else {
                    mutUiState.value = curState.copy(contacts = newList, isSyncing = false)
                }
            }
        }
    }

    override fun sync(syncDownOnly: Boolean) {
        viewModelScope.launch {
            eventMutex.withLock {
                mutUiState.value = mutUiState.value.copy(isSyncing = true)
                contactsRepo.sync(syncDownOnly)
            }
        }
    }

    override fun listContactClick(contact: Contact) {
        TODO("Not yet implemented")
    }

    override fun listCreateClick() {
        TODO("Not yet implemented")
    }

    override fun listDeleteClick(contact: Contact) {
        TODO("Not yet implemented")
    }

    override fun listEditClick(contact: Contact) {
        TODO("Not yet implemented")
    }

    override fun exitSearch() {
        viewModelScope.launch {
            eventMutex.withLock {
                mutUiState.value = mutUiState.value.copy(
                    contacts = contactsRepo.curUpstreamContacts,
                    searchTerm = null
                )
            }
        }
    }

    override fun searchClick() {
        viewModelScope.launch {
            eventMutex.withLock {
                mutUiState.value = mutUiState.value.copy(searchTerm = "")
            }
        }
    }

    override fun searchTermUpdated(newSearchTerm: String) {
        viewModelScope.launch {
            eventMutex.withLock {
                withContext(Dispatchers.Default) {
                    val curState = mutUiState.value
                    mutUiState.value = curState.copy(
                        contacts = contactsRepo.curUpstreamContacts.filter {
                            it.fullName.contains(
                                newSearchTerm,
                                ignoreCase = true
                            )
                        },
                        searchTerm = newSearchTerm
                    )
                }
            }
        }
    }

    override fun detailsDeleteClick() {
        TODO("Not yet implemented")
    }

    override fun onDetailsUpdated(newContact: Contact) {
        TODO("Not yet implemented")
    }

    override fun saveClick() {
        TODO("Not yet implemented")
    }

    override fun detailsEditClick() {
        TODO("Not yet implemented")
    }

    private companion object {
        private const val TAG = "DefaultContactsActivityViewModel"
    }
}
