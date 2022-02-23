package com.salesforce.samples.mobilesynccompose.contacts.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailsUiMode.*
import com.salesforce.samples.mobilesynccompose.core.SealedFailure
import com.salesforce.samples.mobilesynccompose.core.SealedSuccess
import com.salesforce.samples.mobilesynccompose.core.extensions.parallelFilter
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactsRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ContactsActivityViewModel {
    val uiState: StateFlow<ContactsActivityUiState>

    fun detailsDeleteClick()
    fun detailsEditClick()
    fun detailsExitClick()
    fun detailsSaveClick()
    fun detailsUndeleteClick()
    fun onDetailsUpdated(newContact: Contact)

    fun listContactClick(contact: Contact)
    fun listCreateClick()
    fun listDeleteClick(contact: Contact)
    fun listEditClick(contact: Contact)
    fun listUndeleteClick(contact: Contact)

    fun listSearchClick()
    fun listExitSearchClick()
    fun onSearchTermUpdated(newSearchTerm: String)

    fun sync(syncDownOnly: Boolean = false)
}

data class ContactsActivityUiState(
    val listState: ContactsActivityListUiState,
    val detailsState: ContactDetailsUiState?,
    val isSyncing: Boolean,
    val dialog: ContactsActivityDialog?
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
            dialog = null
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

    private fun onContactListUpdate(newList: List<Contact>) = launchWithEventLock {
        val curState = uiState.value
        val curDetail = curState.detailsState

        val filteredContactsDeferred = async {
            curState.listState.searchTerm?.let { searchTerm ->
                newList.parallelFilter { it.fullName.contains(searchTerm, ignoreCase = true) }
            } ?: newList
        }

        val newDetail = curDetail?.let {
            val matchingContact = newList.firstOrNull { it.id == curDetail.origContact.id }
            when (curDetail.mode) {
                Creating -> {
                    if (curDetail.isSaving) {
                        curDetail // The result of the save operation will drive details UI change
                    } else {
                        if (matchingContact == null) {
                            // not saving, no matching upstream changes, so details UI state does not change:
                            curDetail
                        } else {
                            TODO("$TAG - onContactListUpdate(): Creating mode and got upstream contact emission with same ID. Current detail UI state = $curDetail , upstream contact = $matchingContact")
                        }
                    }
                }
                LocallyDeleted -> TODO("$TAG - onContactListUpdate(): Contact is locally deleted")
                Editing -> {
                    if (curDetail.isSaving) {
                        curDetail // The result of the save operation will drive details UI change
                    } else {
                        /* If we are _not_ actively saving the edited contact, do merge conflict
                         * resolution: */
                        when (matchingContact) {
                            null,
                            curDetail.origContact -> curDetail
                            else -> TODO("$TAG - onContactListUpdate(): Received contact update while editing same contact. Current detail UI state = $curDetail , upstream contact = $matchingContact")
                        }
                    }
                }
                Viewing -> {
                    /* If we are currently viewing a contact and an updated version comes in
                     * from the upstream, update the detail fields; otherwise, the details state
                     * remains unchanged. */
                    matchingContact?.toContactDetailsUiState(mode = Viewing) ?: curDetail
                }
            }
        }

        val newListState = curState.listState.copy(contacts = filteredContactsDeferred.await())

        mutUiState.value = curState.copy(
            listState = newListState,
            isSyncing = false,
            detailsState = newDetail
        )
    }

    override fun sync(syncDownOnly: Boolean) = launchWithEventLock {
        mutUiState.value = mutUiState.value.copy(isSyncing = true)
        contactsRepo.sync(syncDownOnly)
    }

    override fun listContactClick(contact: Contact) = launchWithEventLock {
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            mutUiState.value = mutUiState.value.copy(
                dialog = DiscardChanges(
                    onDiscardChanges = this@DefaultContactsActivityViewModel::detailsDiscardChanges,
                    onKeepChanges = this@DefaultContactsActivityViewModel::detailsContinueEditing
                )
            )
        } else {
            mutUiState.value = curState.copy(
                detailsState = contact.toContactDetailsUiState(mode = Viewing),
            )
        }
    }

    override fun listCreateClick() = launchWithEventLock {
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            mutUiState.value = mutUiState.value.copy(
                dialog = DiscardChanges(
                    onDiscardChanges = this@DefaultContactsActivityViewModel::detailsDiscardChanges,
                    onKeepChanges = this@DefaultContactsActivityViewModel::detailsContinueEditing
                )
            )
        } else {
            mutUiState.value = curState.copy(
                detailsState = Contact.createNewLocal().toContactDetailsUiState(mode = Creating),
            )
        }
    }

    override fun listDeleteClick(contact: Contact) = launchWithEventLock {
        val curState = mutUiState.value

        // If there is currently a dialog showing, do not clobber it.
        val newDialog = curState.dialog ?: DeleteConfirmation(
            contactToDelete = contact,
            onCancelDelete = this@DefaultContactsActivityViewModel::dismissCurDialog,
            onDeleteConfirm = { contactToDelete ->
                dismissCurDialog()
                launchDelete(contactToDelete)
            }
        )

        mutUiState.value = curState.copy(dialog = newDialog)
    }

    override fun listUndeleteClick(contact: Contact) = launchWithEventLock {
        TODO("Not yet implemented")
    }

    override fun listEditClick(contact: Contact) = launchWithEventLock {
        // TODO check for if it is locally deleted, and only go to Editing mode when it is not deleted
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            mutUiState.value = mutUiState.value.copy(
                dialog = DiscardChanges(
                    onDiscardChanges = this@DefaultContactsActivityViewModel::detailsDiscardChanges,
                    onKeepChanges = this@DefaultContactsActivityViewModel::detailsContinueEditing
                )
            )
        } else {
            mutUiState.value = curState.copy(
                detailsState = contact.toContactDetailsUiState(mode = Editing),
            )
        }
    }

    private fun detailsDiscardChanges() = launchWithEventLock {
        val curState = mutUiState.value
        mutUiState.value = if (curState.detailsState?.mode != Creating) {
            curState.copy(
                detailsState = curState.detailsState?.origContact?.toContactDetailsUiState(
                    mode = Viewing,
                ),
                dialog = null
            )
        } else {
            curState.copy(detailsState = null, dialog = null)
        }
    }

    private fun detailsContinueEditing(): Unit = launchWithEventLock {
        val curState = mutUiState.value
        if (curState.dialog is DiscardChanges) {
            mutUiState.value = curState.copy(dialog = null)
        }
    }

    override fun listExitSearchClick() = launchWithEventLock {
        mutUiState.value = mutUiState.value.copy(
            listState = ContactsActivityListUiState(
                contacts = contactsRepo.curUpstreamContacts,
                searchTerm = null
            )
        )
    }

    override fun listSearchClick() = launchWithEventLock {
        val newListState = mutUiState.value.listState.copy(searchTerm = "")
        mutUiState.value = mutUiState.value.copy(listState = newListState)
    }

    @Volatile
    private var searchJob: Job? = null

    override fun onSearchTermUpdated(newSearchTerm: String) = launchWithEventLock {
        // Update UI before doing filtering to keep UI responsive:
        mutUiState.value = mutUiState.value.copy(
            listState = mutUiState.value.listState.copy(searchTerm = newSearchTerm)
        )

        // Now do the filtering:
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val filteredContacts = contactsRepo.curUpstreamContacts.parallelFilter {
                ensureActive() // Cooperative cancellation within filter loop
                it.fullName.contains(newSearchTerm, ignoreCase = true)
            }

            eventMutex.withLock {
                ensureActive()
                mutUiState.value = mutUiState.value.copy(
                    listState = mutUiState.value.listState.copy(contacts = filteredContacts)
                )
            }
        }
    }

    override fun detailsDeleteClick() = launchWithEventLock {
        val curState = mutUiState.value
        val curDetail = curState.detailsState ?: return@launchWithEventLock

        fun onDeleteConfirm(contactToDelete: Contact) {
            launchWithEventLock {
                val futureState = mutUiState.value
                mutUiState.value = futureState.copy(
                    detailsState = futureState.detailsState?.copy(isSaving = true),
                    dialog = null
                )
            }
            launchDelete(contactToDelete)
        }

        val newDialog = curState.dialog ?: DeleteConfirmation(
            contactToDelete = curDetail.origContact,
            onCancelDelete = this@DefaultContactsActivityViewModel::dismissCurDialog,
            onDeleteConfirm = ::onDeleteConfirm
        )
        mutUiState.value = curState.copy(dialog = newDialog)
    }

    override fun detailsExitClick() = launchWithEventLock {
        val curState = mutUiState.value

        if (curState.detailsState == null) {
            return@launchWithEventLock
        }

        when (curState.detailsState.mode) {
            Creating -> {
                if (curState.detailsState.isModified) {
                    mutUiState.value = curState.copy(
                        dialog = DiscardChanges(
                            onDiscardChanges = this@DefaultContactsActivityViewModel::detailsDiscardChanges,
                            onKeepChanges = this@DefaultContactsActivityViewModel::detailsContinueEditing
                        )
                    )
                } else {
                    mutUiState.value = curState.copy(detailsState = null)
                }
            }

            Editing -> {
                if (curState.detailsState.isModified) {
                    mutUiState.value = curState.copy(
                        dialog = DiscardChanges(
                            onDiscardChanges = this@DefaultContactsActivityViewModel::detailsDiscardChanges,
                            onKeepChanges = this@DefaultContactsActivityViewModel::detailsContinueEditing
                        )
                    )
                } else {
                    mutUiState.value = curState.copy(
                        detailsState = curState.detailsState.copy(mode = Viewing),
                    )
                }
            }

            LocallyDeleted,
            Viewing -> {
                mutUiState.value = curState.copy(detailsState = null)
            }
        }
    }

    override fun onDetailsUpdated(newContact: Contact) = launchWithEventLock {
        val curState = mutUiState.value
        if (curState.detailsState == null)
            return@launchWithEventLock

        mutUiState.value = curState.copy(
            detailsState = curState.detailsState.copy(
                firstNameVm = newContact.createFirstNameVm(),
                lastNameVm = newContact.createLastNameVm(),
                titleVm = newContact.createTitleVm()
            )
        )
    }

    override fun detailsSaveClick() = launchWithEventLock {
        val curState = mutUiState.value
        val curDetail = curState.detailsState ?: return@launchWithEventLock

        if (curDetail.hasFieldsInErrorState) {
            mutUiState.value = curState.copy(
                detailsState = curDetail.copy(
                    fieldToScrollTo = curDetail.fieldsInErrorState.firstOrNull()
                )
            )
            return@launchWithEventLock
        }

        val newDetailsState = if (!curDetail.isModified) {
            when (curDetail.mode) {
                Creating -> {
                    // TODO Maybe add a Toast or something to inform the user why nothing happened
                    /* no-op */
                    curDetail
                }

                LocallyDeleted -> TODO("$TAG - detailsSaveClick(): Contact is locally deleted")

                Viewing -> {
                    Log.w(
                        TAG,
                        "Got a saveClick event while in Viewing mode. This shouldn't happen and is probably a bug."
                    )
                    curDetail
                }

                Editing -> {
                    curDetail.copy(mode = Viewing)
                }
            }
        } else {
            when (curDetail.mode) {
                Creating,
                Editing -> {
                    launchSave(curDetail.updatedContact)
                    curDetail.copy(isSaving = true)
                }
                LocallyDeleted -> TODO("$TAG - detailsSaveClick(): Contact is locally deleted")
                Viewing -> {
                    Log.w(
                        TAG,
                        "Got a saveClick event while in Viewing mode. This shouldn't happen and is probably a bug."
                    )
                    curDetail
                }
            }
        }

        mutUiState.value = mutUiState.value.copy(detailsState = newDetailsState)
    }

    override fun detailsUndeleteClick() {
        TODO("Not yet implemented")
    }

    private fun launchSave(updatedContact: Contact) = viewModelScope.launch {
        // Be careful to do the contact save _outside_ the event lock to keep things responsive
        val saveResult = contactsRepo.locallyUpsertContact(updatedContact)

        suspend fun onSaveSuccess(updatedContact: Contact) = eventMutex.withLock {
            mutUiState.value = mutUiState.value.copy(
                detailsState = updatedContact.toContactDetailsUiState(
                    mode = Viewing,
                    isSaving = false
                )
            )
        }

        when (saveResult) {
            is SealedFailure -> TODO("$TAG - doEditingSave got save failure: ${saveResult.cause}")
            is SealedSuccess -> onSaveSuccess(saveResult.value)
        }
    }

    private fun launchDelete(contactToDelete: Contact) = viewModelScope.launch {
        val deleteResult = contactsRepo.locallyDeleteContact(contactToDelete)

        fun onDeleteSuccess(deletedContact: Contact?, curState: ContactsActivityUiState) {
            if (curState.detailsState != null) {
                mutUiState.value = curState.copy(
                    detailsState = deletedContact?.toContactDetailsUiState(
                        mode = LocallyDeleted,
                        isSaving = false
                    )
                )
            }
        }

        eventMutex.withLock {
            when (deleteResult) {
                is SealedFailure -> TODO()
                is SealedSuccess -> onDeleteSuccess(deleteResult.value, curState = mutUiState.value)
            }
        }
    }

    override fun detailsEditClick() = launchWithEventLock {
        val curUiState = mutUiState.value
        val newDetails = curUiState.detailsState?.let { curDetails ->
            when (curDetails.mode) {
                Creating -> {
                    Log.w(
                        TAG,
                        "Got a detailsEditClick while in Creating mode. This shouldn't happen and is probably a bug."
                    )
                    curDetails
                }
                Editing -> {
                    Log.w(
                        TAG,
                        "Got a detailsEditClick() when in Edit mode. This shouldn't happen and is probably a bug."
                    )
                    curDetails
                }
                LocallyDeleted -> TODO("$TAG - detailsEditClick(): Contact is locally deleted")
                Viewing -> curDetails.copy(mode = Editing)
            }
        }

        mutUiState.value = curUiState.copy(detailsState = newDetails)
    }

    private fun dismissCurDialog() = launchWithEventLock {
        mutUiState.value = mutUiState.value.copy(dialog = null)
    }

    private fun launchWithEventLock(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch { eventMutex.withLock { block() } }
    }

    private companion object {
        private const val TAG = "DefaultContactsActivityViewModel"
    }
}
