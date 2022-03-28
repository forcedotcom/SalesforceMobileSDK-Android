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
import com.salesforce.samples.mobilesynccompose.core.repos.RepoOperationException
import com.salesforce.samples.mobilesynccompose.core.repos.RepoSyncException
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectSyncableRepo
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectsByIds
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectId
import com.salesforce.samples.mobilesynccompose.core.ui.state.DeleteConfirmationDialogUiState
import com.salesforce.samples.mobilesynccompose.core.ui.state.DiscardChangesDialogUiState
import com.salesforce.samples.mobilesynccompose.core.ui.state.UndeleteConfirmationDialogUiState
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ContactsActivityViewModel : ContactObjectFieldChangeHandler {
    val uiState: StateFlow<ContactsActivityUiState>

    fun listContactClick(contactId: SObjectId)
    fun listCreateClick()

    fun detailsDeleteClick()
    fun listDeleteClick(contactId: SObjectId)

    fun detailsUndeleteClick()
    fun listUndeleteClick(contactId: SObjectId)

    fun detailsEditClick()
    fun listEditClick(contactId: SObjectId)

    fun listSearchClick()
    fun listExitSearchClick()
    fun onSearchTermUpdated(newSearchTerm: String)

    fun detailsExitClick()
    fun detailsSaveClick()

    fun sync(syncDownOnly: Boolean = false)
}

