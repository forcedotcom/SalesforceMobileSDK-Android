/*
 * Copyright (c) 2025-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.samples.authflowtester

import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume

const val FAILED_OPERATION = "The operation could not be completed."
const val UNKNOWN_ERROR = "An unexpected error has occurred."
const val REVOKE_SUCCESS = "The access token has been successfully revoked.  " +
        "You may need to make a REST API request to trigger a token refresh."
const val REQUEST_SUCCESS = "The REST API request completed successfully.  Expand " +
        "'Response Details' section to see the full response."
const val AUTH_REQUIRED = "Please authenticate to use this function."

data class RequestResult(val success: Boolean, val displayValue: String, val response: String? = null)

enum class ConcurrentRequestType(val displayName: String) {
    RESOURCES("Resources"),
    LIMITS("Limits"),
    DESCRIBE_GLOBAL("Describe Global"),
}

enum class ConcurrentRequestState {
    QUEUED,
    IN_FLIGHT,
    SUCCEEDED,
    FAILED,
}

data class ConcurrentRequestResult(
    val index: Int,
    val type: ConcurrentRequestType,
    val endpoint: String,
    val state: ConcurrentRequestState,
    val statusCode: Int? = null,
    val errorDescription: String? = null,
    val responseBody: String? = null,
)

suspend fun revokeAccessTokenAction(client: RestClient?): RequestResult {
    val accessToken = SalesforceSDKManager.getInstance().userAccountManager.currentUser?.authToken
    return revokeAccessTokenAction(client, accessToken)
}

suspend fun revokeAccessTokenAction(
    client: RestClient?,
    accessToken: String?,
): RequestResult {
    // This should never happen.
    if (client == null || accessToken == null) return RequestResult(success = false, AUTH_REQUIRED)

    val encodedToken = withContext(Dispatchers.IO) {
        URLEncoder.encode(accessToken, StandardCharsets.UTF_8.toString())
    }
    val body = "token=$encodedToken".toRequestBody(
        contentType = "application/x-www-form-urlencoded".toMediaType(),
    )
    val request = RestRequest(
        RestRequest.RestMethod.POST,
        RestRequest.RestEndpoint.INSTANCE,
        "/services/oauth2/revoke",
        body,
        /* additionalHttpHeaders = */ emptyMap<String, String>(),
    )

    val result = client.sendAsync(request)
    if (result.isSuccess) {
        val response = result.getOrNull()
        val displayValue = when {
            response == null -> FAILED_OPERATION
            response.isSuccess -> REVOKE_SUCCESS
            !response.isSuccess -> "$FAILED_OPERATION Error code: ${response.statusCode}"
            else -> FAILED_OPERATION
        }

        return RequestResult(response?.isSuccess ?: false, displayValue)
    } else {
        val displayValue = result.exceptionOrNull()?.message ?: UNKNOWN_ERROR
        return RequestResult(false, displayValue)
    }
}

suspend fun makeRestRequest(client: RestClient?, apiVersion: String): RequestResult {
    // This should never happen.
    if (client == null) return RequestResult(success = false, AUTH_REQUIRED)

    val result = client.sendAsync(RestRequest.getCheapRequest(apiVersion))
    if (result.isSuccess) {
        val response = result.getOrNull()
        val displayValue = when {
            response == null -> FAILED_OPERATION
            response.isSuccess -> REQUEST_SUCCESS
            !response.isSuccess -> "$FAILED_OPERATION Error code: ${response.statusCode}"
            else -> FAILED_OPERATION
        }
        val formattedResponse = try {
            val jsonElement = response?.asString()?.let { Json.parseToJsonElement(it) }
            Json { prettyPrint = true }.encodeToString(jsonElement)
        } catch (_: Exception) {
            response?.asString() ?: ""
        }

        return RequestResult(response?.isSuccess ?: false, displayValue, formattedResponse)
    } else {
        return RequestResult(false, result.exceptionOrNull()?.message ?: UNKNOWN_ERROR)
    }
}

