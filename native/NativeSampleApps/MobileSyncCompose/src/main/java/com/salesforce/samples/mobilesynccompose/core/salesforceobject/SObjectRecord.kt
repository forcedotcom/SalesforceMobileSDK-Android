package com.salesforce.samples.mobilesynccompose.core.salesforceobject

sealed interface SObjectRecord<T : SObject> {
    val localStatus: LocalStatus
    val sObject: T
}

data class UpstreamSObjectRecord<T : SObject>(
    val id: String,
    override val localStatus: LocalStatus,
    override val sObject: T
) : SObjectRecord<T>

data class SObjectRecordCreatedDuringThisLoginSession<T : SObject>(
    val id: String,
    override val localStatus: LocalStatus,
    override val sObject: T
) : SObjectRecord<T> {
    companion object {
        const val KEY_LOCAL_ID = "LocalId"
    }
}
