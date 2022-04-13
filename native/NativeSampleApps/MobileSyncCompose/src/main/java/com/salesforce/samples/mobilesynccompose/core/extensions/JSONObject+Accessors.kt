package com.salesforce.samples.mobilesynccompose.core.extensions

import org.json.JSONObject

fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (this.isNotNull(key)) getBoolean(key)
    else null

fun JSONObject.optIntOrNull(key: String): Int? =
    if (this.isNotNull(key)) getInt(key)
    else null

fun JSONObject.optLongOrNull(key: String): Long? =
    if (this.isNotNull(key)) getLong(key)
    else null

fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (this.isNotNull(key)) getDouble(key)
    else null

fun JSONObject.optStringOrNull(key: String): String? =
    if (this.isNotNull(key)) getString(key)
    else null

fun JSONObject.isNotNull(key: String) = !isNull(key)
