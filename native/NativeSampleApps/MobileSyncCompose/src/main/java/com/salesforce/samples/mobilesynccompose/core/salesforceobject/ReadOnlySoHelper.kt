package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.data.ReadOnlyJson
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.StoreRecordMetadata.Companion.KEY_LOCAL_ID
import java.util.*

object ReadOnlySoHelper {
    fun getPrimaryKeyOrThrow(json: ReadOnlyJson): PrimaryKey = json
        .getRequiredStringOrThrow(Constants.ID, valueCanBeBlank = false)
        .let { PrimaryKey(it) }

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

    fun getLocalId(json: ReadOnlyJson): LocalId? =
        json.optStringOrNull(KEY_LOCAL_ID)?.let { LocalId(it) }

    fun getLocalStatus(json: ReadOnlyJson): LocalStatus = json.coerceToLocalStatus()
}
