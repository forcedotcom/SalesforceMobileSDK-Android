package com.salesforce.samples.mobilesynccompose.core.repos

import com.salesforce.androidsdk.mobilesync.util.SyncState

sealed class RepoSyncException : Exception() {
    /**
     * The final state of the sync operation when it failed, or null if the sync made no progress.
     */
    abstract val finalSyncState: SyncState?

    data class SyncDownException(
        override val finalSyncState: SyncState?,
        override val cause: Throwable? = null,
        override val message: String? = null,
    ) : RepoSyncException()

    data class SyncUpException(
        override val finalSyncState: SyncState?,
        override val cause: Throwable? = null,
        override val message: String? = null
    ) : RepoSyncException()

    data class RefreshListException(
        override val finalSyncState: SyncState?,
        override val cause: Throwable? = null,
        override val message: String? = null
    ) : RepoSyncException()
}
