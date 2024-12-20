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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Models a `sfap_api` "chat-generations" endpoint request.
 * See https://developer.salesforce.com/docs/einstein/genai/references/models-api?meta=generateChat
 *
 * @param messages The request messages parameter value
 * @param localization The request localization parameter value
 * @param tags The request tags parameter value
 */
@Serializable
data class SfapApiChatGenerationsRequestBody(
    val messages: Array<Message>,
    val localization: Localization,
    val tags: Tags
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SfapApiChatGenerationsRequestBody

        if (!messages.contentEquals(other.messages)) return false
        if (localization != other.localization) return false
        if (tags != other.tags) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messages.contentHashCode()
        result = 31 * result + localization.hashCode()
        result = 31 * result + tags.hashCode()
        return result
    }


    /**
     * Encodes this request body to JSON text.
     * @return This request body as JSON text
     */
    fun toJson() = Json.encodeToString(serializer(), this)

    @Serializable
    data class Message(
        val role: String,
        val content: String
    )

    @Serializable
    data class Localization(
        val defaultLocale: String,
        val inputLocales: Array<Locale>,
        val expectedLocales: Array<String>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Localization

            if (defaultLocale != other.defaultLocale) return false
            if (!inputLocales.contentEquals(other.inputLocales)) return false
            if (!expectedLocales.contentEquals(other.expectedLocales)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = defaultLocale.hashCode()
            result = 31 * result + inputLocales.contentHashCode()
            result = 31 * result + expectedLocales.contentHashCode()
            return result
        }
    }

    @Serializable
    data class Locale(
        val locale: String,
        val probability: Double
    )

    @Serializable
    class Tags
}
