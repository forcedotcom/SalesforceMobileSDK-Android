package com.salesforce.samples.mobilesynccompose.core.salesforceobject

//data class SObjectId(val primaryKey: String, val localId: String?)

@JvmInline
value class PrimaryKey(val value: String)

@JvmInline
value class LocalId(val value: String)
