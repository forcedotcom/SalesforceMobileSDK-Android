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
package com.salesforce.samples.mobilesynccompose.model.contacts

import android.util.Log
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.target.SyncTarget.LOCAL
import com.salesforce.androidsdk.mobilesync.target.SyncTarget.LOCALLY_DELETED
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.samples.mobilesynccompose.core.SealedFailure
import com.salesforce.samples.mobilesynccompose.core.SealedResult
import com.salesforce.samples.mobilesynccompose.core.SealedSuccess
import com.salesforce.samples.mobilesynccompose.core.extensions.addOrReplaceAll
import com.salesforce.samples.mobilesynccompose.core.extensions.map
import com.salesforce.samples.mobilesynccompose.core.extensions.replaceAll
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The Contacts Repository. It exposes upserting, deleting, and undeleting [Contact] model objects
 * into SmartStore, and it supports the MobileSync operations.
 */
interface ContactsRepo {
    val contactUpdates: Flow<List<Contact>>
    val curUpstreamContacts: List<Contact>
    suspend fun sync(syncDownOnly: Boolean)
    suspend fun locallyUpsertContact(contact: Contact): SealedResult<Contact, Exception>
    suspend fun locallyDeleteContact(contact: Contact): SealedResult<Contact?, Exception>
    suspend fun locallyUndeleteContact(contact: Contact): SealedResult<Contact, Exception>
}

/**
 * The default implementation of the [ContactsRepo].
 */
