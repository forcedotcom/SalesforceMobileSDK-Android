package com.salesforce.samples.mobilesynccompose.core.data

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.LocalStatus
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.coerceToLocalStatus
import org.json.JSONObject

class ReadOnlyJson private constructor(
    private val rawString: String,
    private val safeCopy: JSONObject
) {
    private val hashCode: Int by lazy { safeCopy.hashCode() }

    fun getBoolean(name: String): Boolean = safeCopy.getBoolean(name)
    fun optBoolean(name: String?): Boolean = safeCopy.optBoolean(name)
    fun optBoolean(name: String?, fallback: Boolean): Boolean = safeCopy.optBoolean(name, fallback)

    fun getString(name: String): String = safeCopy.getString(name)
    fun optString(name: String?): String = safeCopy.optString(name)
    fun optString(name: String?, fallback: String): String = safeCopy.optString(name, fallback)
    fun optStringOrNull(name: String): String? =
        if (safeCopy.has(name)) safeCopy.getString(name)
        else null

    fun getInt(name: String): Int = safeCopy.getInt(name)
    fun optInt(name: String?): Int = safeCopy.optInt(name)
    fun optInt(name: String?, fallback: Int): Int = safeCopy.optInt(name, fallback)

    fun getDouble(name: String): Double = safeCopy.getDouble(name)
    fun optDouble(name: String?): Double = safeCopy.optDouble(name)
    fun optDouble(name: String?, fallback: Double): Double = safeCopy.optDouble(name, fallback)

    fun getLong(name: String): Long = safeCopy.getLong(name)
    fun optLong(name: String?): Long = safeCopy.optLong(name)
    fun optLong(name: String?, fallback: Long): Long = safeCopy.optLong(name, fallback)

    fun getJSONObject(name: String): JSONObject = safeCopy.getJSONObject(name)

    fun get(name: String): Any = safeCopy.get(name)
    fun opt(name: String?): Any? = safeCopy.opt(name)

    fun coerceToLocalStatus(): LocalStatus = safeCopy.coerceToLocalStatus()

    fun buildMutableCopy(modifierBlock: JSONObject.() -> Unit = {}): JSONObject =
        JSONObject(rawString).apply(modifierBlock)

    fun copy(modifierBlock: JSONObject.() -> Unit): ReadOnlyJson {
        val safeCopy = buildMutableCopy().apply(modifierBlock)
        val rawString = safeCopy.toString()
        return ReadOnlyJson(rawString = rawString, safeCopy = safeCopy)
    }

    override fun toString(): String = rawString

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReadOnlyJson

        // TODO Will this actually evaluate to true if the fields are all the same but in a different order?
        if (safeCopy != other.safeCopy) return false

        return true
    }

    override fun hashCode(): Int = hashCode

    companion object {
        fun from(inJson: JSONObject) =
            inJson.toString().let { ReadOnlyJson(rawString = it, safeCopy = JSONObject(it)) }
    }
}
