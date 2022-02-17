package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.contacts.events.*
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactDetailsState
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
        TODO("$TAG: onContactListUpdate(newList=$newList)")
    }

    override fun sync(syncDownOnly: Boolean) {
        viewModelScope.launch {
            contactsRepo.sync(syncDownOnly)
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
        TODO("Not yet implemented")
    }

    override fun searchClick() {
        TODO("Not yet implemented")
    }

    override fun searchTermUpdated(newSearchTerm: String) {
        TODO("Not yet implemented")
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
