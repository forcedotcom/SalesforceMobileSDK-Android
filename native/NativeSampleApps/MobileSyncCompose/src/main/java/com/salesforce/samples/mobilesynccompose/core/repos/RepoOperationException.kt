package com.salesforce.samples.mobilesynccompose.core.repos

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.PrimaryKey

sealed class RepoOperationException : Exception() {
    data class InvalidResultObject(
        override val message: String?,
        override val cause: Throwable?
    ) : RepoOperationException()

    data class SmartStoreOperationFailed(
        override val message: String?,
        override val cause: Throwable?
    ) : RepoOperationException()

    data class RecordNotFound(
        val id: PrimaryKey,
        val soupName: String,
        override val cause: Throwable?
    ) : RepoOperationException() {
        override val message: String = "Could not retrieve the requested object from soup=$soupName for ID=$id."
    }
}
