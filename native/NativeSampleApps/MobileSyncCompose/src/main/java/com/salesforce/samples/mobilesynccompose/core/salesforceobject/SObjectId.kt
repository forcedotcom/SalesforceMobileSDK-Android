package com.salesforce.samples.mobilesynccompose.core.salesforceobject

@JvmInline
value class PrimaryKey(val value: String)

@JvmInline
value class LocallyCreatedId(val value: String)

data class SObjectCombinedId(val primaryKey: PrimaryKey, val locallyCreatedId: LocallyCreatedId?)

fun SObjectRecord<*>.buildCombinedId() = SObjectCombinedId(
    primaryKey = primaryKey,
    locallyCreatedId = locallyCreatedId
)
