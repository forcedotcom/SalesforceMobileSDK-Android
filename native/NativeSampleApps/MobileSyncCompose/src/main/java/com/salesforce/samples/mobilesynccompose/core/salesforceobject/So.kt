package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.samples.mobilesynccompose.core.ReadOnlyJson
import org.json.JSONObject

interface So {
    val serverId: String
    val localId: String?
    val localStatus: LocalStatus
    val hasUnsavedChanges: Boolean

    fun buildUpdatedElt(): JSONObject

    companion object {
        const val KEY_LOCAL_ID = "LocalId"
    }
}
