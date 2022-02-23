package com.salesforce.samples.mobilesynccompose.core

sealed interface SealedResult<out S, out F : Any>
data class SealedSuccess<out S, out F : Any>(val value: S) : SealedResult<S, F>
data class SealedFailure<out S, out F : Any>(val cause: F) : SealedResult<S, F>
