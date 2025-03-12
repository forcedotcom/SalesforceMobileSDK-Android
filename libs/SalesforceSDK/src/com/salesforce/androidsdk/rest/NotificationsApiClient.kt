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

import com.salesforce.androidsdk.rest.RestRequest.RestMethod.GET

/**
 * Provides REST client methods for a variety of notifications API endpoints.
 * - `/connect/notifications/types`
 *
 * See https://salesforce.quip.com/KGU3ALoXRCjK#RcfABAPLVfg
 * TODO: Replace the documentation link with the final documentation. ECJ20250310
 *
 * @param apiHostName The Salesforce Notifications API hostname
 * @param restClient The REST client to use
 */
class NotificationsApiClient(
    private val apiHostName: String,
    private val restClient: RestClient
) {

    /**
     * Submit a request to the Notifications API Types endpoint.
     * @return The endpoint's response
     */
    @Suppress("unused")
    @Throws(SfapApiException::class)
    fun fetchNotificationsTypes(): NotificationsTypesResponseBody {

        // Submit the request.
        val restRequest = RestRequest(
            GET,
            "https://$apiHostName/services/data/v64.0/connect/notifications/types",
            mutableMapOf<String, String>()
        )
        val restResponse = restClient.sendSync(restRequest)
        val responseBodyString = restResponse.asString()

        return if (restResponse.isSuccess && responseBodyString != null) {
            NotificationsTypesResponseBody.fromJson(responseBodyString)
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
}
