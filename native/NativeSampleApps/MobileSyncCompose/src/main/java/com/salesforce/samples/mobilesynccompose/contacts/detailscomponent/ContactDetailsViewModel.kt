package com.salesforce.samples.mobilesynccompose.contacts.detailscomponent

import com.salesforce.samples.mobilesynccompose.core.repos.RepoOperationException
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectSyncableRepo
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.isLocallyDeleted
import com.salesforce.samples.mobilesynccompose.core.ui.state.DialogUiState
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

interface ContactDetailsViewModel : ContactDetailsFieldChangeHandler, ContactDetailsUiEventHandler {
    val uiState: StateFlow<ContactDetailsUiState>
    val hasUnsavedChanges: Boolean

    @Throws(HasUnsavedChangesException::class)
    suspend fun clearContactObj()

    @Throws(HasUnsavedChangesException::class)
    suspend fun setContact(recordId: String, startWithEditingEnabled: Boolean)
}

class DefaultContactDetailsViewModel(
    private val parentScope: CoroutineScope,
    private val contactsRepo: SObjectSyncableRepo<ContactObject>,
    startingId: String? = null
) : ContactDetailsViewModel {

    private val stateMutex = Mutex()

    private val mutUiState: MutableStateFlow<ContactDetailsUiState> = MutableStateFlow(
        ContactDetailsUiState.NoContactSelected(
            dataOperationIsActive = true,
            curDialogUiState = null
        )
    )

    override val uiState: StateFlow<ContactDetailsUiState> get() = mutUiState

    @Volatile
    private var targetRecordId: String? = startingId

    @Volatile
    private lateinit var upstreamRecords: Map<String, SObjectRecord<ContactObject>>

//    private val initJob = parentScope.launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
//        val upstreamRecord = if (startingIds != null) {
//            val initialEmission = contactsRepo.records.first()
//
//            initialEmission.locallyCreatedRecords[startingIds.locallyCreatedId]
//                ?: initialEmission.upstreamRecords[startingIds.primaryKey]
//        } else {
//            null
//        }
//
//        eventMutex.withLock {
//            this@DefaultContactDetailsViewModel.upstreamRecord = upstreamRecord
//
//            mutUiState.value = upstreamRecord?.sObject?.buildViewingContactUiState(
//                dataOperationIsActive = false,
//                isEditingEnabled = false
//            ) ?: ContactDetailsUiState.NoContactSelected(dataOperationIsActive = false)
//        }
//    }

    init {
        parentScope.launch(Dispatchers.Default) {
//            initJob.join()
            contactsRepo.recordsById.collect { onNewRecords(it) }
        }
    }

    private suspend fun onNewRecords(
        newRecords: Map<String, SObjectRecord<ContactObject>>
    ) = stateMutex.withLock {
        val curId = targetRecordId ?: return@withLock
        val matchingRecord = newRecords[curId]

        if (matchingRecord == null) {
            targetRecordId = null
            return@withLock
        }

        mutUiState.value = when (val curState = uiState.value) {
            is ContactDetailsUiState.NoContactSelected -> {
                matchingRecord.sObject.buildViewingContactUiState(
                    isEditingEnabled = false,
                    dataOperationIsActive = curState.dataOperationIsActive,
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
                    matchingRecord.localStatus.isLocallyDeleted && hasUnsavedChanges ->
                        ContactDetailsUiState.ViewingContactDetails(
                            firstNameField = curState.firstNameField,
                            lastNameField = curState.lastNameField,
                            titleField = curState.titleField,
                            departmentField = curState.departmentField,
                            isEditingEnabled = curState.isEditingEnabled,
                            dataOperationIsActive = curState.dataOperationIsActive,
                            shouldScrollToErrorField = curState.shouldScrollToErrorField,
                            curDialogUiState = DiscardChangesOrUndeleteDialogUiState(
                                onDiscardChanges = { TODO() },
                                onUndelete = { TODO() }
                            )
                        )

                    // user is editing and there is no incompatible state, so no changes to state:
                    else -> curState
                }
            }
        }
    }

    @Throws(HasUnsavedChangesException::class)
    override suspend fun clearContactObj() = launchWithStateLock {
        TODO("Not yet implemented")
    }

    @Throws(HasUnsavedChangesException::class)
    override suspend fun setContact(
        recordId: String,
        startWithEditingEnabled: Boolean
    ) = launchWithStateLock {
        TODO("Not yet implemented")
    }

    override val hasUnsavedChanges: Boolean
        get() = when (val curState = uiState.value) {
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

    override fun createClick() {
        TODO("Not yet implemented")
    }

    override fun deleteClick() {
        TODO("Not yet implemented")
    }

    override fun undeleteClick() = launchWithStateLock {
        val targetRecordId = targetRecordId ?: return@launchWithStateLock // no id => nothing to do
        val undeleteConfirmation = UndeleteConfirmationDialogUiState(
            objIdToUndelete = targetRecordId,
            objName = upstreamRecords[targetRecordId]?.sObject?.fullName,
            onCancelUndelete = { TODO("onCancelUndelete()") },
            onUndeleteConfirm = { TODO("onUndeleteConfirm()") },
        )

        mutUiState.value = when (val curState = uiState.value) {
            is ContactDetailsUiState.NoContactSelected -> curState.copy(
                curDialogUiState = undeleteConfirmation
            )
            is ContactDetailsUiState.ViewingContactDetails -> curState.copy(
                curDialogUiState = undeleteConfirmation
            )
        }
    }

    override fun editClick() {
        TODO("Not yet implemented")
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

        targetRecordId.also {
            if (it == null) {
                launchCreate(so = so)
            } else {
                launchUpdate(id = it, so = so)
            }
        }
    }

    private fun launchCreate(so: ContactObject) = parentScope.launch {
        stateMutex.withLock {
            mutUiState.value = when (val curState = uiState.value) {
                is ContactDetailsUiState.NoContactSelected -> curState.copy(
                    dataOperationIsActive = true,
                )
                is ContactDetailsUiState.ViewingContactDetails -> curState.copy(
                    isEditingEnabled = false,
                    dataOperationIsActive = true,
                    shouldScrollToErrorField = false,
                )
            }
        }

        // Do the update outside of the state lock
        val createdRecord = try {
            contactsRepo.locallyCreate(so = so)
        } catch (ex: RepoOperationException) {
            TODO("launchCreate() caught ${ex.message}")
            return@launch
        }

        stateMutex.withLock {
            mutUiState.value = when (val curState = uiState.value) {
                is ContactDetailsUiState.ViewingContactDetails ->
                    if (targetRecordId == null) {
                        targetRecordId = createdRecord.id

                        curState.copy(
                            firstNameField = createdRecord.sObject.buildFirstNameField(),
                            lastNameField = createdRecord.sObject.buildLastNameField(),
                            titleField = createdRecord.sObject.buildTitleField(),
                            departmentField = createdRecord.sObject.buildDepartmentField(),
                            dataOperationIsActive = false
                        )
                    } else {
                        // Different record in this VM than what was returned from create operation, so assume that the contact was changed while update was running and do not clobber the UI:
                        curState
                    }

                is ContactDetailsUiState.NoContactSelected -> curState
            }
        }
    }

    private fun launchUpdate(id: String, so: ContactObject) = parentScope.launch {
        // Do the update outside of the event lock
        val updatedRecord = try {
            contactsRepo.locallyUpdate(id = id, so = so)
        } catch (ex: RepoOperationException) {
            TODO("launchUpdate() caught ${ex.message}")
            return@launch
        }

        stateMutex.withLock {
            mutUiState.value = when (val curState = uiState.value) {
                is ContactDetailsUiState.ViewingContactDetails -> {
                    if (updatedRecord.id == targetRecordId) {
                        curState.copy(
                            firstNameField = updatedRecord.sObject.buildFirstNameField(),
                            lastNameField = updatedRecord.sObject.buildLastNameField(),
                            titleField = updatedRecord.sObject.buildTitleField(),
                            departmentField = updatedRecord.sObject.buildDepartmentField(),
                            dataOperationIsActive = false
                        )
                    } else {
                        // Different record in this VM than what was returned from update operation, so assume that the contact was changed while update was running and do not clobber the UI:
                        curState
                    }
                }

                is ContactDetailsUiState.NoContactSelected -> curState
            }
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
        isEditingEnabled: Boolean,
        dataOperationIsActive: Boolean,
        shouldScrollToErrorField: Boolean,
        curDialogUiState: DialogUiState?
    ) = ContactDetailsUiState.ViewingContactDetails(
        firstNameField = buildFirstNameField(),
        lastNameField = buildLastNameField(),
        titleField = buildTitleField(),
        departmentField = buildDepartmentField(),
        isEditingEnabled = isEditingEnabled,
        dataOperationIsActive = dataOperationIsActive,
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
}
