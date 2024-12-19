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
package com.salesforce.androidsdk.auth

import com.salesforce.androidsdk.auth.SfapApiGenerationsResponseBody.Companion.fromJson
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestRequest.RestMethod.POST
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
            SfapApiGenerationsRequestBody(prompt)
                .toJson()
                .toRequestBody(
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
