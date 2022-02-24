package com.salesforce.samples.mobilesynccompose.core

/**
 * A data structure to encapsulate the result of an operation. A [SealedResult] can only be in one
 * of two states: [SealedSuccess] or [SealedFailure]. Using a when-clause on a [SealedResult]
 * enables easy use of either the success value or the failure cause.
 */
sealed interface SealedResult<out S, out F : Any>
data class SealedSuccess<out S, out F : Any>(val value: S) : SealedResult<S, F>
data class SealedFailure<out S, out F : Any>(val cause: F) : SealedResult<S, F>
