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
package com.salesforce.samples.authflowtester.pageObjects

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

abstract class BasePageObject(val composeTestRule: ComposeTestRule) {

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    fun getString(id: Int) = context.getString(id)

    companion object {
        val isFtl: Boolean by lazy {
            Settings.System.getString(
                InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
                /* name = */ "firebase.test.lab"
            ) == "true"
        }
        val TIMEOUT_MS: Long by lazy {
            if (isFtl) 20_000 else 15_000
        }

        val SLEEP_TIME_MS: Long by lazy {
            if (isFtl) 5_000 else 2_500
        }

        /**
         * Extended timeout for Espresso WebView actions ([retryWebAction]) that wait for
         * server-rendered login page content. The Salesforce sandbox login page can take
         * 20–30 s to render interactive form elements after [onPageFinished] fires; this budget
         * covers that latency with headroom for both local emulators and Firebase Test Lab.
         */
        val WEBVIEW_ACTION_TIMEOUT_MS: Long by lazy {
            if (isFtl) 60_000 else 45_000
        }
    }
}