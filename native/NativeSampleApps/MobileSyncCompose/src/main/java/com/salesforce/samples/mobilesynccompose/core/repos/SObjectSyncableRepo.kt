package com.salesforce.samples.mobilesynccompose.core.repos

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.LocalId
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.PrimaryKey
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectModel
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import kotlinx.coroutines.flow.StateFlow

interface SObjectSyncableRepo<T : SObjectModel> {
    val curSObjects: StateFlow<SObjectsByIds<T>>

    @Throws(RepoSyncException.SyncDownException::class)
    suspend fun syncDownOnly()

    @Throws(RepoSyncException::class)
    suspend fun syncUpAndDown()

    @Throws(RepoSyncException.SyncUpException::class)
    suspend fun syncUpOnly()

    @Throws(RepoOperationException::class)
    suspend fun locallyUpdate(id: PrimaryKey, so: T): SObjectRecord<T>

    @Throws(RepoOperationException::class)
    suspend fun locallyCreate(so: T): SObjectRecord<T>

    @Throws(RepoOperationException::class)
    suspend fun locallyDelete(id: PrimaryKey): SObjectRecord<T>?

    @Throws(RepoOperationException::class)
    suspend fun locallyUndelete(id: PrimaryKey): SObjectRecord<T>
}

data class SObjectsByIds<T : SObjectModel>(
    val byPrimaryKey: Map<PrimaryKey, SObjectRecord<T>>,
    val byLocalId: Map<LocalId, SObjectRecord<T>>
)
