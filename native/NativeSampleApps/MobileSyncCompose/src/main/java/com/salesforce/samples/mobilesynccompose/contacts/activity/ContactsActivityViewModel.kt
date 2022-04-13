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
package com.salesforce.samples.mobilesynccompose.contacts.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.*
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListDataOpHandler
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListItemClickHandler
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListViewModel
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.DefaultContactsListViewModel
import com.salesforce.samples.mobilesynccompose.core.extensions.requireIsLocked
import com.salesforce.samples.mobilesynccompose.core.ui.state.DiscardChangesDialogUiState
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactsRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ContactsActivityViewModel {
    val uiState: StateFlow<ContactsActivityUiState>
    val detailsVm: ContactDetailsViewModel
    val listVm: ContactsListViewModel

    fun sync(syncDownOnly: Boolean = false)
}

class DefaultContactsActivityViewModel(
    private val contactsRepo: ContactsRepo
) : ViewModel(), ContactsActivityViewModel {

    override val detailsVm: ContactDetailsViewModel by lazy {
        DefaultContactDetailsViewModel(
            parentScope = viewModelScope,
            contactsRepo = contactsRepo
        )
    }

    override val listVm: ContactsListViewModel by lazy {
        DefaultContactsListViewModel(
            parentScope = viewModelScope,
            contactsRepo = contactsRepo,
            itemClickDelegate = ListClickDelegate(),
            dataOpDelegate = ListDataOpDelegate(),
            searchTermUpdatedDelegate = null
        )
    }

    private val stateMutex = Mutex()
    private val mutUiState = MutableStateFlow(
        ContactsActivityUiState(isSyncing = false, dialogUiState = null)
    )

    override val uiState: StateFlow<ContactsActivityUiState> get() = mutUiState

    override fun sync(syncDownOnly: Boolean) {
        viewModelScope.launch {
            stateMutex.withLock { mutUiState.value = uiState.value.copy(isSyncing = true) }
            if (syncDownOnly) {
                contactsRepo.syncDownOnly()
            } else {
                contactsRepo.syncUpAndDown()
            }
            stateMutex.withLock { mutUiState.value = uiState.value.copy(isSyncing = false) }
        }
    }

    /* If you look closely, you will see that this class' click handlers are running suspending setter
     * methods _within_ the state locks. This should be alarming because this can lead to unresponsive
     * UI, but in this case it is necessary.
     *
     * The user expects atomic operations when they perform a UI action that would change the state
     * of multiple coupled components. If we allow more than one action to contend for the components'
     * setters, it opens up the possibility of inconsistent state, which is deemed here to be worse
     * than "dropping clicks."
     *
     * Note that the UI _thread_ is not locked up during this. All suspending APIs are main-safe, so
     * the UI thread can continue to render and there will be no ANRs. The fact of the matter is:
     * if a component is locked up and cannot finish the set operation, the user must wait until
     * that component cooperates with the setter until they are allowed to take another action.
     *
     * In almost all cases, this will not even be an issue. */
    private inner class ListClickDelegate : ContactsListItemClickHandler {
        override fun contactClick(contactId: String) = launchWithStateLock {
            try {
                detailsVm.setContactOrThrow(recordId = contactId, isEditing = false)
                listVm.setSelectedContact(id = contactId)
            } catch (ex: ContactDetailsException) {
                mutUiState.value = when (ex) {
                    is DataOperationActiveException -> TODO("data op active in contactClick()")
                    is HasUnsavedChangesException -> uiState.value.copy(
                        dialogUiState = DiscardChangesDialogUiState(
                            onDiscardChanges = {
                                forceSetContact(contactId = contactId, isEditing = false)
                                launchWithStateLock { dismissCurDialog() }
                            },
                            onKeepChanges = { launchWithStateLock { dismissCurDialog() } }
                        )
                    )
                }
            }
        }

        private fun forceSetContact(contactId: String?, isEditing: Boolean) = launchWithStateLock {
            try {
                detailsVm.discardChangesAndSetContactOrThrow(
                    recordId = contactId,
                    isEditing = isEditing,
                )
            } catch (ex: DataOperationActiveException) {
                TODO("data op is active in forceSetContact")
            }
        }

        override fun createClick() = launchWithStateLock {
            try {
                detailsVm.setContactOrThrow(recordId = null, isEditing = true)
                listVm.setSelectedContact(id = null)
            } catch (ex: ContactDetailsException) {
                stateMutex.withLock {
                    mutUiState.value = when (ex) {
                        is DataOperationActiveException -> TODO("data op active in createClick()")
                        is HasUnsavedChangesException -> uiState.value.copy(
                            dialogUiState = DiscardChangesDialogUiState(
                                onDiscardChanges = {
                                    forceSetContact(contactId = null, isEditing = true)
                                    launchWithStateLock { dismissCurDialog() }
                                },
                                onKeepChanges = { launchWithStateLock { dismissCurDialog() } }
                            )
                        )
                    }
                }
            }
        }

        override fun editClick(contactId: String) = launchWithStateLock {
            try {
                detailsVm.setContactOrThrow(recordId = contactId, isEditing = true)
                listVm.setSelectedContact(id = contactId)
            } catch (ex: ContactDetailsException) {
                stateMutex.withLock {
                    mutUiState.value = when (ex) {
                        is DataOperationActiveException -> TODO("data op active in editClick()")
                        is HasUnsavedChangesException -> uiState.value.copy(
                            dialogUiState = DiscardChangesDialogUiState(
                                onDiscardChanges = {
                                    forceSetContact(contactId = contactId, isEditing = true)
                                    launchWithStateLock { dismissCurDialog() }
                                },
                                onKeepChanges = { launchWithStateLock { dismissCurDialog() } }
                            )
                        )
                    }
                }
            }
        }
    }

    private inner class ListDataOpDelegate : ContactsListDataOpHandler {
        override fun deleteClick(contactId: String) {
            TODO("Not yet implemented")
        }

        override fun undeleteClick(contactId: String) {
            TODO("Not yet implemented")
        }
    }


    // region Utilities


    /**
     * Convenience method to wrap a method body in a coroutine which acquires the event mutex lock
     * before executing the [block]. Because this launches a new coroutine, it is okay to nest
     * invocations of this method within other [launchWithStateLock] blocks without worrying about
     * deadlocks.
     *
     * Note! If you nest [launchWithStateLock] calls, the outer [launchWithStateLock] [block]
     * will run to completion _before_ the nested [block] is invoked since the outer [block] already
     * has the event lock.
     */
    private fun launchWithStateLock(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch { stateMutex.withLock { block() } }
    }

    private fun dismissCurDialog() {
        stateMutex.requireIsLocked()
        mutUiState.value = mutUiState.value.copy(dialogUiState = null)
    }

    private companion object {
        private const val TAG = "DefaultContactsActivityViewModel"
    }


    // endregion
}
