package com.salesforce.samples.mobilesynccompose.core.repos

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.LocallyCreatedId
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.PrimaryKey
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObject
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import kotlinx.coroutines.flow.Flow

interface SObjectSyncableRepo<T : SObject> {
    val records: Flow<SObjectRecordsByIds<T>>

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

data class SObjectRecordsByIds<T : SObject>(
    val byPrimaryKey: Map<PrimaryKey, SObjectRecord<T>>,
    val byLocallyCreatedId: Map<LocallyCreatedId, SObjectRecord<T>>
)
