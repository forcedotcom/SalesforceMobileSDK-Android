package com.salesforce.samples.mobilesynccompose.core

sealed interface SealedResult<out S : Any, out F : Any>
data class SealedSuccess<out S : Any, out F : Any>(val value: S) : SealedResult<S, F>
data class SealedFailure<out S : Any, out F : Any>(val cause: F) : SealedResult<S, F>
