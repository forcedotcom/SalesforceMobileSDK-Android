package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.samples.mobilesynccompose.core.data.ReadOnlyJson
import org.json.JSONObject

interface SObject {
    val id: SObjectId
    val localStatus: LocalStatus?
    val elt: ReadOnlyJson

    companion object {
        const val KEY_LOCAL_ID = "LocalId"
    }
}
