/*
 * Copyright (c) 2022-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.samples.mobilesynccompose.contacts.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.contacts.state.*
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactDetailsUiMode.*
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

    fun listContactClick(contactId: String)
    fun listCreateClick()

    fun detailsDeleteClick()
    fun listDeleteClick(contactId: String)

    fun detailsUndeleteClick()
    fun listUndeleteClick(contactId: String)

    fun detailsEditClick()
    fun listEditClick(contactId: String)

    fun listSearchClick()
    fun listExitSearchClick()
    fun onSearchTermUpdated(newSearchTerm: String)

    fun detailsExitClick()
    fun detailsSaveClick()
    fun onDetailsUpdated(newContact: Contact)

    fun sync(syncDownOnly: Boolean = false)
}

class DefaultContactsActivityViewModel(
    private val contactsRepo: ContactsRepo
) : ViewModel(), ContactsActivityViewModel {

    // region Class Properties Definitions


    /**
     * All methods that interact with [uiState] or the [curContactsMap] must run under this mutex
     * lock to ensure deterministic behavior. A reminder: Coroutine [Mutex] are _not_ reentrant, so
     * be careful about how you acquire the lock to ensure no deadlocks arise.
     */
    private val eventMutex = Mutex()
    private val mutUiState = MutableStateFlow(
        ContactsActivityUiState(
            listState = ContactsActivityListUiState(contacts = emptyList(), searchTerm = null),
            detailsState = null,
            isSyncing = false,
            dialogUiState = null,
        )
    )
    override val uiState: StateFlow<ContactsActivityUiState> get() = mutUiState

    /**
     * A local copy of the upstream [Contact]s mapped to their IDs, allowing simplified logic for
     * accessing a particular [Contact] by its ID. The only caveat is that it must only be accessed
     * under the lock of the [eventMutex] to keep things deterministic.
     */
    @Volatile
    private var curContactsMap = mapOf<String, Contact>()

    init {
        viewModelScope.launch {
            contactsRepo.curContactList.collect {
                onContactListUpdate(it)
            }
        }
    }


    // endregion
    // region Data Event Handling


    private fun onContactListUpdate(newList: List<Contact>) = launchWithEventLock {
        // Shallow copy b/c we can't guarantee the provided newList object is immutable:
        val safeNewList = ArrayList(newList)
        val curState = uiState.value
        val curListState = curState.listState
        val curDetail = curState.detailsState

        // Parallelize the iteration operations over the list b/c it may be very large:
        val filteredContactsDeferred = async(Dispatchers.Default) {
            curState.listState.searchTerm?.let { searchTerm ->
                safeNewList.filter { it.fullName.contains(searchTerm, ignoreCase = true) }
            } ?: safeNewList
        }

        curContactsMap = withContext(Dispatchers.Default) {
            safeNewList.associateBy { it.id }
        }

        if (curDetail == null || curDetail.mode != Viewing) {
            mutUiState.value = mutUiState.value.copy(
                listState = curListState.copy(contacts = filteredContactsDeferred.await())
            )
            return@launchWithEventLock
        }

        // Check for matching upstream contact in Viewing mode to update the properties of the contact the user is viewing.
        val matchingContact = curContactsMap[curDetail.origContact.id]

        mutUiState.value = curState.copy(
            listState = curListState.copy(contacts = filteredContactsDeferred.await()),
            detailsState = matchingContact?.toContactDetailsUiState(mode = Viewing) ?: curDetail
        )
    }

    override fun sync(syncDownOnly: Boolean) {
        viewModelScope.launch {
            eventMutex.withLock {
                mutUiState.value = mutUiState.value.copy(isSyncing = true)
            }

            if (syncDownOnly) syncDownOnly() else syncUpAndDown()
        }
    }

    private suspend fun syncDownOnly() {
        val syncResults = contactsRepo.syncDownOnly()
        if (syncResults is SealedFailure) {
            TODO("$TAG - syncDownOnly(): Got failed sync down result: $syncResults")
        }

        eventMutex.withLock {
            mutUiState.value = mutUiState.value.copy(isSyncing = false)
        }
    }

    private suspend fun syncUpAndDown() {
        val syncResults = contactsRepo.syncUpAndDown()
        if (syncResults.syncDownResult is SealedFailure || syncResults.syncUpResult is SealedFailure) {
            TODO("$TAG - syncUpAndDown(): Got failed sync result: $syncResults")
        }

        eventMutex.withLock {
            mutUiState.value = mutUiState.value.copy(isSyncing = false)
        }
    }


    // endregion
    // region List UI Interaction Handling


    override fun listContactClick(contactId: String) = launchWithEventLock {
        val contact = curContactsMap[contactId] ?: return@launchWithEventLock

        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            val newDialog = mutUiState.value.dialogUiState ?: DiscardChangesDialogUiState(
                onDiscardChanges = ::onDetailsDiscardChangesFromDialog,
                onKeepChanges = ::dismissCurDialog
            )
            mutUiState.value = mutUiState.value.copy(dialogUiState = newDialog)
        } else {
            mutUiState.value = curState.copy(
                detailsState = contact.toContactDetailsUiState(mode = Viewing),
            )
        }
    }

    override fun listCreateClick() = launchWithEventLock {
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes if no current dialog is present:
            val newDialog = mutUiState.value.dialogUiState ?: DiscardChangesDialogUiState(
                onDiscardChanges = ::onDetailsDiscardChangesFromDialog,
                onKeepChanges = ::dismissCurDialog
            )
            mutUiState.value = mutUiState.value.copy(dialogUiState = newDialog)
        } else {
            mutUiState.value = curState.copy(
                detailsState = Contact.createNewLocal().toContactDetailsUiState(mode = Creating),
            )
        }
    }


    // endregion
    // region Delete Handling


    override fun detailsDeleteClick() = launchWithEventLock {
        val curState = mutUiState.value
        val curDetail = curState.detailsState ?: return@launchWithEventLock

        fun onDeleteConfirm(contactIdToDelete: String) = launchWithEventLock {
            val futureState = mutUiState.value
            val newDetails = futureState.detailsState?.copy(isSaving = true)

            mutUiState.value = futureState.copy(detailsState = newDetails, dialogUiState = null)
            launchDelete(contactIdToDelete)
        }

        val newDialog = curState.dialogUiState ?: DeleteConfirmationDialogUiState(
            contactIdToDelete = curDetail.contactId,
            contactName = curDetail.updatedContact.fullName,
            onCancelDelete = ::dismissCurDialog,
            onDeleteConfirm = ::onDeleteConfirm
        )
        mutUiState.value = curState.copy(dialogUiState = newDialog)
    }

    override fun listDeleteClick(contactId: String) {
        viewModelScope.launch {
            val contact = curContactsMap[contactId]
            val curState = mutUiState.value

            fun onDeleteConfirm(contactIdToDelete: String) = launchWithEventLock {
                val futureState = mutUiState.value
                val newListState = futureState.listState.copy(isSaving = true)

                mutUiState.value = futureState.copy(listState = newListState, dialogUiState = null)
                launchDelete(contactIdToDelete)
            }

            suspend fun onDeleteSuccess(deletedContact: Contact) = eventMutex.withLock {
                val futureState = mutUiState.value
                val newDetails = futureState.detailsState?.let {
                    if (it.contactId == deletedContact.id)
                        deletedContact.toContactDetailsUiState(mode = Viewing, isSaving = false)
                    else
                        null
                }

                mutUiState.value = futureState.copy(
                    detailsState = newDetails,
                    listState = futureState.listState.copy(isSaving = false)
                )
            }

            // If there is currently a dialog showing, do not clobber it.
            val newDialog = curState.dialogUiState ?: DeleteConfirmationDialogUiState(
                contactIdToDelete = contactId,
                contactName = contact?.fullName,
                onCancelDelete = ::dismissCurDialog,
                onDeleteConfirm = ::onDeleteConfirm
            )

            mutUiState.value = curState.copy(dialogUiState = newDialog)
        }
    }

    private fun launchDelete(contactIdToDelete: String) = viewModelScope.launch {
        val deleteResult = contactsRepo.locallyDeleteContact(contactIdToDelete)

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


    // endregion
    // region Undelete Handling


    override fun listUndeleteClick(contactId: String) = launchWithEventLock {
        val contact = curContactsMap[contactId]

        fun onUndeleteConfirm(contactIdToUndelete: String) = launchWithEventLock {
            val futureState = mutUiState.value
            val newListState = futureState.listState.copy(isSaving = true)

            mutUiState.value = futureState.copy(listState = newListState, dialogUiState = null)
            launchUndelete(contactIdToUndelete)
        }

        val curState = mutUiState.value

        // If there is currently a dialog showing, do not clobber it.
        val newDialog = curState.dialogUiState ?: UndeleteConfirmationDialogUiState(
            contactIdToUndelete = contactId,
            contactName = contact?.fullName,
            onCancelUndelete = ::dismissCurDialog,
            onUndeleteConfirm = ::onUndeleteConfirm
        )

        mutUiState.value = curState.copy(dialogUiState = newDialog)
    }

    override fun detailsUndeleteClick() = launchWithEventLock {
        val curDetail = mutUiState.value.detailsState ?: return@launchWithEventLock

        fun onUndeleteConfirm(contactIdToUndelete: String) = launchWithEventLock {
            val futureState = mutUiState.value

            val newDetails = futureState.detailsState?.copy(isSaving = true)

            mutUiState.value = futureState.copy(detailsState = newDetails, dialogUiState = null)
            launchUndelete(contactIdToUndelete)
        }

        // If there is currently a dialog showing, do not clobber it.
        val newDialog = mutUiState.value.dialogUiState ?: UndeleteConfirmationDialogUiState(
            contactIdToUndelete = curDetail.contactId,
            contactName = curDetail.updatedContact.fullName,
            onCancelUndelete = ::dismissCurDialog,
            onUndeleteConfirm = ::onUndeleteConfirm
        )

        mutUiState.value = mutUiState.value.copy(dialogUiState = newDialog)
    }

    private fun launchUndelete(contactIdToUndelete: String) = viewModelScope.launch {
        suspend fun onUndeleteSuccess(undeletedContact: Contact) = eventMutex.withLock {
            val curState = mutUiState.value
            val newDetail =
                if (curState.detailsState == null) null
                else undeletedContact.toContactDetailsUiState(mode = Viewing)

            mutUiState.value = curState.copy(detailsState = newDetail)
        }

        when (val undeleteResult = contactsRepo.locallyUndeleteContact(contactIdToUndelete)) {
            is SealedFailure -> TODO()
            is SealedSuccess -> onUndeleteSuccess(undeleteResult.value)
        }
    }


    // endregion
    // region Edit Click Handling


    override fun detailsEditClick() = launchWithEventLock {
        val curUiState = mutUiState.value
        val newDetails = curUiState.detailsState?.let { curDetails ->
            when (curDetails.mode) {
                Creating -> {
                    // TODO Is there a compile-time way to guarantee that this cannot be called from the Creating state?
                    Log.w(
                        TAG,
                        "Got a detailsEditClick while in Creating mode. This shouldn't happen and is probably a bug."
                    )
                    curDetails
                }
                Editing -> {
                    // TODO Is there a compile-time way to guarantee that this cannot be called from the Editing state?
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

    override fun listEditClick(contactId: String) = launchWithEventLock {
        val contact = curContactsMap[contactId] ?: return@launchWithEventLock
        // TODO check for if it is locally deleted, and only go to Editing mode when it is not deleted
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            val newDialog = mutUiState.value.dialogUiState ?: DiscardChangesDialogUiState(
                onDiscardChanges = ::onDetailsDiscardChangesFromDialog,
                onKeepChanges = ::dismissCurDialog
            )
            mutUiState.value = mutUiState.value.copy(dialogUiState = newDialog)
        } else {
            mutUiState.value = curState.copy(
                detailsState = contact.toContactDetailsUiState(mode = Editing),
            )
        }
    }


    // endregion
    // region Details Discard Changes Handling


    private fun onDetailsDiscardChangesFromDialog() = launchWithEventLock {
        val curDetails = mutUiState.value.detailsState
        val newDetails =
            if (curDetails != null && curDetails.mode != Creating)
                curContactsMap[curDetails.origContact.id]?.toContactDetailsUiState(mode = Viewing)
            else
                null

        mutUiState.value = mutUiState.value.copy(detailsState = newDetails, dialogUiState = null)
    }


    // endregion
    // region List Search Handling


    override fun listExitSearchClick() = launchWithEventLock {
        mutUiState.value = mutUiState.value.copy(
            listState = ContactsActivityListUiState(
                contacts = curContactsMap.values.toList(),
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
                curContactsMap.values.filter {
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


    // endregion
    // region Details UI Interaction Handling


    override fun detailsExitClick() = launchWithEventLock {
        val curState = mutUiState.value

        if (curState.detailsState == null) {
            return@launchWithEventLock
        }

        when (curState.detailsState.mode) {
            Creating -> {
                if (curState.detailsState.isModified) {
                    mutUiState.value = curState.copy(
                        dialogUiState = DiscardChangesDialogUiState(
                            onDiscardChanges = ::onDetailsDiscardChangesFromDialog,
                            onKeepChanges = ::dismissCurDialog
                        )
                    )
                } else {
                    mutUiState.value = curState.copy(detailsState = null)
                }
            }

            Editing -> {
                if (curState.detailsState.isModified) {
                    mutUiState.value = curState.copy(
                        dialogUiState = DiscardChangesDialogUiState(
                            onDiscardChanges = ::onDetailsDiscardChangesFromDialog,
                            onKeepChanges = ::dismissCurDialog
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


    // endregion
    // region Details Local Save Handling


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
                    // TODO Is there a compile-time way to guarantee that this cannot be called from the Viewing state?
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
                    // TODO Is there a compile-time way to guarantee that this cannot be called from the Viewing state?
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


    // endregion
    // region Utilities


    /**
     * Convenience method to wrap a method body in a coroutine which acquires the event mutex lock
     * before executing the [block]. Because this launches a new coroutine, it is okay to nest
     * invocations of this method within other [launchWithEventLock] blocks without worrying about
     * deadlocks.
     *
     * Note! If you nest [launchWithEventLock] calls, the outer [launchWithEventLock] [block]
     * will run to completion _before_ the nested [block] is invoked since the outer [block] already
     * has the event lock.
     */
    private fun launchWithEventLock(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch { eventMutex.withLock { block() } }
    }

    private fun dismissCurDialog() = launchWithEventLock {
        mutUiState.value = mutUiState.value.copy(dialogUiState = null)
    }

    private companion object {
        private const val TAG = "DefaultContactsActivityViewModel"
    }


    // endregion
}
