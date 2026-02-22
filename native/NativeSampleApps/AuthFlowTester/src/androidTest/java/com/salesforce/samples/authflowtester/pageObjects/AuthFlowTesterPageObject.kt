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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.salesforce.samples.authflowtester.ALERT_POSITIVE_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.ALERT_TITLE_CONTENT_DESC
import com.salesforce.samples.authflowtester.CREDS_SECTION_CONTENT_DESC
import com.salesforce.samples.authflowtester.MIGRATE_TOKEN_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.R
import com.salesforce.samples.authflowtester.REQUEST_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.REVOKE_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.components.ACCESS_TOKEN
import com.salesforce.samples.authflowtester.components.CLIENT_ID
import com.salesforce.samples.authflowtester.components.REFRESH_TOKEN
import com.salesforce.samples.authflowtester.components.SCOPES
import com.salesforce.samples.authflowtester.components.TOKEN_FORMAT
import com.salesforce.samples.authflowtester.components.USERNAME
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig
import com.salesforce.samples.authflowtester.testUtility.KnownUserConfig
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection
import com.salesforce.samples.authflowtester.testUtility.testConfig
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals

data class Tokens(
    val accessToken: String,
    val refreshToken: String,
)

const val TIMEOUT_MS: Long = 2_000

class AuthFlowTesterPageObject(composeTestRule: ComposeTestRule): BasePageObject(composeTestRule) {

    fun waitForAppLoad() {
        waitForNode(CREDS_SECTION_CONTENT_DESC, timeoutMillis = TIMEOUT_MS * 3)
    }

    fun revokeAccessToken() {
        waitForNode(REVOKE_BUTTON_CONTENT_DESC)
        // Use performSemanticsAction instead of performClick because
        // performScrollTo doesn't trigger nested scroll, leaving the button
        // behind the collapsed top bar where touch input gets intercepted.
        composeTestRule.onNodeWithContentDescription(REVOKE_BUTTON_CONTENT_DESC)
            .performSemanticsAction(SemanticsActions.OnClick)

        waitForNode(ALERT_TITLE_CONTENT_DESC)
        composeTestRule.onNodeWithContentDescription(ALERT_TITLE_CONTENT_DESC)
            .assertTextEquals(getString(R.string.revoke_successful))

        composeTestRule.onNodeWithContentDescription(ALERT_POSITIVE_BUTTON_CONTENT_DESC)
            .performClick()
        composeTestRule.waitForIdle()
    }

    fun validateApiRequest() {
        waitForNode(REQUEST_BUTTON_CONTENT_DESC)
        composeTestRule.onNodeWithContentDescription(REQUEST_BUTTON_CONTENT_DESC)
            .performSemanticsAction(SemanticsActions.OnClick)

        waitForNode(ALERT_TITLE_CONTENT_DESC)
        composeTestRule.onNodeWithContentDescription(ALERT_TITLE_CONTENT_DESC)
            .assertTextEquals(getString(R.string.request_successful))

        composeTestRule.onNodeWithContentDescription(ALERT_POSITIVE_BUTTON_CONTENT_DESC)
            .performClick()
        composeTestRule.waitForIdle()
    }

    fun getTokens(): Tokens {
        expandUserCredentialsSection()

        return Tokens(
            getSensitiveValue(ACCESS_TOKEN),
            getSensitiveValue(REFRESH_TOKEN),
        )
    }

    fun validateUser(knownLoginHostConfig: KnownLoginHostConfig, knownUserConfig: KnownUserConfig) {
        val expected = testConfig.getUser(knownLoginHostConfig, knownUserConfig)

        expandUserCredentialsSection()
        assertEquals(expected.username, getText(USERNAME))
    }

    fun validateOAuthValues(knownAppConfig: KnownAppConfig, scopeSelection: ScopeSelection) {
        val expected = testConfig.getApp(knownAppConfig)

        expandUserCredentialsSection()
        assertEquals(expected.consumerKey, getSensitiveValue(CLIENT_ID))
        assertEquals(expected.expectedScopesGranted(scopeSelection), getText(SCOPES))
        assertEquals(expected.expectedTokenFormat, getText(TOKEN_FORMAT))
    }

