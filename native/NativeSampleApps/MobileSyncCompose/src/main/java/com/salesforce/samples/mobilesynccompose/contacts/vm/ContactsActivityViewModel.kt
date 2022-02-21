package com.salesforce.samples.mobilesynccompose.contacts.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.contacts.events.*
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailsUiMode.*
import com.salesforce.samples.mobilesynccompose.core.extensions.parallelFilter
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactsRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ContactsActivityViewModel :
    ContactsListEventHandler,
    ContactDetailsDiscardChangesEventHandler,
    ContactsSearchEventHandler,
    ContactEditModeEventHandler,
    ContactViewModeEventHandler {

    val uiState: StateFlow<ContactsActivityUiState>
    fun sync(syncDownOnly: Boolean = false)
}

data class ContactsActivityUiState(
    val listState: ContactsActivityListUiState,
    val detailsState: ContactDetailsUiState?,
    val isSyncing: Boolean,
)

data class ContactsActivityListUiState(
    val contacts: List<Contact>,
    val searchTerm: String?,
)

class DefaultContactsActivityViewModel(
    private val contactsRepo: ContactsRepo
) : ViewModel(), ContactsActivityViewModel {

    private val eventMutex = Mutex()
    private val mutUiState = MutableStateFlow(
        ContactsActivityUiState(
            listState = ContactsActivityListUiState(contacts = emptyList(), searchTerm = null),
            detailsState = null,
            isSyncing = false,
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

    private fun onContactListUpdate(newList: List<Contact>) = withEventLock {
        val curState = uiState.value

        val filteredContactsDeferred = async {
            curState.listState.searchTerm?.let { searchTerm ->
                newList.parallelFilter { it.fullName.contains(searchTerm, ignoreCase = true) }
            } ?: newList
        }

        val newDetail = curState.detailsState?.let { curDetail ->

            val matchingContact = newList.firstOrNull {
                it.id == curState.detailsState.origContact.id
            } ?: return@let null

            when (curDetail.mode) {
                Creating -> TODO("Creating contact, but received upstream with same ID. Upstream=$matchingContact , current=${curDetail.updatedContact}")
                Editing -> when {
                    curDetail.isSaving -> curDetail.copy(
                        mode = Viewing,
                        origContact = matchingContact
                    )
                    else -> TODO("Conflicting contact list update while editing. Upstream=$matchingContact , current=${curDetail.updatedContact}")
                }

                Viewing -> curDetail.copy(origContact = matchingContact)
            }
        }

        val newListState = curState.listState.copy(contacts = filteredContactsDeferred.await())

        mutUiState.value = curState.copy(
            listState = newListState,
            isSyncing = false,
            detailsState = newDetail
        )
    }

    override fun sync(syncDownOnly: Boolean) = withEventLock {
        mutUiState.value = mutUiState.value.copy(isSyncing = true)
        contactsRepo.sync(syncDownOnly)
    }

    override fun listContactClick(contact: Contact) = withEventLock {
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            mutUiState.value = mutUiState.value.copy(
                detailsState = curState.detailsState.copy(showDiscardChanges = true)
            )
        } else {
            mutUiState.value = curState.copy(
                detailsState = contact.toContactDetailsUiState(mode = Viewing),
            )
        }
    }

    override fun listCreateClick() = withEventLock {
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            mutUiState.value = mutUiState.value.copy(
                detailsState = curState.detailsState.copy(showDiscardChanges = true)
            )
        } else {
            mutUiState.value = curState.copy(
                detailsState = Contact.createNewLocal()
                    .toContactDetailsUiState(mode = Creating),
            )
        }
    }

    override fun listDeleteClick(contact: Contact) = withEventLock {
        TODO("Not yet implemented")
    }

    override fun listEditClick(contact: Contact) = withEventLock {
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            mutUiState.value = mutUiState.value.copy(
                detailsState = curState.detailsState.copy(showDiscardChanges = true)
            )
        } else {
            mutUiState.value = curState.copy(
                detailsState = contact.toContactDetailsUiState(mode = Editing),
            )
        }
    }

    override fun discardChanges() = withEventLock {
        val curState = mutUiState.value
        mutUiState.value = if (curState.detailsState?.mode != Creating) {
            curState.copy(
                detailsState = curState.detailsState?.origContact?.toContactDetailsUiState(
                    mode = Viewing,
                    showDiscardChanges = false
                )
            )
        } else {
            curState.copy(detailsState = null)
        }
    }

    override fun continueEditing() {
        mutUiState.value = mutUiState.value.copy(
            detailsState = mutUiState.value.detailsState?.copy(showDiscardChanges = false)
        )
    }

    override fun exitSearch() = withEventLock {
        mutUiState.value = mutUiState.value.copy(
            listState = ContactsActivityListUiState(
                contacts = contactsRepo.curUpstreamContacts,
                searchTerm = null
            )
        )
    }

    override fun searchClick() = withEventLock {
        val newListState = mutUiState.value.listState.copy(searchTerm = "")
        mutUiState.value = mutUiState.value.copy(listState = newListState)
    }

    private var searchJob: Job? = null

    // TODO make the filtering a Job and cancel it on new UI events to avoid UI state change hanging
    override fun searchTermUpdated(newSearchTerm: String) = withEventLock {
        mutUiState.value = mutUiState.value.copy(
            listState = mutUiState.value.listState.copy(searchTerm = newSearchTerm)
        )
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val filteredContacts = contactsRepo.curUpstreamContacts.parallelFilter {
                ensureActive()
                it.fullName.contains(newSearchTerm, ignoreCase = true)
            }
            ensureActive()
            mutUiState.value = mutUiState.value.copy(
                listState = mutUiState.value.listState.copy(contacts = filteredContacts)
            )
        }
    }

    override fun detailsDeleteClick() = withEventLock {
        TODO("Not yet implemented")
    }

    override fun detailsExitClick() = withEventLock {
        val curState = mutUiState.value

        if (curState.detailsState == null) {
            return@withEventLock
        }

        when (curState.detailsState.mode) {
            Creating -> {
                if (curState.detailsState.isModified) {
                    mutUiState.value = curState.copy(
                        detailsState = curState.detailsState.copy(showDiscardChanges = true)
                    )
                } else {
                    mutUiState.value = curState.copy(detailsState = null)
                }
            }

            Editing -> {
                if (curState.detailsState.isModified) {
                    mutUiState.value = curState.copy(
                        detailsState = curState.detailsState.copy(showDiscardChanges = true)
                    )
                } else {
                    mutUiState.value = curState.copy(
                        detailsState = curState.detailsState.copy(mode = Viewing),
                    )
                }
            }

            Viewing -> {
                mutUiState.value = curState.copy(detailsState = null)
            }
        }
    }

    override fun onDetailsUpdated(newContact: Contact) = withEventLock {
        val curState = mutUiState.value
        if (curState.detailsState == null)
            return@withEventLock

        mutUiState.value = curState.copy(
            detailsState = curState.detailsState.copy(
                firstNameVm = newContact.createFirstNameVm(),
                lastNameVm = newContact.createLastNameVm(),
                titleVm = newContact.createTitleVm()
            )
        )
    }

    override fun saveClick() = withEventLock {
        val curState = mutUiState.value
        val curDetail = curState.detailsState ?: return@withEventLock

        if (curDetail.hasFieldsInErrorState) {
            mutUiState.value = curState.copy(
                detailsState = curDetail.copy(
                    fieldToScrollTo = curDetail.fieldsInErrorState.firstOrNull()
                )
            )
            return@withEventLock
        }

        val newUiState = if (!curDetail.isModified) {
            when (curDetail.mode) {
                Creating -> {
                    // TODO Maybe add a Toast or something to inform the user why nothing happened
                    /* no-op */
                    curState
                }

                Viewing -> {
                    Log.w(
                        TAG,
                        "Got a saveClick event while in Viewing mode. This shouldn't happen and is probably a bug."
                    )
                    curState
                }

                Editing -> {
                    curState.copy(detailsState = curDetail.copy(mode = Viewing))
                }
            }
        } else {
            when (curDetail.mode) {
                Creating -> TODO()
                Editing -> {
                    viewModelScope.launch {
                        contactsRepo.saveContact(curDetail.updatedContact)
                    }

                    curState.copy(detailsState = curDetail.copy(isSaving = true))
                }
                Viewing -> {
                    Log.w(
                        TAG,
                        "Got a saveClick event while in Viewing mode. This shouldn't happen and is probably a bug."
                    )
                    curState
                }
            }
        }

        mutUiState.value = newUiState
    }

    override fun detailsEditClick() = withEventLock {
        val curUiState = mutUiState.value
        val newDetails = curUiState.detailsState?.let { curDetails ->
            when (curDetails.mode) {
                Creating -> TODO("Discard changes")
                Editing -> {
                    Log.w(
                        TAG,
                        "Got a detailsEditClick() when in Edit mode. This shouldn't happen and is probably a bug."
                    )
                    curDetails
                }
                Viewing -> curDetails.copy(mode = Editing)
            }
        }

        mutUiState.value = curUiState.copy(detailsState = newDetails)
    }

    private fun withEventLock(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch { eventMutex.withLock { block() } }
    }

    private companion object {
        private const val TAG = "DefaultContactsActivityViewModel"
    }
}
