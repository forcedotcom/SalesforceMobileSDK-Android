package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact
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
    val contactsListUiState: ContactsListUiState
)

sealed interface ContactsActivityUiEvents {
    object ContactCreate : ContactsActivityUiEvents
    data class ContactDelete(val contact: Contact) : ContactsActivityUiEvents
    data class ContactEdit(val contact: Contact) : ContactsActivityUiEvents
    data class ContactView(val contact: Contact) : ContactsActivityUiEvents
    object NavBack : ContactsActivityUiEvents
    object NavUp : ContactsActivityUiEvents
    object SyncClick : ContactsActivityUiEvents
    object LogoutClick : ContactsActivityUiEvents
    object SwitchUserClick : ContactsActivityUiEvents
    object InspectDbClick : ContactsActivityUiEvents
}

sealed interface ContactsActivityDataEvents {
    data class ContactListUpdates(val newContactList: List<Contact>) : ContactsActivityDataEvents
    data class ContactDetailsSaved(val contact: Contact) : ContactsActivityDataEvents
}

sealed interface ListComponentUiEvents {
    object SearchClick : ListComponentUiEvents
    data class SearchTermUpdated(val newSearchTerm: String) : ListComponentUiEvents
}

sealed interface DetailComponentUiEvents {
    object SaveClick : DetailComponentUiEvents
    data class FieldValuesChanged(val newObject: Contact) : DetailComponentUiEvents
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

//private data class TransitionProposal<T>(
//    val event: T,
//    val curDetailState: KClass<out ContactDetailUiState>? = null,
//    val targetDetailState: KClass<out ContactDetailUiState>? = null,
//    val curListState: KClass<out ContactsListUiState>? = null,
//    val targetListState: KClass<out ContactsListUiState>? = null,
//)

class DefaultContactsActivityViewModel(
    private val contactsRepo: ContactsRepo
) : ViewModel(), ContactsActivityViewModel {

//    private val eventInterceptors = mapOf(
//        TransitionProposal(
//            event = DetailComponentUiEvents.SaveClick,
//            curDetailState = EditingContact::class,
//            targetDetailState = ViewingContact::class,
//        ) to { curDetailState: EditingContact, targetDetailState: ViewingContact ->
//            viewModelScope.launch {
//                contactsRepo.saveContact(targetDetailState.contact)
//            }
//        },
//    )
    private val mutUiState: MutableStateFlow<ContactActivityUiState> = MutableStateFlow(
        ContactActivityUiState(NoContactSelected, ContactsListUiState.Loading)
    )

    override val uiState: StateFlow<ContactActivityUiState> get() = mutUiState

    private val eventMutex = Mutex()

    private val mutDbClickEvents = Channel<Unit>()
    override val inspectDbClickEvents: ReceiveChannel<Unit> get() = mutDbClickEvents

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

            val detailTransition = uiState.value.contactDetailsUiState.calculateProposedTransition(event)
            val listTransition = uiState.value.contactsListUiState.calculateProposedTransition(event)

            mutUiState.value = ContactActivityUiState(detailTransition, listTransition)

            eventMutex.unlock()
        }
    }

    override fun handleEvent(event: ContactsActivityUiEvents) {
        viewModelScope.launch {
            eventMutex.lock()

            when (event) {
                ContactsActivityUiEvents.ContactCreate -> TODO()
                is ContactsActivityUiEvents.ContactDelete -> TODO()
                is ContactsActivityUiEvents.ContactEdit -> TODO()
                is ContactsActivityUiEvents.ContactView -> TODO()
                ContactsActivityUiEvents.InspectDbClick -> TODO()
                ContactsActivityUiEvents.LogoutClick -> TODO()
                ContactsActivityUiEvents.NavBack -> TODO()
                ContactsActivityUiEvents.NavUp -> TODO()
                ContactsActivityUiEvents.SwitchUserClick -> TODO()
                ContactsActivityUiEvents.SyncClick -> {
                    sync(syncDownOnly = true)
                }
            }

            val detailTransition =
                uiState.value.contactDetailsUiState.calculateProposedTransition(event)
            val listTransition =
                uiState.value.contactsListUiState.calculateProposedTransition(event)

            mutUiState.value = ContactActivityUiState(detailTransition, listTransition)

            eventMutex.unlock()
        }
    }

    override fun handleEvent(event: DetailComponentUiEvents) {
        viewModelScope.launch {
            eventMutex.lock()

            when (event) {
                is DetailComponentUiEvents.FieldValuesChanged -> {
                    /* no-op */
                }
                DetailComponentUiEvents.SaveClick -> {
                    val detailState = uiState.value.contactDetailsUiState
                    if (detailState is EditingContact) {
                        viewModelScope.launch {
                            contactsRepo.saveContact(detailState.updatedContact)
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

    override fun handleEvent(event: ListComponentUiEvents) {
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
