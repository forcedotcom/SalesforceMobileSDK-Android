package com.salesforce.samples.mobilesynccompose.core.ui.state

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.LocalStatus

enum class SObjectUiSyncState {
    Deleted,
    NotSaved,
    Synced,
    Updated,
}

fun LocalStatus.toUiSyncState(): SObjectUiSyncState = when (this) {
    LocalStatus.LocallyDeleted,
    LocalStatus.LocallyDeletedAndLocallyUpdated -> SObjectUiSyncState.Deleted

    LocalStatus.LocallyCreated,
    LocalStatus.LocallyUpdated -> SObjectUiSyncState.Updated

    LocalStatus.MatchesUpstream -> SObjectUiSyncState.Synced
}
