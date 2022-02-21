package com.salesforce.samples.mobilesynccompose.core.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


suspend fun <T> List<T>.parallelFirstOrNull(
    predicate: (T) -> Boolean
): T? = withContext(Dispatchers.Default) { firstOrNull(predicate) }

suspend fun <T> List<T>.parallelFilter(
    predicate: (T) -> Boolean
): List<T> = withContext(Dispatchers.Default) { filter(predicate) }
