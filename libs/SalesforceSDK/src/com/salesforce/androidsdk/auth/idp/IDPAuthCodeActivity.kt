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

package com.salesforce.androidsdk.auth.idp

/**
 * Activity showing web view through which IDP gets auth code for SP app
 */
import android.os.Bundle
import android.view.View
import android.view.WindowManager.LayoutParams
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import com.salesforce.androidsdk.auth.idp.interfaces.IDPAuthCodeActivity as IDPAuthCodeActivityInterface

class IDPAuthCodeActivity : ComponentActivity(), IDPAuthCodeActivityInterface {

    companion object {
        private val TAG: String = IDPAuthCodeActivity::class.java.simpleName
    }

    private lateinit var _webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        // Protects against screenshots.
        window.setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE)

        // Make navigation and status visible always
        SalesforceSDKManager.getInstance().setViewNavigationVisibility(this)

        // Set content
        setContent {
            MaterialTheme(colorScheme = SalesforceSDKManager.getInstance().colorScheme()) {
                IDPAuthCodeScreen(intent = intent)
            }
        }
    }

    @Composable
    fun IDPAuthCodeScreen(intent: android.content.Intent) {
        val context = LocalContext.current
        val idpManager = SalesforceSDKManager.getInstance().idpManager as? IDPManager
        var isLoading by remember { mutableStateOf(true) }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier
                        .size(50.dp)
                        .fillMaxSize(),
                )
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        _webView = this

                        settings.apply {
                            javaScriptEnabled = true
                            useWideViewPort = true
                            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                        }

                        // Initially invisible
                        visibility = View.INVISIBLE

                        // Don't draw scrollbars
                        isHorizontalScrollBarEnabled = false
                        isVerticalScrollBarEnabled = false

                        // Set WebViewClient
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // Hide the spinner when the page finishes loading
                                isLoading = false
                                // Show web view
                                view?.visibility = View.VISIBLE
                            }
                        }

                        idpManager?.let { manager ->
                            val spAppPackageName = manager.getSrcAppPackageName(intent)
                            manager.attachToActiveFlow(context as IDPAuthCodeActivity, context, spAppPackageName)
                            manager.onReceive(context, intent)
                        } ?: run {
                            SalesforceSDKLogger.d(
                                TAG,
                                "No IDP manager to handle ${LogUtil.intentToString(intent)}"
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(16.dp)
            )
        }
    }

    override val webView: WebView
        get() = _webView
}
