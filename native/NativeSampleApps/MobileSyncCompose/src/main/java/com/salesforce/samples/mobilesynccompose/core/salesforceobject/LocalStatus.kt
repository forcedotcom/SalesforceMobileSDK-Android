package com.salesforce.samples.mobilesynccompose.core.salesforceobject

enum class LocalStatus(
//    val locallyCreated: Boolean,
//    val locallyDeleted: Boolean,
//    val locallyUpdated: Boolean,
//    val local: Boolean
) {
    LocallyCreated(
//        locallyCreated = true,
//        locallyDeleted = false,
//        locallyUpdated = false,
//        local = true
    ),
    LocallyDeleted(
//        locallyCreated = false,
//        locallyDeleted = true,
//        locallyUpdated = false,
//        local = true
    ),
    LocallyUpdated(
//        locallyCreated = false,
//        locallyDeleted = false,
//        locallyUpdated = true,
//        local = true
    ),
    LocallyDeletedAndLocallyUpdated(
//        locallyCreated = false,
//        locallyDeleted = true,
//        locallyUpdated = true,
//        local = true
    ),
    MatchesUpstream(
//        locallyCreated = false,
//        locallyDeleted = false,
//        locallyUpdated = false,
//        local = false
    )
}

val LocalStatus.isLocallyCreated: Boolean get() = when (this) {
    LocalStatus.LocallyCreated -> true
    LocalStatus.LocallyDeleted -> false
    LocalStatus.LocallyUpdated -> false
    LocalStatus.LocallyDeletedAndLocallyUpdated -> false
    LocalStatus.MatchesUpstream -> false
}

val LocalStatus.isLocallyDeleted: Boolean get() = when (this) {
    LocalStatus.LocallyCreated -> false
    LocalStatus.LocallyDeleted -> true
    LocalStatus.LocallyUpdated -> false
    LocalStatus.LocallyDeletedAndLocallyUpdated -> true
    LocalStatus.MatchesUpstream -> false
}

val LocalStatus.isLocallyUpdated: Boolean get() = when (this) {
    LocalStatus.LocallyCreated -> false
    LocalStatus.LocallyDeleted -> false
    LocalStatus.LocallyUpdated -> true
    LocalStatus.LocallyDeletedAndLocallyUpdated -> true
    LocalStatus.MatchesUpstream -> false
}

val LocalStatus.isLocal: Boolean get() = when (this) {
    LocalStatus.MatchesUpstream -> false
    LocalStatus.LocallyCreated,
    LocalStatus.LocallyDeleted,
    LocalStatus.LocallyUpdated,
    LocalStatus.LocallyDeletedAndLocallyUpdated -> true
}
