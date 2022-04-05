package com.salesforce.samples.mobilesynccompose.contacts.vm

import android.util.Log
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
import com.salesforce.samples.mobilesynccompose.core.extensions.takeIfInstance
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectSyncableRepo
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectRecordsByIds
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.*
import com.salesforce.samples.mobilesynccompose.core.ui.state.DialogUiState
import com.salesforce.samples.mobilesynccompose.core.vm.EditableTextFieldUiState
import com.salesforce.samples.mobilesynccompose.core.vm.FieldUiState
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

interface ContactDetailsViewModel : ContactObjectFieldChangeHandler, ContactDetailsUiEventHandler {
    val uiState: StateFlow<ContactDetailsUiState2>

    @Throws(HasUnsavedChangesException::class)
    suspend fun clearContactObj()

    @Throws(HasUnsavedChangesException::class)
    suspend fun setContact(record: SObjectRecord<ContactObject>, startWithEditingEnabled: Boolean)

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

enum class RecordStatus

sealed interface ContactDetailsUiState2 {
    val curDialogUiState: DialogUiState?

    data class HasContact(
        val firstNameField: ContactDetailsField.FirstName,
        val lastNameField: ContactDetailsField.LastName,
        val titleField: ContactDetailsField.Title,
        val departmentField: ContactDetailsField.Department,

        val contactObjLocalStatus: LocalStatus?,
        val isEditingEnabled: Boolean,
        val dataOperationIsActive: Boolean = false,
        val shouldScrollToErrorField: Boolean = false,
        override val curDialogUiState: DialogUiState? = null
    ) : ContactDetailsUiState2 {
        val fullName = ContactObject.formatFullName(
            firstName = firstNameField.fieldValue,
            lastName = lastNameField.fieldValue
        )
    }

    data class InitialLoad(override val curDialogUiState: DialogUiState? = null) :
        ContactDetailsUiState2

    data class NoContactSelected(override val curDialogUiState: DialogUiState? = null) :
        ContactDetailsUiState2
}

class DefaultContactDetailsViewModel(
    private val parentScope: CoroutineScope,
    private val contactsRepo: SObjectSyncableRepo<ContactObject>,
    startingIds: SObjectCombinedId? = null,
//    startingPrimaryKey: PrimaryKey? = null,
//    startingLocalId: LocalId? = null,
) : ContactDetailsViewModel {

    private val eventMutex = Mutex()

    @Volatile
    private var upstreamIds: SObjectCombinedId? = startingIds

//    @Volatile
//    private var upstreamPrimaryKey: PrimaryKey? = startingPrimaryKey
//
//    @Volatile
//    private var upstreamLocalId: LocalId? = startingLocalId

    private val mutUiState: MutableStateFlow<ContactDetailsUiState2> = MutableStateFlow(
        ContactDetailsUiState2.InitialLoad()
    )

    override val uiState: StateFlow<ContactDetailsUiState2> get() = mutUiState

    init {
        parentScope.launch(Dispatchers.Default) {
            contactsRepo.records.collect { onContactListUpdate(it) }
        }
    }

    private suspend fun onContactListUpdate(newRecords: SObjectRecordsByIds<ContactObject>) {
        fun handleInitialLoadingState(newRecords: SObjectRecordsByIds<ContactObject>) {
            val upstreamIds = this.upstreamIds
            if (upstreamIds == null) {
                mutUiState.value is ContactDetailsUiState2.NoContactSelected
                return
            }

            val matchingContact = upstreamIds.locallyCreatedId?.let { newRecords.byLocallyCreatedId[it] }
                ?: newRecords.byPrimaryKey[upstreamIds.primaryKey]

            if (matchingContact == null) {
                Log.w(
                    TAG,
                    "Could not find record for starting id: $upstreamIds"
                )
                this.upstreamIds = null
                return
            }

            this.upstreamIds = matchingContact.buildCombinedId()

            mutUiState.value = ContactDetailsUiState2.HasContact(
                firstNameField = matchingContact.sObject.buildFirstNameField(),
                lastNameField = matchingContact.sObject.buildLastNameField(),
                titleField = matchingContact.sObject.buildTitleField(),
                departmentField = matchingContact.sObject.buildDepartmentField(),
                contactObjLocalStatus = matchingContact.localStatus,
                isEditingEnabled = false,
            )
        }

        fun updateUiWithNewRecords(
            newRecords: SObjectRecordsByIds<ContactObject>,
            curState: ContactDetailsUiState2.HasContact,
            curIds: SObjectCombinedId
        ) {

            val matchingContact = curIds.locallyCreatedId?.let { newRecords.byLocallyCreatedId[it] }
                ?: newRecords.byPrimaryKey[curIds.primaryKey]

            upstreamPrimaryKey = matchingContact.primaryKey
            upstreamLocalId = matchingContact.locallyCreatedId

            mutUiState.value = when {
                // not editing, so simply update all fields to match upstream emission:
                !curState.isEditingEnabled -> curState.copy(
                    firstNameField = matchingContact.sObject.buildFirstNameField(),
                    lastNameField = matchingContact.sObject.buildLastNameField(),
                    titleField = matchingContact.sObject.buildTitleField(),
                    departmentField = matchingContact.sObject.buildDepartmentField(),
                    contactObjLocalStatus = matchingContact.localStatus
                )

                // Upstream was deleted, but we have unsaved changes. Ask the user what they want to do:
                matchingContact.localStatus.isLocallyDeleted && hasUnsavedChanges -> curState.copy(
                    curDialogUiState = DiscardChangesOrUndeleteDialogUiState(
                        onDiscardChanges = { TODO() },
                        onUndelete = { TODO() }
                    )
                )

                else -> curState
            }
        }

        eventMutex.withLock {
            when (val curState = uiState.value) {
                is ContactDetailsUiState2.HasContact -> updateUiWithNewRecords(newRecords, curState)
                is ContactDetailsUiState2.InitialLoad -> handleInitialLoadingState(newRecords)
                is ContactDetailsUiState2.NoContactSelected -> return@withLock
            }
        }
    }

    @Throws(HasUnsavedChangesException::class)
    override suspend fun clearContactObj() {
        TODO("Not yet implemented")
    }

    @Throws(HasUnsavedChangesException::class)
    override suspend fun setContact(
        record: SObjectRecord<ContactObject>,
        startWithEditingEnabled: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override val hasUnsavedChanges: Boolean
        get() = TODO("Not yet implemented")

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
                /* First check if the local ID of the updated record matches the current local ID,
                 * and then fall back to the primary key. This is because the primary key can change
                 * after a locally-created record is synced up. */
                val updatedMatchesId =
                    (updatedRecord.locallyCreatedId != null && updatedRecord.locallyCreatedId == upstreamLocalId)
                            || updatedRecord.primaryKey == upstreamPrimaryKey

                // Sync the current record IDs if we found a match
                if (updatedMatchesId) {
                    upstreamPrimaryKey = updatedRecord.primaryKey
                    upstreamLocalId = updatedRecord.locallyCreatedId
                }

                when (val curState = uiState.value) {
                    is ContactDetailsUiState2.HasContact ->
                        if (updatedMatchesId) {
                            curState.copy(
                                firstNameField = updatedRecord.sObject.buildFirstNameField(),
                                lastNameField = updatedRecord.sObject.buildLastNameField(),
                                titleField = updatedRecord.sObject.buildTitleField(),
                                departmentField = updatedRecord.sObject.buildDepartmentField(),
                                dataOperationIsActive = false
                            )
                        } else {
                            curState.copy(dataOperationIsActive = false)
                        }

                    is ContactDetailsUiState2.InitialLoad,
                    is ContactDetailsUiState2.NoContactSelected -> curState
                }.let {
                    mutUiState.value = it
                }
            }
        }