fun concurrentRestRequest(
    index: Int,
    apiVersion: String,
    failedRequestIndex: Int?,
): Pair<ConcurrentRequestType, RestRequest> {
    val type = ConcurrentRequestType.entries[index % ConcurrentRequestType.entries.size]
    val request = if (failedRequestIndex == index) {
        RestRequest(
            RestRequest.RestMethod.GET,
            "/services/data/v$apiVersion/authflowtester-invalid-endpoint-$index",
        )
    } else {
        when (type) {
            ConcurrentRequestType.RESOURCES -> RestRequest.getRequestForResources(apiVersion)
            ConcurrentRequestType.LIMITS -> RestRequest.getRequestForLimits(apiVersion)
            ConcurrentRequestType.DESCRIBE_GLOBAL -> RestRequest.getRequestForDescribeGlobal(apiVersion)
        }
    }
    return type to request
}

suspend fun makeConcurrentRestRequest(
    client: RestClient?,
    index: Int,
    apiVersion: String,
    failedRequestIndex: Int?,
    onSubmitted: () -> Unit = {},
): ConcurrentRequestResult {
    val (type, request) = concurrentRestRequest(index, apiVersion, failedRequestIndex)
    if (client == null) {
        return ConcurrentRequestResult(
            index = index,
            type = type,
            endpoint = request.path,
            state = ConcurrentRequestState.FAILED,
            errorDescription = AUTH_REQUIRED,
        )
    }

    val result = client.sendConcurrentAsync(request, onSubmitted)
    return result.fold(
        onSuccess = { response ->
            val responseBody = try {
                response.asString()
            } catch (e: Exception) {
                e.message
            } finally {
                response.consumeQuietly()
            }
            if (response.isSuccess) {
                ConcurrentRequestResult(
                    index = index,
                    type = type,
                    endpoint = request.path,
                    state = ConcurrentRequestState.SUCCEEDED,
                    statusCode = response.statusCode,
                )
            } else {
                ConcurrentRequestResult(
                    index = index,
                    type = type,
                    endpoint = request.path,
                    state = ConcurrentRequestState.FAILED,
                    statusCode = response.statusCode,
                    errorDescription = "$FAILED_OPERATION Error code: ${response.statusCode}",
                    responseBody = responseBody,
                )
            }
        },
        onFailure = { error ->
            ConcurrentRequestResult(
                index = index,
                type = type,
                endpoint = request.path,
                state = ConcurrentRequestState.FAILED,
                errorDescription = error.message ?: UNKNOWN_ERROR,
            )
        },
    )
}

/**
 * Enqueues the request through RestClient's real asynchronous API, then reports submission. This
 * gives the in-flight interruption modes a concrete barrier: the trigger cannot run until each
 * counted call is owned by OkHttp's dispatcher.
 */
private suspend fun RestClient.sendConcurrentAsync(
    request: RestRequest,
    onSubmitted: () -> Unit,
): Result<RestResponse> = suspendCancellableCoroutine { continuation ->
    try {
        val call = sendAsync(request, object : RestClient.AsyncRequestCallback {
            override fun onSuccess(request: RestRequest, response: RestResponse) {
                if (continuation.isActive) continuation.resume(Result.success(response))
                else response.consumeQuietly()
            }

            override fun onError(exception: Exception) {
                if (continuation.isActive) continuation.resume(Result.failure(exception))
            }
        })
        continuation.invokeOnCancellation { call.cancel() }
        onSubmitted()
    } catch (exception: Exception) {
        if (continuation.isActive) continuation.resume(Result.failure(exception))
    }
}

suspend fun RestClient.sendAsync(request: RestRequest): Result<RestResponse> {
    return withContext(Dispatchers.IO) {
        try {
            when (val response = sendSync(request)) {
                null -> Result.failure(Exception(UNKNOWN_ERROR))
                else -> Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
