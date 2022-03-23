package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.samples.mobilesynccompose.core.ReadOnlyJson
import org.json.JSONObject

interface SObject {
    val id: SObjectId
    val localStatus: LocalStatus
    val curPropertiesAreModifiedFromOriginal: Boolean
    val originalElt: ReadOnlyJson

    fun buildUpdatedElt(): JSONObject

    companion object {
        const val KEY_LOCAL_ID = "LocalId"
    }
}
