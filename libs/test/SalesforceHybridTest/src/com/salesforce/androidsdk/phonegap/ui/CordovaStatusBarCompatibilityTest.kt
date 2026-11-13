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

import android.Manifest
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CordovaStatusBarCompatibilityTest {
    @get:Rule
    val notificationPermission: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else GrantPermissionRule.grant()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun test_givenPlatformStatusBar_whenFractionalAlpha_thenNativeColorIsRounded() = withActivity { activity ->
        change(activity, "window.statusbar.setBackgroundColor('rgba(255, 0, 153, 0.5)')")
        instrumentation.runOnMainSync {
            val bar = requireNotNull(activity.window.decorView.findViewWithTag<View>("statusBarView"))
            assertEquals(Color.argb(128, 255, 0, 153), (bar.background as ColorDrawable).color)
        }
    }

    @Test
    fun test_givenPlatformStatusBar_whenOpaqueColor_thenNativeColorMatches() = withActivity { activity ->
        change(activity, "window.statusbar.setBackgroundColor('rgb(255, 0, 153)')")
        instrumentation.runOnMainSync {
            val bar = requireNotNull(activity.window.decorView.findViewWithTag<View>("statusBarView"))
            assertEquals(Color.rgb(255, 0, 153), (bar.background as ColorDrawable).color)
        }
    }

    @Test
    fun test_givenPlatformStatusBar_whenVisibilityChanges_thenNativeViewUpdates() = withActivity { activity ->
        listOf(false, true).forEach { visible ->
            change(activity, "window.statusbar.visible = $visible")
            instrumentation.runOnMainSync {
                val bar = requireNotNull(activity.window.decorView.findViewWithTag<View>("statusBarView"))
                assertEquals(if (visible) View.VISIBLE else View.GONE, bar.visibility)
            }
        }
    }

    private fun change(activity: CordovaRuntimeTestActivity, script: String) {
        instrumentation.runOnMainSync {
            (activity.cordovaWebView.view as WebView).evaluateJavascript("""
                if (window.StatusBar) throw new Error('This fixture requires the platform route');
                $script;
                cordova.exec(null, null, 'RuntimeProbe', 'report', ['changed']);
            """.trimIndent(), null)
        }
        assertEquals("changed", activity.reports.poll(10, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun withActivity(block: (CordovaRuntimeTestActivity) -> Unit) {
        ActivityScenario.launch(CordovaRuntimeTestActivity::class.java).use { scenario ->
            lateinit var activity: CordovaRuntimeTestActivity
            scenario.onActivity { activity = it }
            assertEquals("ready", activity.reports.poll(10, TimeUnit.SECONDS))
            block(activity)
        }
    }
}
