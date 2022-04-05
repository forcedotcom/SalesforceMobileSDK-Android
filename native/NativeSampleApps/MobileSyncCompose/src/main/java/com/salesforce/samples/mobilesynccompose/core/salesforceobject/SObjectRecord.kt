package com.salesforce.samples.mobilesynccompose.core.salesforceobject

data class SObjectRecord<T : SObject>(
    val primaryKey: PrimaryKey,
    val locallyCreatedId: LocallyCreatedId?,
    val localStatus: LocalStatus,
    val sObject: T
) {
    companion object {
        const val KEY_LOCAL_ID = "LocalId"
    }
}
