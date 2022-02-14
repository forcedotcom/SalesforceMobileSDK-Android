package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactDetailUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityDataEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactDetailUiState
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactsListUiState
import com.salesforce.samples.mobilesynccompose.contacts.state.EditingContact
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

    init {
        viewModelScope.launch {
            sync(syncDownOnly = false)
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

//            when (event) {
//                ContactsActivityUiEvents.ContactCreate -> TODO()
//                is ContactsActivityUiEvents.ContactDelete -> TODO()
//                is ContactsActivityUiEvents.ContactEdit -> TODO()
//                is ContactsActivityUiEvents.ContactView -> TODO()
//                ContactsActivityUiEvents.InspectDbClick -> TODO()
//                ContactsActivityUiEvents.LogoutClick -> TODO()
//                ContactsActivityUiEvents.NavBack -> TODO()
//                ContactsActivityUiEvents.NavUp -> TODO()
//                ContactsActivityUiEvents.SwitchUserClick -> TODO()
//                ContactsActivityUiEvents.SyncClick -> {
//                    sync(syncDownOnly = true)
//                }
//            }

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

            when (event) {
                is ContactDetailUiEvents.FieldValuesChanged -> {
                    /* no-op */
                }
                ContactDetailUiEvents.SaveClick -> {
                    val detailState = uiState.value.contactDetailsUiState
                    if (detailState is EditingContact && !detailState.hasFieldsInErrorState) {
                        viewModelScope.launch {
                            contactsRepo.saveContact(detailState.updatedContact).getOrNull()?.also {
//                                handleDataEvent(ContactsActivityDataEvents.ContactDetailsSaved(it))
                            }
                        }
                    }
                }
            }

            val detailTransition =
                uiState.value.contactDetailsUiState.calculateProposedTransition(event)
            mutUiState.value = mutUiState.value.copy(contactDetailsUiState = detailTransition)

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

    private fun sync(syncDownOnly: Boolean) {
        viewModelScope.launch {
            contactsRepo.sync(syncDownOnly)
        }
    }

    private companion object {
        private const val TAG = "DefaultContactsActivityViewModel"
    }
}
