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

fun JSONObject.putIfAbsent(key: String, value: Boolean): JSONObject =
    if (!this.has(key)) put(key, value)
    else this

fun JSONObject.putIfAbsent(key: String, value: Int): JSONObject =
    if (!this.has(key)) put(key, value)
    else this

fun JSONObject.putIfAbsent(key: String, value: Long): JSONObject =
    if (!this.has(key)) put(key, value)
    else this

fun JSONObject.putIfAbsent(key: String, value: Double): JSONObject =
    if (!this.has(key)) put(key, value)
    else this

fun JSONObject.putIfAbsent(key: String, value: Any?): JSONObject =
    if (!this.has(key)) put(key, value)
    else this
