package com.salesforce.samples.mobilesynccompose.contacts.detailscomponent

import com.salesforce.samples.mobilesynccompose.core.repos.SObjectRecordsByIds
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectSyncableRepo
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.PrimaryKey
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectCombinedId
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.core.ui.state.DialogUiState
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactValidationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ContactDetailsViewModel : ContactDetailsFieldChangeHandler, ContactDetailsUiEventHandler {
    val uiState: StateFlow<ContactDetailsUiState>
    val hasUnsavedChanges: Boolean

    @Throws(HasUnsavedChangesException::class)
    suspend fun clearContactObj()

    @Throws(HasUnsavedChangesException::class)
    suspend fun setContact(recordIds: SObjectCombinedId, startWithEditingEnabled: Boolean)
}

class DefaultContactDetailsViewModel(
    private val parentScope: CoroutineScope,
    private val contactsRepo: SObjectSyncableRepo<ContactObject>,
    startingIds: SObjectCombinedId? = null,
) : ContactDetailsViewModel {

    private val eventMutex = Mutex()

    private val mutUiState: MutableStateFlow<ContactDetailsUiState> = MutableStateFlow(
        ContactDetailsUiState.NoContactSelected(dataOperationIsActive = true)
    )

    override val uiState: StateFlow<ContactDetailsUiState> get() = mutUiState

    @Volatile
    private var upstreamRecord: SObjectRecord<ContactObject>? = null

    private val initJob = parentScope.launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
        val upstreamRecord = if (startingIds != null) {
            val initialEmission = contactsRepo.recordsById.first()

            initialEmission.locallyCreatedRecords[startingIds.locallyCreatedId]
                ?: initialEmission.upstreamRecords[startingIds.primaryKey]
        } else {
            null
        }

        eventMutex.withLock {
            this@DefaultContactDetailsViewModel.upstreamRecord = upstreamRecord

            mutUiState.value = upstreamRecord?.sObject?.buildViewingContactUiState(
                dataOperationIsActive = false,
                isEditingEnabled = false
            ) ?: ContactDetailsUiState.NoContactSelected(dataOperationIsActive = false)
        }
    }

    init {
        parentScope.launch(Dispatchers.Default) {
            initJob.join()
            contactsRepo.recordsById.collect { onNewRecords(it) }
        }
    }

    private suspend fun onNewRecords(
        newRecords: SObjectRecordsByIds<ContactObject>
    ) = eventMutex.withLock {

        val curState = uiState.value as? ContactDetailsUiState.ViewingContactDetails
            ?: return@withLock

        val upstreamRecord = this.upstreamRecord
            ?: return@withLock

        val matchingRecord =
            upstreamRecord.locallyCreatedId?.let { newRecords.locallyCreatedRecords[it] }
                ?: newRecords.upstreamRecords[upstreamRecord.primaryKey]
                ?: return@withLock

        this.upstreamRecord = matchingRecord

        mutUiState.value = when {
            // not editing, so simply update all fields to match upstream emission:
            !curState.isEditingEnabled -> curState.copy(
                firstNameField = matchingRecord.sObject.buildFirstNameField(),
                lastNameField = matchingRecord.sObject.buildLastNameField(),
                titleField = matchingRecord.sObject.buildTitleField(),
                departmentField = matchingRecord.sObject.buildDepartmentField(),
            )

            // Upstream was deleted, but we have unsaved changes. Ask the user what they want to do:
            matchingRecord.localStatus.isLocallyDeleted && hasUnsavedChanges -> curState.copy(
                curDialogUiState = DiscardChangesOrUndeleteDialogUiState(
                    onDiscardChanges = { TODO() },
                    onUndelete = { TODO() }
                )
            )

            // user is editing and there is no incompatible state, so no changes to state:
            else -> curState
        }
    }

    @Throws(HasUnsavedChangesException::class)
    override suspend fun clearContactObj() {
        if (initJob.isActive) {
            initJob.cancel()
        }
        TODO("Not yet implemented")
    }

    @Throws(HasUnsavedChangesException::class)
    override suspend fun setContact(
        recordIds: SObjectCombinedId,
        startWithEditingEnabled: Boolean
    ) {
        if (initJob.isActive) {
            initJob.cancel()
        }
        TODO("Not yet implemented")
    }

    override val hasUnsavedChanges: Boolean
        get() = when (val curState = uiState.value) {
            is ContactDetailsUiState.ViewingContactDetails -> upstreamRecord?.let {
                try {
                    curState.toSObjectOrThrow() == it.sObject
                } catch (ex: ContactValidationException) {
                    // invalid field values means there are unsaved changes
                    true
                }
            } ?: true
            is ContactDetailsUiState.NoContactSelected -> false
        }

    override fun createClick() {
        TODO("Not yet implemented")
    }

    override fun deleteClick() {
        TODO("Not yet implemented")
    }

    override fun undeleteClick() {
        TODO("Not yet implemented")
    }

    override fun editClick() {
        TODO("Not yet implemented")
    }

    override fun exitClick() {
        TODO("Not yet implemented")
    }

    override fun saveClick() = launchWithEventLock {
        val curState = uiState.value as? ContactDetailsUiState.ViewingContactDetails
            ?: return@launchWithEventLock

        val so = try {
            curState.toSObjectOrThrow()
        } catch (ex: Exception) {
            mutUiState.value = curState.copy(shouldScrollToErrorField = true)
            return@launchWithEventLock
        }

        upstreamRecord.also {
            if (it == null) {
                // Creating
                mutUiState.value = curState.copy(dataOperationIsActive = true)
                launchCreate(so = so)
            } else {
                // Updating
                mutUiState.value = curState.copy(dataOperationIsActive = true)
                launchUpdate(id = it.primaryKey, so = so)
            }
        }
    }

    private fun launchCreate(so: ContactObject) = parentScope.launch {
        // Do the update outside of the event lock
        val createdRecord = contactsRepo.locallyCreate(so = so)

        eventMutex.withLock {
            mutUiState.value = when (val curState = uiState.value) {
                is ContactDetailsUiState.ViewingContactDetails ->
                    if (upstreamRecord == null) {
                        upstreamRecord = createdRecord

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

    private fun launchUpdate(id: PrimaryKey, so: ContactObject) = parentScope.launch {
        // Do the update outside of the event lock
        val updatedRecord = contactsRepo.locallyUpdate(id = id, so = so)

        eventMutex.withLock {
            mutUiState.value = when (val curState = uiState.value) {
                is ContactDetailsUiState.ViewingContactDetails -> {
                    val updatedRecordMatchesCurId =
                        upstreamRecord?.locallyCreatedId == updatedRecord.locallyCreatedId
                                || upstreamRecord?.primaryKey == updatedRecord.primaryKey

                    if (updatedRecordMatchesCurId) {
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

    override fun onFirstNameChange(newFirstName: String) = launchWithEventLock {
        val curState = uiState.value as? ContactDetailsUiState.ViewingContactDetails
            ?: return@launchWithEventLock

        mutUiState.value = curState.copy(
            firstNameField = curState.firstNameField.copy(fieldValue = newFirstName)
        )
    }

    override fun onLastNameChange(newLastName: String) = launchWithEventLock {
        val curState = uiState.value as? ContactDetailsUiState.ViewingContactDetails
            ?: return@launchWithEventLock

        mutUiState.value = curState.copy(
            lastNameField = curState.lastNameField.copy(fieldValue = newLastName)
        )
    }

    override fun onTitleChange(newTitle: String) = launchWithEventLock {
        val curState = uiState.value as? ContactDetailsUiState.ViewingContactDetails
            ?: return@launchWithEventLock

        mutUiState.value = curState.copy(
            titleField = curState.titleField.copy(fieldValue = newTitle)
        )
    }

    override fun onDepartmentChange(newDepartment: String) = launchWithEventLock {
        val curState = uiState.value as? ContactDetailsUiState.ViewingContactDetails
            ?: return@launchWithEventLock

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
        dataOperationIsActive: Boolean = false,
        shouldScrollToErrorField: Boolean = false,
        curDialogUiState: DialogUiState? = null
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

    private fun launchWithEventLock(block: suspend CoroutineScope.() -> Unit) {
        parentScope.launch {
            eventMutex.withLock { block() }
        }
    }

    private companion object {
        private const val TAG = "DefaultContactDetailsViewModel"
    }
}
