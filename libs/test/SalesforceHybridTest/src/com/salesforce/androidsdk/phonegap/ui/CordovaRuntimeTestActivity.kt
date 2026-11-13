/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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
package com.salesforce.androidsdk.phonegap.ui

import android.os.Bundle
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.PluginEntry
import org.json.JSONArray
import com.salesforce.androidsdk.rest.RestClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

/** Local, unauthenticated fixture for the real Mobile SDK WebView and bridge. */
class CordovaRuntimeTestActivity : SalesforceDroidGapActivity() {
    val reports = LinkedBlockingQueue<String>()
    val backEvents = LinkedBlockingQueue<String>()
    val stopped = CountDownLatch(1)
    val focusGained = CountDownLatch(1)
    private var loaded = false

    override fun loadConfig() {
        super.loadConfig()
        preferences.set("Fullscreen", intent.getBooleanExtra("fullscreen", false))
        preferences.set("AndroidEdgeToEdge", intent.getBooleanExtra("edgeToEdge", false))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cordovaWebView.pluginManager.addService(PluginEntry("RuntimeProbe", object : CordovaPlugin() {
            override fun execute(action: String, args: JSONArray, callback: CallbackContext): Boolean {
                if (action != "report") return false
                val value = args.getString(0)
                if (value == "back") backEvents.offer(value) else reports.offer(value)
                callback.success()
                return true
            }
        }))
    }

    // Keep authentication out of this controlled local-content fixture.
    override fun onResume(restClient: RestClient?) {
        if (!loaded) {
            loaded = true
            loadUrl("file:///android_asset/www/cordova-runtime.html")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) focusGained.countDown()
    }

    override fun onStop() {
        super.onStop()
        stopped.countDown()
    }
}
