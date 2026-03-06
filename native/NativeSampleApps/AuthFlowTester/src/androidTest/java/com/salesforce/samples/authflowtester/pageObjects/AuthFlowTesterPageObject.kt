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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.samples.authflowtester.ALERT_POSITIVE_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.ALERT_TITLE_CONTENT_DESC
import com.salesforce.samples.authflowtester.CREDS_SECTION_CONTENT_DESC
import com.salesforce.samples.authflowtester.MIGRATE_TOKEN_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.MIGRATE_USER_RADIO_CONTENT_DESC
import com.salesforce.samples.authflowtester.R
import com.salesforce.samples.authflowtester.REQUEST_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.REVOKE_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.SCROLL_CONTAINER_CONTENT_DESC
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
import com.salesforce.androidsdk.R as sdkR


data class Tokens(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * Page object for the AuthFlowTester app.
 */
class AuthFlowTesterPageObject(composeTestRule: ComposeTestRule): BasePageObject(composeTestRule) {

    fun waitForAppLoad() {
        waitForNode(CREDS_SECTION_CONTENT_DESC, timeoutMillis = TIMEOUT_MS)
    }

    fun switchToUser(
        knownUserConfig: KnownUserConfig,
        knownLoginHostConfig: KnownLoginHostConfig = KnownLoginHostConfig.REGULAR_AUTH,
    ) {
        openUserPicker()

        // Find the user's display name from authenticated accounts
        val expectedUsername = testConfig.getUser(knownLoginHostConfig, knownUserConfig).username
        val authenticatedUsers = UserAccountManager.getInstance().authenticatedUsers
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val displayName = authenticatedUsers?.find { it.username == expectedUsername }?.displayName
            ?: throw AssertionError("User '$expectedUsername' not found in authenticated accounts")

        // Tap the user row in the picker
        val userRow = device.findObject(UiSelector().textContains(displayName))
        if (!userRow.waitForExists(TIMEOUT_MS)) {
            throw AssertionError("User '$displayName' not found on user picker")
        }
        userRow.click()

        // Wait for app to resume after picker closes, then re-send the user
        // switch broadcast. The original broadcast fires while the activity is
        // stopped (Recomposer paused), so the Compose state update may not
        // trigger recomposition. Re-sending ensures Compose processes it.
        waitForAppLoad()
        UserAccountManager.getInstance().sendUserSwitchIntent(
            UserAccountManager.USER_SWITCH_TYPE_DEFAULT, null
        )
        composeTestRule.waitForIdle()
    }

    fun addNewAccount() {
        openUserPicker()

        // Tap "Add New Account" on the user picker (separate activity — use UiAutomator)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val addNewAccountDesc = context.getString(sdkR.string.sf__add_new_account_content_description)
        val addNewAccountButton = device.findObject(UiSelector().descriptionContains(addNewAccountDesc))
        if (!addNewAccountButton.waitForExists(TIMEOUT_MS)) {
            throw AssertionError("Add New Account button not found on user picker")
        }
        addNewAccountButton.click()
    }

    private fun openUserPicker() {
        val switchUserDesc = getString(R.string.switch_user)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val pickerDesc = context.getString(sdkR.string.sf__account_picker_content_description)
        val picker = device.findObject(UiSelector().descriptionContains(pickerDesc))

        var found = false
        for (i in 1..3) {
            if (picker.exists()) {
                found = true
                break
            }

            try {
                waitForNode(switchUserDesc)
                composeTestRule.onNodeWithContentDescription(switchUserDesc)
                    .performSemanticsAction(SemanticsActions.OnClick)
                composeTestRule.waitForIdle()
            } catch (e: Throwable) {
                // The icon might not be found if the picker is already slowly opening and covering the screen
            }

            if (picker.waitForExists(TIMEOUT_MS)) {
                found = true
                break
            }
        }
        if (!found) {
            throw AssertionError("User picker not found")
        }
    }

    fun isAppLoaded(): Boolean =
        try {
            composeTestRule.onAllNodesWithContentDescription(CREDS_SECTION_CONTENT_DESC)
                .fetchSemanticsNodes().isNotEmpty()
        } catch (_: IllegalStateException) {
            false // Compose hierarchy temporarily unavailable
        }

    fun revokeAccessToken() {
        composeTestRule.onNodeWithContentDescription(SCROLL_CONTAINER_CONTENT_DESC)
            .performScrollToNode(hasContentDescription(REVOKE_BUTTON_CONTENT_DESC))

        waitForNode(REVOKE_BUTTON_CONTENT_DESC, timeoutMillis = TIMEOUT_MS)
        // Use performSemanticsAction instead of performClick because
        // performScrollTo doesn't trigger nested scroll, leaving the button
        // behind the collapsed top bar where touch input gets intercepted.
        composeTestRule.onNodeWithContentDescription(REVOKE_BUTTON_CONTENT_DESC)
            .performSemanticsAction(SemanticsActions.OnClick)

        waitForNode(ALERT_TITLE_CONTENT_DESC, timeoutMillis = TIMEOUT_MS)
        composeTestRule.onNodeWithContentDescription(ALERT_TITLE_CONTENT_DESC)
            .assertTextEquals(getString(R.string.revoke_successful))

        composeTestRule.onNodeWithContentDescription(ALERT_POSITIVE_BUTTON_CONTENT_DESC)
            .performClick()
        composeTestRule.waitForIdle()
    }

    fun validateApiRequest() {
        waitForNode(REQUEST_BUTTON_CONTENT_DESC, timeoutMillis = TIMEOUT_MS)
        composeTestRule.onNodeWithContentDescription(REQUEST_BUTTON_CONTENT_DESC)
            .performSemanticsAction(SemanticsActions.OnClick)

        waitForNode(ALERT_TITLE_CONTENT_DESC, timeoutMillis = TIMEOUT_MS)
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

        waitForNode(CREDS_SECTION_CONTENT_DESC)
        
        // Wait for the UI to update asynchronously after login or user switch.
        // The view may be recreated and collapsed when the current user state updates.
        composeTestRule.waitUntil(TIMEOUT_MS) {
            try {
                val nodes = composeTestRule.onAllNodesWithContentDescription(USERNAME).fetchSemanticsNodes()
                val isVisible = nodes.isNotEmpty()
                var isMatch = false

                if (isVisible) {
                    val config = nodes.first().config
                    if (config.contains(SemanticsProperties.Text)) {
                        isMatch = config[SemanticsProperties.Text].last().text == expected.username
                    }
                } else {
                    composeTestRule.onNodeWithContentDescription(CREDS_SECTION_CONTENT_DESC).performClick()
                    composeTestRule.waitForIdle()
                }

                isMatch
            } catch (_: Exception) {
                false
            }
        }
        assertEquals(expected.username, getText(USERNAME))
    }

    fun validateOAuthValues(knownAppConfig: KnownAppConfig, scopeSelection: ScopeSelection) {
        val expected = testConfig.getApp(knownAppConfig)
        val (accessToken, refreshToken) = getTokens()

        expandUserCredentialsSection(targetNode = CLIENT_ID)
        assertEquals(expected.consumerKey, getSensitiveValue(CLIENT_ID))
        assertEquals(expected.expectedScopesGranted(scopeSelection), getText(SCOPES))
        assertEquals(expected.expectedTokenFormat, getText(TOKEN_FORMAT))
        if (expected.issuesJwt) {
            assert(accessToken.length > refreshToken.length) {
                "JWT access token (${accessToken.length}) should be longer than refresh token (${refreshToken.length})"
            }
        } else {
            assert(accessToken.isNotEmpty()) { "Expected non-empty opaque access token" }
        }
        assert(refreshToken.isNotEmpty()) { "Expected non-empty refresh token" }
    }

    fun migrateToNewApp(
        knownAppConfig: KnownAppConfig,
        scopeSelection: ScopeSelection,
        knownUserConfig: KnownUserConfig? = null,
        knownLoginHostConfig: KnownLoginHostConfig = KnownLoginHostConfig.REGULAR_AUTH,
    ) {
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
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        // Select the target user if specified (user list only visible with multiple users)
        if (knownUserConfig != null) {
            val expectedUsername = testConfig.getUser(knownLoginHostConfig, knownUserConfig).username
            val radioDesc = MIGRATE_USER_RADIO_CONTENT_DESC + expectedUsername
            waitForNode(radioDesc)
            composeTestRule.onNodeWithContentDescription(radioDesc)
                .performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()
        }

        // Wait for bottom sheet, then tap JSON import
        val jsonDesc = getString(R.string.json_content_description)
        waitForNode(jsonDesc)
        composeTestRule.onNodeWithContentDescription(jsonDesc)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        // Tap import button
        waitForNode(ALERT_POSITIVE_BUTTON_CONTENT_DESC)
        composeTestRule.onNodeWithContentDescription(ALERT_POSITIVE_BUTTON_CONTENT_DESC)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        // Tap migrate button
        waitForNode(MIGRATE_TOKEN_BUTTON_CONTENT_DESC)
        composeTestRule.onNodeWithContentDescription(MIGRATE_TOKEN_BUTTON_CONTENT_DESC)
            .performSemanticsAction(SemanticsActions.OnClick)

        AuthorizationPageObject(composeTestRule).tapAllowAfterMigration()

        // Wait for migration to complete and sheet to auto-dismiss.
        // For background user migration, the sheet won't auto-dismiss
        // (compose recomposer was paused), so tap the close button instead.
        val closeDesc = getString(R.string.close_content_description)
        try {
            waitForNodeGone(closeDesc)
        } catch (_: Exception) {
            composeTestRule.onNodeWithContentDescription(closeDesc)
                .performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()
            waitForNodeGone(closeDesc)
        }

        // Wait for the app UI to refresh with new token data
        waitForAppLoad()
    }

    private fun expandUserCredentialsSection(targetNode: String = ACCESS_TOKEN) {
        waitForNode(CREDS_SECTION_CONTENT_DESC)

        // Wait until the target node is visible, expanding the card if needed.
        // The key() block in TesterUI may recreate UserCredentialsView (collapsing
        // the card) between expansion and access, so poll until it stabilizes.
        composeTestRule.waitUntil(TIMEOUT_MS) {
            try {
                val visible = composeTestRule.onAllNodesWithContentDescription(targetNode)
                    .fetchSemanticsNodes().isNotEmpty()
                if (!visible) {
                    composeTestRule.onNodeWithContentDescription(CREDS_SECTION_CONTENT_DESC)
                        .performClick()
                    composeTestRule.waitForIdle()
                }
                visible
            } catch (_: Exception) {
                false
            }
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