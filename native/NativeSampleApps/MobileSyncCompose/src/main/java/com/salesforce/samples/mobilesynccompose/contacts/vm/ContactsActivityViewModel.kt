package com.salesforce.samples.mobilesynccompose.contacts.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailsUiMode.*
import com.salesforce.samples.mobilesynccompose.core.SealedFailure
import com.salesforce.samples.mobilesynccompose.core.SealedSuccess
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

    fun listContactClick(contactId: String)
    fun listCreateClick()
    fun listDeleteClick(contactId: String)
    fun listEditClick(contactId: String)
    fun listUndeleteClick(contactId: String)

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

    private var curContactsMap = mapOf<String, Contact>()

    private fun onContactListUpdate(newList: List<Contact>) = launchWithEventLock {
        val curContactsMapDeferred = async(Dispatchers.Default) { newList.associateBy { it.id } }

        val curState = uiState.value
        val curDetail = curState.detailsState

        val filteredContactsDeferred = async(Dispatchers.Default) {
            curState.listState.searchTerm?.let { searchTerm ->
                newList.filter { it.fullName.contains(searchTerm, ignoreCase = true) }
            } ?: newList
        }

        val newDetail = curDetail?.let {
            val matchingContact = withContext(Dispatchers.Default) {
                newList.firstOrNull { it.id == curDetail.origContact.id }
            }

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
                Editing -> {
                    if (curDetail.isSaving) {
                        curDetail // The result of the save operation will drive details UI change
                    } else {
                        /* If we are _not_ actively saving the edited contact, do merge conflict
                         * resolution: */
                        when (matchingContact) {
                            null -> null
                            curDetail.origContact -> curDetail
                            else -> TODO("$TAG - onContactListUpdate(): Received contact update while editing same contact. Current detail UI state = $curDetail , upstream contact = $matchingContact")
                        }
                    }
                }
                Viewing -> {
                    /* If we are currently viewing a contact and an updated version comes in
                     * from the upstream, update the detail fields; otherwise, the details state
                     * remains unchanged. */
                    matchingContact?.toContactDetailsUiState(mode = Viewing)
                }
            }
        }

        val newListState = curState.listState.copy(
            contacts = filteredContactsDeferred.await(),
            isSaving = false
        )

        mutUiState.value = curState.copy(
            listState = newListState,
            isSyncing = false,
            detailsState = newDetail
        )

        curContactsMap = curContactsMapDeferred.await()
    }

    override fun sync(syncDownOnly: Boolean) = launchWithEventLock {
        mutUiState.value = mutUiState.value.copy(isSyncing = true)
        contactsRepo.sync(syncDownOnly)
    }

    override fun listContactClick(contactId: String) = launchWithEventLock {
        val contact = curContactsMap[contactId] ?: return@launchWithEventLock

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

    override fun listDeleteClick(contactId: String) = launchWithEventLock {
        val contact = curContactsMap[contactId] ?: return@launchWithEventLock

        val curState = mutUiState.value

        fun onDeleteConfirm(contactIdToDelete: String) {
            dismissCurDialog()
            val contactToDelete = curContactsMap[contactIdToDelete] ?: return

            launchDelete(contactToDelete)
            mutUiState.value = mutUiState.value.copy(
                listState = mutUiState.value.listState.copy(isSaving = true)
            )
        }

        // If there is currently a dialog showing, do not clobber it.
        val newDialog = curState.dialog ?: DeleteConfirmation(
            contactToDelete = contact,
            onCancelDelete = { launchWithEventLock { dismissCurDialog() } },
            onDeleteConfirm = ::onDeleteConfirm
        )

        mutUiState.value = curState.copy(dialog = newDialog)
    }

    override fun listUndeleteClick(contactId: String) = launchWithEventLock {
        val contact = curContactsMap[contactId] ?: return@launchWithEventLock
        if (!contact.locallyDeleted) return@launchWithEventLock

        fun onUndeleteConfirm(contactIdToUndelete: String) = launchWithEventLock inner@{
            dismissCurDialog()
            val futureContact = curContactsMap[contactId] ?: return@inner

            launchUndelete(futureContact)
            mutUiState.value = mutUiState.value.copy(
                listState = mutUiState.value.listState.copy(isSaving = true)
            )
        }

        val curState = mutUiState.value
        val newDialog = curState.dialog ?: UndeleteConfirmation(
            contactToUndelete = contact,
            onCancelUndelete = { launchWithEventLock { dismissCurDialog() } },
            onUndeleteConfirm = ::onUndeleteConfirm
        )

        mutUiState.value = curState.copy(dialog = newDialog)
    }

    override fun listEditClick(contactId: String) = launchWithEventLock {
        val contact = curContactsMap[contactId] ?: return@launchWithEventLock
        // TODO check for if it is locally deleted, and only go to Editing mode when it is not deleted
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            mutUiState.value = mutUiState.value.copy(
                dialog = DiscardChanges(
                    onDiscardChanges = ::detailsDiscardChanges,
                    onKeepChanges = ::detailsContinueEditing
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
            val filteredContacts = withContext(Dispatchers.Default) {
                contactsRepo.curUpstreamContacts.filter {
                    ensureActive() // Cooperative cancellation within filter loop
                    it.fullName.contains(newSearchTerm, ignoreCase = true)
                }
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

        fun onDeleteConfirm(contactIdToDelete: String) {
            dismissCurDialog()
            val contactToDelete = curContactsMap[contactIdToDelete] ?: return

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
            onCancelDelete = { launchWithEventLock { dismissCurDialog() } },
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

    override fun detailsUndeleteClick() = launchWithEventLock {
        val curDetail = mutUiState.value.detailsState
        if (curDetail == null || !curDetail.origContact.locallyDeleted) return@launchWithEventLock

        fun onUndeleteConfirm(contactIdToDelete: String) = launchWithEventLock inner@{
            dismissCurDialog()
            val contactToDelete = curContactsMap[contactIdToDelete] ?: return@inner

            launchUndelete(contactToDelete)

            val futureDetail = mutUiState.value.detailsState
            mutUiState.value = mutUiState.value.copy(
                detailsState = futureDetail?.copy(isSaving = true)
            )
        }
        mutUiState.value = mutUiState.value.copy(
            dialog = UndeleteConfirmation(
                contactToUndelete = curDetail.origContact,
                onCancelUndelete = { launchWithEventLock { dismissCurDialog() } },
                onUndeleteConfirm = ::onUndeleteConfirm
            )
        )
    }

    private fun launchSave(updatedContact: Contact) = viewModelScope.launch {
        suspend fun onSaveSuccess(updatedContact: Contact) = eventMutex.withLock {
            mutUiState.value = mutUiState.value.copy(
                detailsState = updatedContact.toContactDetailsUiState(
                    mode = Viewing,
                    isSaving = false
                )
            )
        }

        // Be careful to do the contact save _outside_ the event lock to keep things responsive
        when (val saveResult = contactsRepo.locallyUpsertContact(updatedContact)) {
            is SealedFailure -> TODO("$TAG - doEditingSave got save failure: ${saveResult.cause}")
            is SealedSuccess -> onSaveSuccess(saveResult.value)
        }
    }

    private fun launchDelete(contactToDelete: Contact) = viewModelScope.launch {
        val deleteResult = contactsRepo.locallyDeleteContact(contactToDelete)

        suspend fun onDeleteSuccess(deletedContact: Contact?) = eventMutex.withLock {
            val curState = mutUiState.value
            if (curState.detailsState != null) {
                mutUiState.value = curState.copy(
                    detailsState = deletedContact?.toContactDetailsUiState(
                        mode = Viewing,
                        isSaving = false
                    )
                )
            }
        }

        when (deleteResult) {
            is SealedFailure -> TODO()
            is SealedSuccess -> onDeleteSuccess(deleteResult.value)
        }
    }

    private fun launchUndelete(contactToUndelete: Contact) = viewModelScope.launch {
        suspend fun onUndeleteSuccess(undeletedContact: Contact) = eventMutex.withLock {
            val curState = mutUiState.value
            val newDetail =
                if (curState.detailsState == null) null
                else undeletedContact.toContactDetailsUiState(mode = Viewing)

            mutUiState.value = curState.copy(detailsState = newDetail)
        }

        when (val undeleteResult = contactsRepo.locallyUndeleteContact(contactToUndelete)) {
            is SealedFailure -> TODO()
            is SealedSuccess -> onUndeleteSuccess(undeleteResult.value)
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
                Viewing -> curDetails.copy(mode = Editing)
            }
        }

        mutUiState.value = curUiState.copy(detailsState = newDetails)
    }

    /**
     * Only call this from within an event lock!
     */
    private fun dismissCurDialog() {
        mutUiState.value = mutUiState.value.copy(dialog = null)
    }

    private fun launchWithEventLock(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch { eventMutex.withLock { block() } }
    }

    private companion object {
        private const val TAG = "DefaultContactsActivityViewModel"
    }
}
