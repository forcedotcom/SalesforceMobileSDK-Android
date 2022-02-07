package com.salesforce.samples.mobilesynccompose.model.contacts

import android.util.Log
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.samples.mobilesynccompose.core.extensions.map
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface ContactsRepo {
    val contactUpdates: Flow<List<ContactObject>>
    suspend fun sync(syncDownOnly: Boolean)
//    fun saveContact(...)
//    fun deleteContact(...)
}

class DefaultContactsRepo(
    account: UserAccount? = null, // default to current account
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ContactsRepo {

    private val store = MobileSyncSDKManager.getInstance().getSmartStore(account)
    private val syncManager = SyncManager.getInstance(account)
    private val syncMutex = Mutex()

    /* Replay latest update on new `Flow.collect { }` so all new collectors get current state.
     * We're not using StateFlow with an `emptyList()` as a starting value because this flow is
     * about updates, and an empty list would logically indicate that there are no contacts to be
     * found. */
//    private val mutContactUpdates = MutableSharedFlow<List<ContactObject>>(replay = 1)
    // TODO replace this with ☝️
    private val mutContactUpdates = MutableStateFlow<List<ContactObject>>((1..100).map {
        ContactObject(
            id = it.toString(),
            firstName = "First",
            middleName = "Middle",
            lastName = "Last $it",
            title = "Title"
        )
    })
    override val contactUpdates: Flow<List<ContactObject>> get() = mutContactUpdates

    override suspend fun sync(syncDownOnly: Boolean) = withContext(ioDispatcher) {
        data class SyncResults(val syncDownResult: SyncState, val syncUpResult: SyncState?)

        val result = syncMutex.withLock {
            val upResult = if (!syncDownOnly) syncUp() else null

            currentCoroutineContext().ensureActive() // cooperative cancellation

            if (upResult != null && upResult.isDone) {
                SyncResults(syncDown(), upResult)
            } else {
                // sync up failed
                Log.e(TAG, "The Sync Up operation failed: $upResult")
                // TODO better handle this failure case
                return@withContext
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

    // Individual syncs cannot be cancelled, so we don't use suspendCancellableCoroutine
    private suspend fun syncDown(): SyncState = suspendCoroutine { cont ->
        val callback: (SyncState) -> Unit = { cont.resume(it) }
        // TODO Replace this raw string with compile time constant:
        syncManager.reSync("syncDownContacts", callback)
    }

    // Individual syncs cannot be cancelled, so we don't use suspendCancellableCoroutine
    private suspend fun syncUp(): SyncState = suspendCoroutine { cont ->
        val callback: (SyncState) -> Unit = { cont.resume(it) }
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
        mutContactUpdates.emit(contactResults.map { ContactObject(it) })
    }

    private fun onSyncDownFailed(syncDownResult: SyncState) {
        TODO("onSyncDownFailed() syncDownResult = $syncDownResult")
    }

    private companion object {
        private const val TAG = "DefaultContactsRepo"
    }
}
