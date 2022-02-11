package com.salesforce.samples.mobilesynccompose.model.contacts

import android.util.Log
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.samples.mobilesynccompose.core.extensions.map
import com.salesforce.samples.mobilesynccompose.core.extensions.replaceAll
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface ContactsRepo {
    val contactUpdates: Flow<List<Contact>>
    suspend fun sync(syncDownOnly: Boolean)
    suspend fun saveContact(updatedContactObject: Contact): Result<Contact>
//    fun deleteContact(...)
}

data class SyncOperationResults(val syncUpSuccess: Boolean, val syncDownSuccess: Boolean)

class DefaultContactsRepo(
    account: UserAccount?, // TODO this shouldn't be nullable. The logic whether to instantiate this object should be moved higher up, but this is a quick fix to get things testable
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ContactsRepo {

    private val store = MobileSyncSDKManager.getInstance().getSmartStore(account)
    private val syncManager = SyncManager.getInstance(account)
    private val syncMutex = Mutex()

    private val mutContactListState = MutableStateFlow(emptyList<Contact>())

    /* Replay latest update on new `Flow.collect { }` so all new collectors get current state.
     * We're not using StateFlow with an `emptyList()` as a starting value because this flow is
     * about updates, and an empty list would logically indicate that there are no contacts to be
     * found. */
//    private val mutContactUpdates = MutableSharedFlow<List<Contact>>(replay = 1)
    override val contactUpdates: Flow<List<Contact>> get() = mutContactListState//mutContactUpdates

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

    override suspend fun saveContact(updatedContactObject: Contact): Result<Contact> =
        withContext(ioDispatcher) {
            val result = kotlin.runCatching {
                Contact.coerceFromJson(
                    // TODO Use compile-time constant for soup name
                    store.upsert("contacts", updatedContactObject.toJson())
                )
            }

            result.getOrNull()?.let { updatedContact ->
                val updatedList = mutContactListState.value
                    .replaceAll(newValue = updatedContact) { it.id == updatedContact.id }
                mutContactListState.value = updatedList
            }

            result
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
        mutContactListState.value = contactResults.map { Contact.coerceFromJson(it) }
    }

    private fun onSyncDownFailed(syncDownResult: SyncState) {
        TODO("onSyncDownFailed() syncDownResult = $syncDownResult")
    }

    private companion object {
        private const val TAG = "DefaultContactsRepo"
    }
}
