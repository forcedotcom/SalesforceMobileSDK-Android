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

fun <T> List<T>.minusAll(selector: (T) -> Boolean): List<T> {
    val results = mutableListOf<T>()
    this.forEach {
        if (!selector(it)) {
            results.add(it)
        }
    }
    return results
}

data class ResultPartition<out S>(
    val successes: List<S>,
    val failures: List<Throwable>
)

fun <T> Iterable<Result<T>>.partitionBySuccess(): ResultPartition<T> {
    val successes = mutableListOf<T>()
    val failures = mutableListOf<Throwable>()

    this.forEach { result ->
        result.getOrNull()?.also {
            successes.add(it)
            return@forEach
        }
        result.exceptionOrNull()?.also {
            failures.add(it)
            return@forEach
        }
    }

    return ResultPartition(successes = successes, failures = failures)
}

fun <T> List<T>.replaceAll(newValue: T, predicate: (T) -> Boolean): List<T> {
    return map { if (predicate(it)) newValue else it }
}

fun <T> List<T>.replaceAllOrAddNew(newValue: T, predicate: (T) -> Boolean): List<T> {
    val results = mutableListOf<T>()
    var hasReplaced = false
    this.forEach {

        if (predicate(it)) {
            hasReplaced = true
            results.add(newValue)
        } else {
            results.add(it)
        }
    }
    if (!hasReplaced) {
        results.add(newValue)
    }

    return results
}
