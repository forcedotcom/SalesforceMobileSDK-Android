/*
 * Copyright (c) 2024-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.rest

import com.salesforce.androidsdk.rest.RestRequest.RestMethod.POST
import com.salesforce.androidsdk.rest.SfapApiGenerationsResponseBody.Companion.fromJson
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Provides REST client methods for a variety of `sfap_api` endpoints.
 * - `chat-generations`
 * - `embeddings`
 * - `feedback`
 * - `generations`
 *
 * See https://developer.salesforce.com/docs/einstein/genai/guide/access-models-api-with-rest.html
 *
 * @param apiHostName The Salesforce `sfap_api` hostname
 * @param modelName The model name to request from.  For possible
 * values, see https://developer.salesforce.com/docs/einstein/genai/guide/api-names.html.
 * Note that the `embeddings` endpoint requires an embeddings-enabled model such
 * as `sfdc_ai__DefaultOpenAITextEmbeddingAda_002`.  Also note that submitting
 * to the `feedback` endpoint does not require value for this parameter
 * @param restClient The REST client to use
 */
class SfapApiClient(
    private val apiHostName: String,
    private val modelName: String? = null,
    private val restClient: RestClient
) {

    /**
     * Submit a request to the `sfap_api` `embeddings` endpoint.
     * @param requestBody The `embeddings` request body
     * @return The endpoint's response
     */
    @Throws(SfapApiException::class)
    fun fetchGeneratedEmbeddings(
        requestBody: SfapApiEmbeddingsRequestBody
    ): SfapApiEmbeddingsResponseBody {

        // Submit the request.
        val restRequest = RestRequest(
            POST,
            "https://$apiHostName/einstein/platform/v1/models/$modelName/embeddings",
            requestBody
                .toJson()
                .toRequestBody(CONTENT_TYPE_HEADER_VALUE_APPLICATION_JSON.toMediaTypeOrNull()),
            generateSfapApiHeaders()
        )
        val restResponse = restClient.sendSync(restRequest)
        val responseBodyString = restResponse.asString()
        return if (restResponse.isSuccess && responseBodyString != null) {
            SfapApiEmbeddingsResponseBody.fromJson(responseBodyString)
        } else {
            val errorResponseBody = SfapApiErrorResponseBody.fromJson(responseBodyString)
            throw SfapApiException(
                errorCode = errorResponseBody.errorCode,
                message = responseBodyString,
                messageCode = errorResponseBody.messageCode,
                source = responseBodyString
            )
        }
    }

    /**
     * Fetches generated chat responses from the `sfap_api` `chat-generations`
     * endpoint.
     * @param requestBody The `chat-generations` request body
     * @return The endpoint's response
     */
    @Throws(SfapApiException::class)
    fun fetchGeneratedChat(
        requestBody: SfapApiChatGenerationsRequestBody
    ): SfapApiChatGenerationsResponseBody {

        // Request the generated chat responses.
        val restRequest = RestRequest(
            POST,
            "https://$apiHostName/einstein/platform/v1/models/$modelName/chat-generations",
            requestBody.toJson()
                .toRequestBody(CONTENT_TYPE_HEADER_VALUE_APPLICATION_JSON.toMediaTypeOrNull()),
            generateSfapApiHeaders()
        )
        val restResponse = restClient.sendSync(restRequest)

        val responseBodyString = restResponse.asString()
        return if (restResponse.isSuccess && responseBodyString != null) {
            SfapApiChatGenerationsResponseBody.fromJson(responseBodyString)
        } else {
            val errorResponseBody = SfapApiErrorResponseBody.fromJson(responseBodyString)
            throw SfapApiException(
                errorCode = errorResponseBody.errorCode,
                message = responseBodyString,
                messageCode = errorResponseBody.messageCode,
                source = responseBodyString
            )
        }
    }

    /**
     * Fetches generated text from the `sfap_api` "generations" endpoint.
     * @param prompt The prompt request parameter value
     * @return The endpoint's response
     */
    @Throws(SfapApiException::class)
    fun fetchGeneratedText(
        prompt: String
    ): SfapApiGenerationsResponseBody {

        // Request the generated text.
        val restRequest = RestRequest(
            POST,
            "https://$apiHostName/einstein/platform/v1/models/$modelName/generations",
            SfapApiGenerationsRequestBody(prompt)
                .toJson()
                .toRequestBody(CONTENT_TYPE_HEADER_VALUE_APPLICATION_JSON.toMediaTypeOrNull()),
            generateSfapApiHeaders()
        )
        val restResponse = restClient.sendSync(restRequest)

        val responseBodyString = restResponse.asString()
        return if (restResponse.isSuccess && responseBodyString != null) {
            fromJson(responseBodyString)
        } else {
            val errorResponseBody = SfapApiErrorResponseBody.fromJson(responseBodyString)
            throw SfapApiException(
                errorCode = errorResponseBody.errorCode,
                message = responseBodyString,
                messageCode = errorResponseBody.messageCode,
                source = responseBodyString
            )
        }
    }

    /**
     * Submits feedback for previously generated text from the `sfap_api`
     * endpoints to the `sfap_api` `feedback` endpoint.
     * @param requestBody The `feedback` request body
     * @return The endpoint's response
     */
    @Throws(SfapApiException::class)
    fun submitGeneratedTextFeedback(
        requestBody: SfapApiFeedbackRequestBody
    ): SfapApiFeedbackResponseBody {

        // Submit the feedback.
        val restRequest = RestRequest(
            POST,
            "https://$apiHostName/einstein/platform/v1/feedback",
            requestBody
                .toJson()
                .toRequestBody(CONTENT_TYPE_HEADER_VALUE_APPLICATION_JSON.toMediaTypeOrNull()),
            generateSfapApiHeaders()
        )
        val restResponse = restClient.sendSync(restRequest)

        val responseBodyString = restResponse.asString()
        return if (restResponse.isSuccess && responseBodyString != null) {
            SfapApiFeedbackResponseBody.fromJson(responseBodyString)
        } else {
            val errorResponseBody = SfapApiErrorResponseBody.fromJson(responseBodyString)
            throw SfapApiException(
                errorCode = errorResponseBody.errorCode,
                message = responseBodyString,
                messageCode = errorResponseBody.messageCode,
                source = responseBodyString
            )
        }
    }

    companion object {

        internal val jsonIgnoreUnknownKeys = Json { ignoreUnknownKeys = true }

        private const val CONTENT_TYPE_HEADER_VALUE_APPLICATION_JSON = "application/json; charset=utf-8"

        private fun generateSfapApiHeaders() = mutableMapOf(
            "x-sfdc-app-context" to "EinsteinGPT",
            "x-client-feature-id" to "ai-platform-models-connected-app"
        )
    }
}
