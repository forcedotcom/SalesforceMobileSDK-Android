package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.ReadOnlyJson
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObject.Companion.KEY_LOCAL_ID
import java.util.*

object ReadOnlySoHelper {
    fun getServerIdOrThrow(json: ReadOnlyJson): String = json
        .getRequiredStringOrThrow(Constants.ID)
        .ifBlank {
            throw InvalidPropertyValue(
                propertyKey = Constants.ID,
                allowedValuesDescription = "ID must not be blank.",
                offendingJsonString = json.toString()
            )
        }

    fun requireSoType(json: ReadOnlyJson, requiredObjType: String) {
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

    fun getLocalId(json: ReadOnlyJson): String? = json.optStringOrNull(KEY_LOCAL_ID)
    fun getLocalStatus(json: ReadOnlyJson): LocalStatus = json.coerceToLocalStatus()
}
