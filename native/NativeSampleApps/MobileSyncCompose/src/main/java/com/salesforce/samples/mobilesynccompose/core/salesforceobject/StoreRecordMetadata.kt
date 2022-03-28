package com.salesforce.samples.mobilesynccompose.core.salesforceobject

data class StoreRecordMetadata(
    val id: PrimaryKey,
    val localId: LocalId?,
    val localStatus: LocalStatus,
) {
    companion object {
        const val KEY_LOCAL_ID = "LocalId"
    }
}

data class SObjectRecord<T : SObjectModel>(
    val primaryKey: PrimaryKey,
    val localId: LocalId?,
    val localStatus: LocalStatus,
    val model: T
)
