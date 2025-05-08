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
package com.salesforce.androidsdk.rest

import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.collections.Map.Entry

/**
 * Provides WebSocket client methods for a variety of Agentforce Speech
 * Foundations sfap_api endpoints.
 *
 * Einstein Transcribe Streaming API:
 * - `wss://api.salesforce.com/einstein/platform/v1/models/transcribeV1/streaming-transcriptions`
 * - https://git.soma.salesforce.com/gist/rshahaf/3ee54892dfccd17fed5da2c304b5b743
 *
 * TODO: Replace the documentation link with the final documentation. ECJ20250507
 *
 * @param apiHostName The Salesforce `sfap_api` hostname
 * @param modelName The model name to request from.
 * @param restClient The REST client to use
 */
class AgentforceSpeechFoundationsApiClient(
    private val apiHostName: String,
    private val modelName: String? = null,
    private val restClient: RestClient,
) {

    /**
     * Opens and returns a websocket connection to the ASF sfap_api streaming
     * transcriptions endpoint which can then be used to send and receive
     * speech-to-text messages.
     * @param webSocketListener The websocket listener
     */
    fun openStreamingTranscriptionsWebSocket(
        webSocketListener: WebSocketListener,
    ): WebSocket {

        val request = Request.Builder()
            .url(
                "wss://$apiHostName/einstein/platform/v1/models/$modelName/streaming-transcriptions${
                    mapOf(
                        "engine" to "aws",
                        "media-encoding" to "pcm"
                    ).toQueryParameters()
                }"
            )
            .addAuthorizedAsfHeaders(restClient)
            .build()

        return restClient.newWebSocket(
            request,
            webSocketListener
        )
    }

    /**
     * Folds this map of strings into a URL query string.
     * @return A URL query string representation of this map
     */
    private fun Map<String, String>.toQueryParameters() = entries.fold("?") { accumulator: String, entry: Entry<String, String> ->
        "${accumulator}${entry.key}=${entry.value}&"
    }

    /**
     * Adds ASF sfap_api required headers, included authorization, to this
     * request builder.
     * @param restClient The REST client to use for authorization
     */
    private fun Request.Builder.addAuthorizedAsfHeaders(
        restClient: RestClient,
    ) = this.apply {
        header("Authorization", "Bearer ${restClient.authToken}")
        header("x-client-feature-id", "external-edc")
        header("x-sfdc-app-context", "EinsteinGPT")
    }
}
