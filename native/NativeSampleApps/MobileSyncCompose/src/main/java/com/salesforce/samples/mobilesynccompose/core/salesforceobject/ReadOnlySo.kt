package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.ReadOnlyJson
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.CoreSalesforceObjectImpl.Companion.KEY_LOCAL_ID
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject.Companion.KEY_FIRST_NAME
import org.json.JSONObject
import java.util.*

interface ReadOnlySo {
    val localId: String?
    val serverId: String

    //    val objectType: String
    val localStatus: LocalStatus
//    val locallyCreated: Boolean
//    val locallyDeleted: Boolean
//    val locallyUpdated: Boolean
//    val local: Boolean

    val updatedElt: JSONObject
}

object ReadOnlySoHelper {
    fun getServerIdOrThrow(json: ReadOnlyJson): String = json
        .getRequiredStringOrThrow(Constants.ID)
        .ifBlank {
            throw InvalidPropertyValue(
                propertyKey = Constants.ID,
                allowedValuesDescription = "ID must not be blank.",
                offendingJson = safeCopy
            )
        }

    fun requireSoType(json: ReadOnlyJson, requiredObjType: String) {
        val attributes = originalElt.getRequiredObjectOrThrow(Constants.ATTRIBUTES)

        val type = attributes.optString(Constants.TYPE.lowercase(Locale.US))
        if (type != requiredObjType) {
            throw IncorrectObjectType(
                expectedObjectType = requiredObjType,
                foundObjectType = type,
                offendingJson = safeCopy,
            )
        }
    }

    fun getLocalId(json: ReadOnlyJson): String? = json.optStringOrNull(KEY_LOCAL_ID)
    fun getLocalStatus(json: ReadOnlyJson): LocalStatus = json.coerceToLocalStatus()
}

data class ContactObject2(
    val firstName: String?,
    val accountId: String?,
    override val serverId: String,
    override val localId: String?,
    override val localStatus: LocalStatus,
    private val originalElt: ReadOnlyJson
) : ReadOnlySo {

    override val updatedElt: JSONObject by lazy {
        originalElt.buildMutableCopy().apply {
            putOpt(KEY_FIRST_NAME, firstName)
        }
    }
}
