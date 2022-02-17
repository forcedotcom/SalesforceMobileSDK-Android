package com.salesforce.samples.mobilesynccompose.contacts.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactEditModeEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactViewModeEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsSearchEventHandler
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface ContactsActivityViewModel :
    ContactsListEventHandler,
    ContactsSearchEventHandler,
    ContactEditModeEventHandler,
    ContactViewModeEventHandler {

    val uiState: StateFlow<ContactsActivityUiState>
    fun sync(syncDownOnly: Boolean = false)
}

data class ContactsActivityUiState(
    val contacts: List<Contact>,
    val detailsState: ContactDetailsUiState?,
    val searchTerm: String?,
    val isSyncing: Boolean,
    val showDiscardChanges: Boolean
)

class DefaultContactsActivityViewModel(
    private val contactsRepo: ContactsRepo
) : ViewModel(), ContactsActivityViewModel {

    private val eventMutex = Mutex()
    private val mutUiState = MutableStateFlow(
        ContactsActivityUiState(
            contacts = emptyList(),
            detailsState = null,
            searchTerm = null,
            isSyncing = false,
            showDiscardChanges = false
        )
    )
    override val uiState: StateFlow<ContactsActivityUiState> get() = mutUiState

    init {
        viewModelScope.launch {
            contactsRepo.contactUpdates.collect {
                onContactListUpdate(it)
            }
        }
    }

    private fun onContactListUpdate(newList: List<Contact>) = withEventLock {
        val curState = uiState.value
        if (curState.detailsState != null) {
            when (curState.detailsState.mode) {
                ContactDetailsUiMode.Creating -> TODO()
                ContactDetailsUiMode.Editing -> {
                    TODO("have to check if saving")
//                    val matchingContact = newList.parallelFirstOrNull {
//                        it.id == curState.detailsState.origContact.id
//                    }
//                    when (matchingContact) {
//                        null,
//                        curState.detailsState.origContact -> {
//                            /* no-op */
//                        }
//                        else
//                    }
                }
                ContactDetailsUiMode.Viewing -> TODO()
            }
        } else {
            mutUiState.value = curState.copy(contacts = newList, isSyncing = false)
        }
    }

    override fun sync(syncDownOnly: Boolean) = withEventLock {
        mutUiState.value = mutUiState.value.copy(isSyncing = true)
        contactsRepo.sync(syncDownOnly)
    }

    override fun listContactClick(contact: Contact) = withEventLock {
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            TODO("$TAG - listContactClick show discard changes")
        } else {
            mutUiState.value = curState.copy(
                detailsState = contact.toContactDetailsUiState(mode = ContactDetailsUiMode.Viewing),
            )
        }
    }

    override fun listCreateClick() = withEventLock {
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            TODO("$TAG - listCreateClick show discard changes")
        } else {
            mutUiState.value = curState.copy(
                detailsState = Contact.createNewLocal()
                    .toContactDetailsUiState(mode = ContactDetailsUiMode.Creating),
            )
        }
    }

    override fun listDeleteClick(contact: Contact) = withEventLock {
        TODO("Not yet implemented")
    }

    override fun listEditClick(contact: Contact) = withEventLock {
        val curState = mutUiState.value

        if (curState.detailsState != null && curState.detailsState.isModified) {
            // editing contact, so ask to discard changes
            TODO("$TAG - listEditClick show discard changes")
        } else {
            mutUiState.value = curState.copy(
                detailsState = contact.toContactDetailsUiState(mode = ContactDetailsUiMode.Editing),
            )
        }
    }

    override fun exitSearch() = withEventLock {
        mutUiState.value = mutUiState.value.copy(
            contacts = contactsRepo.curUpstreamContacts,
            searchTerm = null
        )
    }

    override fun searchClick() = withEventLock {
        mutUiState.value = mutUiState.value.copy(searchTerm = "")
    }

    // TODO make the filtering a Job and cancel it on new UI events to avoid UI state change hanging
    override fun searchTermUpdated(newSearchTerm: String) = withEventLock {
        mutUiState.value = mutUiState.value.copy(
            contacts = contactsRepo.curUpstreamContacts.parallelFilter {
                it.fullName.contains(
                    newSearchTerm,
                    ignoreCase = true
                )
            },
            searchTerm = newSearchTerm
        )
//        val upstreamContacts = contactsRepo.curUpstreamContacts
//
//        val filterBlock: () -> List<Contact> = {
//            upstreamContacts.filter {
//                it.fullName.contains(newSearchTerm, ignoreCase = true)
//            }
//        }
//
//        // Don't bother doing expensive context-switching unless we would get a perf benefit:
//        if (upstreamContacts.size > 100) {
//            withContext(Dispatchers.Default) {
//                mutUiState.value = mutUiState.value.copy(
//                    contacts = filterBlock(),
//                    searchTerm = newSearchTerm
//                )
//            }
//        } else {
//            mutUiState.value = mutUiState.value.copy(
//                contacts = filterBlock(),
//                searchTerm = newSearchTerm
//            )
//        }
    }

    private suspend fun <T> List<T>.parallelFilter(
        thresholdSize: UInt = 100u,
        predicate: (T) -> Boolean
    ): List<T> {
        return if (size > thresholdSize.toInt()) {
            withContext(Dispatchers.Default) {
                filter(predicate)
            }
        } else {
            filter(predicate)
        }
    }

    private suspend fun <T> List<T>.parallelFirstOrNull(
        thresholdSize: UInt = 100u,
        predicate: (T) -> Boolean
    ): T? {
        return if (size > thresholdSize.toInt()) {
            withContext(Dispatchers.Default) {
                firstOrNull(predicate)
            }
        } else {
            firstOrNull(predicate)
        }
    }

    override fun detailsDeleteClick() = withEventLock {
        TODO("Not yet implemented")
    }

    override fun detailsExitClick() = withEventLock {
        val curState = mutUiState.value

        if (curState.detailsState == null) {
            return@withEventLock
        }

        when (curState.detailsState.mode) {
            ContactDetailsUiMode.Creating -> {
                if (curState.detailsState.isModified) {
                    TODO("Exit click when modified")
                } else {
                    mutUiState.value = curState.copy(detailsState = null)
                }
            }

            ContactDetailsUiMode.Editing -> {
                if (curState.detailsState.isModified) {
                    TODO("Exit click when modified")
                } else {
                    mutUiState.value = curState.copy(
                        detailsState = curState.detailsState.copy(mode = ContactDetailsUiMode.Viewing),
                    )
                }
            }

            ContactDetailsUiMode.Viewing -> {
                mutUiState.value = curState.copy(detailsState = null)
            }
        }
    }

    override fun onDetailsUpdated(newContact: Contact) = withEventLock {
        val curState = mutUiState.value
        if (curState.detailsState == null)
            return@withEventLock

        mutUiState.value = curState.copy(
            detailsState = curState.detailsState.copy(
                firstNameVm = newContact.createFirstNameVm(),
                lastNameVm = newContact.createLastNameVm(),
                titleVm = newContact.createTitleVm()
            )
        )
    }

    override fun saveClick() = withEventLock {
        val curState = mutUiState.value
        val curDetail = curState.detailsState ?: return@withEventLock

        if (curDetail.hasFieldsInErrorState) {
            mutUiState.value = curState.copy(
                detailsState = curDetail.copy(
                    fieldToScrollTo = curDetail.fieldsInErrorState.firstOrNull()
                )
            )
            return@withEventLock
        }

        val newUiState = if (!curDetail.isModified) {
            when (curDetail.mode) {
                ContactDetailsUiMode.Creating -> {
                    // TODO Maybe add a Toast or something to inform the user why nothing happened
                    /* no-op */
                    curState
                }

                ContactDetailsUiMode.Viewing -> {
                    Log.w(
                        TAG,
                        "Got a saveClick event while in Viewing mode. This shouldn't happen and is probably a bug."
                    )
                    curState
                }

                ContactDetailsUiMode.Editing -> {
                    curState.copy(detailsState = curDetail.copy(mode = ContactDetailsUiMode.Viewing))
                }
            }
        } else {
            when (curDetail.mode) {
                ContactDetailsUiMode.Creating -> TODO()
                ContactDetailsUiMode.Editing -> {
                    viewModelScope.launch {
                        contactsRepo.saveContact(curDetail.updatedContact)
                    }

                    curState.copy(detailsState = curDetail.copy(isSaving = true))
                }
                ContactDetailsUiMode.Viewing -> {
                    Log.w(
                        TAG,
                        "Got a saveClick event while in Viewing mode. This shouldn't happen and is probably a bug."
                    )
                    curState
                }
            }
        }

        mutUiState.value = newUiState
    }

    override fun detailsEditClick() {
        TODO("Not yet implemented")
    }

    private fun withEventLock(block: suspend () -> Unit) {
        viewModelScope.launch { eventMutex.withLock { block() } }
    }

    private companion object {
        private const val TAG = "DefaultContactsActivityViewModel"
    }
}
