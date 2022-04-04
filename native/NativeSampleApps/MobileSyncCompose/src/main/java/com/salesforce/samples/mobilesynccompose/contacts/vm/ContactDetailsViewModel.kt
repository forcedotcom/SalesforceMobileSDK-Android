package com.salesforce.samples.mobilesynccompose.contacts.vm

import android.util.Log
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.core.extensions.takeIfInstance
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectSyncableRepo
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectsByIds
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.LocalId
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.LocalStatus
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.PrimaryKey
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
    suspend fun setContactObj(obj: ContactObject, startWithEditingEnabled: Boolean)
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

sealed interface ContactDetailsUiState2 {
    data class HasContact(
        val fields: List<FieldUiState>,
        val contactObjLocalStatus: LocalStatus,
        val isEditingEnabled: Boolean,
        val dataOperationIsActive: Boolean = false,
        val shouldScrollToErrorField: Boolean = false,
        val curDialogUiState: DialogUiState? = null
    ) : ContactDetailsUiState2

    object InitialLoad : ContactDetailsUiState2
    object NoContactSelected : ContactDetailsUiState2

    data class PersonalInfoFields(
        val firstNameField: ContactDetailsField.FirstName,
        val lastNameField: ContactDetailsField.LastName
    ) {
        val allFields: List<ContactDetailsField> = listOf(firstNameField, lastNameField)
        val fullName = buildString {
            firstNameField.fieldValue?.let { append("$it ") }
            lastNameField.fieldValue?.let { append(it) }
        }.trim()
    }

    data class BusinessInfoFields(
        val titleField: ContactDetailsField.Title,
        val departmentField: ContactDetailsField.Department
    ) {
        val allFields: List<ContactDetailsField> = listOf(titleField, departmentField)
    }
}

