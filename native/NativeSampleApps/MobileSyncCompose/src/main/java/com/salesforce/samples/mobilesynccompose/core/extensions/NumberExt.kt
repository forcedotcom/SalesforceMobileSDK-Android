package com.salesforce.samples.mobilesynccompose.core.extensions

fun UInt.coerceToPositiveInt(): Int =
    if (this > Int.MAX_VALUE.toUInt())
        Int.MAX_VALUE
    else
        this.toInt()
