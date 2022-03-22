package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.So.Companion.KEY_LOCAL_ID
import org.json.JSONObject
import java.util.*

interface SalesforceObjectDeserializer<T : So> {
    @Throws(CoerceException::class)
    fun coerceFromJsonOrThrow(json: JSONObject): T
}

//abstract class SalesforceObjectDeserializerBase<T : SalesforceObjectContainer>(
//    protected val objType: String
//) : SalesforceObjectDeserializer<T> {
//
//    /**
//     * Creates the in-memory instance of this model type. The contract of this method is such that
//     * the [verifiedJson] is guaranteed to have all the required fields (ID, type, etc.) for object
//     * creation. This saves implementers from needing to verify these fields are valid when implementing
//     * model classes.
//     */
//    protected abstract fun createModelInstance(verifiedJson: JSONObject): T
//
//    /**
//     * Base implementation of a [SalesforceObjectDeserializer] [coerceFromJson] method. It verifies
//     * creates a model using these properties. Default/new values will be used if
//     * optional properties are missing. The provided [JSONObject] can be safely mutated after
//     * this method returns without this model being affected.
//     *
//     * @param json The input [JSONObject] to deserialize into a model.
//     * @return The newly created model or the [CoerceException] informing you why the coercion failed.
//     */
//    @Throws(CoerceException::class)
//    override fun coerceFromJson(json: JSONObject): T {
//        val safeJson = JSONObject(json.toString())
//
//        val attributes = safeJson.getRequiredObjectOrThrow(Constants.ATTRIBUTES)
//
//        val type = attributes.optString(Constants.TYPE.lowercase(Locale.US))
//        if (type != Constants.ACCOUNT) {
//            throw IncorrectObjectType(
//                expectedObjectType = Constants.ACCOUNT,
//                foundObjectType = type,
//                offendingJsonString = json,
//            )
//        }
//
//        safeJson.getRequiredStringOrThrow(Constants.ID).ifBlank {
//            throw InvalidPropertyValue(
//                propertyKey = Constants.ID,
//                allowedValuesDescription = "ID must not be blank.",
//                offendingJsonString = safeJson
//            )
//        }
//
//        return createModelInstance(verifiedJson = safeJson)
//    }
//
//}

/**
 * Convenience method for setting up a JSON with the properties required for all Salesforce Objects.
 *
 * This will create a local ID, add the correct object type, and set the correct combination of
 * local flags on the returned JSON, leaving the rest of the customization to the subclasses to implement.
 */
fun createNewSoupEltBase(forObjType: String): JSONObject {
    val localId = SyncTarget.createLocalId()
    val attributes = JSONObject().put(Constants.TYPE.lowercase(Locale.US), forObjType)

    return JSONObject()
        .put(Constants.ID, localId)
        .put(KEY_LOCAL_ID, localId)
        .put(Constants.ATTRIBUTES, attributes)
        .put(SyncTarget.LOCALLY_CREATED, true)
        .put(SyncTarget.LOCALLY_DELETED, false)
        .put(SyncTarget.LOCALLY_UPDATED, false)
        .put(SyncTarget.LOCAL, true)
}