class DefaultContactsRepo(
    account: UserAccount?, // TODO this shouldn't be nullable. The logic whether to instantiate this object should be moved higher up, but this is a quick fix to get things testable
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ContactsRepo {

    private val store = MobileSyncSDKManager.getInstance().getSmartStore(account)
    private val syncManager = SyncManager.getInstance(account)
    private val syncMutex = Mutex()
    private val listMutex = Mutex()

    @Volatile
    private var mutUpstreamContacts: List<Contact> = emptyList()
    override val curUpstreamContacts: List<Contact> get() = mutUpstreamContacts

    /* Simulating StateFlow, but using SharedFlow allows emissions of the same content without
     * conflation */
    private val mutContactUpdates = MutableSharedFlow<List<Contact>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val contactUpdates: Flow<List<Contact>> get() = mutContactUpdates

    init {
        mutContactUpdates.tryEmit(mutUpstreamContacts) // tryEmit will always succeed when BufferOverflow.DROP_OLDEST is specified.
    }

    override suspend fun sync(syncDownOnly: Boolean) = withContext(ioDispatcher) {
        data class SyncResults(val syncDownResult: SyncState, val syncUpResult: SyncState?)

        val result = syncMutex.withLock {
            val upResult = if (!syncDownOnly) syncUp() else null

            currentCoroutineContext().ensureActive() // cooperative cancellation

            if (!syncDownOnly && (upResult == null || !upResult.isDone)) {
                // sync up failed
                Log.e(TAG, "The Sync Up operation failed: $upResult")
                // TODO better handle this failure case
                return@withContext
            } else {
                SyncResults(syncDown(), upResult)
            }
        }

        currentCoroutineContext().ensureActive()

        when (result.syncDownResult.status) {
            SyncState.Status.DONE -> onSyncDownSuccess()
            SyncState.Status.FAILED -> onSyncDownFailed(result.syncDownResult)
            SyncState.Status.NEW,
            SyncState.Status.STOPPED,
            SyncState.Status.RUNNING,
            null -> {
                /* no-op */
            }
        }
    }

    override suspend fun locallyUpsertContact(contact: Contact) = withContext(ioDispatcher) {
        val result: SealedResult<Contact, Exception> = try {
            SealedSuccess(
                Contact.coerceFromJson(
                    // TODO Use compile-time constant for soup name
                    store.upsert("contacts", contact.toJson())
                )
            )
        } catch (ex: Exception) {
            SealedFailure(ex)
        }

        when (result) {
            is SealedFailure -> TODO("Got exception when upserting contact: ${result.cause}")
            is SealedSuccess -> {
                val updatedList = listMutex.withLock {
                    mutUpstreamContacts.addOrReplaceAll(newValue = result.value) {
                        it.id == result.value.id
                    }.also {
                        mutUpstreamContacts = it
                    }
                }
                mutContactUpdates.emit(updatedList)
            }
        }

        result
    }

    override suspend fun locallyDeleteContact(contact: Contact) = withContext(ioDispatcher) {
        val result: SealedResult<Contact?, Exception> = try {
            val soupEntryId = store.lookupSoupEntryId("contacts", Constants.ID, contact.id)
            if (soupEntryId < 0) {
                throw IllegalStateException("Tried to locally delete contact, but soup ID was not found.  Contact = $contact")
            }

            if (contact.locallyCreated) {
                store.delete("contacts", soupEntryId)
                SealedSuccess(null)
            } else {
                locallyDeleteUpstreamContact(contact, soupEntryId)
            }
        } catch (ex: Exception) {
            SealedFailure(cause = ex)
        }

        when (result) {
            is SealedFailure -> TODO("Got exception when deleting contact: ${result.cause}")
            is SealedSuccess -> {
                val updatedList = listMutex.withLock {
                    if (result.value == null) {
                        mutUpstreamContacts.filterNot { it.id == contact.id }
                    } else {
                        mutUpstreamContacts
                            .replaceAll(newValue = result.value) { it.id == result.value.id }
                    }.also {
                        mutUpstreamContacts = it
                    }
                }
                mutContactUpdates.emit(updatedList)
            }
        }

        result
    }

    private fun locallyDeleteUpstreamContact(
        upstreamContact: Contact,
        soupEntryId: Long
    ): SealedResult<Contact, Exception> {
        val newContact =
            store.update(
                "contacts",
                upstreamContact.toJson()
                    .putOpt(LOCALLY_DELETED, true)
                    .putOpt(LOCAL, true),
                soupEntryId
            )

        return SealedSuccess(Contact.coerceFromJson(newContact))
    }

    override suspend fun locallyUndeleteContact(contact: Contact) = withContext(ioDispatcher) {
        val soupEntryId = store.lookupSoupEntryId("contacts", Constants.ID, contact.id)
        if (soupEntryId < 0) {
            throw IllegalStateException("Tried to locally delete contact, but soup ID was not found.  Contact = $contact")
        }

        val updatedContactResult: SealedResult<Contact, Exception> = try {
            store.update(
                "contacts",
                contact.toJson()
                    .putOpt(LOCALLY_DELETED, false)
                    .putOpt(LOCAL, contact.locallyCreated || contact.locallyUpdated),
                soupEntryId
            ).let {
                SealedSuccess(value = Contact.coerceFromJson(it))
            }
        } catch (ex: Exception) {
            SealedFailure(cause = ex)
        }

        when (updatedContactResult) {
            is SealedFailure -> TODO()
            is SealedSuccess -> {
                val listToEmit = listMutex.withLock {
                    mutUpstreamContacts.replaceAll(newValue = updatedContactResult.value) {
                        it.id == updatedContactResult.value.id
                    }.also {
                        mutUpstreamContacts = it
                    }
                }
                mutContactUpdates.emit(listToEmit)
            }
        }

        updatedContactResult
    }

    // Individual syncs cannot be cancelled, so we don't use suspendCancellableCoroutine
    private suspend fun syncDown(): SyncState = suspendCoroutine { cont ->
        // TODO: will MobileSync use the same instance of the callback (this coroutine block) in the STOPPED -> RUNNING -> DONE flow?
        val callback: (SyncState) -> Unit = {
            when (it.status) {
                // terminal states
                SyncState.Status.DONE,
                SyncState.Status.FAILED -> cont.resume(it)

                SyncState.Status.NEW,
                SyncState.Status.RUNNING,
                SyncState.Status.STOPPED,
                null -> {
                    /* no-op; suspending for terminal state */
                }
            }
        }
        // TODO Replace this raw string with compile time constant:
        syncManager.reSync("syncDownContacts", callback)
    }

    // Individual syncs cannot be cancelled, so we don't use suspendCancellableCoroutine
    private suspend fun syncUp(): SyncState = suspendCoroutine { cont ->
        val callback: (SyncState) -> Unit = {
            when (it.status) {
                // terminal states
                SyncState.Status.DONE,
                SyncState.Status.FAILED,
                SyncState.Status.STOPPED -> cont.resume(it)

                SyncState.Status.NEW,
                SyncState.Status.RUNNING,
                null -> {
                    /* no-op; suspending for terminal state */
                }
            }
        }
        // TODO Replace this raw string with compile time constant:
        syncManager.reSync("syncUpContacts", callback)
    }

    private suspend fun onSyncDownSuccess() {
        val contactResults = store.query(
            QuerySpec.buildAllQuerySpec(
                "contacts",
                null,
                null,
                10_000
            ),
            0
        )
        val contacts = contactResults.map { Contact.coerceFromJson(it) }
        mutUpstreamContacts = contacts
        mutContactUpdates.emit(contacts)
    }

    private fun onSyncDownFailed(syncDownResult: SyncState) {
        Log.e(TAG, "onSyncDownFailed() syncDownResult = $syncDownResult")
    }

    private companion object {
        private const val TAG = "DefaultContactsRepo"
    }
}
