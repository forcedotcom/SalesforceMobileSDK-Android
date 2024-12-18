package com.salesforce.androidsdk.auth

import com.salesforce.androidsdk.auth.HttpAccess.DEFAULT
import com.salesforce.androidsdk.auth.OAuth2ClientCredentialsJwt.OAuth2InvalidTokenException
import com.salesforce.androidsdk.auth.SfapApiGenerationsResponseBody.Companion.fromJson
import kotlinx.serialization.json.Json.Default.encodeToString
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Provides generated text from the `sfap_api` "generations" endpoint.
 *
 * See https://developer.salesforce.com/docs/einstein/genai/guide/access-models-api-with-rest.html
 *
 * @param apiHostName The Salesforce `sfap_api` hostname
 * @param modelName The model name to request generations from.  For possible
 * values, see https://developer.salesforce.com/docs/einstein/genai/guide/api-names.html
 */
class SfapApiGenerations(
    private val apiHostName: String = API_HOST_NAME_PROD,
    private val modelName: String
) {

    @Throws(
        IOException::class,
        OAuth2InvalidTokenException::class,
        SfapApiException::class
    )
    fun fetch(
        prompt: String,
        clientCredentialsJwt: OAuth2ClientCredentialsJwt
    ): SfapApiGenerationsResponseBody {

        // Request the generated text.
        val response = DEFAULT.okHttpClient.newCall(
            Request.Builder().url(
                "https://$apiHostName/einstein/platform/v1/models/$modelName/generations"
            )
                .header(
                    "Authorization",
                    "Bearer ${clientCredentialsJwt.token}"
                )
                .header("Content-Type", "application/json")
                .header("x-sfdc-app-context", "EinsteinGPT")
                .header("x-client-feature-id", "ai-platform-models-connected-app")
                .post(
                    encodeToString(
                        SfapApiGenerationsRequestBody.serializer(),
                        SfapApiGenerationsRequestBody(prompt = prompt)
                    ).toRequestBody(
                        "application/json; charset=utf-8".toMediaTypeOrNull()
                    )
                ).build()
        ).execute()
        val responseBodyString = response.body?.string()
        return if (response.isSuccessful && responseBodyString != null) {
            fromJson(responseBodyString)
        } else if (response.code == 401 && responseBodyString == "Error: Invalid token") {
            throw OAuth2InvalidTokenException(responseBodyString)
        } else {
            throw SfapApiException(responseBodyString)
        }
    }

    // region Companion

    companion object {

        /** The Salesforce `sfap_api` development host name */
        @Suppress("unused")
        const val API_HOST_NAME_DEV = "dev.api.salesforce.com"

        /** The Salesforce `sfap_api` production host name */
        const val API_HOST_NAME_PROD = "api.salesforce.com"
    }

    // endregion
}
