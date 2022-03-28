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
