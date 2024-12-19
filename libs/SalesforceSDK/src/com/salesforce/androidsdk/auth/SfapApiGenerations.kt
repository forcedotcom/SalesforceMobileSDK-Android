package com.salesforce.androidsdk.auth

import com.salesforce.androidsdk.auth.SfapApiGenerationsResponseBody.Companion.fromJson
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestRequest.RestMethod.POST
import kotlinx.serialization.json.Json.Default.encodeToString
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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
 * @param restClient The REST client to use
 */
class SfapApiGenerations(
    private val apiHostName: String,
    private val modelName: String,
    private val restClient: RestClient
) {

    /**
     * Fetches generated text from the `sfap_api` "generations" endpoint.
     * @param prompt The prompt request parameter value
     * @return The endpoint's response
     */
    @Throws(
        IOException::class,
        SfapApiException::class
    )
    fun fetch(
        prompt: String
    ): SfapApiGenerationsResponseBody {

        // Request the generated text.
        val restRequest = RestRequest(
            POST,
            "https://$apiHostName/einstein/platform/v1/models/$modelName/generations",
            encodeToString(
                SfapApiGenerationsRequestBody.serializer(),
                SfapApiGenerationsRequestBody(prompt)
            ).toRequestBody(
                "application/json; charset=utf-8".toMediaTypeOrNull()
            ),
            mutableMapOf(
                "x-sfdc-app-context" to "EinsteinGPT",
                "x-client-feature-id" to "ai-platform-models-connected-app"
            )
        )
        val restResponse = restClient.sendSync(restRequest)

        val responseBodyString = restResponse.asString()
        return if (restResponse.isSuccess && responseBodyString != null) {
            fromJson(responseBodyString)
        } else {
            throw SfapApiException(responseBodyString)
        }
    }
}
