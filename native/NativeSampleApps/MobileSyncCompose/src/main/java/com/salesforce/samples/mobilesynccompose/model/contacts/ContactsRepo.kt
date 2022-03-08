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
import com.salesforce.samples.mobilesynccompose.core.extensions.firstOrNull
import com.salesforce.samples.mobilesynccompose.core.extensions.map
import com.salesforce.samples.mobilesynccompose.core.extensions.replaceAll
import com.salesforce.samples.mobilesynccompose.core.extensions.replaceAllOrAddNew
import com.salesforce.samples.mobilesynccompose.core.mapSuccess
import com.salesforce.samples.mobilesynccompose.model.SyncFailure
import com.salesforce.samples.mobilesynccompose.model.SyncNotStarted
import com.salesforce.samples.mobilesynccompose.model.SyncDownAndUpResults
import com.salesforce.samples.mobilesynccompose.model.SyncRuntimeFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The Contacts Repository. It exposes upserting, deleting, and undeleting [Contact] model objects
 * into SmartStore, and it supports the MobileSync operations.
 */
interface ContactsRepo {
    /**
     *
     */
    val curContactList: StateFlow<List<Contact>>
    suspend fun syncDownOnly(): SealedResult<Unit, SyncFailure>
    suspend fun syncUpAndDown(): SyncDownAndUpResults
    suspend fun syncUpOnly(): SealedResult<Unit, SyncFailure>
    suspend fun locallyUpsertContact(contact: Contact): SealedResult<Contact, Exception>
    suspend fun locallyDeleteContact(contactId: String): SealedResult<Contact, Exception>
    suspend fun locallyUndeleteContact(contactId: String): SealedResult<Contact, Exception>
}

/**
 * The default implementation of the [ContactsRepo].
 */
