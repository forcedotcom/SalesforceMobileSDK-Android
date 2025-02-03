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
 * Models a `sfap_api` `feedback` endpoint request.
 * See https://developer.salesforce.com/docs/einstein/genai/references/models-api?meta=submitFeedback
 *
 * The endpoint accepts a `appFeedback` object as well.  To provide
 * `appFeedback`, subclass and introduce a new parameter of any object type
 * named `appFeedback`.  Also, override the `toJson` method to use the subclass
 * serializer instead of the the serializer provided by this class.
 */
@Serializable
@Suppress("unused")
open class SfapApiFeedbackRequestBody(
    val id: String? = null,
    val appGeneration: String? = null,
    val appGenerationId: String? = null,
    val feedback: String? = null,
    val feedbackText: String? = null,
    val generationId: String? = null,
    val source: String? = null
) {

    /**
     * Encodes this request body to JSON text.
     * @return This request body as JSON text
     */
    open fun toJson() = Json.encodeToString(serializer(), this)
}
