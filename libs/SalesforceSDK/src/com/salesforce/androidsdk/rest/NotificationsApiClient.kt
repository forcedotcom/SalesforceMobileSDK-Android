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

import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.RestRequest.MEDIA_TYPE_JSON
import com.salesforce.androidsdk.rest.RestRequest.RestMethod.GET
import com.salesforce.androidsdk.rest.RestRequest.RestMethod.POST
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Provides REST client methods for a variety of notifications API endpoints.
 * - Notifications Types: `/connect/notifications/types`
 * - Notifications Actions: `/connect/notifications/${notificationId}/actions/${actionKey}`
 *
 * See https://salesforce.quip.com/KGU3ALoXRCjK#RcfABAPLVfg
 * TODO: Replace the documentation link with the final documentation. ECJ20250310
 *
 * @param restClient The REST client to use
 */
class NotificationsApiClient(
    private val restClient: RestClient
) {

    /**
     * Submit a request to the Notifications API Types endpoint.
     * @return The endpoint's response
     */
    @Suppress("unused")
    @Throws(SfapApiException::class)
    fun fetchNotificationsTypes(): NotificationsTypesResponseBody? {
        val context = SalesforceSDKManager.getInstance().appContext
        val apiVersion = ApiVersionStrings.getVersionNumber(context)

        // Submit the request.
        if (apiVersion < "v64.0") { // TODO: Remove once MSDK default API version is 64 or greater.
            SalesforceSDKLogger.w(TAG, "Cannot request Salesforce push notifications types with API less than v64.0")
            return null
        }

        val restRequest = RestRequest(
            GET,
            "https://${restClient.clientInfo.instanceUrl.host}/${ApiVersionStrings.getBasePath()}/connect/notifications/types",
            mutableMapOf<String, String>()
        )
        val restResponse = restClient.sendSync(restRequest)
        val responseBodyString = restResponse.asString()

        return if (restResponse.isSuccess && responseBodyString != null) {
            NotificationsTypesResponseBody.fromJson(responseBodyString)
        } else {
            val errorResponseBody = responseBodyString?.let { NotificationsApiErrorResponseBody.fromJson(responseBodyString) }
            throw NotificationsApiException(
                errorCode = errorResponseBody?.firstOrNull()?.errorCode,
                message = errorResponseBody?.firstOrNull()?.message ?: "No error response body was provided by the API endpoint.",
                messageCode = errorResponseBody?.firstOrNull()?.messageCode,
                source = responseBodyString
            )
        }
    }

    /**
     * Submit a request to the Notifications API actions endpoint.
     * @return The endpoint's response
     */
    @Throws(NotificationsApiException::class)
    fun submitNotificationAction(
        notificationId: String,
        actionKey: String
    ): NotificationsActionsResponseBody? {
        val context = SalesforceSDKManager.getInstance().appContext
        val apiVersion = ApiVersionStrings.getVersionNumber(context)

        // Submit the request.
        if (apiVersion < "v64.0") { // TODO: Remove once MSDK default API version is 64 or greater.
            SalesforceSDKLogger.w(TAG, "Cannot submit Salesforce Notifications API action with API less than v64.0")
            return null
        }

        val restRequest = RestRequest(
            POST,
            "https://${restClient.clientInfo.instanceUrl.host}/${ApiVersionStrings.getBasePath()}/connect/notifications/${notificationId}/actions/${actionKey}",
            "".toRequestBody(MEDIA_TYPE_JSON),
            mutableMapOf<String, String>()
        )
        val restResponse = restClient.sendSync(restRequest)
        val responseBodyString = restResponse?.asString()

        return if (restResponse?.isSuccess == true && responseBodyString != null) {
            NotificationsActionsResponseBody.fromJson(responseBodyString)
        } else {
            val errorResponseBody = responseBodyString?.let { NotificationsApiErrorResponseBody.fromJson(responseBodyString) }
            throw NotificationsApiException(
                errorCode = errorResponseBody?.firstOrNull()?.errorCode,
                message = errorResponseBody?.firstOrNull()?.message ?: "No error response body was provided by the API endpoint.",
                messageCode = errorResponseBody?.firstOrNull()?.messageCode,
                source = responseBodyString
            )
        }
    }

    companion object {
        private const val TAG = "NotificationsApiClient"
    }
}
