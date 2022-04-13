package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord.Companion.KEY_LOCAL_ID
import org.json.JSONObject
import java.util.*

interface SObjectDeserializer<T : SObject> {
    @Throws(CoerceException::class)
    fun coerceFromJsonOrThrow(json: JSONObject): SObjectRecord<T>
}

abstract class SObjectDeserializerBase<T : SObject>(val objectType: String) :
    SObjectDeserializer<T> {

    @Throws(CoerceException::class)
    override fun coerceFromJsonOrThrow(json: JSONObject): SObjectRecord<T> {
        SObjectDeserializerHelper.requireSoType(json, objectType)

        val id = SObjectDeserializerHelper.getIdOrThrow(json)
        val localStatus = json.coerceToLocalStatus()
        val model = buildModel(fromJson = json)

        return SObjectRecord(id = id, localStatus = localStatus, sObject = model)
    }

    @Throws(CoerceException::class)
    protected abstract fun buildModel(fromJson: JSONObject): T
}

/**
 * Convenience method for setting up a JSON with the properties required for all Salesforce Objects.
 *
 * This will create a local ID, add the correct object type, and set the correct combination of
 * local flags on the returned JSON, leaving the rest of the customization to the subclasses to implement.
 */
fun createNewSoupEltBase(forObjType: String): JSONObject {
    val attributes = JSONObject().put(Constants.TYPE.lowercase(Locale.US), forObjType)
    val id = SyncTarget.createLocalId()

    return JSONObject().apply {
        put(Constants.ID, id)
        put(KEY_LOCAL_ID, id)
        put(Constants.ATTRIBUTES, attributes)
        put(SyncTarget.LOCALLY_CREATED, true)
        put(SyncTarget.LOCALLY_DELETED, false)
        put(SyncTarget.LOCALLY_UPDATED, false)
        put(SyncTarget.LOCAL, true)
    }
}