    fun migrateToNewApp(knownAppConfig: KnownAppConfig, scopeSelection: ScopeSelection) {
        val (_, consumerKey, redirectUri, scopes) =
            testConfig.getAppWithRequestScopes(knownAppConfig, scopeSelection)
        val jsonApp = buildJsonObject {
            put("remoteConsumerKey", consumerKey)
            put("oauthRedirectURI", redirectUri)
            put("oauthScopes", scopes)
        }.toString()

        // Copy JSON to clipboard
        (context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
            ClipData.newPlainText("label", jsonApp)
        )

        // Tap creds section to dismiss OS clipboard popup
        composeTestRule.onNodeWithContentDescription(CREDS_SECTION_CONTENT_DESC)
            .performClick()
        composeTestRule.waitForIdle()

        // Tap "Migrate Access Token" bottom bar icon
        val migrateDesc = getString(R.string.migrate_access_token)
        waitForNode(migrateDesc)
        composeTestRule.onNodeWithContentDescription(migrateDesc)
            .performClick()
        composeTestRule.waitForIdle()

        // Wait for bottom sheet, then tap JSON import
        val jsonDesc = getString(R.string.json_content_description)
        waitForNode(jsonDesc)
        composeTestRule.onNodeWithContentDescription(jsonDesc)
            .performClick()
        composeTestRule.waitForIdle()

        // Tap import button
        waitForNode(ALERT_POSITIVE_BUTTON_CONTENT_DESC)
        composeTestRule.onNodeWithContentDescription(ALERT_POSITIVE_BUTTON_CONTENT_DESC)
            .performClick()
        composeTestRule.waitForIdle()

        // Tap migrate button
        waitForNode(MIGRATE_TOKEN_BUTTON_CONTENT_DESC)
        composeTestRule.onNodeWithContentDescription(MIGRATE_TOKEN_BUTTON_CONTENT_DESC)
            .performClick()

        // Wait for migration to complete (bottom sheet dismisses)
        waitForNodeGone(MIGRATE_TOKEN_BUTTON_CONTENT_DESC)
    }

    private fun expandUserCredentialsSection() {
        waitForNode(CREDS_SECTION_CONTENT_DESC)

        val alreadyExpanded = composeTestRule.onAllNodesWithContentDescription(ACCESS_TOKEN)
            .fetchSemanticsNodes().isNotEmpty()
        if (!alreadyExpanded) {
            composeTestRule.onNodeWithContentDescription(CREDS_SECTION_CONTENT_DESC)
                .performClick()
            composeTestRule.waitForIdle()
            waitForNode(ACCESS_TOKEN)
        }
    }

    /** Wait for a node with the given content description to exist. */
    private fun waitForNode(contentDesc: String, timeoutMillis: Long = TIMEOUT_MS) {
        composeTestRule.waitUntil(timeoutMillis) {
            try {
                composeTestRule.onAllNodesWithContentDescription(contentDesc)
                    .fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) {
                false // Compose hierarchy temporarily unavailable
            }
        }
    }

    /** Wait for a node with the given content description to disappear. */
    private fun waitForNodeGone(contentDesc: String, timeoutMillis: Long = TIMEOUT_MS) {
        composeTestRule.waitUntil(timeoutMillis) {
            try {
                composeTestRule.onAllNodesWithContentDescription(contentDesc)
                    .fetchSemanticsNodes().isEmpty()
            } catch (_: IllegalStateException) {
                false
            }
        }
    }

    private fun getSensitiveValue(contentDescription: String): String {
        val node = composeTestRule.onNodeWithContentDescription(contentDescription)
        node.performScrollTo()
        node.performSemanticsAction(SemanticsActions.OnClick) // Reveal full value
        composeTestRule.waitForIdle()

        val text = node.fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .last().text // Value is last; first is the label (e.g. "Access Token:")

        assert(!text.contains("...")) {
            "Got truncated value for '$contentDescription': $text"
        }

        node.performSemanticsAction(SemanticsActions.OnClick) // Hide value
        composeTestRule.waitForIdle()
        return text
    }

    private fun getText(contentDescription: String): String {
        val node = composeTestRule.onNodeWithContentDescription(contentDescription)
        node.performScrollTo()
        return node.fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .last().text // Value is last; first is the label
    }
}