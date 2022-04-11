package com.salesforce.samples.mobilesynccompose.core.repos

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.*
import kotlinx.coroutines.flow.Flow

/**
 * This interface purposefully does not expose a "get by ID" API. The emissions via [recordsById]
 * represent the current snapshot of the records in SmartStore and should be responded to in a
 * reactive manner.  In highly parallel environments it is possible that the "get by ID" API would
 * return inconsistent results.
 */
interface SObjectSyncableRepo<T : SObject> {
    val recordsById: Flow<Map<String, SObjectRecord<T>>>

    @Throws(RepoSyncException.SyncDownException::class)
    suspend fun syncDownOnly()

    @Throws(RepoSyncException::class)
    suspend fun syncUpAndDown()

    @Throws(RepoSyncException.SyncUpException::class)
    suspend fun syncUpOnly()

    @Throws(RepoOperationException::class)
    suspend fun locallyUpdate(id: String, so: T): SObjectRecord<T>

    @Throws(RepoOperationException::class)
    suspend fun locallyCreate(so: T): SObjectRecord<T>

    @Throws(RepoOperationException::class)
    suspend fun locallyDelete(id: String): SObjectRecord<T>?

    @Throws(RepoOperationException::class)
    suspend fun locallyUndelete(id: String): SObjectRecord<T>
}
