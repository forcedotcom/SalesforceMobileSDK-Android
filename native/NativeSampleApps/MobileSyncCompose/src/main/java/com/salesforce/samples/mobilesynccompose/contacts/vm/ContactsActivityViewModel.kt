package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactsRepo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

interface ContactsActivityViewModel
    : ContactActivityEventHandler,
    ListComponentEventHandler,
    DetailComponentEventHandler {

    val uiState: StateFlow<ContactActivityUiState>

    val inspectDbClickEvents: ReceiveChannel<Unit>
}

data class ContactActivityUiState(
    val contactDetailsUiState: ContactDetailUiState,
    val contactListUiState: ContactListUiState
)

sealed interface ContactsActivityUiEvents {
    object ContactCreate : ContactsActivityUiEvents
    data class ContactDelete(val contact: ContactObject) : ContactsActivityUiEvents
    data class ContactEdit(val contact: ContactObject) : ContactsActivityUiEvents
    data class ContactView(val contact: ContactObject) : ContactsActivityUiEvents
    data class ContactListUpdates(val newContactList: List<ContactObject>) :
        ContactsActivityUiEvents

    object NavBack : ContactsActivityUiEvents
    object NavUp : ContactsActivityUiEvents
    object Sync : ContactsActivityUiEvents
    object Logout : ContactsActivityUiEvents
    object SwitchUser : ContactsActivityUiEvents
    object InspectDb : ContactsActivityUiEvents
}

sealed interface ListComponentUiEvents {
    object SearchClick : ListComponentUiEvents
    data class SearchTermUpdated(val newSearchTerm: String) : ListComponentUiEvents
}

sealed interface DetailComponentUiEvents {
    object SaveClick : DetailComponentUiEvents
    data class FieldValuesChanged(val newObject: ContactObject) : DetailComponentUiEvents
}

fun interface ContactActivityEventHandler {
    fun handleEvent(event: ContactsActivityUiEvents)
}

fun interface ListComponentEventHandler {
    fun handleEvent(event: ListComponentUiEvents)
}

fun interface DetailComponentEventHandler {
    fun handleEvent(event: DetailComponentUiEvents)
}

class DefaultContactsActivityViewModel(
    private val contactsRepo: ContactsRepo
) : ViewModel(), ContactsActivityViewModel {

    private val mutUiState: MutableStateFlow<ContactActivityUiState> = MutableStateFlow(
        ContactActivityUiState(NoContactSelected, ContactListUiState.Loading)
    )

    override val uiState: StateFlow<ContactActivityUiState> get() = mutUiState

    private val eventMutex = Mutex()

    private val mutDbClickEvents = Channel<Unit>()
    override val inspectDbClickEvents: ReceiveChannel<Unit> get() = mutDbClickEvents

    init {
        viewModelScope.launch {
            contactsRepo.contactUpdates.collect { contacts ->
                handleEvent(ContactsActivityUiEvents.ContactListUpdates(contacts))
            }
        }
    }

    override fun handleEvent(event: ContactsActivityUiEvents) {
        viewModelScope.launch {
            eventMutex.lock()

            if (event is ContactsActivityUiEvents.Sync) {
                sync(syncDownOnly = true)
            }

            val detailTransition =
                uiState.value.contactDetailsUiState.calculateProposedTransition(event)
            val listTransition =
                uiState.value.contactListUiState.calculateProposedTransition(event)

            mutUiState.value = ContactActivityUiState(detailTransition, listTransition)

            eventMutex.unlock()
        }
    }

    override fun handleEvent(event: DetailComponentUiEvents) {
        viewModelScope.launch {
            eventMutex.lock()

            val detailTransition =
                uiState.value.contactDetailsUiState.calculateProposedTransition(event)
            mutUiState.value = mutUiState.value.copy(contactDetailsUiState = detailTransition)

            eventMutex.unlock()
        }
    }

    override fun handleEvent(event: ListComponentUiEvents) {
        viewModelScope.launch {
            eventMutex.lock()

            uiState.value.let { state ->
                val listTransition = state.contactListUiState.calculateProposedTransition(event)
                mutUiState.value = state.copy(contactListUiState = listTransition)
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
