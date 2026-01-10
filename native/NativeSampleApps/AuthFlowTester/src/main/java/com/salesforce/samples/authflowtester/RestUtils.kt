package com.salesforce.samples.authflowtester

import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val FAILED_OPERATION = "The operation could not be completed."
const val UNKNOWN_ERROR = "An unexpected error has occurred."
const val REVOKE_SUCCESS = "The access token has been successfully revoked.  " +
        "You may need to make a REST API request to trigger a token refresh."
const val REQUEST_SUCCESS = "The REST API request completed successfully.  Expand " +
        "'Response Details' section to see the full response."
const val AUTH_REQUIRED = "Please authenticate to use this function."

data class RequestResult(val success: Boolean, val displayValue: String, val response: String? = null)

suspend fun revokeAccessTokenAction(client: RestClient?): RequestResult {
    // This should never happen.
    if (client == null) return RequestResult(success = false, AUTH_REQUIRED)

    val token = SalesforceSDKManager.getInstance().userAccountManager.currentUser.authToken
    val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.toString())
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

suspend fun RestClient.sendAsync(request: RestRequest): Result<RestResponse> {
    return suspendCoroutine { continuation ->
        sendAsync(request, object : RestClient.AsyncRequestCallback {
            override fun onSuccess(request: RestRequest?, response: RestResponse?) {
                val result: Result<RestResponse> = if (response == null) {
                    Result.failure(Exception(UNKNOWN_ERROR))
                } else {
                    Result.success(response)
                }
                continuation.resume(result)
            }

            override fun onError(exception: Exception?) {
                continuation.resume(Result.failure(exception ?: Exception(UNKNOWN_ERROR)))
            }
        })
    }
}