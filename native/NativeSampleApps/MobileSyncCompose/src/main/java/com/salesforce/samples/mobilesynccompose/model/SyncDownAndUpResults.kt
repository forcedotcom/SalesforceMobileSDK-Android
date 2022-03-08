package com.salesforce.samples.mobilesynccompose.model

import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.samples.mobilesynccompose.core.SealedResult

data class SyncDownAndUpResults(
    val syncDownResult: SealedResult<Unit, SyncFailure>,
    val syncUpResult: SealedResult<Unit, SyncFailure>?
)

sealed interface SyncFailure
data class SyncNotStarted(val exception: Exception) : SyncFailure
data class SyncRuntimeFailure(val syncState: SyncState) : SyncFailure
