package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import com.salesforce.androidsdk.mobilesync.target.SyncTarget.*
import com.salesforce.samples.mobilesynccompose.core.data.ReadOnlyJson
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
        throw MissingRequiredProperty(propertyKey = key, offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun ReadOnlyJson.getRequiredStringOrThrow(key: String, valueCanBeBlank: Boolean = true): String =
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
        throw MissingRequiredProperty(propertyKey = key, offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun JSONObject.getRequiredIntOrThrow(key: String): Int =
    try {
        this.getInt(key)
    } catch (ex: JSONException) {
        throw MissingRequiredProperty(propertyKey = key, offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun JSONObject.getRequiredLongOrThrow(key: String): Long =
    try {
        this.getLong(key)
    } catch (ex: JSONException) {
        throw MissingRequiredProperty(propertyKey = key, offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun ReadOnlyJson.getRequiredLongOrThrow(key: String): Long =
    try {
        this.getLong(name = key)
    } catch (ex: Exception) {
        throw MissingRequiredProperty(propertyKey = key, offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun JSONObject.getRequiredDoubleOrThrow(key: String): Double =
    try {
        this.getDouble(key)
    } catch (ex: JSONException) {
        throw MissingRequiredProperty(propertyKey = key, offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun JSONObject.getRequiredBooleanOrThrow(key: String): Boolean =
    try {
        this.getBoolean(key)
    } catch (ex: JSONException) {
        throw MissingRequiredProperty(propertyKey = key, offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun JSONObject.getRequiredObjectOrThrow(key: String): JSONObject =
    try {
        this.getJSONObject(key)
    } catch (ex: JSONException) {
        throw MissingRequiredProperty(propertyKey = key, offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun ReadOnlyJson.getRequiredObjectOrThrow(key: String): JSONObject =
    try {
        this.getJSONObject(key)
    } catch (ex: JSONException) {
        throw MissingRequiredProperty(propertyKey = key, offendingJsonString = this.toString())
    }

@Throws(CoerceException::class)
fun JSONObject.getRequiredArrayOrThrow(key: String): JSONArray =
    try {
        this.getJSONArray(key)
    } catch (ex: JSONException) {
        throw MissingRequiredProperty(propertyKey = key, offendingJsonString = this.toString())
    }

//@Throws(CoerceException::class)
fun JSONObject.coerceToLocalStatus(): LocalStatus {
    val locallyCreated: Boolean = optBoolean(LOCALLY_CREATED, false)
    val locallyDeleted: Boolean = optBoolean(LOCALLY_DELETED, false)
    val locallyUpdated: Boolean = optBoolean(LOCALLY_UPDATED, false)

//    if (local) {
//        if (!(locallyDeleted || locallyCreated || locallyUpdated)) {
//            throw InvalidPropertyValue(
//                propertyKey = LOCAL,
//                allowedValuesDescription = "Cannot be local=true if no other local flags are set to true.",
//                this.toString()
//            )
//        }
//    } else {
//        if (locallyDeleted || locallyCreated || locallyUpdated) {
//            throw InvalidPropertyValue(
//                propertyKey = LOCAL,
//                allowedValuesDescription = "Cannot be local=false if other local flags are set to true",
//                this.toString()
//            )
//        }
//    }
//
//    if (locallyCreated && locallyDeleted) {
//        throw InvalidPropertyValue(
//            propertyKey = "$LOCALLY_CREATED + $LOCALLY_DELETED",
//            allowedValuesDescription = "Cannot be both locally-deleted and locally-created.",
//            this.toString()
//        )
//    }
//
//    if (locallyCreated && locallyUpdated) {
//        throw InvalidPropertyValue(
//            propertyKey = "$LOCALLY_CREATED + $LOCALLY_UPDATED",
//            allowedValuesDescription = "Cannot be both locally-created and locally-updated."
//        )
//    }

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
