package com.salesforce.samples.mobilesynccompose.contacts.vm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectSyncableRepo
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.PrimaryKey
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectCombinedId
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.isLocallyDeleted
import com.salesforce.samples.mobilesynccompose.core.ui.state.DialogUiState
import com.salesforce.samples.mobilesynccompose.core.vm.EditableTextFieldUiState
import com.salesforce.samples.mobilesynccompose.core.vm.FieldUiState
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

interface ContactDetailsViewModel : ContactObjectFieldChangeHandler, ContactDetailsUiEventHandler {
    val uiState: StateFlow<ContactDetailsUiState2>

    @Throws(HasUnsavedChangesException::class)
    suspend fun clearContactObj()

    @Throws(HasUnsavedChangesException::class)
    suspend fun setContact(recordIds: SObjectCombinedId, startWithEditingEnabled: Boolean)

    val hasUnsavedChanges: Boolean
}

data class HasUnsavedChangesException(override val message: String?) : Exception()

interface ContactDetailsUiEventHandler {
    fun createClick()
    fun deleteClick()
    fun undeleteClick()
    fun editClick()
    fun exitClick()
    fun saveClick()
}

data class DiscardChangesOrUndeleteDialogUiState(
    val onDiscardChanges: () -> Unit,
    val onUndelete: () -> Unit,
) : DialogUiState {
    @Composable
    override fun RenderDialog(modifier: Modifier) {
        AlertDialog(
            onDismissRequest = { /* Disallow skipping by back button */ },
            buttons = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDiscardChanges) { Text(stringResource(id = cta_discard)) }
                    TextButton(onClick = onUndelete) { Text(stringResource(id = cta_undelete)) }
                }
            },
            // TODO use string res
            title = { Text("Deleted while editing") },
            text = { Text("This contact was deleted while you were editing it. You may undelete it and continue editing, or you may discard your unsaved changes.") },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            modifier = modifier
        )
    }
}

sealed interface ContactDetailsUiState2 {
    val curDialogUiState: DialogUiState?
    val dataOperationIsActive: Boolean

    data class HasContact(
        val firstNameField: ContactDetailsField.FirstName,
        val lastNameField: ContactDetailsField.LastName,
        val titleField: ContactDetailsField.Title,
        val departmentField: ContactDetailsField.Department,

        val isEditingEnabled: Boolean,
        override val dataOperationIsActive: Boolean = false,
        val shouldScrollToErrorField: Boolean = false,
        override val curDialogUiState: DialogUiState? = null
    ) : ContactDetailsUiState2 {
        val fullName = ContactObject.formatFullName(
            firstName = firstNameField.fieldValue,
            lastName = lastNameField.fieldValue
        )
    }

    data class NoContactSelected(
        override val dataOperationIsActive: Boolean = false,
        override val curDialogUiState: DialogUiState? = null
    ) : ContactDetailsUiState2
}

