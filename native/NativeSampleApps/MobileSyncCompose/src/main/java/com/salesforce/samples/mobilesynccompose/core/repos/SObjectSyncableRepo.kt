package com.salesforce.samples.mobilesynccompose.core.repos

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.*
import kotlinx.coroutines.flow.Flow

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

//class SObjectRecordsByIds<T : SObject>(
//    private val upstreamRecords: Map<String, UpstreamSObjectRecord<T>>,
//    private val locallyCreatedRecords: Map<String, SObjectRecordCreatedDuringThisLoginSession<T>>
//) {
//    operator fun get(key: String): SObjectRecord<T>? {
//        return if (SyncTarget.isLocalId(key)) {
//            locallyCreatedRecords[key]
//        } else {
//            upstreamRecords[key]
//        }
//    }
//
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//
//        other as SObjectRecordsByIds<*>
//
//        if (upstreamRecords != other.upstreamRecords) return false
//        if (locallyCreatedRecords != other.locallyCreatedRecords) return false
//
//        return true
//    }
//
//    override fun hashCode(): Int {
//        var result = upstreamRecords.hashCode()
//        result = 31 * result + locallyCreatedRecords.hashCode()
//        return result
//    }
//}
