package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactsRepo
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityState.ViewContactDetails
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityState.ViewContactList
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface ContactActivityViewModel {
    val uiState: StateFlow<ContactActivityState>
    val detailVm: ContactDetailViewModel
    val listVm: ContactListViewModel

    val inspectDbClickEvents: ReceiveChannel<Unit>

    fun inspectDb()
    fun logout()
    fun switchUser()
    fun sync(syncDownOnly: Boolean = false) // default to always syncing everything, up and down
}

// TODO this really is more of just a layout state, and it should probably be renamed accordingly
sealed interface ContactActivityState {
    object ViewContactDetails : ContactActivityState
    object ViewContactList : ContactActivityState
//    object ListDetail : ContactActivityState
}

class DefaultContactActivityViewModel(
    private val contactsRepo: ContactsRepo
) : ViewModel(), ContactActivityViewModel {

    private val mutUiState: MutableStateFlow<ContactActivityState> =
        MutableStateFlow(ViewContactList)

    override val uiState: StateFlow<ContactActivityState> get() = mutUiState

    private val contactSelectionEvents = MutableStateFlow<ContactObject?>(null)
    override val detailVm: ContactDetailViewModel = DefaultContactDetailViewModel(
        contactSelectionEvents = contactSelectionEvents,
        parentScope = viewModelScope,
        onBackDelegate = {
            viewModelScope.launch {
                contactSelectionEvents.emit(null)
                mutUiState.emit(ViewContactList)
            }
        }
    )

    override val listVm: ContactListViewModel = DefaultContactListViewModel(
        contactUpdates = contactsRepo.contactUpdates,
        parentScope = viewModelScope,
        onContactSelectedDelegate = { contact ->
            viewModelScope.launch {
                contactSelectionEvents.emit(contact)
                mutUiState.emit(ViewContactDetails)
            }
        }
    )

    private val mutDbClickEvents = Channel<Unit>()
    override val inspectDbClickEvents: ReceiveChannel<Unit> get() = mutDbClickEvents

    override fun inspectDb() {
        viewModelScope.launch {
            mutDbClickEvents.send(Unit)
        }
    }

    override fun logout() {
        TODO("Not yet implemented")
    }

    override fun switchUser() {
        TODO("Not yet implemented")
    }

    override fun sync(syncDownOnly: Boolean) {
        viewModelScope.launch {
            contactsRepo.sync(syncDownOnly)
        }
    }
}
