/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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
 * specific prior written permission of the copyright holder.
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
package com.salesforce.samples.authflowtester

import android.content.Context
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicInteger

/** Records the final token-request User-Agent for UI-test assertions. */
class TokenRequestCapturingHttpAccess(context: Context) : HttpAccess(context, null) {

    override fun createNewClientBuilder(): OkHttpClient.Builder =
        super.createNewClientBuilder().addNetworkInterceptor { chain ->
            val request = chain.request()
            if (SalesforceSDKManager.getInstance().isUiTesting &&
                request.url.encodedPath.endsWith(TOKEN_ENDPOINT_PATH)
            ) {
                lastTokenRequestUserAgentState.value = request.header(USER_AGENT_HEADER)
                tokenRequestCountState.value = tokenRequestCount.incrementAndGet()
            }
            chain.proceed(request)
        }

    companion object {
        private const val TOKEN_ENDPOINT_PATH = "/services/oauth2/token"
        private const val USER_AGENT_HEADER = "User-Agent"

        private val lastTokenRequestUserAgentState = MutableStateFlow<String?>(null)
        val lastTokenRequestUserAgent = lastTokenRequestUserAgentState.asStateFlow()

        private val tokenRequestCount = AtomicInteger(0)
        private val tokenRequestCountState = MutableStateFlow(0)
        val capturedTokenRequestCount = tokenRequestCountState.asStateFlow()
    }
}
