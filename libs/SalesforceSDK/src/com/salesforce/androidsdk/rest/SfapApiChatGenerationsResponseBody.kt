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
 * Models a `sfap_api` "chat-generations" endpoint response.
 */
@Serializable
data class SfapApiChatGenerationsResponseBody(
    val id: String? = null,
    val generationDetails: GenerationDetails? = null
) {

    /** The original JSON used to initialize this response body */
    var sourceJson: String? = null
        private set

    @Serializable
    data class GenerationDetails(
        val generations: Array<Generation>? = null,
        val parameters: Parameters? = null
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as GenerationDetails

            if (!generations.contentEquals(other.generations)) return false
            if (parameters != other.parameters) return false

            return true
        }

        override fun hashCode(): Int {
            var result = generations.contentHashCode()
            result = 31 * result + parameters.hashCode()
            return result
        }

        @Serializable
        data class Generation(
            val id: String? = null,
            val content: String? = null,
            val contentQuality: ContentQuality? = null,
            val parameters: Parameters? = null,
            val role: String? = null,
            val timestamp: Long? = null
        ) {

            @Serializable
            data class Parameters(
                @SerialName("finish_reason") val finishReason: String? = null,
                val refusal: String? = null,
                val index: Int? = null,
                val logprobs: String? = null
            )

            @Serializable
            data class ContentQuality(
                val scanToxicity: ScanToxicity? = null
            ) {

                @Serializable
                data class ScanToxicity(
                    val isDetected: Boolean? = null,
                    val categories: Array<Category>? = null
                ) {

                    @Serializable
                    data class Category(
                        val categoryName: String? = null,
                        val score: Double? = null
                    )

                    override fun equals(other: Any?): Boolean {
                        if (this === other) return true
                        if (javaClass != other?.javaClass) return false

                        other as ScanToxicity

                        if (isDetected != other.isDetected) return false
                        if (categories != null) {
                            if (other.categories == null) return false
                            if (!categories.contentEquals(other.categories)) return false
                        } else if (other.categories != null) return false

                        return true
                    }

                    override fun hashCode(): Int {
                        var result = isDetected.hashCode()
                        result = 31 * result + (categories?.contentHashCode() ?: 0)
                        return result
                    }
                }
            }
        }
    }

    @Serializable
    data class Parameters(
        val created: Int? = null,
        val model: String? = null,
        val `object`: String? = null,
        @SerialName("system_fingerprint")
        val systemFingerprint: String? = null,
        val usage: Usage? = null
    ) {

        @Serializable
        data class Usage(
            @SerialName("completion_tokens") val completionTokens: Int? = null,
            @SerialName("completion_tokens_details") val completionTokensDetails: CompletionTokensDetails? = null,
            @SerialName("prompt_tokens") val promptTokens: Int? = null,
            @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null,
            @SerialName("total_tokens") val totalTokens: Int? = null
        ) {

            @Serializable
            data class CompletionTokensDetails(
                @SerialName("reasoning_tokens") val reasoningTokens: Int = 0,
                @SerialName("audio_tokens") val audioTokens: Int = 0,
                @SerialName("accepted_prediction_tokens") val acceptedPredictionTokens: Int = 0,
                @SerialName("rejected_prediction_tokens") val rejectedPredictionTokens: Int = 0
            )

            @Serializable
            data class PromptTokensDetails(
                @SerialName("cached_tokens") val cachedTokens: Int = 0,
                @SerialName("audio_tokens") val audioTokens: Int = 0
            )
        }
    }

    // region Companion

    companion object {

        /**
         * Returns an `sfap_api` "chat-generations" endpoint response from the
         * JSON text.
         * @param json The JSON text
         * @return The `sfap_api` "chat-generations" endpoint response
         */
        fun fromJson(json: String): SfapApiChatGenerationsResponseBody {

            val result = jsonIgnoreUnknownKeys.decodeFromString<SfapApiChatGenerationsResponseBody>(json)
            result.sourceJson = json
            return result
        }
    }

    // endregion
}
