package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.contacts.model.ContactsRepo
import com.salesforce.samples.mobilesynccompose.contacts.ui.TempContactObject
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityState.ViewContactDetails
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityState.ViewContactList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface ContactActivityViewModel {
    val uiState: StateFlow<ContactActivityState>
    val detailVm: ContactDetailViewModel
    val listVm: ContactListViewModel

    fun inspectDb()
    fun logout()
    fun switchUser()
    fun sync()
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

    private val contactSelectionEvents = MutableStateFlow<TempContactObject?>(null)
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

    override fun inspectDb() {
        TODO("Not yet implemented")
    }

    override fun logout() {
        TODO("Not yet implemented")
    }

    override fun switchUser() {
        TODO("Not yet implemented")
    }

    override fun sync() {
        TODO("Not yet implemented")
    }
}
