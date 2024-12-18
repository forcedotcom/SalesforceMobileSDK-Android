package com.salesforce.androidsdk.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Models a `sfap_api` "generations" endpoint response.
 */
@Serializable
data class SfapApiGenerationsResponseBody(
    val id: String? = null, val generation: Generation? = null, val moreGenerations: String? = null, val prompt: String? = null, val parameters: Parameters? = null
) {

    @Serializable
    data class Generation(
        val id: String? = null, val generatedText: String? = null, val contentQuality: ContentQuality? = null, val parameters: Parameters? = null
    ) {

        @Serializable
        data class ContentQuality(
            val scanToxicity: ScanToxicity? = null
        ) {

            @Serializable
            data class ScanToxicity(
                val isDetected: Boolean = false, val categories: Array<Category>? = null
            ) {

                @Serializable
                data class Category(
                    val categoryName: String? = null, val score: Float = 0f
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

        @Serializable
        data class Parameters(
            @SerialName("finish_reason") val finishReason: String? = null, val refusal: String? = null, val index: Int = 0, val logprobs: String? = null
        )
    }

    @Serializable
    data class Parameters(
        val created: Int = 0, val usage: Usage? = null, val model: String? = null
    ) {

        @SerialName("system_fingerprint")
        val systemFingerprint: String? = null
        val `object`: String? = null

        @Serializable
        data class Usage(
            @SerialName("completion_tokens") val completionTokens: Int = 0,

            @SerialName("prompt_tokens") val promptTokens: Int = 0,

            @SerialName("completion_tokens_details") val completionTokensDetails: CompletionTokensDetails? = null,

            @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null,

            @SerialName("total_tokens") val totalTokens: Int = 0
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

                @SerialName("audio_tokens") val audiTokens: Int = 0
            )
        }
    }


    // region Companion

    companion object {

        /**
         * Returns an `sfap_api` "generations" endpoint response from the JSON
         * text.
         * @param json The JSON text
         * @return The `sfap_api` "generations" endpoint response
         */
        fun fromJson(json: String) = Json.decodeFromString<SfapApiGenerationsResponseBody>(json)
    }

    // endregion
}
