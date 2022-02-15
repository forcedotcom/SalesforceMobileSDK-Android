package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactDetailUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityDataEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactDetailUiState
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactsListUiState
import com.salesforce.samples.mobilesynccompose.contacts.state.EditMode
import com.salesforce.samples.mobilesynccompose.contacts.state.NoContactSelected
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactsRepo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

interface ContactsActivityViewModel
    : ContactsActivityEventHandler,
    ContactsListEventHandler,
    ContactDetailEventHandler {

    val uiState: StateFlow<ContactsActivityUiState>

    val inspectDbClickEvents: ReceiveChannel<Unit>
    val logoutClickEvents: ReceiveChannel<Unit>
}

data class ContactsActivityUiState(
    val contactDetailsUiState: ContactDetailUiState,
    val contactsListUiState: ContactsListUiState
)

fun interface ContactsActivityEventHandler {
    fun handleEvent(event: ContactsActivityUiEvents)
}

fun interface ContactsListEventHandler {
    fun handleEvent(event: ContactsListUiEvents)
}

fun interface ContactDetailEventHandler {
    fun handleEvent(event: ContactDetailUiEvents)
}

class DefaultContactsActivityViewModel(
    private val contactsRepo: ContactsRepo
) : ViewModel(), ContactsActivityViewModel {

    private val mutUiState: MutableStateFlow<ContactsActivityUiState> = MutableStateFlow(
        ContactsActivityUiState(NoContactSelected, ContactsListUiState.Loading)
    )

    override val uiState: StateFlow<ContactsActivityUiState> get() = mutUiState

    private val eventMutex = Mutex()

    private val mutDbClickEvents = Channel<Unit>()
    override val inspectDbClickEvents: ReceiveChannel<Unit> get() = mutDbClickEvents

    private val mutLogoutClickEvents = Channel<Unit>()
    override val logoutClickEvents: ReceiveChannel<Unit> get() = mutLogoutClickEvents

    init {
        viewModelScope.launch {
            contactsRepo.contactUpdates.collect { contacts ->
                handleDataEvent(ContactsActivityDataEvents.ContactListUpdates(contacts))
            }
        }
    }

    private fun handleDataEvent(event: ContactsActivityDataEvents) {
        viewModelScope.launch {
            eventMutex.lock()

            val detailTransition =
                uiState.value.contactDetailsUiState.calculateProposedTransition(event)
            val listTransition =
                uiState.value.contactsListUiState.calculateProposedTransition(event)

            mutUiState.value = ContactsActivityUiState(detailTransition, listTransition)

            eventMutex.unlock()
        }
    }

    override fun handleEvent(event: ContactsActivityUiEvents) {
        viewModelScope.launch {
            eventMutex.lock()

            when (event) {
                ContactsActivityUiEvents.SyncClick -> sync(syncDownOnly = false)
                ContactsActivityUiEvents.InspectDbClick -> inspectDb()
                ContactsActivityUiEvents.LogoutClick -> logout()
                else -> {
                    /* no-op */
                }
            }

            val detailTransition =
                uiState.value.contactDetailsUiState.calculateProposedTransition(event)
            val listTransition =
                uiState.value.contactsListUiState.calculateProposedTransition(event)

            mutUiState.value = ContactsActivityUiState(detailTransition, listTransition)

            eventMutex.unlock()
        }
    }

    override fun handleEvent(event: ContactDetailUiEvents) {
        viewModelScope.launch {
            eventMutex.lock()

            val detailTransition =
                uiState.value.contactDetailsUiState.calculateProposedTransition(event)

            mutUiState.value = mutUiState.value.copy(contactDetailsUiState = detailTransition)

            when (event) {
                ContactDetailUiEvents.SaveClick -> {
                    if (detailTransition is EditMode.Saving) {
                        viewModelScope.launch {
                            contactsRepo.saveContact(detailTransition.updatedContact)
                        }
                    }
                }
                ContactDetailUiEvents.DetailNavBack -> {
                    // TODO Check for deep link and if so delegate the back handling to system nav
                }
                else -> {
                    /* no-op */
                }
            }

            eventMutex.unlock()
        }
    }

    override fun handleEvent(event: ContactsListUiEvents) {
        viewModelScope.launch {
            eventMutex.lock()

            uiState.value.let { state ->
                val listTransition = state.contactsListUiState.calculateProposedTransition(event)
                mutUiState.value = state.copy(contactsListUiState = listTransition)
            }

            eventMutex.unlock()
        }
    }

    private fun inspectDb() {
        viewModelScope.launch {
            mutDbClickEvents.send(Unit)
        }
    }

    private fun logout() {
        viewModelScope.launch {
            mutLogoutClickEvents.send(Unit)
        }
    }

    private fun sync(syncDownOnly: Boolean) {
        viewModelScope.launch {
            contactsRepo.sync(syncDownOnly)
        }
    }

    private companion object {
        private const val TAG = "DefaultContactsActivityViewModel"
    }
}
