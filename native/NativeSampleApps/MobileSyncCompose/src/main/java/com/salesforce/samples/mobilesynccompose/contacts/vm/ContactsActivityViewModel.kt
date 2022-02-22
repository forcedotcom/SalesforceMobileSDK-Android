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

    private fun onContactListUpdate(newList: List<Contact>) = withEventLock {
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

    override fun sync(syncDownOnly: Boolean) = withEventLock {
        mutUiState.value = mutUiState.value.copy(isSyncing = true)
        contactsRepo.sync(syncDownOnly)
    }

    override fun listContactClick(contact: Contact) = withEventLock {
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            mutUiState.value = mutUiState.value.copy(showDiscardChanges = true)
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
            mutUiState.value = mutUiState.value.copy(showDiscardChanges = true)
        } else {
            mutUiState.value = curState.copy(
                detailsState = Contact.createNewLocal().toContactDetailsUiState(mode = Creating),
            )
        }
    }

    override fun listDeleteClick(contact: Contact) = withEventLock {

    }

    override fun listUndeleteClick(contact: Contact) = withEventLock {
        TODO("Not yet implemented")
    }

    override fun listEditClick(contact: Contact) = withEventLock {
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            mutUiState.value = mutUiState.value.copy(showDiscardChanges = true)
        } else {
            mutUiState.value = curState.copy(
                detailsState = contact.toContactDetailsUiState(mode = Editing),
            )
        }
    }

    override fun detailsDiscardChanges() = withEventLock {
        val curState = mutUiState.value
        mutUiState.value = if (curState.detailsState?.mode != Creating) {
            curState.copy(
                detailsState = curState.detailsState?.origContact?.toContactDetailsUiState(
                    mode = Viewing,
                ),
                showDiscardChanges = false
            )
        } else {
            curState.copy(detailsState = null, showDiscardChanges = false)
        }
    }

    override fun detailsContinueEditing() = withEventLock {
        mutUiState.value = mutUiState.value.copy(showDiscardChanges = false)
    }

    override fun listExitSearchClick() = withEventLock {
        mutUiState.value = mutUiState.value.copy(
            listState = ContactsActivityListUiState(
                contacts = contactsRepo.curUpstreamContacts,
                searchTerm = null
            )
        )
    }

    override fun listSearchClick() = withEventLock {
        val newListState = mutUiState.value.listState.copy(searchTerm = "")
        mutUiState.value = mutUiState.value.copy(listState = newListState)
    }

    @Volatile
    private var searchJob: Job? = null

    override fun onSearchTermUpdated(newSearchTerm: String) = withEventLock {
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
                    mutUiState.value = curState.copy(showDiscardChanges = true)
                } else {
                    mutUiState.value = curState.copy(detailsState = null)
                }
            }

            Editing -> {
                if (curState.detailsState.isModified) {
                    mutUiState.value = curState.copy(showDiscardChanges = true)
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

    override fun detailsSaveClick() = withEventLock {
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

    private fun launchSave(updatedContact: Contact) {
        viewModelScope.launch {
            // Be careful to do the contact save _outside_ the event lock to keep things responsive
            val saveResult = contactsRepo.locallyUpsertContact(updatedContact)
            eventMutex.withLock {
                when (saveResult) {
                    is SealedFailure -> TODO("$TAG - doEditingSave got save failure: ${saveResult.cause}")
                    is SealedSuccess -> mutUiState.value = mutUiState.value.copy(
                        detailsState = saveResult.value.toContactDetailsUiState(
                            mode = Viewing,
                            isSaving = false
                        )
                    )
                }
            }
        }
    }

    override fun detailsEditClick() = withEventLock {
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

    private fun withEventLock(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch { eventMutex.withLock { block() } }
    }

    private companion object {
        private const val TAG = "DefaultContactsActivityViewModel"
    }
}
