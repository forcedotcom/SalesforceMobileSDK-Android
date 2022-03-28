package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import org.json.JSONObject

interface SObjectModel {
    fun JSONObject.applyMemoryModelProperties(): JSONObject
}
