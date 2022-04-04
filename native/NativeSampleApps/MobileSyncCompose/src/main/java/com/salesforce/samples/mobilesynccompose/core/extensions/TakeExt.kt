package com.salesforce.samples.mobilesynccompose.core.extensions

inline fun <reified T> Any.takeIfInstance(): T? = if (this is T) this else null
