package com.salesforce.samples.mobilesynccompose.core.extensions

import kotlinx.coroutines.sync.Mutex

fun Mutex.requireIsLocked() {
    require(isLocked) { "This operation is only permitted while this mutex is locked." }
}
