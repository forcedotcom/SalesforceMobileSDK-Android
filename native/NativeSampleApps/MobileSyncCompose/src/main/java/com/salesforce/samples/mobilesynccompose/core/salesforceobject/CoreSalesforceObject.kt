package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.androidsdk.mobilesync.target.SyncTarget.*
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.extensions.optStringOrNull
import com.salesforce.samples.mobilesynccompose.core.extensions.putIfAbsent
import org.json.JSONObject
import java.util.*

interface CoreSalesforceObject {
    val localId: String?
    val serverId: String

    //    val objectType: String
    val localStatus: LocalStatus
//    val locallyCreated: Boolean
//    val locallyDeleted: Boolean
//    val locallyUpdated: Boolean
//    val local: Boolean

    fun buildSafeEltCopy(): JSONObject
}

interface ImmutableSo : CoreSalesforceObject {
    val hasUnsavedChanges: Boolean
}

data class CoreSalesforceObjectImpl
@Throws(CoerceException::class) constructor(
    private val soupEltCopier: JsonCopier,
    private val objectType: String
) : CoreSalesforceObject {
    override val serverId: String
    override val localId: String?
    override val localStatus: LocalStatus

    init {
        val safeCopy = soupEltCopier.buildCopy()
        val attributes = safeCopy.getRequiredObjectOrThrow(Constants.ATTRIBUTES)

        val type = attributes.optString(Constants.TYPE.lowercase(Locale.US))
        if (type != objectType) {
            throw IncorrectObjectType(
                expectedObjectType = objectType,
                foundObjectType = type,
                offendingJson = safeCopy,
            )
        }

        serverId = safeCopy
            .getRequiredStringOrThrow(Constants.ID)
            .ifBlank {
                throw InvalidPropertyValue(
                    propertyKey = Constants.ID,
                    allowedValuesDescription = "ID must not be blank.",
                    offendingJson = safeCopy
                )
            }
        localId = safeCopy.optStringOrNull(KEY_LOCAL_ID)
        localStatus = safeCopy.coerceToLocalStatus()
    }

//    override val locallyCreated: Boolean by localStatus::locallyCreated
//    override val locallyDeleted: Boolean by localStatus::locallyDeleted
//    override val locallyUpdated: Boolean by localStatus::locallyUpdated
//    override val local: Boolean by localStatus::local

    override fun buildSafeEltCopy(): JSONObject = soupEltCopier.buildCopy().apply {
        putIfAbsent(LOCALLY_CREATED, localStatus.isLocallyCreated)
        putIfAbsent(LOCALLY_DELETED, localStatus.isLocallyDeleted)
        putIfAbsent(LOCALLY_UPDATED, localStatus.isLocallyUpdated)
        putIfAbsent(LOCAL, localStatus.isLocal)

        putIfAbsent(Constants.ID, serverId)

        if (localId != null) {
            put(KEY_LOCAL_ID, localId)
        } else {
            remove(KEY_LOCAL_ID)
        }
    }

    companion object {
        const val KEY_LOCAL_ID = "LocalId"
    }
}
