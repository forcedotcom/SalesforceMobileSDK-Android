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
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.salesforce.androidsdk.app.SalesforceSDKManager
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CordovaRuntimeTest {
    @get:Rule
    val notificationPermission: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        /* No runtime notification permission is needed below API 33. */
        GrantPermissionRule.grant()
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun test_givenLocalPage_whenDeviceReady_thenNativeSdkInfoBridgeResponds() = withActivity { _, activity ->
        assertEquals(SalesforceSDKManager.SDK_VERSION, javascript(activity, sdkVersion).getString("value"))
    }

    @Test
    fun test_givenBackListener_whenFirstBackThenRemoved_thenJavaScriptAndFallbackWork() = verifyBack()

    @Test
    fun test_givenBackListener_whenDispatcherBackThenRemoved_thenJavaScriptAndFallbackWork() =
        verifyBack(dispatcher = true)

    private fun verifyBack(dispatcher: Boolean = false) = withActivity { _, activity ->
        javascript(activity, """
            new Promise((resolve, reject) => {
                window.backCount = 0;
                window.backHandler = () => {
                    window.backCount++;
                    cordova.exec(null, null, 'RuntimeProbe', 'report', ['back']);
                };
                document.addEventListener('backbutton', window.backHandler, false);
                cordova.exec(resolve, reject, 'CoreAndroid', 'overrideBackbutton', [true]);
            })
        """.trimIndent())
        instrumentation.waitForIdleSync()
        if (dispatcher) instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        else instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertEquals("back", activity.backEvents.poll(10, TimeUnit.SECONDS))
        val count = javascript(activity, """
            new Promise(resolve => requestAnimationFrame(() =>
                requestAnimationFrame(() => resolve(window.backCount))))
        """.trimIndent()).getInt("value")
        assertEquals(1, count)
        assertFalse(activity.isFinishing)

        javascript(activity, """
            new Promise((resolve, reject) => {
                document.removeEventListener('backbutton', window.backHandler);
                cordova.exec(resolve, reject, 'CoreAndroid', 'overrideBackbutton', [false]);
            })
        """.trimIndent())
        instrumentation.waitForIdleSync()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertTrue("Back fallback must leave the root page", activity.stopped.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun test_givenActivity_whenRecreatedAndResumed_thenBridgeStillResponds() = withActivity { scenario, _ ->
        scenario.recreate()
        lateinit var recreated: CordovaRuntimeTestActivity
        scenario.onActivity { recreated = it }
        assertEquals("ready", report(recreated))
        assertEquals(SalesforceSDKManager.SDK_VERSION, javascript(recreated, sdkVersion).getString("value"))
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals(SalesforceSDKManager.SDK_VERSION, javascript(recreated, sdkVersion).getString("value"))
    }

    @Test
    fun test_givenInput_whenImeShownAndDismissed_thenViewportAndBridgeRecover() = verifyKeyboard()

    @Test
    fun test_givenEdgeToEdgeInput_whenImeShownAndDismissed_thenViewportAndBridgeRecover() =
        verifyKeyboard(edgeToEdge = true)

    @Test
    fun test_givenFullscreenInput_whenImeShownAndDismissed_thenFullscreenMarginsRemain() =
        verifyKeyboard(fullscreen = true)

    private fun verifyKeyboard(fullscreen: Boolean = false, edgeToEdge: Boolean = false) {
        withSecureSetting("show_ime_with_hard_keyboard", "1",
            setOf("null", "0", "1")) {
            withActivity(fullscreen, edgeToEdge) { _, activity ->
                lateinit var webView: WebView
                instrumentation.runOnMainSync {
                    webView = activity.cordovaWebView.view as WebView
                    webView.requestFocus()
                }
                assertEquals("entry", javascript(activity, """
                    new Promise(resolve => {
                        const input = document.getElementById('entry');
                        input.focus();
                        input.scrollIntoView();
                        requestAnimationFrame(() => resolve(document.activeElement.id));
                    })
                """.trimIndent()).getString("value"))
                val point = javascript(activity, """
                    (() => {
                        const rect = document.getElementById('entry').getBoundingClientRect();
                        return {x: (rect.left + rect.width / 2) * devicePixelRatio,
                            y: (rect.top + rect.height / 2) * devicePixelRatio};
                    })()
                """.trimIndent()).getJSONObject("value")
                awaitIme(activity, true) {
                    val location = IntArray(2)
                    instrumentation.runOnMainSync { webView.getLocationOnScreen(location) }
                    val x = location[0] + point.getDouble("x").toFloat()
                    val y = location[1] + point.getDouble("y").toFloat()
                    val now = SystemClock.uptimeMillis()
                    listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                        val event = MotionEvent.obtain(now, now, action, x, y, 0).apply {
                            source = InputDevice.SOURCE_TOUCHSCREEN
                        }
                        try {
                            injectTouch(event)
                        } finally {
                            event.recycle()
                        }
                    }
                }
                if (fullscreen) {
                    instrumentation.runOnMainSync {
                        assertEquals(0, (webView.layoutParams as FrameLayout.LayoutParams).bottomMargin)
                    }
                } else {
                    assertTrue(javascript(activity, """
                        new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() => {
                            document.getElementById('entry').scrollIntoView();
                            resolve(document.getElementById('entry').getBoundingClientRect().bottom <= innerHeight + 1);
                        })))
                    """.trimIndent()).getBoolean("value"))
                }
                awaitIme(activity, false) {
                    assumeTrue("Device must allow input shell commands",
                        fixtureShell("input keyevent KEYCODE_BACK").isBlank())
                }
                assertFalse(activity.isFinishing)
                assertEquals(SalesforceSDKManager.SDK_VERSION, javascript(activity, sdkVersion).getString("value"))
            }
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private fun fixtureShell(command: String): String = try {
        shell(command)
    } catch (exception: SecurityException) {
        throw AssumptionViolatedException("Device must allow fixture shell commands", exception)
    } catch (exception: IOException) {
        throw AssumptionViolatedException("Fixture shell output must be readable", exception)
    }

    private fun withSecureSetting(name: String, value: String,
        supportedValues: Set<String>, block: () -> Unit) {
        val previous = fixtureShell("settings get secure $name").trim()
        assumeTrue("Unsupported $name value for this fixture", previous in supportedValues)
        try {
            if (previous != value) {
                fixtureShell("settings put secure $name $value")
                assumeTrue("Device must allow changing $name",
                    fixtureShell("settings get secure $name").trim() == value)
            }
            block()
        } finally {
            if (shell("settings get secure $name").trim() != previous) {
                if (previous == "null") shell("settings delete secure $name")
                else shell("settings put secure $name $previous")
                assertEquals("Fixture must restore $name", previous,
                    shell("settings get secure $name").trim())
            }
        }
    }

    private fun injectTouch(event: MotionEvent) {
        val injected = try {
            instrumentation.uiAutomation.injectInputEvent(event, true)
        } catch (exception: SecurityException) {
            throw AssumptionViolatedException("Device must allow touch injection", exception)
        }
        assumeTrue("Device must allow touch injection", injected)
    }

    private fun awaitIme(activity: CordovaRuntimeTestActivity, visible: Boolean, action: () -> Unit) {
        val latch = CountDownLatch(1)
        val root = activity.window.decorView
        val listener = ViewTreeObserver.OnPreDrawListener {
            if (ViewCompat.getRootWindowInsets(root)?.isVisible(WindowInsetsCompat.Type.ime()) == visible) {
                latch.countDown()
            }
            true
        }
        try {
            instrumentation.runOnMainSync {
                root.viewTreeObserver.addOnPreDrawListener(listener)
            }
            action()
            assertTrue("IME visibility must become $visible", latch.await(10, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { root.viewTreeObserver.removeOnPreDrawListener(listener) }
        }
    }

    private fun withActivity(fullscreen: Boolean = false, edgeToEdge: Boolean = false,
        block: (ActivityScenario<CordovaRuntimeTestActivity>, CordovaRuntimeTestActivity) -> Unit) {
        val intent = Intent(instrumentation.targetContext, CordovaRuntimeTestActivity::class.java)
            .putExtra("fullscreen", fullscreen).putExtra("edgeToEdge", edgeToEdge)
        withSecureSetting("immersive_mode_confirmations", "confirmed",
            setOf("null", "confirmed")) {
            ActivityScenario.launch<CordovaRuntimeTestActivity>(intent).use { scenario ->
                lateinit var activity: CordovaRuntimeTestActivity
                scenario.onActivity { activity = it }
                assertEquals("ready", report(activity))
                assertTrue("Fixture must receive window focus", activity.focusGained.await(10, TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
                block(scenario, activity)
            }
        }
    }

    private fun javascript(activity: CordovaRuntimeTestActivity, expression: String): JSONObject {
        instrumentation.runOnMainSync {
            (activity.cordovaWebView.view as WebView).evaluateJavascript("""
                Promise.resolve($expression).then(value => {
                    cordova.exec(null, null, 'RuntimeProbe', 'report', [JSON.stringify({value})]);
                }, error => {
                    cordova.exec(null, null, 'RuntimeProbe', 'report', [JSON.stringify({error: String(error)})]);
                });
            """.trimIndent(), null)
        }
        val result = JSONObject(report(activity))
        assertFalse("JavaScript operation failed: $result", result.has("error"))
        return result
    }

    private fun report(activity: CordovaRuntimeTestActivity): String =
        requireNotNull(activity.reports.poll(10, TimeUnit.SECONDS)) { "Cordova bridge report timed out" }

    private val sdkVersion = """
        new Promise((resolve, reject) => cordova.exec(info => resolve(info.sdkVersion), reject,
            'com.salesforce.sdkinfo', 'getInfo', []))
    """.trimIndent()
}
