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
package com.salesforce.samples.authflowtester

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import android.Manifest
import android.os.Build
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.samples.authflowtester.components.UserCredentialsView
import com.salesforce.samples.authflowtester.pageObjects.AuthFlowTesterPageObject
import okhttp3.Request
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RequestUserAgentPresentationTests {
    @get:Rule(order = 0)
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else GrantPermissionRule.grant()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val user = UserAccountBuilder.getInstance().orgId("test-org").userId("test-user").build()

    @After
    fun cleanup() {
        TokenRequestCapturingHttpAccess.reset()
        SalesforceSDKManager.getInstance().isUiTesting = false
    }

    private fun capture(owned: Boolean = false): String {
        SalesforceSDKManager.getInstance().isUiTesting = true
        TokenRequestCapturingHttpAccess.reset()
        val request = Request.Builder()
            .url("https://example.invalid/services/oauth2/token?excluded=secret")
            .header("User-Agent", "test-agent ftr_OT")
            .tag(com.salesforce.androidsdk.accounts.UserAccount::class.java, if (owned) user else null)
            .build()
        val client = TokenRequestCapturingHttpAccess(InstrumentationRegistry.getInstrumentation().targetContext)
            .okHttpClient
        val call = client.newCall(request)
        client.eventListenerFactory.create(call).requestHeadersEnd(call, request)
        compose.waitForIdle()
        return request.header("User-Agent").orEmpty()
    }

    private fun exportedOther(): JSONObject {
        val method = Class.forName("com.salesforce.samples.authflowtester.components.UserCredentialsViewKt")
            .getDeclaredMethod("generateCredentialsJSON", com.salesforce.androidsdk.accounts.UserAccount::class.java)
        method.isAccessible = true
        return JSONObject(method.invoke(null, user) as String).getJSONObject("Other")
    }

    @Test
    fun test_givenCaptureLabels_whenRendered_thenAppStringResourcesAreUsed() {
        capture()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val labels = listOf(
            "sf__request_user_agent_high_water_sequence",
            "sf__last_token_request_user_agent",
            "sf__captured_token_request_count",
            "sf__request_user_agents",
        ).map { name ->
            val id = context.resources.getIdentifier(name, "string", context.packageName)
            assertTrue("Missing app string resource: $name", id != 0)
            context.getString(id)
        }
        compose.setContent { MaterialTheme { UserCredentialsView(user) } }
        compose.onNodeWithText("User Credentials").performClick()
        labels.take(3).forEach { compose.onNodeWithText("$it:").assertExists() }
        compose.onNodeWithContentDescription(REQUEST_USER_AGENTS_CONTENT_DESC)
            .assertTextEquals(labels.last())
        assertTrue(exportedOther().has("Request User Agents"))
        compose.onNodeWithContentDescription("request_capture_high_water", useUnmergedTree = true)
            .assertTextEquals("1")
        compose.onNodeWithContentDescription(TOKEN_ENDPOINT_USER_AGENT_CONTENT_DESC, useUnmergedTree = true)
            .assertTextEquals("test-agent ftr_OT")
        compose.onNodeWithContentDescription(TOKEN_ENDPOINT_REQUEST_COUNT_CONTENT_DESC, useUnmergedTree = true)
            .assertTextEquals("1")
    }

    @Test
    fun test_givenCapturedRequest_whenExported_thenOnlyFourTypedFieldsAreIncluded() {
        val agent = capture()
        val other = exportedOther()
        assertTrue("UI-test export must contain request observations", other.has("Request User Agents"))
        val entry = other.getJSONArray("Request User Agents").getJSONObject(0)
        assertEquals(setOf("path", "userKey", "userAgent", "seq"), entry.keys().asSequence().toSet())
        assertEquals("/services/oauth2/token", entry.getString("path"))
        assertEquals("unknown", entry.getString("userKey"))
        assertEquals(agent, entry.getString("userAgent"))
        assertTrue(entry.getLong("seq") > 0)
        SalesforceSDKManager.getInstance().isUiTesting = false
        assertFalse(exportedOther().has("Request User Agents"))
    }

    @Test
    fun test_givenCapturedRequest_whenRendered_thenStableRowsAreTestOnly() {
        val agent = capture()
        compose.setContent { MaterialTheme { UserCredentialsView(user) } }
        compose.onNodeWithText("User Credentials").performClick()
        compose.onNodeWithContentDescription("request_user_agents").assertTextEquals(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.sf__request_user_agents)
        )
        val seq = TokenRequestCapturingHttpAccess.capturedRequests.value.single().seq
        compose.onNodeWithContentDescription("request_user_agent_$seq")
            .assertTextEquals("/services/oauth2/token · unknown · $agent")
    }

    @Test
    fun test_givenCaptureDisabled_whenRendered_thenSectionIsAbsent() {
        capture()
        SalesforceSDKManager.getInstance().isUiTesting = false
        compose.setContent { MaterialTheme { UserCredentialsView(user) } }
        compose.onNodeWithText("User Credentials").performClick()
        compose.onNodeWithContentDescription("request_user_agents").assertDoesNotExist()
    }

    @Test
    fun test_givenEmptyCapture_whenExported_thenArrayIsAbsent() {
        SalesforceSDKManager.getInstance().isUiTesting = true
        TokenRequestCapturingHttpAccess.reset()
        assertFalse(exportedOther().has("Request User Agents"))
    }

    @Test
    fun test_givenCollapsedOtherSection_whenPageObjectReads_thenTypedEntriesAreRecovered() {
        val agent = capture()
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState()).semantics {
                    contentDescription = SCROLL_CONTAINER_CONTENT_DESC
                }) { UserCredentialsView(user) }
            }
        }
        compose.onNodeWithText("User Credentials").performClick()
        compose.onNodeWithText("Other").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(USER_AGENT_CONTENT_DESC).assertDoesNotExist()
        val page = AuthFlowTesterPageObject(compose)
        val entries = page.getCapturedRequestUserAgents()
        assertEquals(1, entries.size)
        assertEquals(agent, entries.single().userAgent)
        assertEquals("/services/oauth2/token", entries.single().path)
        assertEquals("unknown", entries.single().userKey)
        assertEquals(entries.single().seq, page.getRequestUserAgentHighWaterSequence())
    }

    @Test
    fun test_givenNewestOwnerRemoved_whenPageObjectReads_thenHighWaterDoesNotDecrease() {
        capture(owned = true)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState()).semantics {
                    contentDescription = SCROLL_CONTAINER_CONTENT_DESC
                }) { UserCredentialsView(user) }
            }
        }
        val page = AuthFlowTesterPageObject(compose)
        val before = page.getRequestUserAgentHighWaterSequence()
        assertTrue(before > 0)
        TokenRequestCapturingHttpAccess.retireUser(user)
        compose.waitForIdle()
        assertEquals(before, page.getRequestUserAgentHighWaterSequence())
    }
}
