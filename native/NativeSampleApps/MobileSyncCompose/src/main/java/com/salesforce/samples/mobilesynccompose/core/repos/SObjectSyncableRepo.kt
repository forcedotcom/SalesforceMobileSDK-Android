package com.salesforce.samples.mobilesynccompose.core.repos

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObject
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectId
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

interface SObjectSyncableRepo<T : SObject> {
    val curSObjects: StateFlow<SObjectsByIds<T>>

    @Throws(RepoSyncException.SyncDownException::class)
    suspend fun syncDownOnly()

    @Throws(RepoSyncException::class)
    suspend fun syncUpAndDown()

    @Throws(RepoSyncException.SyncUpException::class)
    suspend fun syncUpOnly()

    @Throws(RepoOperationException::class)
    suspend fun locallyUpsert(so: T): T

    @Throws(RepoOperationException::class)
    suspend fun locallyUpdate(id: SObjectId, modifier: JSONObject.() -> Unit): T

    @Throws(RepoOperationException::class)
    suspend fun createNewLocal(modifier: JSONObject.() -> Unit): T

    @Throws(RepoOperationException::class)
    suspend fun locallyDelete(id: SObjectId): T?

    @Throws(RepoOperationException::class)
    suspend fun locallyUndelete(id: SObjectId): T
}

data class SObjectsByIds<T : SObject>(
    val byPrimaryKey: Map<String, T>,
    val byLocalId: Map<String, T>
)
