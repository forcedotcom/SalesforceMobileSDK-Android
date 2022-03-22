package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.samples.mobilesynccompose.core.ReadOnlyJson
import org.json.JSONObject

interface So {
    val id: SoId
//    val serverId: ServerId
//    val localId: LocalId?
    val localStatus: LocalStatus
    val hasUnsavedChanges: Boolean

    fun buildUpdatedElt(): JSONObject

    companion object {
        const val KEY_LOCAL_ID = "LocalId"
    }
}

//val So.preferredId: SoId get() = localId ?: serverId
