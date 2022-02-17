package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactEditModeEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactViewModeEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListEventHandler
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactsRepo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface ContactsActivityViewModel :
    ContactsListEventHandler,
    ContactEditModeEventHandler,
    ContactViewModeEventHandler {

    val uiState: StateFlow<ContactsActivityUiState>
    fun sync(syncDownOnly: Boolean = false)
}

object ContactsActivityUiState

class DefaultContactsActivityViewModel(
    private val contactsRepo: ContactsRepo
) : ViewModel(), ContactsActivityViewModel {

    override val uiState: StateFlow<ContactsActivityUiState>
        get() = TODO("Not yet implemented")

    override fun sync(syncDownOnly: Boolean) {
        viewModelScope.launch {
            contactsRepo.sync(syncDownOnly)
        }
    }

    override fun contactClick(contact: Contact) {
        TODO("Not yet implemented")
    }

    override fun contactCreateClick() {
        TODO("Not yet implemented")
    }

    override fun contactDeleteClick(contact: Contact) {
        TODO("Not yet implemented")
    }

    override fun contactEditClick(contact: Contact) {
        TODO("Not yet implemented")
    }

    override fun searchClick() {
        TODO("Not yet implemented")
    }

    override fun searchTermUpdated(newSearchTerm: String) {
        TODO("Not yet implemented")
    }

    override fun deleteClick() {
        TODO("Not yet implemented")
    }

    override fun onDetailsUpdated(newContact: Contact) {
        TODO("Not yet implemented")
    }

    override fun saveClick() {
        TODO("Not yet implemented")
    }

    override fun editClick() {
        TODO("Not yet implemented")
    }

    private companion object {
        private const val TAG = "DefaultContactsActivityViewModel"
    }
}