class DefaultContactDetailsViewModel(
    private val parentScope: CoroutineScope,
    private val contactsRepo: SObjectSyncableRepo<ContactObject>,
    startingPrimaryKey: PrimaryKey? = null,
    startingLocalId: LocalId? = null,
) : ContactDetailsViewModel {

    private val eventMutex = Mutex()

    @Volatile
    private var upstreamPrimaryKey: PrimaryKey? = startingPrimaryKey

    @Volatile
    private var upstreamLocalId: LocalId? = startingLocalId

    private val mutUiState: MutableStateFlow<ContactDetailsUiState2> = MutableStateFlow(
        ContactDetailsUiState2.InitialLoad
    )

    override val uiState: StateFlow<ContactDetailsUiState2> get() = mutUiState

    init {
        parentScope.launch(Dispatchers.Default) {
            contactsRepo.records.collect { onContactListUpdate(it) }
        }
    }

    private suspend fun onContactListUpdate(newObjects: SObjectsByIds<ContactObject>) {
        fun handleInitialLoadingState(newObjects: SObjectsByIds<ContactObject>) {
            if (upstreamLocalId == null && upstreamPrimaryKey == null) {
                mutUiState.value = ContactDetailsUiState2.NoContactSelected
                return
            }

            val matchingContact = upstreamLocalId?.let { newObjects.byLocalId[it] }
                ?: upstreamPrimaryKey?.let { newObjects.byPrimaryKey[it] }

            if (matchingContact == null) {
                Log.w(
                    TAG,
                    "Could not find record for starting PrimaryKey=$upstreamPrimaryKey + LocalId=$upstreamLocalId"
                )
                upstreamLocalId = null
                upstreamPrimaryKey = null
                return
            }

            upstreamPrimaryKey = matchingContact.primaryKey
            upstreamLocalId = matchingContact.localId

            mutUiState.value = ContactDetailsUiState2.HasContact(
                personalInfoFields = matchingContact.model.buildPersonalInfoFields(),
                businessFields = matchingContact.model.buildBusinessInfoFields(),
                contactObjLocalStatus = matchingContact.localStatus,
                isEditingEnabled = false,
            )
        }

        fun updateUiWithNewRecords(
            newObjects: SObjectsByIds<ContactObject>,
            curState: ContactDetailsUiState2.HasContact
        ) {
            val matchingContact = upstreamLocalId?.let { newObjects.byLocalId[it] }
                ?: upstreamPrimaryKey?.let { newObjects.byPrimaryKey[it] }
                ?: return // no matching contact, so do nothing

            /* This syncs the two keys in an atomic operation so that they are guaranteed to be
             * consistent when needed by other instance methods. Setting them ONLY here is the
             * simplest way to keep everything consistent. */
            upstreamPrimaryKey = matchingContact.primaryKey
            upstreamLocalId = matchingContact.localId

            // If in Editing mode, keep user's changes; else simply update all fields to match new emission
            if (!curState.isEditingEnabled) {
                mutUiState.value = curState.copy(
                    personalInfoFields = matchingContact.model.buildPersonalInfoFields(),
                    businessFields = matchingContact.model.buildBusinessInfoFields(),
                )
            }
        }

        eventMutex.withLock {
            when (val curState = uiState.value) {
                is ContactDetailsUiState2.HasContact -> updateUiWithNewRecords(newObjects, curState)
                ContactDetailsUiState2.InitialLoad -> handleInitialLoadingState(newObjects)
                ContactDetailsUiState2.NoContactSelected -> return@withLock
            }
        }
    }

    @Throws(HasUnsavedChangesException::class)
    override suspend fun clearContactObj() {
        TODO("Not yet implemented")
    }

    @Throws(HasUnsavedChangesException::class)
    override suspend fun setContactObj(obj: ContactObject, startWithEditingEnabled: Boolean) {
        TODO("Not yet implemented")
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
                /* First check if the local ID of the updated record matches the current local ID,
                 * and then fall back to the primary key. This is because the primary key can change
                 * after a locally-created record is synced up. */
                val updatedMatchesId =
                    (updatedRecord.localId != null && updatedRecord.localId == upstreamLocalId)
                            || updatedRecord.primaryKey == upstreamPrimaryKey

                // Sync the current record IDs if we found a match
                if (updatedMatchesId) {
                    upstreamPrimaryKey = updatedRecord.primaryKey
                    upstreamLocalId = updatedRecord.localId
                }

                when (val curState = uiState.value) {
                    is ContactDetailsUiState2.HasContact ->
                        if (updatedMatchesId) {
                            curState.copy(
                                personalInfoFields = updatedRecord.model.buildPersonalInfoFields(),
                                businessFields = updatedRecord.model.buildBusinessInfoFields(),
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
                    upstreamLocalId = createdRecord.localId
                }

                when (val curState = uiState.value) {
                    is ContactDetailsUiState2.HasContact ->
                        if (shouldUpdateUi)
                            curState.copy(
                                personalInfoFields = createdRecord.model.buildPersonalInfoFields(),
                                businessFields = createdRecord.model.buildBusinessInfoFields(),
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
            firstName = personalInfoFields.firstNameField.fieldValue,
            lastName = personalInfoFields.lastNameField.fieldValue ?: "",
            title = businessFields.titleField.fieldValue,
            department = businessFields.departmentField.fieldValue
        )
    }

    override fun onFirstNameChange(newFirstName: String) {
        parentScope.launch {
            eventMutex.withLock {
                val curState = uiState.value.takeIfInstance<ContactDetailsUiState2.HasContact>()
                    ?: return@withLock

                mutUiState.value = curState.copy(
                    personalInfoFields = curState.personalInfoFields.copy(
                        firstNameField = curState.personalInfoFields.firstNameField.copy(
                            fieldValue = newFirstName,
                        )
                    )
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
                    personalInfoFields = curState.personalInfoFields.copy(
                        lastNameField = curState.personalInfoFields.lastNameField.copy(
                            fieldValue = newLastName,
                        )
                    )
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
                    businessFields = curState.businessFields.copy(
                        titleField = curState.businessFields.titleField.copy(
                            fieldValue = newTitle
                        )
                    ),
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
                    businessFields = curState.businessFields.copy(
                        departmentField = curState.businessFields.departmentField.copy(
                            fieldValue = newDepartment
                        )
                    ),
                )
            }
        }
    }

    private fun ContactObject.buildPersonalInfoFields() = ContactDetailsUiState2.PersonalInfoFields(
        firstNameField = ContactDetailsField.FirstName(
            fieldValue = firstName,
            onValueChange = this@DefaultContactDetailsViewModel::onFirstNameChange
        ),
        lastNameField = ContactDetailsField.LastName(
            fieldValue = lastName,
            onValueChange = this@DefaultContactDetailsViewModel::onLastNameChange
        ),
    )

    private fun ContactObject.buildBusinessInfoFields() = ContactDetailsUiState2.BusinessInfoFields(
        titleField = ContactDetailsField.Title(
            fieldValue = title,
            onValueChange = this@DefaultContactDetailsViewModel::onTitleChange
        ),
        departmentField = ContactDetailsField.Department(
            fieldValue = department,
            onValueChange = this@DefaultContactDetailsViewModel::onDepartmentChange
        ),
    )

    private companion object {
        private const val TAG = "DefaultContactDetailsViewModel"
    }
}

//sealed interface ContactDetailsField2 {
//    fun toTextFieldUiState(): TextFieldUiState
//
//    @JvmInline
//    value class FirstName(val value: String?) : ContactDetailsField2 {
//        override fun toTextFieldUiState(): TextFieldUiState = object : TextFieldUiState {
//            override val maxLines: UInt
//                get() = TODO("Not yet implemented")
//            override val fieldValue: String?
//                get() = TODO("Not yet implemented")
//            override val isInErrorState: Boolean
//                get() = TODO("Not yet implemented")
//            override val isEnabled: Boolean
//                get() = TODO("Not yet implemented")
//            override val labelRes: Int?
//                get() = TODO("Not yet implemented")
//            override val helperRes: Int?
//                get() = TODO("Not yet implemented")
//            override val placeholderRes: Int?
//                get() = TODO("Not yet implemented")
//        }
//    }
//
//    @JvmInline
//    value class LastName(val value: String?) : ContactDetailsField2 {
//        override fun toTextFieldUiState(): TextFieldUiState {
//            TODO("Not yet implemented")
//        }
//    }
//
//    @JvmInline
//    value class Title(val value: String?) : ContactDetailsField2 {
//        override fun toTextFieldUiState(): TextFieldUiState {
//            TODO("Not yet implemented")
//        }
//    }
//
//    @JvmInline
//    value class Department(val value: String?) : ContactDetailsField2 {
//        override fun toTextFieldUiState(): TextFieldUiState {
//            TODO("Not yet implemented")
//        }
//    }
//}

sealed interface ContactDetailsField : FieldUiState {
    data class FirstName(
        override val fieldValue: String?,
        override val onValueChange: (newValue: String) -> Unit,
        override val isEnabled: Boolean = true,
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
        override val isEnabled: Boolean = true,
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
        override val isEnabled: Boolean = true,
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
        override val isEnabled: Boolean = true,
        override val maxLines: UInt = 1u
    ) : ContactDetailsField, EditableTextFieldUiState {
        override val isInErrorState: Boolean = false
        override val labelRes: Int = label_contact_department
        override val helperRes: Int? = null
        override val placeholderRes: Int = label_contact_department
    }
}
