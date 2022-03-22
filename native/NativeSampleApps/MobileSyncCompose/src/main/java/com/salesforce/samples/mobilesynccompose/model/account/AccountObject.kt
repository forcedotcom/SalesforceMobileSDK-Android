package com.salesforce.samples.mobilesynccompose.model.account

import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.ReadOnlyJson
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.*
import org.json.JSONObject

data class AccountObject(
    val name: String,
    override val id: SoId,
    override val localStatus: LocalStatus,
    private val elt: ReadOnlyJson
) : So {

    override fun buildUpdatedElt(): JSONObject = elt.buildMutableCopy().apply {
        put(Constants.NAME, name)
    }

    override val hasUnsavedChanges: Boolean by lazy {
        elt.optStringOrNull(Constants.NAME) != name
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
                id = SoId(primaryKey = serverId, localId = localId),
                localStatus = ReadOnlySoHelper.getLocalStatus(safeJson),
                elt = safeJson,
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
