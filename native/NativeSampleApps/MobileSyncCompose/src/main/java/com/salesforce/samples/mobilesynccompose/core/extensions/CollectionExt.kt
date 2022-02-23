package com.salesforce.samples.mobilesynccompose.core.extensions

inline fun <reified T> List<T>.replaceAll(newValue: T, predicate: (T) -> Boolean): List<T> {
    return map { if(predicate(it)) newValue else it }
}

inline fun <reified T> List<T>.addOrReplaceAll(newValue: T, predicate: (T) -> Boolean): List<T> {
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
