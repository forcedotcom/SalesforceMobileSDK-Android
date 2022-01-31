package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.contacts.ui.TempContactObject
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityState.ViewContactDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface ContactActivityViewModel {
    val activityState: StateFlow<ContactActivityState>
    fun viewContact(contact: TempContactObject)
    fun editContact(contact: TempContactObject)
    fun deleteContact(contact: TempContactObject)
    fun createNewContact()

    fun showSearch()
    fun sync()
}

sealed interface ContactActivityState {
    val contacts: List<TempContactObject>

    data class ViewContactDetails(
        override val contacts: List<TempContactObject>,
        val selectedContact: TempContactObject?,
        val isSearchMode: Boolean,
        val currentSearchTerm: String?
    ) : ContactActivityState

    data class EditContactDetails(
        override val contacts: List<TempContactObject>,
        val selectedContact: TempContactObject
    ) : ContactActivityState

    data class CreateNewContact(override val contacts: List<TempContactObject>) :
        ContactActivityState
}

class DefaultContactActivityViewModel : ViewModel(), ContactActivityViewModel {
    private val contacts = (0..100).map {
        TempContactObject(
            id = it,
            name = "Name $it",
            title = "Title $it"
        )
    }
    private val mutableState = MutableStateFlow(
        ViewContactDetails(
            contacts = contacts,
            selectedContact = null,
            isSearchMode = false,
            currentSearchTerm = null
        )
    )

    override val activityState: StateFlow<ContactActivityState> get() = mutableState

    override fun viewContact(contact: TempContactObject) {
        viewModelScope.launch {
            mutableState.value.let {
                mutableState.emit(it.copy(selectedContact = contact))
            }
        }
    }

    override fun editContact(contact: TempContactObject) {
        TODO("editContact")
    }

    override fun deleteContact(contact: TempContactObject) {
        TODO("deleteContact")
    }

    override fun createNewContact() {
        TODO("createNewContact")
    }

    override fun showSearch() {
        TODO("showSearch")
    }

    override fun sync() {
        TODO("sync")
    }
}