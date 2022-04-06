package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.extensions.optStringOrNull
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord.Companion.KEY_LOCAL_ID
import org.json.JSONObject
import java.util.*

object SObjectDeserializerHelper {
//    fun getPrimaryKeyOrThrow(json: JSONObject): String = json
//        .getRequiredStringOrThrow(Constants.ID, valueCanBeBlank = false)

    fun requireSoType(json: JSONObject, requiredObjType: String) {
        val attributes = json.getRequiredObjectOrThrow(Constants.ATTRIBUTES)

        val type = attributes.optString(Constants.TYPE.lowercase(Locale.US))
        if (type != requiredObjType) {
            throw IncorrectObjectType(
                expectedObjectType = requiredObjType,
                foundObjectType = type,
                offendingJsonString = json.toString()
            )
        }
    }

    fun getIdOrThrow(json: JSONObject): String {
        val localId = json.optStringOrNull(KEY_LOCAL_ID)

        if (localId != null) {
            return localId
        }

        if (!json.has(Constants.ID)) {
            throw MissingRequiredProperties(
                offendingJsonString = json.toString(),
                KEY_LOCAL_ID,
                Constants.ID
            )
        }

        return json.getString(Constants.ID).ifBlank {
            throw InvalidPropertyValue(
                propertyKey = Constants.ID,
                allowedValuesDescription = "ID must not be blank.",
                offendingJsonString = json.toString()
            )
        }
    }
}
