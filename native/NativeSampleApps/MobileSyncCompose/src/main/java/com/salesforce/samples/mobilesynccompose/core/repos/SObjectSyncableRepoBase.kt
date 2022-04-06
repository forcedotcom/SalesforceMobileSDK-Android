package com.salesforce.samples.mobilesynccompose.core.repos

import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.target.SyncTarget.LOCAL
import com.salesforce.androidsdk.mobilesync.target.SyncTarget.LOCALLY_DELETED
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.samples.mobilesynccompose.core.extensions.*
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.*
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecordCreatedDuringThisLoginSession.Companion.KEY_LOCAL_ID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

abstract class SObjectSyncableRepoBase<T : SObject>(
    account: UserAccount?,// TODO this shouldn't be nullable. The logic whether to instantiate this object should be moved higher up, but this is a quick fix to get things testable
    protected val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SObjectSyncableRepo<T> {

    // region Public and Private Properties


    private val syncMutex = Mutex()
    private val listMutex = Mutex()

    private val mutUpstreamRecordsById = mutableMapOf<String, UpstreamSObjectRecord<T>>()
    private val mutLocallyCreatedRecordsById =
        mutableMapOf<String, SObjectRecordCreatedDuringThisLoginSession<T>>()
    private val mutState = MutableSharedFlow<SObjectRecordsByIds<T>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val records: Flow<SObjectRecordsByIds<T>> = mutState.distinctUntilChanged()

    protected val store: SmartStore = MobileSyncSDKManager.getInstance().getSmartStore(account)
    protected val syncManager: SyncManager = SyncManager.getInstance(account)

    protected abstract val soupName: String
    protected abstract val syncDownName: String
    protected abstract val syncUpName: String
    protected abstract val deserializer: SObjectDeserializer<T>
    protected abstract val TAG: String


    // endregion
    // region Public Sync Implementation


    @Throws(RepoSyncException::class, RepoOperationException::class)
    override suspend fun syncUpAndDown() = withContext(ioDispatcher) {
        syncMutex.withLock {
            doSyncUp()
            ensureActive() // cooperative cancellation before doing sync down
            doSyncDown()
        }

        // don't cooperate with cancel at this point because we need to emit the new list of objects
        withContext(NonCancellable) { refreshRecordsList() }
    }

    @Throws(RepoSyncException.SyncDownException::class, RepoOperationException::class)
    override suspend fun syncDownOnly() = withContext(ioDispatcher) {
        syncMutex.withLock { doSyncDown() }

        // don't cooperate with cancel at this point because we need to emit the new list of objects
        withContext(NonCancellable) { refreshRecordsList() }
    }

    @Throws(RepoSyncException.SyncUpException::class, RepoOperationException::class)
    override suspend fun syncUpOnly() = withContext(ioDispatcher) {
        syncMutex.withLock { doSyncUp() }
        Unit
    }


    // endregion
    // region Private Sync Implementation


    @Throws(RepoSyncException.SyncDownException::class)
    private suspend fun doSyncDown(): SyncState = suspendCoroutine { cont ->
        // TODO: will MobileSync use the same instance of the callback (this coroutine block) in the STOPPED -> RUNNING -> DONE flow?
        val callback: (SyncState) -> Unit = {
            when (it.status) {
                // terminal states
                SyncState.Status.DONE -> cont.resume(it)
                SyncState.Status.FAILED,
                SyncState.Status.STOPPED -> cont.resumeWithException(
                    RepoSyncException.SyncDownException(finalSyncState = it)
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
            syncManager.reSync(syncDownName, callback)
        } catch (ex: CancellationException) {
            // swallow cancellations since individual syncs cannot be cancelled
        } catch (ex: Exception) {
            cont.resumeWithException(
                RepoSyncException.SyncDownException(finalSyncState = null, cause = ex)
            )
        }
    }

    @Throws(RepoSyncException.SyncUpException::class)
    private suspend fun doSyncUp(): SyncState = suspendCoroutine { cont ->
        val callback: (SyncState) -> Unit = {
            when (it.status) {
                // terminal states
                SyncState.Status.DONE -> cont.resume(it)

                SyncState.Status.FAILED,
                SyncState.Status.STOPPED -> cont.resumeWithException(
                    RepoSyncException.SyncUpException(finalSyncState = it)
                )

                SyncState.Status.NEW,
                SyncState.Status.RUNNING,
                null -> {
                    /* no-op; suspending for terminal state */
                }
            }
        }

        try {
            syncManager.reSync(syncUpName, callback)
        } catch (ex: CancellationException) {
            // swallow cancellations since individual syncs cannot be cancelled
        } catch (ex: Exception) {
            cont.resumeWithException(
                RepoSyncException.SyncUpException(finalSyncState = null, cause = ex)
            )
        }
    }


    // endregion
    // region Local Modifications


    @Throws(RepoOperationException::class)
    override suspend fun locallyUpdate(id: String, so: T) =
        withContext(ioDispatcher + NonCancellable) {
            val updateResult = try {
                val elt = with(so) {
                    retrieveByIdOrThrowOperationException(id = id)
                        .elt
                        .applyObjProperties()
                }

                store.upsert(soupName, elt)
            } catch (ex: Exception) {
                throw RepoOperationException.SmartStoreOperationFailed(
                    message = "Failed to upsert the object in SmartStore.",
                    cause = ex
                )
            }

            val result = updateResult.coerceUpdatedObjToModelOrCleanupAndThrow()
            updateStateWithObject(result)
            result
        }

    @Throws(RepoOperationException::class)
    override suspend fun locallyCreate(so: T): SObjectRecord<T> =
        withContext(ioDispatcher + NonCancellable) {
            val createResult = try {
                val elt = with(so) {
                    createNewSoupEltBase(forObjType = objectType)
                        .applyObjProperties()
                }

                store.upsert(soupName, elt)
            } catch (ex: Exception) {
                throw RepoOperationException.SmartStoreOperationFailed(
                    message = "Failed to create the object in SmartStore.",
                    cause = ex
                )
            }

            val result = createResult.coerceUpdatedObjToModelOrCleanupAndThrow()
            updateStateWithObject(result)
            result
        }

    @Throws(RepoOperationException::class)
    override suspend fun locallyDelete(id: String) =
        withContext(ioDispatcher + NonCancellable) {
            suspend fun saveDeleteToStore(elt: JSONObject, soupId: Long): SObjectRecord<T> {
                elt
                    .putOpt(LOCALLY_DELETED, true)
                    .putOpt(LOCAL, true)

                val updatedJson = try {
                    store.update(soupName, elt, soupId)
                } catch (ex: Exception) {
                    throw RepoOperationException.SmartStoreOperationFailed(
                        message = "Locally-delete operation failed. Could not save the updated object in SmartStore.",
                        cause = ex
                    )
                }

                return updatedJson.coerceUpdatedObjToModelOrCleanupAndThrow()
            }

            val retrieved = retrieveByIdOrThrowOperationException(id)
            val localStatus = retrieved.elt.coerceToLocalStatus()
            val result: SObjectRecord<T>? = when {
                localStatus.isLocallyCreated -> {
                    try {
                        store.delete(soupName, retrieved.soupId)
                    } catch (ex: Exception) {
                        throw RepoOperationException.SmartStoreOperationFailed(
                            message = "Failed deleting locally-created object. SmartStore.delete(soupName=$soupName, soupId=${retrieved.soupId}) threw an exception.",
                            cause = ex
                        )
                    }
                    null
                }
                localStatus.isLocallyDeleted -> retrieved.elt
                    .coerceUpdatedObjToModelOrCleanupAndThrow()

                else -> saveDeleteToStore(elt = retrieved.elt, soupId = retrieved.soupId)
            }

            if (result == null) {
                val retrievedPrimaryKey = retrieved.elt.optStringOrNull(Constants.ID)
                val retrievedLocalId = retrieved.elt.optStringOrNull(KEY_LOCAL_ID)

                removeAllFromObjectList(
                    primaryKey = retrievedPrimaryKey,
                    locallyCreatedId = retrievedLocalId
                )
            } else {
                updateStateWithObject(result)
            }

            result
        }

    @Throws(RepoOperationException::class)
    override suspend fun locallyUndelete(id: String) =
        withContext(ioDispatcher + NonCancellable) {
            suspend fun saveUndeleteToStore(
                elt: JSONObject,
                soupEntryId: Long
            ): SObjectRecord<T> {

                val curLocalStatus = elt.coerceToLocalStatus()
                elt
                    .putOpt(LOCALLY_DELETED, false)
                    .putOpt(
                        LOCAL,
                        curLocalStatus.isLocallyCreated || curLocalStatus.isLocallyUpdated
                    )

                val updatedJson = try {
                    store.update(soupName, elt, soupEntryId)
                } catch (ex: Exception) {
                    throw RepoOperationException.SmartStoreOperationFailed(
                        message = "Locally-undelete operation failed. Could not save the updated object in SmartStore.",
                        cause = ex
                    )
                }

                return updatedJson.coerceUpdatedObjToModelOrCleanupAndThrow()
            }

            val retrieved = retrieveByIdOrThrowOperationException(id)

            val result: SObjectRecord<T> =
                if (!retrieved.elt.coerceToLocalStatus().isLocallyDeleted)
                    retrieved.elt.coerceUpdatedObjToModelOrCleanupAndThrow()
                else
                    saveUndeleteToStore(retrieved.elt, retrieved.soupId)

            updateStateWithObject(result)

            result
        }


    // endregion
    // region Protected Store Operations


    @Throws(RepoOperationException::class)
    protected suspend fun refreshRecordsList(): Unit = withContext(ioDispatcher) {
        val (parseSuccesses, parseFailures) = runFetchAllQuery()
        setRecordsList(parseSuccesses)
    }

    protected suspend fun setRecordsList(records: List<SObjectRecord<T>>) = listMutex.withLock {
        mutUpstreamRecordsById.clear()
        mutLocallyCreatedRecordsById.clear()

        records.forEach { record ->
            when (record) {
                is SObjectRecordCreatedDuringThisLoginSession ->
                    mutLocallyCreatedRecordsById[record.id] = record

                is UpstreamSObjectRecord ->
                    mutUpstreamRecordsById[record.id] = record
            }
        }

        mutState.emit(
            SObjectRecordsByIds(
                upstreamRecords = mutUpstreamRecordsById.toMap(), // shallow copy
                locallyCreatedRecords = mutLocallyCreatedRecordsById.toMap() // shallow copy
            )
        )
    }

    @Throws(RepoOperationException::class)
    protected suspend fun runFetchAllQuery(): ResultPartition<SObjectRecord<T>> =
        withContext(ioDispatcher) {
            val queryResults = try {
                // TODO we may need to page this to support arbitrary lists greater than 10k
                store.query(
                    QuerySpec.buildAllQuerySpec(
                        soupName,
                        null,
                        null,
                        10_000
                    ),
                    0
                )
            } catch (ex: Exception) {
                throw RepoOperationException.SmartStoreOperationFailed(
                    message = "Failed to refresh the repo objects list. The objects list may be out of date with SmartStore.",
                    cause = ex
                )
            }

            // TODO What to do with parse failures?  Swallow them?
            queryResults
                .map { runCatching { deserializer.coerceFromJsonOrThrow(it) } }
                .partitionBySuccess()
        }


    // endregion
    // region Convenience Methods


    /**
     * Convenience method for running an object list update procedure while under the Mutex for said
     * list. Also eliminates the need for subclasses to handle Mutexes themselves.
     */
    protected suspend fun updateStateWithObject(obj: SObjectRecord<T>): Unit =
        listMutex.withLock {
            when (obj) {
                is SObjectRecordCreatedDuringThisLoginSession -> mutLocallyCreatedRecordsById[obj.id] = obj
                is UpstreamSObjectRecord -> mutUpstreamRecordsById[obj.id] = obj
            }

            mutState.emit(
                SObjectRecordsByIds(
                    upstreamRecords = mutUpstreamRecordsById.toMap(), // shallow copy
                    locallyCreatedRecords = mutLocallyCreatedRecordsById.toMap() // shallow copy
                )
            )
        }

    /**
     * Convenience method for running an object list update procedure while under the Mutex for said
     * list. Also eliminates the need for subclasses to handle Mutexes themselves.
     */
    protected suspend fun removeAllFromObjectList(
        primaryKey: String?,
        locallyCreatedId: String?
    ) {
        listMutex.withLock {
            primaryKey?.let { mutUpstreamRecordsById.remove(it) }
            locallyCreatedId?.let { mutLocallyCreatedRecordsById.remove(it) }

            mutState.emit(
                SObjectRecordsByIds(
                    upstreamRecords = mutUpstreamRecordsById.toMap(),
                    locallyCreatedRecords = mutLocallyCreatedRecordsById.toMap()
                )
            )
        }
    }

    /**
     * Convenience method for the common procedure of trying to coerce the SmartStore updated object
     * to a model while catching the coerce exception and removing that object from the objects list
     * to maintain data integrity.
     */
    @Throws(RepoOperationException.InvalidResultObject::class)
    protected suspend fun JSONObject.coerceUpdatedObjToModelOrCleanupAndThrow() = try {
        deserializer.coerceFromJsonOrThrow(this)
    } catch (ex: Exception) {
        val primaryKey = this.optStringOrNull(Constants.ID)
        val localId = this.optStringOrNull(KEY_LOCAL_ID)

        removeAllFromObjectList(primaryKey = primaryKey, locallyCreatedId = localId)

        throw RepoOperationException.InvalidResultObject(
            message = "SmartStore operation was successful, but failed to deserialize updated JSON. This object has been removed from the list of objects in this repo to preserve data integrity",
            cause = ex
        )
    }

    /**
     * Convenience method for the common procedure of trying to retrieve a single object by its ID
     * or throwing the corresponding exception.
     */
    @Throws(RepoOperationException.RecordNotFound::class)
    protected fun retrieveByIdOrThrowOperationException(id: String) = try {
        if (SyncTarget.isLocalId(id)) {
            store.retrieveSingleById(soupName = soupName, idColName = KEY_LOCAL_ID, id = id)
        } else {
            store.retrieveSingleById(soupName = soupName, idColName = Constants.ID, id = id)
        }
    } catch (ex: Exception) {
        throw RepoOperationException.RecordNotFound(id = id, soupName = soupName, cause = ex)
    }


    // endregion
}
