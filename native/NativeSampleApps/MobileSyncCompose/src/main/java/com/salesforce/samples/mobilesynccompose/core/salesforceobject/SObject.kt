package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import org.json.JSONObject

interface SObject {
    fun JSONObject.applyObjProperties(): JSONObject
    val objectType: String
}
