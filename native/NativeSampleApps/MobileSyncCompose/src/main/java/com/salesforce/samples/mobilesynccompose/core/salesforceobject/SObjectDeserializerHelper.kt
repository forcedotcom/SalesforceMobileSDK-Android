package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.extensions.optStringOrNull
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecordCreatedDuringThisLoginSession.Companion.KEY_LOCAL_ID
import org.json.JSONObject
import java.util.*

object SObjectDeserializerHelper {
    fun getPrimaryKeyOrThrow(json: JSONObject): String = json
        .getRequiredStringOrThrow(Constants.ID, valueCanBeBlank = false)

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

    fun getLocalId(json: JSONObject): String? = json.optStringOrNull(KEY_LOCAL_ID)
}
