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

import com.salesforce.androidsdk.rest.SfapApiClient.Companion.jsonIgnoreUnknownKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models a `sfap_api` `embeddings` endpoint response.
 */
@Serializable
data class SfapApiEmbeddingsResponseBody(
    val embeddings: Array<Embedding>? = null,
    val parameters: Parameters? = null
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SfapApiEmbeddingsResponseBody

        if (embeddings != null) {
            if (other.embeddings == null) return false
            if (!embeddings.contentEquals(other.embeddings)) return false
        } else if (other.embeddings != null) return false
        if (parameters != other.parameters) return false
        if (sourceJson != other.sourceJson) return false

        return true
    }

    override fun hashCode(): Int {
        var result = embeddings?.contentHashCode() ?: 0
        result = 31 * result + (parameters?.hashCode() ?: 0)
        result = 31 * result + (sourceJson?.hashCode() ?: 0)
        return result
    }

    @Serializable
    data class Embedding(
        val embedding: Array<Double>? = null,
        val index: Int? = null
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Embedding

            if (embedding != null) {
                if (other.embedding == null) return false
                if (!embedding.contentEquals(other.embedding)) return false
            } else if (other.embedding != null) return false
            if (index != other.index) return false

            return true
        }

        override fun hashCode(): Int {
            var result = embedding?.contentHashCode() ?: 0
            result = 31 * result + (index ?: 0)
            return result
        }
    }

    @Serializable
    data class Parameters(
        val model: String? = null,
        val `object`: String? = null,
        val usage: Usage? = null
    ) {

        @Serializable
        data class Usage(
            @SerialName("prompt_tokens") val promptTokens: Int? = null,
            @SerialName("total_tokens") val totalTokens: Int? = null
        )
    }

    /** The original JSON used to initialize this response body */
    var sourceJson: String? = null
        private set

    // region Companion

    companion object {

        /**
         * Returns an `sfap_api` `embeddings` endpoint response from the JSON
         * text.
         * @param json The JSON text
         * @return The `sfap_api` `embeddings` endpoint response
         */
        fun fromJson(json: String): SfapApiEmbeddingsResponseBody {

            val result = jsonIgnoreUnknownKeys.decodeFromString<SfapApiEmbeddingsResponseBody>(json)
            result.sourceJson = json
            return result
        }
    }

    // endregion
}
