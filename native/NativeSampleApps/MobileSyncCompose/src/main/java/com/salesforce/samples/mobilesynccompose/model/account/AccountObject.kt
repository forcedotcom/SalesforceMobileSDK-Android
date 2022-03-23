package com.salesforce.samples.mobilesynccompose.model.account

import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.ReadOnlyJson
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.*
import org.json.JSONObject

data class AccountObject(
    val name: String,
    override val id: SObjectId,
    override val localStatus: LocalStatus,
    override val originalElt: ReadOnlyJson
) : SObject {

    override fun buildUpdatedElt(): JSONObject = originalElt.buildMutableCopy().apply {
        put(Constants.NAME, name)
    }

    override val curPropertiesAreModifiedFromOriginal: Boolean by lazy {
        originalElt.optStringOrNull(Constants.NAME) != name
    }

    companion object : SalesforceObjectDeserializer<AccountObject> {
        private const val OBJECT_TYPE = Constants.ACCOUNT

        @Throws(CoerceException::class)
        override fun coerceFromJsonOrThrow(json: JSONObject): AccountObject {
            val safeJson = ReadOnlyJson(json)
            ReadOnlySoHelper.requireSoType(safeJson, OBJECT_TYPE)

            val serverId = ReadOnlySoHelper.getServerIdOrThrow(safeJson)
            val localId = ReadOnlySoHelper.getLocalId(safeJson)

            return AccountObject(
                id = SObjectId(primaryKey = serverId, localId = localId),
                localStatus = ReadOnlySoHelper.getLocalStatus(safeJson),
                originalElt = safeJson,
                name = safeJson.getRequiredStringOrThrow(Constants.NAME),
            )
        }

        fun createNewLocal(name: String) = createNewSoupEltBase(forObjType = OBJECT_TYPE)
            .apply {
                put(Constants.NAME, name)
            }.let {
                coerceFromJsonOrThrow(it)
            }
    }
}
