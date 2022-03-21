package com.salesforce.samples.mobilesynccompose.core.repos

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SalesforceObjectContainer
import kotlinx.coroutines.flow.StateFlow

interface SObjectSyncableRepo<T : SalesforceObjectContainer> {
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
    suspend fun locallyDelete(id: String): T?

    @Throws(RepoOperationException::class)
    suspend fun locallyUndelete(id: String): T
}