class DefaultContactsRepo(
    account: UserAccount?, // TODO this shouldn't be nullable. The logic whether to instantiate this object should be moved higher up, but this is a quick fix to get things testable
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ContactsRepo {

    // region Public Properties


    private val mutCurContactList = MutableStateFlow<List<Contact>>(emptyList())
    override val curContactList: StateFlow<List<Contact>> get() = mutCurContactList


    // endregion
    // region Private Properties


    private val store = MobileSyncSDKManager.getInstance().getSmartStore(account)
    private val syncManager = SyncManager.getInstance(account)
    private val syncMutex = Mutex()
    private val listMutex = Mutex()


    // endregion
    // region Public Sync Implementation


    override suspend fun syncUpAndDown() = withContext(ioDispatcher) {
        val upResult: SealedResult<SyncState, SyncFailure>?
        val downResult: SealedResult<SyncState, SyncFailure>

        syncMutex.withLock {
            upResult = doSyncUp()
            currentCoroutineContext().ensureActive() // cooperative cancellation before doing sync down
            downResult = doSyncDown()
        }

        // don't cooperate with cancel at this point because we need to emit the new list of contacts
        withContext(NonCancellable) {
            when (downResult) {
                is SealedFailure -> onSyncDownFailed(downResult.cause)
                is SealedSuccess -> onSyncDownSuccess()
            }
        }

        SyncDownAndUpResults(
            syncDownResult = downResult.mapSuccess { Unit },
            syncUpResult = upResult?.mapSuccess { Unit }
        )
    }

    override suspend fun syncDownOnly() = withContext(ioDispatcher) {
        val downResult = syncMutex.withLock {
            doSyncDown()
        }

        // don't cooperate with cancel at this point because we need to emit the new list of contacts
        withContext(NonCancellable) {
            when (downResult) {
                is SealedFailure -> onSyncDownFailed(downResult.cause)
                is SealedSuccess -> onSyncDownSuccess()
            }
        }

        downResult.mapSuccess { Unit }
    }

    override suspend fun syncUpOnly() = withContext(ioDispatcher) {
        syncMutex.withLock {
            doSyncUp().mapSuccess { Unit }
        }
    }


    // endregion
    // region Private Sync Implementation


    private suspend fun onSyncDownSuccess() {
        // TODO we may need to page this to support arbitrary contact lists greater than 10k
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

        listMutex.withLock {
            mutCurContactList.value = contacts
        }
    }

    private fun onSyncDownFailed(syncDownFailure: SyncFailure) {
        Log.e(TAG, "onSyncDownFailed() syncDownResult = $syncDownFailure")
    }

    // Individual syncs cannot be cancelled, so we don't use suspendCancellableCoroutine
    private suspend fun doSyncDown(): SealedResult<SyncState, SyncFailure> =
        suspendCoroutine { cont ->
            // TODO: will MobileSync use the same instance of the callback (this coroutine block) in the STOPPED -> RUNNING -> DONE flow?
            val callback: (SyncState) -> Unit = {
                when (it.status) {
                    // terminal states
                    SyncState.Status.DONE -> cont.resume(SealedSuccess(it))
                    SyncState.Status.FAILED,
                    SyncState.Status.STOPPED -> cont.resume(
                        SealedFailure(cause = SyncRuntimeFailure(syncState = it))
                    )

                    // TODO are these strictly transient states for as long as this coroutine is running?
                    SyncState.Status.NEW,
                    SyncState.Status.RUNNING,
                    null -> {
                        /* no-op; suspending for terminal state */
                    }
                }
            }
            try {
                // TODO Replace this raw string with compile time constant:
                syncManager.reSync("syncDownContacts", callback)
            } catch (ex: Exception) {
                cont.resume(SealedFailure(SyncNotStarted(exception = ex)))
            }
        }

    // Individual syncs cannot be cancelled, so we don't use suspendCancellableCoroutine
    private suspend fun doSyncUp(): SealedResult<SyncState, SyncFailure> =
        suspendCoroutine { cont ->
            val callback: (SyncState) -> Unit = {
                when (it.status) {
                    // terminal states
                    SyncState.Status.DONE -> cont.resume(SealedSuccess(value = it))

                    SyncState.Status.FAILED,
                    SyncState.Status.STOPPED -> cont.resume(
                        SealedFailure(cause = SyncRuntimeFailure(syncState = it))
                    )

                    SyncState.Status.NEW,
                    SyncState.Status.RUNNING,
                    null -> {
                        /* no-op; suspending for terminal state */
                    }
                }
            }

            try {
                // TODO Replace this raw string with compile time constant:
                syncManager.reSync("syncUpContacts", callback)
            } catch (ex: Exception) {
                cont.resume(SealedFailure(cause = SyncNotStarted(exception = ex)))
            }
        }


    // endregion
    // region Local Modifications


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

        withContext(NonCancellable) {
            when (result) {
                is SealedFailure -> TODO("Got exception when upserting contact: ${result.cause}")
                is SealedSuccess -> {
                    listMutex.withLock {
                        mutCurContactList.value = mutCurContactList.value
                            .replaceAllOrAddNew(newValue = result.value) { it.id == result.value.id }
                    }
                }
            }
        }

        result
    }

    override suspend fun locallyDeleteContact(contactId: String) = withContext(ioDispatcher) {
        fun saveContactDelete(
            upstreamContact: Contact,
            soupEntryId: Long
        ): SealedResult<Contact, Exception> {
            return try {
                val updatedJson = store.update(
                    "contacts",
                    upstreamContact.toJson()
                        .putOpt(LOCALLY_DELETED, true)
                        .putOpt(LOCAL, true),
                    soupEntryId
                )

                SealedSuccess(Contact.coerceFromJson(updatedJson))
            } catch (ex: Exception) {
                SealedFailure(cause = ex)
            }
        }

        val result: SealedResult<Contact, Exception> = try {
            val soupEntryId = store.lookupSoupEntryId("contacts", Constants.ID, contactId)
            if (soupEntryId < 0) {
                throw IllegalStateException("Tried to locally delete contact, but soup ID was not found.  Contact ID = $contactId")
            }

            val contact = store.retrieve("contacts", soupEntryId).firstOrNull()?.let {
                Contact.coerceFromJson(it)
            }

            when {
                contact == null -> throw IllegalStateException("Retrieving Contact with Soup ID $soupEntryId and Contact ID $contactId returned no results.")
                contact.locallyDeleted -> SealedSuccess(contact)
                else -> saveContactDelete(contact, soupEntryId)
            }
        } catch (ex: Exception) {
            SealedFailure(cause = ex)
        }

        withContext(NonCancellable) {
            when (result) {
                is SealedFailure -> TODO("Got exception when deleting contact: ${result.cause}")
                is SealedSuccess -> {
                    listMutex.withLock {
                        mutCurContactList.value = mutCurContactList.value
                            .replaceAll(newValue = result.value) { it.id == result.value.id }
                    }
                }
            }
        }

        result
    }

    override suspend fun locallyUndeleteContact(contactId: String) = withContext(ioDispatcher) {
        fun saveContactUndelete(
            upstreamContact: Contact,
            soupEntryId: Long
        ): SealedResult<Contact, Exception> {
            return try {
                val updatedJson = store.update(
                    "contacts",
                    upstreamContact.toJson()
                        .putOpt(LOCALLY_DELETED, false)
                        .putOpt(
                            LOCAL,
                            upstreamContact.locallyCreated || upstreamContact.locallyUpdated
                        ),
                    soupEntryId
                )

                SealedSuccess(Contact.coerceFromJson(updatedJson))
            } catch (ex: Exception) {
                SealedFailure(cause = ex)
            }
        }

        val result: SealedResult<Contact, Exception> = try {
            val soupEntryId = store.lookupSoupEntryId("contacts", Constants.ID, contactId)
            if (soupEntryId < 0) {
                throw IllegalStateException("Tried to locally delete contact, but soup ID was not found.  Contact ID = $contactId")
            }

            val contact = store.retrieve("contacts", soupEntryId).firstOrNull()?.let {
                Contact.coerceFromJson(it)
            }

            when {
                contact == null -> throw IllegalStateException("Retrieving Contact with Soup ID $soupEntryId and Contact ID $contactId returned no results.")
                !contact.locallyDeleted -> SealedSuccess(contact)
                else -> saveContactUndelete(contact, soupEntryId)
            }
        } catch (ex: Exception) {
            SealedFailure(cause = ex)
        }

        withContext(NonCancellable) {
            when (result) {
                is SealedFailure -> TODO()
                is SealedSuccess -> {
                    listMutex.withLock {
                        mutCurContactList.value = mutCurContactList.value
                            .replaceAll(newValue = result.value) { it.id == result.value.id }
                    }
                }
            }
        }

        result
    }


    // endregion


    private companion object {
        private const val TAG = "DefaultContactsRepo"
    }
}
