package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.extensions.optStringOrNull
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord.Companion.KEY_LOCAL_ID
import org.json.JSONObject
import java.util.*

object SObjectDeserializerHelper {
    fun getPrimaryKeyOrThrow(json: JSONObject): PrimaryKey = json
        .getRequiredStringOrThrow(Constants.ID, valueCanBeBlank = false)
        .let { PrimaryKey(it) }

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

    fun getLocalId(json: JSONObject): LocallyCreatedId? =
        json.optStringOrNull(KEY_LOCAL_ID)?.let { LocallyCreatedId(it) }
}