class DefaultContactsActivityViewModel(
    private val contactsRepo: SObjectSyncableRepo<ContactObject>
) : ViewModel(), ContactsActivityViewModel {

    // region Class Properties Definitions


    /**
     * All methods that interact with [uiState] or the [curContactsByLocalId] must run under this mutex
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
    private var curContactsByPrimaryKey = mapOf<String, ContactObject>()

    @Volatile
    private var curContactsByLocalId = mapOf<String, ContactObject>()

    init {
        viewModelScope.launch {
            contactsRepo.curSObjects.collect {
                onContactListUpdate(it)
            }
        }
    }


    // endregion
    // region Data Event Handling


    private fun onContactListUpdate(newObjects: SObjectsByIds<ContactObject>) =
        launchWithEventLock {
            // Shallow copy b/c we can't guarantee the provided newList object is immutable:
            val curState = uiState.value
            val curListState = curState.listState
            val curDetail = curState.detailsState

            // Parallelize the iteration operations over the list b/c it may be very large:
            val filteredContactsDeferred = async(Dispatchers.Default) {
                curState.listState.searchTerm?.let { searchTerm ->
                    newObjects.byPrimaryKey.values.filter {
                        it.fullName?.contains(
                            searchTerm,
                            ignoreCase = true
                        ) == true
                    }
                } ?: newObjects.byPrimaryKey.values.toList()
            }

            curContactsByPrimaryKey = newObjects.byPrimaryKey
            curContactsByLocalId = newObjects.byLocalId
//        curContactsBySoupId.clear()

//            withContext(Dispatchers.Default) {
//                safeNewList.forEach {
//                    curContactsByPrimaryKey[it.id.primaryKey] = it
//                    if (it.id.localId != null) {
//                        curContactsByLocalId[it.id.localId] = it
//                    }
//                }
//            safeNewList.forEach { contact ->
//                contact.accept {
//                    ifInMemoryOnly { so -> curContactsByLocalId[so.id] = so }
//
//                    ifSaved { so, soupId ->
//                        curContactsByLocalId[so.id] = so
//                        curContactsBySoupId[soupId] = so
//                    }
//                }
//            }
//            }

            if (curDetail == null || curDetail.mode != Viewing) {
                mutUiState.value = mutUiState.value.copy(
                    listState = curListState.copy(contacts = filteredContactsDeferred.await())
                )
                return@launchWithEventLock
            }

            // Check for matching contact in Viewing mode to update the properties of the contact the user is viewing.
            val matchingContact: ContactObject? =
                curContactsByLocalId[curDetail.contactObj.id.localId]
                    ?: curContactsByPrimaryKey[curDetail.contactObj.id.primaryKey]

            mutUiState.value = curState.copy(
                listState = curListState.copy(contacts = filteredContactsDeferred.await()),
                detailsState = matchingContact?.toContactDetailsUiState(
                    mode = Viewing,
                    fieldValueChangeHandler = this@DefaultContactsActivityViewModel
                ) ?: curDetail
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
        try {
            contactsRepo.syncDownOnly()
        } catch (ex: RepoSyncException) {
            TODO(ex.message ?: ex.toString())
        }

        eventMutex.withLock {
            mutUiState.value = mutUiState.value.copy(isSyncing = false)
        }
    }

    private suspend fun syncUpAndDown() {
        try {
            contactsRepo.syncUpAndDown()
        } catch (ex: RepoSyncException) {
            TODO(ex.message ?: ex.toString())
        }

        eventMutex.withLock {
            mutUiState.value = mutUiState.value.copy(isSyncing = false)
        }
    }


    // endregion
    // region List UI Interaction Handling


    override fun listContactClick(contactId: SObjectId) = launchWithEventLock {
        val contact = curContactsByLocalId[contactId.localId]
            ?: curContactsByPrimaryKey[contactId.primaryKey]
            ?: return@launchWithEventLock

        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.contactObj.curPropertiesAreModifiedFromOriginal) {
            // editing contact, so ask to discard changes
            val newDialog = mutUiState.value.dialogUiState ?: DiscardChangesDialogUiState(
                onDiscardChanges = ::onDetailsDiscardChangesFromDialog,
                onKeepChanges = ::dismissCurDialog
            )
            mutUiState.value = mutUiState.value.copy(dialogUiState = newDialog)
        } else {
            mutUiState.value = curState.copy(
                detailsState = contact.toContactDetailsUiState(
                    mode = Viewing,
                    fieldValueChangeHandler = this@DefaultContactsActivityViewModel
                ),
            )
        }
    }

    override fun listCreateClick() = launchWithEventLock {
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.contactObj.curPropertiesAreModifiedFromOriginal) {
            // editing contact, so ask to discard changes if no current dialog is present:
            val newDialog = mutUiState.value.dialogUiState ?: DiscardChangesDialogUiState(
                onDiscardChanges = ::onDetailsDiscardChangesFromDialog,
                onKeepChanges = ::dismissCurDialog
            )
            mutUiState.value = mutUiState.value.copy(dialogUiState = newDialog)
        } else {
            mutUiState.value = curState.copy(
                detailsState = ContactObject.createNewLocal()
                    .toContactDetailsUiState(
                        mode = Creating,
                        fieldValueChangeHandler = this@DefaultContactsActivityViewModel
                    ),
            )
        }
    }


    // endregion
    // region Delete Handling


    override fun detailsDeleteClick() = launchWithEventLock {
        val curState = mutUiState.value
        val curDetail = curState.detailsState ?: return@launchWithEventLock

        fun onDeleteConfirm(contactIdToDelete: SObjectId) = launchWithEventLock {
            val futureState = mutUiState.value
            val newDetails = futureState.detailsState?.copy(isSaving = true)

            mutUiState.value = futureState.copy(detailsState = newDetails, dialogUiState = null)
            launchDelete(contactIdToDelete)
        }

        val newDialog = curState.dialogUiState ?: DeleteConfirmationDialogUiState(
            objIdToDelete = curDetail.contactObj.id,
            objName = curDetail.contactObj.fullName,
            onCancelDelete = ::dismissCurDialog,
            onDeleteConfirm = ::onDeleteConfirm
        )
        mutUiState.value = curState.copy(dialogUiState = newDialog)
    }

    override fun listDeleteClick(contactId: SObjectId) {
        viewModelScope.launch {
            val contact = curContactsByLocalId[contactId.localId]
                ?: curContactsByPrimaryKey[contactId.primaryKey]

            val curState = mutUiState.value

            fun onDeleteConfirm(contactIdToDelete: SObjectId) = launchWithEventLock {
                val futureState = mutUiState.value
                val newListState = futureState.listState.copy(isSaving = true)

                mutUiState.value = futureState.copy(listState = newListState, dialogUiState = null)
                launchDelete(contactIdToDelete)
            }

//            suspend fun onDeleteSuccess(deletedContact: ContactObject) = eventMutex.withLock {
//                val futureState = mutUiState.value
//                val newDetails = futureState.detailsState?.let {
//                    if (it.contactObj.serverId == deletedContact.serverId)
//                        deletedContact.toContactDetailsUiState(mode = Viewing, isSaving = false)
//                    else
//                        null
//                }
//
//                mutUiState.value = futureState.copy(
//                    detailsState = newDetails,
//                    listState = futureState.listState.copy(isSaving = false)
//                )
//            }

            // If there is currently a dialog showing, do not clobber it.
            val newDialog = curState.dialogUiState ?: DeleteConfirmationDialogUiState(
                objIdToDelete = contactId,
                objName = contact?.fullName,
                onCancelDelete = ::dismissCurDialog,
                onDeleteConfirm = ::onDeleteConfirm
            )

            mutUiState.value = curState.copy(dialogUiState = newDialog)
        }
    }

    private fun launchDelete(contactIdToDelete: SObjectId) = viewModelScope.launch {
        try {
            val deletedContact = contactsRepo.locallyDelete(contactIdToDelete)
            val curState = mutUiState.value
            if (curState.detailsState != null) {
                mutUiState.value = curState.copy(
                    detailsState = deletedContact?.toContactDetailsUiState(
                        mode = Viewing,
                        fieldValueChangeHandler = this@DefaultContactsActivityViewModel,
                        isSaving = false
                    )
                )
            }
        } catch (ex: RepoOperationException) {
            val toastMessage = when (ex) {
                is RepoOperationException.InvalidResultObject -> TODO()
                is RepoOperationException.ObjectNotFound -> TODO()
                is RepoOperationException.SmartStoreOperationFailed -> TODO()
            }
        }
    }


    // endregion
    // region Undelete Handling


    override fun listUndeleteClick(contactId: SObjectId) = launchWithEventLock {
        val contact = curContactsByLocalId[contactId.localId]
            ?: curContactsByPrimaryKey[contactId.primaryKey]

        fun onUndeleteConfirm(contactIdToUndelete: SObjectId) = launchWithEventLock {
            val futureState = mutUiState.value
            val newListState = futureState.listState.copy(isSaving = true)

            mutUiState.value = futureState.copy(listState = newListState, dialogUiState = null)
            launchUndelete(contactIdToUndelete)
        }

        val curState = mutUiState.value

        // If there is currently a dialog showing, do not clobber it.
        val newDialog = curState.dialogUiState ?: UndeleteConfirmationDialogUiState(
            objIdToUndelete = contactId,
            objName = contact?.fullName,
            onCancelUndelete = ::dismissCurDialog,
            onUndeleteConfirm = ::onUndeleteConfirm
        )

        mutUiState.value = curState.copy(dialogUiState = newDialog)
    }

    override fun detailsUndeleteClick() = launchWithEventLock {
        val curDetail = mutUiState.value.detailsState ?: return@launchWithEventLock

        fun onUndeleteConfirm(contactIdToUndelete: SObjectId) = launchWithEventLock {
            val futureState = mutUiState.value

            val newDetails = futureState.detailsState?.copy(isSaving = true)

            mutUiState.value = futureState.copy(detailsState = newDetails, dialogUiState = null)
            launchUndelete(contactIdToUndelete)
        }

        // If there is currently a dialog showing, do not clobber it.
        val newDialog = mutUiState.value.dialogUiState ?: UndeleteConfirmationDialogUiState(
            objIdToUndelete = curDetail.contactObj.id,
            objName = curDetail.contactObj.fullName,
            onCancelUndelete = ::dismissCurDialog,
            onUndeleteConfirm = ::onUndeleteConfirm
        )

        mutUiState.value = mutUiState.value.copy(dialogUiState = newDialog)
    }

    private fun launchUndelete(contactIdToUndelete: SObjectId) = viewModelScope.launch {
        suspend fun onUndeleteSuccess(undeletedContact: ContactObject) = eventMutex.withLock {
            val curState = mutUiState.value
            val newDetail =
                if (curState.detailsState == null) null
                else undeletedContact.toContactDetailsUiState(
                    mode = Viewing,
                    fieldValueChangeHandler = this@DefaultContactsActivityViewModel
                )

            mutUiState.value = curState.copy(detailsState = newDetail)
        }

        try {
            onUndeleteSuccess(contactsRepo.locallyUndelete(contactIdToUndelete))
        } catch (ex: RepoOperationException) {
            TODO(ex.message ?: ex.toString())
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

    override fun listEditClick(contactId: SObjectId) = launchWithEventLock {
        val contact = curContactsByLocalId[contactId.localId]
            ?: curContactsByPrimaryKey[contactId.primaryKey]
            ?: return@launchWithEventLock

        // TODO check for if it is locally deleted, and only go to Editing mode when it is not deleted
        val curState = mutUiState.value

        if (curState.detailsState == null) {
            mutUiState.value = curState.copy(
                detailsState = contact.toContactDetailsUiState(
                    mode = Editing,
                    fieldValueChangeHandler = this@DefaultContactsActivityViewModel
                ),
            )
            return@launchWithEventLock
        }

        val curDetailContact = curState.detailsState.contactObj.id.let {
            curContactsByLocalId[it.localId] ?: curContactsByPrimaryKey[it.primaryKey]
        }

        if (curState.detailsState.contactObj != curDetailContact) {
            // editing contact, so ask to discard changes
            val newDialog = mutUiState.value.dialogUiState ?: DiscardChangesDialogUiState(
                onDiscardChanges = ::onDetailsDiscardChangesFromDialog,
                onKeepChanges = ::dismissCurDialog
            )
            mutUiState.value = mutUiState.value.copy(dialogUiState = newDialog)
        } else {
            mutUiState.value = curState.copy(
                detailsState = contact.toContactDetailsUiState(
                    mode = Editing,
                    fieldValueChangeHandler = this@DefaultContactsActivityViewModel
                ),
            )
        }
    }


    // endregion
    // region Details Discard Changes Handling


    private fun onDetailsDiscardChangesFromDialog() = launchWithEventLock {
        val curDetails = mutUiState.value.detailsState
        val newDetails =
            if (curDetails != null && curDetails.mode != Creating) {
                val originalContact = curDetails.contactObj.let { contact ->
                    curContactsByLocalId[contact.id.localId]
                        ?: curContactsByPrimaryKey[contact.id.primaryKey]
                }
                originalContact?.toContactDetailsUiState(
                    mode = Viewing,
                    fieldValueChangeHandler = this@DefaultContactsActivityViewModel
                )
            } else {
                null
            }

        mutUiState.value = mutUiState.value.copy(detailsState = newDetails, dialogUiState = null)
    }


    // endregion
    // region List Search Handling


    override fun listExitSearchClick() = launchWithEventLock {
        mutUiState.value = mutUiState.value.copy(
            listState = ContactsActivityListUiState(
                contacts = curContactsByPrimaryKey.values.toList(),
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
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            val filteredContacts = curContactsByPrimaryKey.values.filter {
                ensureActive() // Cooperative cancellation within filter loop
                it.fullName?.contains(newSearchTerm, ignoreCase = true) == true
            }

            eventMutex.withLock {
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

        val curDetailsContact = curState.detailsState.contactObj
        val matchingContact = curContactsByLocalId[curDetailsContact.id.localId]
            ?: curContactsByPrimaryKey[curDetailsContact.id.primaryKey]

        when (curState.detailsState.mode) {
            Creating -> {
                if (curDetailsContact != matchingContact) {
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
                if (curDetailsContact != matchingContact) {
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

    override fun onFirstNameChange(id: SObjectId, newFirstName: String?) = launchWithEventLock {
        val curState = mutUiState.value
        if (curState.detailsState == null)
            return@launchWithEventLock

        val contact = curContactsByLocalId[id.localId]
            ?: curContactsByPrimaryKey[id.primaryKey]
            ?: return@launchWithEventLock

        mutUiState.value = curState.copy(
            detailsState = curState.detailsState.copy(
                contactObj = contact.copy(firstName = newFirstName)
            )
        )
    }

    override fun onLastNameChange(id: SObjectId, newLastName: String?) = launchWithEventLock {
        val curState = mutUiState.value
        if (curState.detailsState == null)
            return@launchWithEventLock

        val contact = curContactsByLocalId[id.localId]
            ?: curContactsByPrimaryKey[id.primaryKey]
            ?: return@launchWithEventLock

        mutUiState.value = curState.copy(
            detailsState = curState.detailsState.copy(
                contactObj = contact.copy(lastName = newLastName)
            )
        )
    }

    override fun onTitleChange(id: SObjectId, newTitle: String?) = launchWithEventLock {
        val curState = mutUiState.value
        if (curState.detailsState == null)
            return@launchWithEventLock

        val contact = curContactsByLocalId[id.localId]
            ?: curContactsByPrimaryKey[id.primaryKey]
            ?: return@launchWithEventLock

        mutUiState.value = curState.copy(
            detailsState = curState.detailsState.copy(
                contactObj = contact.copy(title = newTitle)
            )
        )
    }

    override fun onDepartmentChange(id: SObjectId, newDepartment: String?) = launchWithEventLock {
        val curState = mutUiState.value
        if (curState.detailsState == null)
            return@launchWithEventLock

        val contact = curContactsByLocalId[id.localId]
            ?: curContactsByPrimaryKey[id.primaryKey]
            ?: return@launchWithEventLock

        mutUiState.value = curState.copy(
            detailsState = curState.detailsState.copy(
                contactObj = contact.copy(department = newDepartment)
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
                detailsState = curDetail.copy(shouldScrollToErrorField = true)
            )
            return@launchWithEventLock
        }

        val newDetailsState = if (!curDetail.contactObj.curPropertiesAreModifiedFromOriginal) {
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
                    launchSave(curDetail.contactObj)
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

    private fun launchSave(updatedContact: ContactObject) = viewModelScope.launch {
        suspend fun onSaveSuccess(updatedContact: ContactObject) = eventMutex.withLock {
            mutUiState.value = mutUiState.value.copy(
                detailsState = updatedContact.toContactDetailsUiState(
                    mode = Viewing,
                    fieldValueChangeHandler = this@DefaultContactsActivityViewModel,
                    isSaving = false
                )
            )
        }

        // Be careful to do the contact save _outside_ the event lock to keep things responsive
        try {
            onSaveSuccess(contactsRepo.locallyUpsert(updatedContact))
        } catch (ex: RepoOperationException) {
            TODO(ex.message ?: ex.toString())
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
