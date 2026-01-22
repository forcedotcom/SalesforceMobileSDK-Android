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
package com.salesforce.samples.authflowtester

import android.app.Application
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.OAuthConfig
import com.salesforce.androidsdk.util.ResourceReaderHelper
import com.salesforce.androidsdk.util.urlHostOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AuthFlowTesterApplication : Application() {

    companion object {
        private const val FEATURE_APP_USES_KOTLIN = "KT"
    }

    override fun onCreate() {
        super.onCreate()
        SalesforceSDKManager.initNative(
            applicationContext,
            AuthFlowTesterActivity::class.java,
        )

        with(SalesforceSDKManager.getInstance()) {
            registerUsedAppFeature(FEATURE_APP_USES_KOTLIN)

            appConfigForLoginHost = { server: String ->
                var oauthConfig: OAuthConfig? = null
                val jsonConfig = ResourceReaderHelper.readAssetFile(
                    /* ctx = */ this@AuthFlowTesterApplication,
                    /* assetFilePath = */ "ui_test_config.json",
                )

                if (jsonConfig != null) {
                    try {
                        val jsonObject = Json.parseToJsonElement(jsonConfig).jsonObject
                        val loginHost = jsonObject["loginHost"]?.jsonPrimitive?.content

                        // Check if server matches the loginHost from config
                        if (loginHost != null && server.urlHostOrNull() == loginHost.urlHostOrNull()) {
                            val apps = jsonObject["apps"]?.jsonArray

                            // Find the ca_basic_opaque app
                            val basicApp = apps?.firstOrNull { app ->
                                app.jsonObject["name"]?.jsonPrimitive?.content == "ca_basic_opaque"
                            }?.jsonObject

                            basicApp?.let { app ->
                                val consumerKey = app["consumerKey"]?.jsonPrimitive?.content
                                val redirectUri = app["redirectUri"]?.jsonPrimitive?.content

                                if (consumerKey != null && redirectUri != null) {
                                    oauthConfig = OAuthConfig(
                                        consumerKey = consumerKey,
                                        redirectUri = redirectUri,
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }

                oauthConfig
            }
        }
    }
}