package com.salesforce.samples.mobilesynccompose.contacts.detailscomponent

import com.salesforce.samples.mobilesynccompose.core.extensions.requireIsLocked
import com.salesforce.samples.mobilesynccompose.core.repos.RepoOperationException
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectSyncableRepo
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.LocalStatus
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.core.ui.state.DeleteConfirmationDialogUiState
import com.salesforce.samples.mobilesynccompose.core.ui.state.DiscardChangesDialogUiState
import com.salesforce.samples.mobilesynccompose.core.ui.state.SObjectUiSyncState
import com.salesforce.samples.mobilesynccompose.core.ui.state.UndeleteConfirmationDialogUiState
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactValidationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

interface ContactDetailsViewModel : ContactDetailsFieldChangeHandler, ContactDetailsUiEventHandler {
    val uiState: StateFlow<ContactDetailsUiState>

    @Throws(ContactDetailsException::class)
    suspend fun clearContactObj()

    @Throws(ContactDetailsException::class)
    suspend fun setContact(recordId: String, startWithEditingEnabled: Boolean)
}

class DefaultContactDetailsViewModel(
    private val parentScope: CoroutineScope,
    private val contactsRepo: SObjectSyncableRepo<ContactObject>,
) : ContactDetailsViewModel {

    private val stateMutex = Mutex()
    private val dataOpDelegate = DataOperationDelegate()

    private val mutUiState: MutableStateFlow<ContactDetailsUiState> = MutableStateFlow(
        ContactDetailsUiState.NoContactSelected(
            dataOperationIsActive = false,
            curDialogUiState = null
        )
    )

    override val uiState: StateFlow<ContactDetailsUiState> get() = mutUiState

    @Volatile
    private var curRecordId: String? = null

    @Volatile
    private lateinit var upstreamRecords: Map<String, SObjectRecord<ContactObject>>

    init {
        parentScope.launch(Dispatchers.Default) {
            contactsRepo.recordsById.collect { onNewRecords(it) }
        }
    }

    private suspend fun onNewRecords(
        newRecords: Map<String, SObjectRecord<ContactObject>>
    ) = stateMutex.withLock {
        upstreamRecords = newRecords

        val curId = curRecordId ?: return@withLock
        val matchingRecord = newRecords[curId] ?: return@withLock

        mutUiState.value = when (val curState = uiState.value) {
            is ContactDetailsUiState.NoContactSelected -> {
                matchingRecord.sObject.buildViewingContactUiState(
                    uiSyncState = matchingRecord.localStatus.toUiSyncState(),
                    isEditingEnabled = false,
                    shouldScrollToErrorField = false,
                )
            }

            is ContactDetailsUiState.ViewingContactDetails -> {
                when {
                    // not editing, so simply update all fields to match upstream emission:
                    !curState.isEditingEnabled -> curState.copy(
                        firstNameField = matchingRecord.sObject.buildFirstNameField(),
                        lastNameField = matchingRecord.sObject.buildLastNameField(),
                        titleField = matchingRecord.sObject.buildTitleField(),
                        departmentField = matchingRecord.sObject.buildDepartmentField(),
                    )

                    // Upstream was deleted, but we have unsaved changes. Ask the user what they want to do:
                    // TODO DO NOT DO THIS.  You cannot trigger a dialog from a data event.  There must
                    //  be a different approach...
                    /* Ideas: create a "snapshot" of the SO as soon as they begin editing, and only
                     * upon clicking save: if the upstream record doesn't equal the snapshot, prompt
                     * for choice. */
//                    matchingRecord.localStatus.isLocallyDeleted && hasUnsavedChanges -> curState.copy(
//                        curDialogUiState = DiscardChangesOrUndeleteDialogUiState(
//                            onDiscardChanges = { TODO() },
//                            onUndelete = { TODO() }
//                        )
//                    )

                    // user is editing and there is no incompatible state, so no changes to state:
                    else -> curState
                }
            }
        }
    }

    @Throws(ContactDetailsException::class)
    override suspend fun clearContactObj() = launchWithStateLock {
        if (dataOpDelegate.dataOperationIsActive)
            throw DataOperationActiveException(
                message = "Cannot change details content while there are data operations active."
            )

        if (hasUnsavedChanges)
            throw HasUnsavedChangesException()

        mutUiState.value = ContactDetailsUiState.NoContactSelected(
            dataOperationIsActive = dataOpDelegate.dataOperationIsActive,
            curDialogUiState = uiState.value.curDialogUiState
        )
    }

    @Throws(ContactDetailsException::class)
    override suspend fun setContact(
        recordId: String,
        startWithEditingEnabled: Boolean
    ) = launchWithStateLock {
        if (!this@DefaultContactDetailsViewModel::upstreamRecords.isInitialized
            || dataOpDelegate.dataOperationIsActive
        ) {
            throw DataOperationActiveException(
                message = "Cannot change details content while there are data operations active."
            )
        }

        if (hasUnsavedChanges) {
            throw HasUnsavedChangesException()
        }

        val newRecord = upstreamRecords[recordId] ?: TODO("Did not find record with ID $recordId")

        mutUiState.value = when (val curState = uiState.value) {
            is ContactDetailsUiState.NoContactSelected -> newRecord.sObject.buildViewingContactUiState(
                uiSyncState = newRecord.localStatus.toUiSyncState(),
                isEditingEnabled = startWithEditingEnabled,
                shouldScrollToErrorField = false,
            )
            is ContactDetailsUiState.ViewingContactDetails -> curState.copy(
                firstNameField = newRecord.sObject.buildFirstNameField(),
                lastNameField = newRecord.sObject.buildLastNameField(),
                titleField = newRecord.sObject.buildTitleField(),
                departmentField = newRecord.sObject.buildDepartmentField(),
                isEditingEnabled = startWithEditingEnabled,
                shouldScrollToErrorField = false
            )
        }
    }

    private val hasUnsavedChanges: Boolean
        get() {
            stateMutex.requireIsLocked()
            return when (val curState = uiState.value) {
                is ContactDetailsUiState.ViewingContactDetails -> curRecordId?.let {
                    try {
                        curState.toSObjectOrThrow() == upstreamRecords[it]?.sObject
                    } catch (ex: ContactValidationException) {
                        // invalid field values means there are unsaved changes
                        true
                    }
                } ?: true // creating new contact, so assume changes are present
                is ContactDetailsUiState.NoContactSelected -> false
            }
        }

    override fun createClick() = launchWithStateLock {
        if (hasUnsavedChanges) {
            mutUiState.value = uiState.value.copy(
                curDialogUiState = DiscardChangesDialogUiState(
                    onDiscardChanges = { TODO("createClick() onDiscardChanges") },
                    onKeepChanges = ::dismissCurDialog
                )
            )
            return@launchWithStateLock
        }

        mutUiState.value = ContactDetailsUiState.ViewingContactDetails(
            firstNameField = ContactDetailsField.FirstName(
                fieldValue = null,
                onValueChange = ::onFirstNameChange
            ),
            lastNameField = ContactDetailsField.LastName(
                fieldValue = null,
                onValueChange = ::onLastNameChange
            ),
            titleField = ContactDetailsField.Title(
                fieldValue = null,
                onValueChange = ::onTitleChange
            ),
            departmentField = ContactDetailsField.Department(
                fieldValue = null,
                onValueChange = ::onDepartmentChange
            ),

            uiSyncState = SObjectUiSyncState.NotSaved,

            isEditingEnabled = true,
            dataOperationIsActive = dataOpDelegate.dataOperationIsActive,
            shouldScrollToErrorField = false,
            curDialogUiState = uiState.value.curDialogUiState
        )
    }

    override fun deleteClick() = launchWithStateLock {
        val targetRecordId = curRecordId ?: return@launchWithStateLock // no id => nothing to do

        mutUiState.value = uiState.value.copy(
            curDialogUiState = DeleteConfirmationDialogUiState(
                objIdToDelete = targetRecordId,
                objName = upstreamRecords[targetRecordId]?.sObject?.fullName,
                onCancelDelete = ::dismissCurDialog,
                onDeleteConfirm = {
                    dataOpDelegate.handleDataEvent(event = DetailsDataEvent.Delete(it))
                }
            )
        )
    }

    override fun undeleteClick() = launchWithStateLock {
        val targetRecordId = curRecordId ?: return@launchWithStateLock // no id => nothing to do

        mutUiState.value = uiState.value.copy(
            curDialogUiState = UndeleteConfirmationDialogUiState(
                objIdToUndelete = targetRecordId,
                objName = upstreamRecords[targetRecordId]?.sObject?.fullName,
                onCancelUndelete = ::dismissCurDialog,
                onUndeleteConfirm = {
                    dataOpDelegate.handleDataEvent(event = DetailsDataEvent.Undelete(it))
                },
            )
        )
    }

    private fun dismissCurDialog() = launchWithStateLock {
        mutUiState.value = uiState.value.copy(curDialogUiState = null)
    }

    override fun editClick() = launchWithStateLock {
        mutUiState.value = when (val curState = uiState.value) {
            is ContactDetailsUiState.NoContactSelected -> curState
            is ContactDetailsUiState.ViewingContactDetails -> curState.copy(isEditingEnabled = true)
        }
    }

    override fun exitClick() {
        TODO("Not yet implemented")
    }

    override fun saveClick() = launchWithStateLock {
        // If not viewing details, we cannot build the SObject, so there is nothing to do
        val curState = uiState.value as? ContactDetailsUiState.ViewingContactDetails
            ?: return@launchWithStateLock

        val so = try {
            curState.toSObjectOrThrow()
        } catch (ex: Exception) {
            mutUiState.value = curState.copy(shouldScrollToErrorField = true)
            return@launchWithStateLock
        }

        val eventHandled = dataOpDelegate.handleDataEvent(
            event = curRecordId?.let { DetailsDataEvent.Update(id = it, so = so) }
                ?: DetailsDataEvent.Create(so = so)
        )

        if (!eventHandled) {
            // TODO should there be any prompt to the user for when a data op is already active?
        }
    }

    override fun onFirstNameChange(newFirstName: String) = launchWithStateLock {
        val curState = uiState.value as? ContactDetailsUiState.ViewingContactDetails
            ?: return@launchWithStateLock

        mutUiState.value = curState.copy(
            firstNameField = curState.firstNameField.copy(fieldValue = newFirstName)
        )
    }

    override fun onLastNameChange(newLastName: String) = launchWithStateLock {
        val curState = uiState.value as? ContactDetailsUiState.ViewingContactDetails
            ?: return@launchWithStateLock

        mutUiState.value = curState.copy(
            lastNameField = curState.lastNameField.copy(fieldValue = newLastName)
        )
    }

    override fun onTitleChange(newTitle: String) = launchWithStateLock {
        val curState = uiState.value as? ContactDetailsUiState.ViewingContactDetails
            ?: return@launchWithStateLock

        mutUiState.value = curState.copy(
            titleField = curState.titleField.copy(fieldValue = newTitle)
        )
    }

    override fun onDepartmentChange(newDepartment: String) = launchWithStateLock {
        val curState = uiState.value as? ContactDetailsUiState.ViewingContactDetails
            ?: return@launchWithStateLock

        mutUiState.value = curState.copy(
            departmentField = curState.departmentField.copy(fieldValue = newDepartment)
        )
    }

    private fun ContactDetailsUiState.ViewingContactDetails.toSObjectOrThrow() = ContactObject(
        firstName = firstNameField.fieldValue,
        lastName = lastNameField.fieldValue ?: "",
        title = titleField.fieldValue,
        department = departmentField.fieldValue
    )

    private fun ContactObject.buildViewingContactUiState(
        uiSyncState: SObjectUiSyncState,
        isEditingEnabled: Boolean,
        shouldScrollToErrorField: Boolean,
    ): ContactDetailsUiState.ViewingContactDetails {
        stateMutex.requireIsLocked()

        return ContactDetailsUiState.ViewingContactDetails(
            firstNameField = buildFirstNameField(),
            lastNameField = buildLastNameField(),
            titleField = buildTitleField(),
            departmentField = buildDepartmentField(),
            uiSyncState = uiSyncState,
            isEditingEnabled = isEditingEnabled,
            dataOperationIsActive = dataOpDelegate.dataOperationIsActive,
            shouldScrollToErrorField = shouldScrollToErrorField,
            curDialogUiState = uiState.value.curDialogUiState,
        )
    }

    private fun ContactObject.buildFirstNameField() = ContactDetailsField.FirstName(
        fieldValue = firstName,
        onValueChange = this@DefaultContactDetailsViewModel::onFirstNameChange
    )

    private fun ContactObject.buildLastNameField() = ContactDetailsField.LastName(
        fieldValue = lastName,
        onValueChange = this@DefaultContactDetailsViewModel::onLastNameChange
    )

    private fun ContactObject.buildTitleField() = ContactDetailsField.Title(
        fieldValue = title,
        onValueChange = this@DefaultContactDetailsViewModel::onTitleChange
    )

    private fun ContactObject.buildDepartmentField() = ContactDetailsField.Department(
        fieldValue = department,
        onValueChange = this@DefaultContactDetailsViewModel::onDepartmentChange
    )

    private fun launchWithStateLock(block: suspend CoroutineScope.() -> Unit) {
        parentScope.launch {
            stateMutex.withLock { block() }
        }
    }

    private companion object {
        private const val TAG = "DefaultContactDetailsViewModel"
    }

    private inner class DataOperationDelegate {
        private val mutDataOperationIsActive = AtomicBoolean(false)
        val dataOperationIsActive: Boolean get() = mutDataOperationIsActive.get()

        fun handleDataEvent(event: DetailsDataEvent): Boolean {
            val handlingEvent = mutDataOperationIsActive.compareAndSet(false, true)
            if (handlingEvent) {
                when (event) {
                    is DetailsDataEvent.Create -> launchSave(forId = null, so = event.so)
                    is DetailsDataEvent.Update -> launchSave(forId = event.id, so = event.so)
                    is DetailsDataEvent.Delete -> launchDelete(forId = event.id)
                    is DetailsDataEvent.Undelete -> launchUndelete(forId = event.id)
                }
            }

            return handlingEvent
        }

        private fun launchDelete(forId: String) = parentScope.launch {
            val updatedRecord = try {
                contactsRepo.locallyDelete(id = forId)
            } catch (ex: RepoOperationException) {
                throw ex // WIP crash the app with full exception for now
            }

            mutDataOperationIsActive.set(false)

            stateMutex.withLock {
                mutUiState.value = updatedRecord?.sObject?.buildViewingContactUiState(
                    uiSyncState = SObjectUiSyncState.Deleted,
                    isEditingEnabled = false,
                    shouldScrollToErrorField = false
                ) ?: ContactDetailsUiState.NoContactSelected(
                    dataOperationIsActive = false,
                    curDialogUiState = uiState.value.curDialogUiState
                )
            }
        }

        private fun launchUndelete(forId: String) = parentScope.launch {
            val updatedRecord = try {
                contactsRepo.locallyUndelete(id = forId)
            } catch (ex: RepoOperationException) {
                throw ex // WIP crash the app with full exception for now
            }

            mutDataOperationIsActive.set(false)

            stateMutex.withLock {
                mutUiState.value = updatedRecord.sObject.buildViewingContactUiState(
                    uiSyncState = updatedRecord.localStatus.toUiSyncState(),
                    isEditingEnabled = false,
                    shouldScrollToErrorField = false
                )
            }
        }

        private fun launchSave(forId: String?, so: ContactObject) = parentScope.launch {
            stateMutex.withLock {
                mutUiState.value = uiState.value.copy(dataOperationIsActive = true)
            }

            val record = try {
                if (forId == null) {
                    contactsRepo.locallyCreate(so = so)
                } else {
                    contactsRepo.locallyUpdate(id = forId, so = so)
                }
            } catch (ex: RepoOperationException) {
                throw ex // WIP crash the app with full exception for now
            }

            mutDataOperationIsActive.set(false)

            // This clobbers the UI regardless of what state it is in b/c we are assuming that no
            // changes to the VM can happen while this data operation is running.
            stateMutex.withLock {
                mutUiState.value = record.sObject.buildViewingContactUiState(
                    uiSyncState = record.localStatus.toUiSyncState(),
                    isEditingEnabled = false,
                    shouldScrollToErrorField = false,
                )
            }
        }
    }
}

private sealed interface DetailsDataEvent {
    @JvmInline
    value class Delete(val id: String) : DetailsDataEvent

    @JvmInline
    value class Undelete(val id: String) : DetailsDataEvent

    data class Update(val id: String, val so: ContactObject) : DetailsDataEvent

    @JvmInline
    value class Create(val so: ContactObject) : DetailsDataEvent
}

private fun LocalStatus.toUiSyncState(): SObjectUiSyncState = when (this) {
    LocalStatus.LocallyDeleted,
    LocalStatus.LocallyDeletedAndLocallyUpdated -> SObjectUiSyncState.Deleted

    LocalStatus.LocallyCreated,
    LocalStatus.LocallyUpdated -> SObjectUiSyncState.Updated

    LocalStatus.MatchesUpstream -> SObjectUiSyncState.Synced
}