        fun launchCreate(so: ContactObject) = parentScope.launch {
            // Do the update outside of the event lock
            val createdRecord = contactsRepo.locallyCreate(so = so)

            eventMutex.withLock {
                val shouldUpdateUi = upstreamPrimaryKey == null && upstreamLocalId == null
                if (shouldUpdateUi) {
                    upstreamPrimaryKey = createdRecord.primaryKey
                    upstreamLocalId = createdRecord.locallyCreatedId
                }

                when (val curState = uiState.value) {
                    is ContactDetailsUiState2.HasContact ->
                        if (shouldUpdateUi)
                            curState.copy(
                                firstNameField = createdRecord.sObject.buildFirstNameField(),
                                lastNameField = createdRecord.sObject.buildLastNameField(),
                                titleField = createdRecord.sObject.buildTitleField(),
                                departmentField = createdRecord.sObject.buildDepartmentField(),
                                dataOperationIsActive = false
                            )
                        else
                            curState.copy(dataOperationIsActive = false)

                    is ContactDetailsUiState2.InitialLoad,
                    is ContactDetailsUiState2.NoContactSelected -> curState

                }.let {
                    mutUiState.value = it
                }
            }
        }

        parentScope.launch {
            eventMutex.withLock {
                val curState = uiState.value.takeIfInstance<ContactDetailsUiState2.HasContact>()
                    ?: return@withLock

                val so = curState.buildSoFromUiState().getOrNull()
                if (so == null) {
                    mutUiState.value = curState.copy(shouldScrollToErrorField = true)
                    return@withLock
                }

                val primaryKey = upstreamPrimaryKey
                if (primaryKey == null) {
                    // Creating
                    mutUiState.value = curState.copy(dataOperationIsActive = true)
                    launchCreate(so = so)
                } else {
                    // Updating
                    mutUiState.value = curState.copy(dataOperationIsActive = true)
                    launchUpdate(id = primaryKey, so = so)
                }
            }
        }
    }

    private fun ContactDetailsUiState2.HasContact.buildSoFromUiState() = runCatching {
        ContactObject(
            firstName = firstNameField.fieldValue,
            lastName = lastNameField.fieldValue ?: "",
            title = titleField.fieldValue,
            department = departmentField.fieldValue
        )
    }

    override fun onFirstNameChange(newFirstName: String) {
        parentScope.launch {
            eventMutex.withLock {
                val curState = uiState.value.takeIfInstance<ContactDetailsUiState2.HasContact>()
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
                val curState = uiState.value.takeIfInstance<ContactDetailsUiState2.HasContact>()
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
                val curState = uiState.value.takeIfInstance<ContactDetailsUiState2.HasContact>()
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
                val curState = uiState.value.takeIfInstance<ContactDetailsUiState2.HasContact>()
                    ?: return@withLock

                mutUiState.value = curState.copy(
                    departmentField = curState.departmentField.copy(fieldValue = newDepartment)
                )
            }
        }
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
            var isInErrorStateProxy: Boolean
            var helperResProxy: Int?

            try {
                ContactObject.validateLastName(fieldValue)

                isInErrorStateProxy = false
                helperResProxy = null
            } catch (ex: ContactValidationException) {
                isInErrorStateProxy = true
                helperResProxy = when (ex) {
                    ContactValidationException.LastNameCannotBeBlank -> help_cannot_be_blank
                }
            }

            isInErrorState = isInErrorStateProxy
            helperRes = helperResProxy
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
