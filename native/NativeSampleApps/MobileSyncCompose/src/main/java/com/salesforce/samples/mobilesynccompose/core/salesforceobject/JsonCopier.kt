package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import org.json.JSONObject

class JsonCopier private constructor(val rawString: String) {
    private val comparisonObj: JSONObject by lazy { JSONObject(rawString) }
    constructor(inJson: JSONObject) : this(inJson.toString())
    fun buildCopy() = JSONObject(rawString)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JsonCopier

        if (comparisonObj != other.comparisonObj) return false

        return true
    }

    override fun hashCode(): Int {
        return comparisonObj.hashCode()
    }
}
