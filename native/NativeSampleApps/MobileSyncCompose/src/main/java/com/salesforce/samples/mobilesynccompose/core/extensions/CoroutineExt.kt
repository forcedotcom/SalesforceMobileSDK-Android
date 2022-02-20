package com.salesforce.samples.mobilesynccompose.core.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


suspend fun <T> List<T>.parallelFirstOrNull(
    thresholdSize: UInt = 100u,
    predicate: (T) -> Boolean
): T? {
    return if (size > thresholdSize.coerceToPositiveInt()) {
        withContext(Dispatchers.Default) {
            firstOrNull(predicate)
        }
    } else {
        firstOrNull(predicate)
    }
}

suspend fun <T> List<T>.parallelFilter(
    thresholdSize: UInt = 100u,
    predicate: (T) -> Boolean
): List<T> {
    return if (size > thresholdSize.coerceToPositiveInt()) {
        withContext(Dispatchers.Default) {
            filter(predicate)
        }
    } else {
        filter(predicate)
    }
}
