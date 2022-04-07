package com.salesforce.samples.mobilesynccompose.contacts.detailscomponent

import com.salesforce.samples.mobilesynccompose.core.extensions.requireIsLocked
import com.salesforce.samples.mobilesynccompose.core.repos.RepoOperationException
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectSyncableRepo
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.core.ui.state.DeleteConfirmationDialogUiState
import com.salesforce.samples.mobilesynccompose.core.ui.state.DialogUiState
import com.salesforce.samples.mobilesynccompose.core.ui.state.UndeleteConfirmationDialogUiState
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactValidationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

private enum class DetailsDataEvent {
    Delete,
    Undelete,
    Save,
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
    private var targetRecordId: String? = null

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
        val curId = targetRecordId ?: return@withLock
        val matchingRecord = newRecords[curId] ?: return@withLock

        mutUiState.value = when (val curState = uiState.value) {
            is ContactDetailsUiState.NoContactSelected -> {
                matchingRecord.sObject.buildViewingContactUiState(
                    isEditingEnabled = false,
                    shouldScrollToErrorField = false,
                    curDialogUiState = curState.curDialogUiState
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
                isEditingEnabled = startWithEditingEnabled,
                shouldScrollToErrorField = false,
                curDialogUiState = curState.curDialogUiState
            )
            is ContactDetailsUiState.ViewingContactDetails -> curState.copy(
                firstNameField = newRecord.sObject.buildFirstNameField(),
                lastNameField = newRecord.sObject.buildLastNameField(),
                titleField = newRecord.sObject.buildTitleField(),
                departmentField = newRecord.sObject.buildDepartmentField()
            )
        }
    }

    private val hasUnsavedChanges: Boolean
        get() {
            stateMutex.requireIsLocked()
            return when (val curState = uiState.value) {
                is ContactDetailsUiState.ViewingContactDetails -> targetRecordId?.let {
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

    override fun createClick() {
        TODO("Not yet implemented")
    }

    override fun deleteClick() = launchWithStateLock {
        val targetRecordId = targetRecordId ?: return@launchWithStateLock // no id => nothing to do

        mutUiState.value = uiState.value.copy(
            curDialogUiState = DeleteConfirmationDialogUiState(
                objIdToDelete = targetRecordId,
                objName = upstreamRecords[targetRecordId]?.sObject?.fullName,
                onCancelDelete = ::dismissCurDialog,
                onDeleteConfirm = { dataOpDelegate.handleDataEvent(event = DetailsDataEvent.Delete) }
            )
        )
    }

    override fun undeleteClick() = launchWithStateLock {
        val targetRecordId = targetRecordId ?: return@launchWithStateLock // no id => nothing to do

        mutUiState.value = uiState.value.copy(
            curDialogUiState = UndeleteConfirmationDialogUiState(
                objIdToUndelete = targetRecordId,
                objName = upstreamRecords[targetRecordId]?.sObject?.fullName,
                onCancelUndelete = ::dismissCurDialog,
                onUndeleteConfirm = { dataOpDelegate.handleDataEvent(event = DetailsDataEvent.Undelete) },
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

    override fun saveClick() {
        dataOpDelegate.handleDataEvent(event = DetailsDataEvent.Save)
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
        isEditingEnabled: Boolean,
        shouldScrollToErrorField: Boolean,
        curDialogUiState: DialogUiState?
    ) = ContactDetailsUiState.ViewingContactDetails(
        firstNameField = buildFirstNameField(),
        lastNameField = buildLastNameField(),
        titleField = buildTitleField(),
        departmentField = buildDepartmentField(),
        isEditingEnabled = isEditingEnabled,
        dataOperationIsActive = dataOpDelegate.dataOperationIsActive,
        shouldScrollToErrorField = shouldScrollToErrorField,
        curDialogUiState = curDialogUiState,
    )

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

        fun handleDataEvent(event: DetailsDataEvent) {
            if (mutDataOperationIsActive.compareAndSet(false, true)) {
                when (event) {
                    DetailsDataEvent.Save -> launchSave()
                    DetailsDataEvent.Delete -> TODO()
                    DetailsDataEvent.Undelete -> TODO()
                }
                mutDataOperationIsActive.set(false)
            }
        }

        private fun launchSave() = parentScope.launch {
            val recordDeferred = stateMutex.withLock {
                // If not viewing details, we cannot build the SObject, so there is nothing to do
                val curState = uiState.value as? ContactDetailsUiState.ViewingContactDetails
                    ?: return@launch

                val so = try {
                    curState.toSObjectOrThrow()
                } catch (ex: Exception) {
                    mutUiState.value = curState.copy(shouldScrollToErrorField = true)
                    return@launch
                }

                mutUiState.value = uiState.value.copy(dataOperationIsActive = true)

                targetRecordId.let {
                    if (it == null) {
                        parentScope.async { contactsRepo.locallyCreate(so = so) }
                    } else {
                        parentScope.async { contactsRepo.locallyUpdate(id = it, so = so) }
                    }
                }
            }

            val record = try {
                recordDeferred.await()
            } catch (ex: RepoOperationException) {
                TODO("launchSave() caught ${ex.message}")
                return@launch
            }

            stateMutex.withLock {
                mutUiState.value = when (val curState = uiState.value) {
                    is ContactDetailsUiState.ViewingContactDetails -> {
                        curState.copy(
                            firstNameField = record.sObject.buildFirstNameField(),
                            lastNameField = record.sObject.buildLastNameField(),
                            titleField = record.sObject.buildTitleField(),
                            departmentField = record.sObject.buildDepartmentField(),
                            dataOperationIsActive = false
                        )
                    }

                    is ContactDetailsUiState.NoContactSelected -> curState.copy(
                        dataOperationIsActive = false
                    )
                }
            }
        }
    }
}
