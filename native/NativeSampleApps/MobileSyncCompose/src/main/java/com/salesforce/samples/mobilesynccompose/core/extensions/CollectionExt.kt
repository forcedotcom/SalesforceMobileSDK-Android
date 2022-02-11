package com.salesforce.samples.mobilesynccompose.core.extensions

inline fun <reified T> List<T>.replaceAll(newValue: T, predicate: (T) -> Boolean): List<T> {
    return map { if(predicate(it)) newValue else it }
}
