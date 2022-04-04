package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.data.ReadOnlyJson
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.StoreRecordMetadata.Companion.KEY_LOCAL_ID
import org.json.JSONObject
import java.util.*

interface SObjectDeserializer<T : SObjectModel> {
    @Throws(CoerceException::class)
    fun coerceFromJsonOrThrow(json: ReadOnlyJson): SObjectRecord<T>

    val objectType: String
}

abstract class SObjectDeserializerBase<T : SObjectModel>(override val objectType: String) :
    SObjectDeserializer<T> {

    @Throws(CoerceException::class)
    override fun coerceFromJsonOrThrow(json: ReadOnlyJson): SObjectRecord<T> {
        ReadOnlySoHelper.requireSoType(json, objectType)

        val primaryKey = ReadOnlySoHelper.getPrimaryKeyOrThrow(json)
        val localId = ReadOnlySoHelper.getLocalId(json)

        return SObjectRecord(
            primaryKey = primaryKey,
            localId = localId,
            localStatus = json.coerceToLocalStatus(),
            model = buildModel(fromJson = json),
        )
    }

    @Throws(CoerceException::class)
    protected abstract fun buildModel(fromJson: ReadOnlyJson): T
}

/**
 * Convenience method for setting up a JSON with the properties required for all Salesforce Objects.
 *
 * This will create a local ID, add the correct object type, and set the correct combination of
 * local flags on the returned JSON, leaving the rest of the customization to the subclasses to implement.
 */
fun createNewSoupEltBase(forObjType: String): JSONObject {
    val attributes = JSONObject().put(Constants.TYPE.lowercase(Locale.US), forObjType)
    val id = SyncTarget.createLocalId()//.let { SObjectId(primaryKey = it, localId = it) }

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
