/*
 * Copyright (c) 2022-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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

fun JSONArray.firstOrNull(): JSONObject? = if (length() > 0) this.getJSONObject(0) else null

@Throws(NoSuchElementException::class)
fun JSONArray.first(): JSONObject {
    if (length() < 1)
        throw NoSuchElementException()

    return this.getJSONObject(0)
}

inline fun JSONArray.firstOrNull(predicate: (JSONObject) -> Boolean): JSONObject? {
    var result: JSONObject? = null

    this.forEach {
        if (predicate(it)) {
            result = it
            return@forEach
        }
    }

    return result
}
