/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the copyright notice, conditions and disclaimer are
 * retained. Neither salesforce.com nor contributors' names may be used to endorse
 * derived products without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DAMAGES ARISING
 * FROM USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.phonegap.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.config.BootConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HybridRequestUserAgentTest {
    @After
    fun tearDown() = unmockkAll()

    @Test
    fun test_givenIdentityTaggedRequest_whenHybridIsLocal_thenUserAgentContainsLocalQualifier() =
        assertIdentityUserAgent(local = true, qualifier = "HybridLocal")

    @Test
    fun test_givenIdentityTaggedRequest_whenHybridIsRemote_thenUserAgentContainsRemoteQualifier() =
        assertIdentityUserAgent(local = false, qualifier = "HybridRemote")

    private fun assertIdentityUserAgent(local: Boolean, qualifier: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        mockkStatic(BootConfig::class)
        every { BootConfig.getBootConfig(any()) } returns mockk {
            every { isLocal() } returns local
        }
        val request = Request.Builder().url("https://example.invalid/services/data/")
            .tag(HttpAccess.RequestUserIdentity::class.java, HttpAccess.RequestUserIdentity("org", "user"))
            .build()
        val chain = mockk<Interceptor.Chain> {
            every { this@mockk.request() } returns request
            every { proceed(any()) } answers {
                val decorated = args[0] as Request
                assertTrue(decorated.header("User-Agent")!!.contains(" $qualifier uid_"))
                Response.Builder().request(decorated).protocol(Protocol.HTTP_1_1).code(200)
                    .message("OK").body("".toResponseBody()).build()
            }
        }

        HttpAccess.UserAgentInterceptor().intercept(chain).close()
    }
}
