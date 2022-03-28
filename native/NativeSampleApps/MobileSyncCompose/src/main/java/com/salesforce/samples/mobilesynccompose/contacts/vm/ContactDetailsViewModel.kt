package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.contacts.state.*
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactDetailsUiMode.Viewing
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectSyncableRepo
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectsByIds
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.LocalStatus
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectId
import com.salesforce.samples.mobilesynccompose.core.ui.state.DialogUiState
import com.salesforce.samples.mobilesynccompose.core.vm.TextFieldViewModel
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ContactDetailsViewModel : ContactObjectFieldChangeHandler, ContactDetailsUiEventHandler {
    val uiState: StateFlow<ContactDetailsUiState2?>

    @Throws(HasUnsavedChangesException::class)
    suspend fun clearContactObj()

    @Throws(HasUnsavedChangesException::class)
    suspend fun setContactObj(obj: ContactObject, mode: ContactDetailsUiMode)
}

data class HasUnsavedChangesException(override val message: String?) : Exception()

interface ContactDetailsUiEventHandler {
    fun deleteClick()
    fun undeleteClick()
    fun editClick()
    fun exitClick()
}

data class ContactDetailsUiState2(
    val vmList: List<TextFieldViewModel>,
    val contactObjLocalStatus: LocalStatus,
    val mode: ContactDetailsUiMode,
    val isSaving: Boolean = false,
    val shouldScrollToErrorField: Boolean = false,
    val curDialogUiState: DialogUiState? = null
)

class DefaultContactDetailsViewModel(
    private val parentScope: CoroutineScope,
    private val contactsRepo: SObjectSyncableRepo<ContactObject>,
    startingContact: ContactObject?,
    startingMode: ContactDetailsUiMode = Viewing,
) : ContactDetailsViewModel {

    private val eventMutex = Mutex()

    private val mutUiState = MutableStateFlow(
        startingContact?.let {
            ContactDetailsUiState2(
                vmList = listOf(
                    startingContact.createFirstNameVm(::onFirstNameChange),
                    startingContact.createLastNameVm(::onLastNameChange),
                    startingContact.createTitleVm(::onTitleChange),
                    startingContact.createDepartmentVm(::onDepartmentChange)
                ),
                contactObjLocalStatus = it.localStatus,
                mode = startingMode
            )
        }
    )
    override val uiState: StateFlow<ContactDetailsUiState2?> get() = mutUiState

    @Volatile
    private var curContact: ContactObject? = startingContact

//    @Volatile
//    private var curContactsByPrimaryKey = mapOf<String, ContactObject>()
//
//    @Volatile
//    private var curContactsByLocalId = mapOf<String, ContactObject>()

    init {
        parentScope.launch {
            contactsRepo.curSObjects.collect { onContactListUpdate(it) }
        }
    }

    private suspend fun onContactListUpdate(newObjects: SObjectsByIds<ContactObject>) =
        eventMutex.withLock {
            val contact = curContact ?: return@withLock
            val matchingContact = contact.id.localId?.let { newObjects.byLocalId[it] }
                ?: newObjects.byPrimaryKey[contact.id.primaryKey]
                ?: return@withLock
        }

    @Throws(HasUnsavedChangesException::class)
    override suspend fun clearContactObj() {
        TODO("Not yet implemented")
    }

    @Throws(HasUnsavedChangesException::class)
    override suspend fun setContactObj(obj: ContactObject, mode: ContactDetailsUiMode) {
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

    override fun onFirstNameChange(id: SObjectId, newFirstName: String?) {
        TODO("Not yet implemented")
    }

    override fun onLastNameChange(id: SObjectId, newLastName: String?) {
        TODO("Not yet implemented")
    }

    override fun onTitleChange(id: SObjectId, newTitle: String?) {
        TODO("Not yet implemented")
    }

    override fun onDepartmentChange(id: SObjectId, newDepartment: String?) {
        TODO("Not yet implemented")
    }
}
