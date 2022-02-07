package com.salesforce.samples.mobilesynccompose.core.extensions

import org.json.JSONArray
import org.json.JSONObject

inline fun JSONArray.forEach(block: (JSONObject) -> Unit) {
    val length = length().also { if (it < 1) return }

    for (i in 0 until length) {
        block(getJSONObject(i))
    }
}

inline fun JSONArray.forEachIndexed(block: (index: Int, JSONObject) -> Unit) {
    val length = length().also { if (it < 1) return }

    for (i in 0 until length) {
        block(i, getJSONObject(i))
    }
}

inline fun <reified T> JSONArray.map(mapper: (JSONObject) -> T): List<T> {
    val results = mutableListOf<T>()

    this.forEach { jsonObject ->
        results.add(mapper(jsonObject))
    }

    return results
}