class DefaultContactDetailsViewModel(
    private val parentScope: CoroutineScope,
    private val contactsRepo: SObjectSyncableRepo<ContactObject>,
    startingIds: SObjectCombinedId? = null,
) : ContactDetailsViewModel {

    private val eventMutex = Mutex()

    private val mutUiState: MutableStateFlow<ContactDetailsUiState2> = MutableStateFlow(
        ContactDetailsUiState2.NoContactSelected(dataOperationIsActive = true)
    )

    override val uiState: StateFlow<ContactDetailsUiState2> get() = mutUiState

    @Volatile
    private var upstreamRecord: SObjectRecord<ContactObject>? = null

    private val initJob = parentScope.launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
        val upstreamRecord = if (startingIds != null) {
            val initialEmission = contactsRepo.records.first()

            initialEmission.byLocallyCreatedId[startingIds.locallyCreatedId]
                ?: initialEmission.byPrimaryKey[startingIds.primaryKey]
        } else {
            null
        }

        eventMutex.withLock {
            this@DefaultContactDetailsViewModel.upstreamRecord = upstreamRecord

            mutUiState.value = upstreamRecord?.sObject?.buildHasContactUiState(
                dataOperationIsActive = false,
                isEditingEnabled = false
            ) ?: ContactDetailsUiState2.NoContactSelected(dataOperationIsActive = false)
        }
    }

    init {
        parentScope.launch(Dispatchers.Default) {
            initJob.join()

            contactsRepo.records.collect { newRecords ->
                eventMutex.withLock {
                    val curState = uiState.value as? ContactDetailsUiState2.HasContact
                        ?: return@withLock

                    val matchingRecord = upstreamRecord?.let {
                        it.locallyCreatedId?.let { newRecords.byLocallyCreatedId[it] }
                            ?: newRecords.byPrimaryKey[it.primaryKey]
                    } ?: return@withLock

                    upstreamRecord = matchingRecord

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
            }
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
            is ContactDetailsUiState2.HasContact -> upstreamRecord?.let {
                try {
                    curState.toSObjectOrThrow() == it.sObject
                } catch (ex: ContactValidationException) {
                    // invalid field values means there are unsaved changes
                    true
                }
            } ?: true
            is ContactDetailsUiState2.NoContactSelected -> false
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

    override fun saveClick() {
        fun launchUpdate(id: PrimaryKey, so: ContactObject) = parentScope.launch {
            // Do the update outside of the event lock
            val updatedRecord = contactsRepo.locallyUpdate(id = id, so = so)

            eventMutex.withLock {
                mutUiState.value = when (val curState = uiState.value) {
                    is ContactDetailsUiState2.HasContact -> {
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
                            // Different record in this VM than what was returned from update operation, so assume that the contact was changed while update was running:
                            curState
                        }
                    }

                    is ContactDetailsUiState2.NoContactSelected -> curState
                }
            }
        }

        fun launchCreate(so: ContactObject) = parentScope.launch {
            // Do the update outside of the event lock
            val createdRecord = contactsRepo.locallyCreate(so = so)

            eventMutex.withLock {
                mutUiState.value = when (val curState = uiState.value) {
                    is ContactDetailsUiState2.HasContact ->
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
                            curState.copy(dataOperationIsActive = false)
                        }

                    is ContactDetailsUiState2.NoContactSelected -> curState
                }
            }
        }

        parentScope.launch {
            eventMutex.withLock {
                val curState = uiState.value as? ContactDetailsUiState2.HasContact
                    ?: return@withLock

                val so = try {
                    curState.toSObjectOrThrow()
                } catch (ex: Exception) {
                    mutUiState.value = curState.copy(shouldScrollToErrorField = true)
                    return@withLock
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
        }
    }

    private fun ContactDetailsUiState2.HasContact.toSObjectOrThrow() = ContactObject(
        firstName = firstNameField.fieldValue,
        lastName = lastNameField.fieldValue ?: "",
        title = titleField.fieldValue,
        department = departmentField.fieldValue
    )

    override fun onFirstNameChange(newFirstName: String) {
        parentScope.launch {
            eventMutex.withLock {
                val curState = uiState.value as? ContactDetailsUiState2.HasContact
                    ?: return@withLock

                mutUiState.value = curState.copy(
                    firstNameField = curState.firstNameField.copy(fieldValue = newFirstName)
                )
            }
        }
    }

    override fun onLastNameChange(newLastName: String) {
        parentScope.launch {
            eventMutex.withLock {
                val curState = uiState.value as? ContactDetailsUiState2.HasContact
                    ?: return@withLock

                mutUiState.value = curState.copy(
                    lastNameField = curState.lastNameField.copy(fieldValue = newLastName)
                )
            }
        }
    }

    override fun onTitleChange(newTitle: String) {
        parentScope.launch {
            eventMutex.withLock {
                val curState = uiState.value as? ContactDetailsUiState2.HasContact
                    ?: return@withLock

                mutUiState.value = curState.copy(
                    titleField = curState.titleField.copy(fieldValue = newTitle)
                )
            }
        }
    }

    override fun onDepartmentChange(newDepartment: String) {
        parentScope.launch {
            eventMutex.withLock {
                val curState = uiState.value as? ContactDetailsUiState2.HasContact
                    ?: return@withLock

                mutUiState.value = curState.copy(
                    departmentField = curState.departmentField.copy(fieldValue = newDepartment)
                )
            }
        }
    }

    private fun ContactObject.buildHasContactUiState(
        isEditingEnabled: Boolean,
        dataOperationIsActive: Boolean = false,
        shouldScrollToErrorField: Boolean = false,
        curDialogUiState: DialogUiState? = null
    ) = ContactDetailsUiState2.HasContact(
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

    private companion object {
        private const val TAG = "DefaultContactDetailsViewModel"
    }
}

sealed interface ContactDetailsField : FieldUiState {
    data class FirstName(
        override val fieldValue: String?,
        override val onValueChange: (newValue: String) -> Unit,
        override val fieldIsEnabled: Boolean = true,
        override val maxLines: UInt = 1u
    ) : ContactDetailsField, EditableTextFieldUiState {
        override val isInErrorState: Boolean = false
        override val helperRes: Int? = null
        override val labelRes: Int = label_contact_first_name
        override val placeholderRes: Int = label_contact_first_name
    }

    data class LastName(
        override val fieldValue: String?,
        override val onValueChange: (newValue: String) -> Unit,
        override val fieldIsEnabled: Boolean = true,
        override val maxLines: UInt = 1u
    ) : ContactDetailsField, EditableTextFieldUiState {
        override val isInErrorState: Boolean
        override val helperRes: Int?

        init {
            val validateException = runCatching { ContactObject.validateLastName(fieldValue) }
                .exceptionOrNull() as ContactValidationException?

            if (validateException == null) {
                isInErrorState = false
                helperRes = null
            } else {
                isInErrorState = true
                helperRes = when (validateException) {
                    ContactValidationException.LastNameCannotBeBlank -> help_cannot_be_blank
                }
            }
        }

        override val labelRes: Int = label_contact_last_name
        override val placeholderRes: Int = label_contact_last_name
    }

    data class Title(
        override val fieldValue: String?,
        override val onValueChange: (newValue: String) -> Unit,
        override val fieldIsEnabled: Boolean = true,
        override val maxLines: UInt = 1u
    ) : ContactDetailsField, EditableTextFieldUiState {
        override val isInErrorState: Boolean = false
        override val labelRes: Int = label_contact_title
        override val helperRes: Int? = null
        override val placeholderRes: Int = label_contact_title
    }

    data class Department(
        override val fieldValue: String?,
        override val onValueChange: (newValue: String) -> Unit,
        override val fieldIsEnabled: Boolean = true,
        override val maxLines: UInt = 1u
    ) : ContactDetailsField, EditableTextFieldUiState {
        override val isInErrorState: Boolean = false
        override val labelRes: Int = label_contact_department
        override val helperRes: Int? = null
        override val placeholderRes: Int = label_contact_department
    }
}
