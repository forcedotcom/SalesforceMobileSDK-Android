package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.androidsdk.mobilesync.target.SyncTarget.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

@Throws(CoerceException::class)
fun JSONObject.getRequiredStringOrThrow(key: String, valueCanBeBlank: Boolean = true): String =
    try {
        val value = this.getString(key)
        if (!valueCanBeBlank) {
            value.ifBlank {
                throw InvalidPropertyValue(
                    propertyKey = key,
                    allowedValuesDescription = "$key must not be blank.",
                    offendingJsonString = this.toString()
                )
            }
        }

        value
    } catch (ex: JSONException) {
        throw MissingRequiredProperties(propertyKeys = arrayOf(key), offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun JSONObject.getRequiredIntOrThrow(key: String): Int =
    try {
        this.getInt(key)
    } catch (ex: JSONException) {
        throw MissingRequiredProperties(propertyKeys = arrayOf(key), offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun JSONObject.getRequiredLongOrThrow(key: String): Long =
    try {
        this.getLong(key)
    } catch (ex: JSONException) {
        throw MissingRequiredProperties(propertyKeys = arrayOf(key), offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun JSONObject.getRequiredDoubleOrThrow(key: String): Double =
    try {
        this.getDouble(key)
    } catch (ex: JSONException) {
        throw MissingRequiredProperties(propertyKeys = arrayOf(key), offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun JSONObject.getRequiredBooleanOrThrow(key: String): Boolean =
    try {
        this.getBoolean(key)
    } catch (ex: JSONException) {
        throw MissingRequiredProperties(propertyKeys = arrayOf(key), offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun JSONObject.getRequiredObjectOrThrow(key: String): JSONObject =
    try {
        this.getJSONObject(key)
    } catch (ex: JSONException) {
        throw MissingRequiredProperties(propertyKeys = arrayOf(key), offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun JSONObject.getRequiredArrayOrThrow(key: String): JSONArray =
    try {
        this.getJSONArray(key)
    } catch (ex: JSONException) {
        throw MissingRequiredProperties(propertyKeys = arrayOf(key), offendingJsonString = this.toString())
    }

fun JSONObject.coerceToLocalStatus(): LocalStatus {
    val locallyCreated: Boolean = optBoolean(LOCALLY_CREATED, false)
    val locallyDeleted: Boolean = optBoolean(LOCALLY_DELETED, false)
    val locallyUpdated: Boolean = optBoolean(LOCALLY_UPDATED, false)

    return when {
        locallyDeleted -> {
            if (locallyUpdated) LocalStatus.LocallyDeletedAndLocallyUpdated
            else LocalStatus.LocallyDeleted
        }
        locallyCreated -> LocalStatus.LocallyCreated
        locallyUpdated -> LocalStatus.LocallyUpdated
        else -> LocalStatus.MatchesUpstream
    }
}
