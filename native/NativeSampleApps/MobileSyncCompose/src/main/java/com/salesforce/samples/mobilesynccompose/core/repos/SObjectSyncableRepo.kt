package com.salesforce.samples.mobilesynccompose.core.repos

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObject
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectId
import kotlinx.coroutines.flow.StateFlow

interface SObjectSyncableRepo<T : SObject> {
    val curSObjectList: StateFlow<List<T>>

    @Throws(RepoSyncException.SyncDownException::class)
    suspend fun syncDownOnly()

    @Throws(RepoSyncException::class)
    suspend fun syncUpAndDown()

    @Throws(RepoSyncException.SyncUpException::class)
    suspend fun syncUpOnly()

    @Throws(RepoOperationException::class)
    suspend fun locallyUpsert(so: T): T

    @Throws(RepoOperationException::class)
    suspend fun locallyDelete(id: SObjectId): T?

    @Throws(RepoOperationException::class)
    suspend fun locallyUndelete(id: SObjectId): T
}
