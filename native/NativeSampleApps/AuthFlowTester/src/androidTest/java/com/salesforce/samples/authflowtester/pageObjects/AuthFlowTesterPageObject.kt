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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.Features
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_NATIVE
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_USER_AGENT_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID
import com.salesforce.androidsdk.app.Features.FEATURE_BEACON
import com.salesforce.androidsdk.app.Features.FEATURE_TOKEN_FORMAT_JWT
import com.salesforce.androidsdk.app.Features.FEATURE_TOKEN_FORMAT_OPAQUE
import com.salesforce.androidsdk.app.Features.FEATURE_TOKEN_MIGRATION
import com.salesforce.samples.authflowtester.ALERT_POSITIVE_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.ALERT_TITLE_CONTENT_DESC
import com.salesforce.samples.authflowtester.CREDS_SECTION_CONTENT_DESC
import com.salesforce.samples.authflowtester.DOWNGRADE_FROM_DPOP_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.MIGRATE_TOKEN_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.MIGRATE_USER_RADIO_CONTENT_DESC
import com.salesforce.samples.authflowtester.UPGRADE_TO_DPOP_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.R
import com.salesforce.samples.authflowtester.REQUEST_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.REVOKE_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.SCROLL_CONTAINER_CONTENT_DESC
import com.salesforce.samples.authflowtester.TOKEN_ENDPOINT_USER_AGENT_CONTENT_DESC
import com.salesforce.samples.authflowtester.TOKEN_ENDPOINT_REQUEST_COUNT_CONTENT_DESC
import com.salesforce.samples.authflowtester.USER_AGENT_CONTENT_DESC
import com.salesforce.samples.authflowtester.ConcurrentRequestType
import com.salesforce.samples.authflowtester.components.ACCESS_TOKEN
import com.salesforce.samples.authflowtester.components.CLIENT_ID
import com.salesforce.samples.authflowtester.components.CONTENT_DOMAIN
import com.salesforce.samples.authflowtester.components.CONTENT_SID
import com.salesforce.samples.authflowtester.components.ConcurrentRequestTestHooks
import com.salesforce.samples.authflowtester.components.DPOP_KEY_THUMBPRINT
import com.salesforce.samples.authflowtester.components.DPOP_NONCE
import com.salesforce.samples.authflowtester.components.LIGHTNING_DOMAIN
import com.salesforce.samples.authflowtester.components.LIGHTNING_SID
import com.salesforce.samples.authflowtester.components.MAIN_SID
import com.salesforce.samples.authflowtester.components.PARENT_SID
import com.salesforce.samples.authflowtester.components.REFRESH_TOKEN
import com.salesforce.samples.authflowtester.components.SCOPES
import com.salesforce.samples.authflowtester.components.OAUTH_TOKEN_TYPE
import com.salesforce.samples.authflowtester.components.TOKEN_FORMAT
import com.salesforce.samples.authflowtester.components.UI_SID
import com.salesforce.samples.authflowtester.components.USERNAME
import com.salesforce.samples.authflowtester.components.VF_DOMAIN
import com.salesforce.samples.authflowtester.components.VF_SID
import com.salesforce.samples.authflowtester.components.MANY_REQUEST_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.components.MANY_REQUEST_COMPLETED_COUNT_CONTENT_DESC
import com.salesforce.samples.authflowtester.components.MANY_REQUEST_ERROR_COPY_CONTENT_DESC
import com.salesforce.samples.authflowtester.components.MANY_REQUEST_ERROR_DETAILS_CONTENT_DESC
import com.salesforce.samples.authflowtester.components.MANY_REQUEST_FAILURE_COUNT_CONTENT_DESC
import com.salesforce.samples.authflowtester.components.MANY_REQUEST_INTERRUPTION_STATUS_CONTENT_DESC
import com.salesforce.samples.authflowtester.components.MANY_REQUEST_IN_FLIGHT_COUNT_CONTENT_DESC
import com.salesforce.samples.authflowtester.components.MANY_REQUEST_OPTIONS_CONTENT_DESC
import com.salesforce.samples.authflowtester.components.MANY_REQUEST_OPTIONS_EXPANDED_STATE
import com.salesforce.samples.authflowtester.components.MANY_REQUEST_OPTIONS_TEST_TAG
import com.salesforce.samples.authflowtester.components.MANY_REQUEST_QUEUED_COUNT_CONTENT_DESC
import com.salesforce.samples.authflowtester.components.MANY_REQUEST_SUCCESS_COUNT_CONTENT_DESC
import com.salesforce.samples.authflowtester.components.ManyRequestInterruption
import com.salesforce.samples.authflowtester.components.manyRequestCountContentDescription
import com.salesforce.samples.authflowtester.components.manyRequestInterruptionContentDescription
import com.salesforce.samples.authflowtester.components.manyRequestSquareTestTag
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig
import com.salesforce.samples.authflowtester.testUtility.KnownUserConfig
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection
import com.salesforce.samples.authflowtester.testUtility.testConfig
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import com.salesforce.androidsdk.R as sdkR

private const val APP_LOAD_TIMEOUT_MS = 30_000L
private const val READ_RETRY_INTERVAL_MS = 500L
private const val SENSITIVE_TOGGLE_SETTLE_TIMEOUT_MS = 2_000L
private const val EMPTY_VALUE_PLACEHOLDER = "(empty)"
private const val MANY_REQUEST_TIMEOUT_MS = 120_000L
private const val MANY_REQUEST_REVEAL_SCROLL_ATTEMPTS = 12
private const val MANY_REQUEST_REVEAL_SCROLL_STEP_PX = 180f

data class Tokens(
    val accessToken: String,
    val refreshToken: String,
)

data class DpopInfo(
    val tokenType: String,
    val nonce: String,
    val keyThumbprint: String,
)

/**
 * Page object for the AuthFlowTester app.
 */
class AuthFlowTesterPageObject(composeTestRule: ComposeTestRule): BasePageObject(composeTestRule) {

    fun waitForAppLoad() {
        waitForNode(CREDS_SECTION_CONTENT_DESC, timeoutMillis = APP_LOAD_TIMEOUT_MS)
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

    fun waitForAppUnloaded(timeoutMillis: Long = APP_LOAD_TIMEOUT_MS) {
        try {
            composeTestRule.waitUntil(timeoutMillis) { !isAppLoaded() }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError(
                "Timed out after ${timeoutMillis}ms waiting for the session detail screen to close",
                e,
            )
        }
    }

    fun revokeAccessToken() {
        composeTestRule.onNodeWithContentDescription(SCROLL_CONTAINER_CONTENT_DESC)
            .performScrollToNode(hasContentDescription(REVOKE_BUTTON_CONTENT_DESC))

        clickRevokeButton()
        composeTestRule.onNodeWithContentDescription(ALERT_TITLE_CONTENT_DESC)
            .assertTextEquals(getString(R.string.revoke_successful))

        composeTestRule.onNodeWithContentDescription(ALERT_POSITIVE_BUTTON_CONTENT_DESC)
            .performClick()
        composeTestRule.waitForIdle()
    }

    fun validateApiRequest() {
        clickRequestButton()
        composeTestRule.onNodeWithContentDescription(ALERT_TITLE_CONTENT_DESC)
            .assertTextEquals(getString(R.string.request_successful))

        composeTestRule.onNodeWithContentDescription(ALERT_POSITIVE_BUTTON_CONTENT_DESC)
            .performClick()
        composeTestRule.waitForIdle()
    }

    fun validateDefaultManyRequestOptions() {
        scrollToManyRequestNode(MANY_REQUEST_OPTIONS_CONTENT_DESC)
        composeTestRule.onNodeWithContentDescription(MANY_REQUEST_OPTIONS_CONTENT_DESC)
            .assertTextEquals("Options: 20 requests · Mixed · Manual")
    }

    fun configureFailedManyRequest(index: Int) {
        ConcurrentRequestTestHooks.failedRequestIndex = index
    }

    fun selectManyRequestCount(count: Int) {
        expandManyRequestOptions()
        val contentDescription = manyRequestCountContentDescription(count)
        waitForNode(contentDescription)
        composeTestRule.onNodeWithContentDescription(contentDescription).performClick()
        composeTestRule.waitForIdle()
    }

    fun selectManyRequestInterruption(interruption: ManyRequestInterruption) {
        expandManyRequestOptions()
        revealManyRequestNode(
            manyRequestInterruptionContentDescription(ManyRequestInterruption.MANUAL)
        )
        val contentDescription = manyRequestInterruptionContentDescription(interruption)
        revealManyRequestNode(contentDescription)
        composeTestRule.onNodeWithContentDescription(contentDescription).performClick()
        composeTestRule.waitForIdle()
    }

    private fun expandManyRequestOptions() {
        val firstCountContentDescription = manyRequestCountContentDescription(5)
        val alreadyExpanded = composeTestRule
            .onAllNodesWithContentDescription(firstCountContentDescription)
            .fetchSemanticsNodes()
            .isNotEmpty()
        if (alreadyExpanded) return

        scrollToManyRequestNode(MANY_REQUEST_OPTIONS_CONTENT_DESC)
        composeTestRule.onNodeWithTag(MANY_REQUEST_OPTIONS_TEST_TAG, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForManyRequestOptionsExpanded()
        revealManyRequestNode(firstCountContentDescription)
    }

    private fun waitForManyRequestOptionsExpanded() {
        try {
            composeTestRule.waitUntil(TIMEOUT_MS) {
                composeTestRule
                    .onAllNodesWithTag(MANY_REQUEST_OPTIONS_TEST_TAG, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .firstOrNull()
                    ?.config
                    ?.let { config ->
                        if (config.contains(SemanticsProperties.StateDescription)) {
                            config[SemanticsProperties.StateDescription]
                        } else {
                            null
                        }
                    } ==
                    MANY_REQUEST_OPTIONS_EXPANDED_STATE
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Concurrent request options did not expand", e)
        }
        composeTestRule.waitForIdle()
    }

    fun startManyRequests() {
        scrollToManyRequestNode(MANY_REQUEST_BUTTON_CONTENT_DESC)
        composeTestRule.onNodeWithContentDescription(MANY_REQUEST_BUTTON_CONTENT_DESC)
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    fun waitForManyRequestsToComplete(
        configuredCount: Int,
        timeoutMillis: Long = MANY_REQUEST_TIMEOUT_MS,
    ): ManyRequestCounts {
        val expected = "$configuredCount / $configuredCount completed"
        try {
            composeTestRule.waitUntil(timeoutMillis) {
                readTextOrNull(MANY_REQUEST_COMPLETED_COUNT_CONTENT_DESC) == expected
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError(
                "Timed out after ${timeoutMillis}ms waiting for concurrent REST batch: " +
                    "${readTextOrNull(MANY_REQUEST_COMPLETED_COUNT_CONTENT_DESC)}",
                e,
            )
        }
        return manyRequestCounts(configuredCount)
    }

    fun waitForManyRequestInterruption(
        expectedStatus: String,
        timeoutMillis: Long = MANY_REQUEST_TIMEOUT_MS,
    ) {
        try {
            composeTestRule.waitUntil(timeoutMillis) {
                ConcurrentRequestTestHooks.lastInterruptionStatus == expectedStatus ||
                    readTextOrNull(MANY_REQUEST_INTERRUPTION_STATUS_CONTENT_DESC) == expectedStatus
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError(
                "Timed out after ${timeoutMillis}ms waiting for interruption status " +
                    "'$expectedStatus'; last status was " +
                    "'${readTextOrNull(MANY_REQUEST_INTERRUPTION_STATUS_CONTENT_DESC)}'",
                e,
            )
        }
    }

    fun waitForManyRequestsSubmitted(
        minimumCount: Int = 5,
        timeoutMillis: Long = TIMEOUT_MS,
    ) {
        try {
            composeTestRule.waitUntil(timeoutMillis) {
                ConcurrentRequestTestHooks.submittedCount >= minimumCount
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError(
                "Timed out waiting for $minimumCount submitted requests; observed " +
                    ConcurrentRequestTestHooks.submittedCount,
                e,
            )
        }
    }

    fun validateMixedSuccessfulRequests(count: Int) {
        val expectedRequestTypes = listOf(
            ConcurrentRequestType.RESOURCES,
            ConcurrentRequestType.RESOURCES,
            ConcurrentRequestType.DESCRIBE_GLOBAL,
        )
        repeat(count) { index ->
            waitForManyRequestSquareState(index, "Succeeded")
            val expectedType = expectedRequestTypes[index % expectedRequestTypes.size]
            val descriptions = composeTestRule.onNodeWithTag(manyRequestSquareTestTag(index))
                .fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
            assertEquals(listOf("Request ${index + 1}, ${expectedType.displayName}"), descriptions)
        }
    }

    fun failedManyRequestIndices(count: Int): List<Int> =
        (0 until count).filter { index ->
            composeTestRule.onNodeWithTag(manyRequestSquareTestTag(index))
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "Failed"
        }.map { it + 1 }

    fun tapFailedManyRequest(index: Int, type: ConcurrentRequestType): String {
        val testTag = manyRequestSquareTestTag(index)
        waitForManyRequestSquareState(index, "Failed")
        scrollToManyRequestTag(testTag)
        val node = composeTestRule.onNodeWithTag(testTag)
        val descriptions = node.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertEquals(listOf("Request ${index + 1}, ${type.displayName}"), descriptions)
        node.performClick()
        waitForNode(MANY_REQUEST_ERROR_DETAILS_CONTENT_DESC)
        return requireNotNull(readTextOrNull(MANY_REQUEST_ERROR_DETAILS_CONTENT_DESC))
    }

    fun validateManyRequestErrorCopyAvailable() {
        waitForNode(MANY_REQUEST_ERROR_COPY_CONTENT_DESC)
    }

    private fun manyRequestCounts(configuredCount: Int): ManyRequestCounts {
        val successes = firstInteger(readText(MANY_REQUEST_SUCCESS_COUNT_CONTENT_DESC))
        val failures = firstInteger(readText(MANY_REQUEST_FAILURE_COUNT_CONTENT_DESC))
        val completed = firstInteger(readText(MANY_REQUEST_COMPLETED_COUNT_CONTENT_DESC))
        val inFlight = firstInteger(readText(MANY_REQUEST_IN_FLIGHT_COUNT_CONTENT_DESC))
        val queued = firstInteger(readText(MANY_REQUEST_QUEUED_COUNT_CONTENT_DESC))
        assertEquals("Every request should have one terminal result", configuredCount, completed)
        assertEquals("Successes plus failures should equal completed", completed, successes + failures)
        assertEquals("No request should remain queued after completion", 0, queued)
        assertEquals("No request should remain in flight after completion", 0, inFlight)
        return ManyRequestCounts(completed, successes, failures, inFlight, queued)
    }

    private fun firstInteger(text: String): Int =
        Regex("\\d+").find(text)?.value?.toInt()
            ?: throw AssertionError("Expected an integer in '$text'")

    private fun readText(contentDescription: String): String =
        requireNotNull(readTextOrNull(contentDescription)) {
            "No text found for node '$contentDescription'"
        }

    private fun readTextOrNull(contentDescription: String): String? = try {
        val nodes = composeTestRule
            .onAllNodesWithContentDescription(contentDescription)
            .fetchSemanticsNodes()
        nodes.firstOrNull()?.config?.let { config ->
            if (config.contains(SemanticsProperties.Text)) {
                config[SemanticsProperties.Text].lastOrNull()?.text
            } else {
                null
            }
        }
    } catch (_: Throwable) {
        null
    }

    private fun scrollToManyRequestNode(contentDescription: String) {
        composeTestRule.onNodeWithContentDescription(SCROLL_CONTAINER_CONTENT_DESC)
            .performScrollToNode(hasContentDescription(contentDescription))
        waitForNode(contentDescription)
    }

    /**
     * Brings a control added below the visible part of the non-lazy scroll container into its
     * semantics tree. `performScrollToNode` cannot target a node until Compose exposes it, so
     * advance in small bounded steps before doing the precise scroll.
     */
    private fun revealManyRequestNode(contentDescription: String) {
        val scrollContainer = composeTestRule
            .onNodeWithContentDescription(SCROLL_CONTAINER_CONTENT_DESC)
        repeat(MANY_REQUEST_REVEAL_SCROLL_ATTEMPTS) {
            val isExposed = composeTestRule
                .onAllNodesWithContentDescription(contentDescription)
                .fetchSemanticsNodes()
                .isNotEmpty()
            if (isExposed) {
                scrollToManyRequestNode(contentDescription)
                return
            }
            scrollContainer.performTouchInput {
                swipe(
                    start = center + Offset(0f, MANY_REQUEST_REVEAL_SCROLL_STEP_PX / 2),
                    end = center - Offset(0f, MANY_REQUEST_REVEAL_SCROLL_STEP_PX / 2),
                    durationMillis = 100,
                )
            }
            composeTestRule.waitForIdle()
        }
        waitForNode(contentDescription)
    }

    private fun scrollToManyRequestTag(testTag: String) {
        composeTestRule.onNodeWithContentDescription(SCROLL_CONTAINER_CONTENT_DESC)
            .performScrollToNode(hasTestTag(testTag))
        waitForManyRequestSquareState(
            index = testTag.substringAfterLast('_').toInt() - 1,
            expectedState = null,
        )
    }

    private fun waitForManyRequestSquareState(
        index: Int,
        expectedState: String?,
        timeoutMillis: Long = TIMEOUT_MS,
    ) {
        val testTag = manyRequestSquareTestTag(index)
        try {
            composeTestRule.waitUntil(timeoutMillis) {
                val nodes = composeTestRule.onAllNodesWithTag(testTag).fetchSemanticsNodes()
                nodes.firstOrNull()?.let { node ->
                    expectedState == null ||
                        (node.config.contains(SemanticsProperties.StateDescription) &&
                            node.config[SemanticsProperties.StateDescription] == expectedState)
                } == true
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError(
                "Timed out waiting for request ${index + 1} state '$expectedState'",
                e,
            )
        }
    }

    /** Clicks the revoke-access-token button and waits for its result dialog. */
    private fun clickRevokeButton() = clickAndWaitForAlert(REVOKE_BUTTON_CONTENT_DESC)

    /** Clicks the make-REST-API-request button and waits for its result dialog. */
    private fun clickRequestButton() = clickAndWaitForAlert(REQUEST_BUTTON_CONTENT_DESC)

    /**
     * Clicks the button identified by [buttonContentDesc] and waits for the
     * resulting alert dialog to appear. Waits for Compose to be idle before
     * clicking so the OnClick semantic is registered (after a user switch or
     * activity resume, the button can be momentarily out of composition).
     * Retries the click once if the alert does not appear within TIMEOUT_MS
     * and the button is still in its initial (clickable) state — the network
     * call may have failed silently, leaving showAlertDialog=false.
     */
    private fun clickAndWaitForAlert(buttonContentDesc: String) {
        waitForNode(buttonContentDesc, timeoutMillis = TIMEOUT_MS)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(buttonContentDesc)
            .performSemanticsAction(SemanticsActions.OnClick)

        try {
            waitForNode(ALERT_TITLE_CONTENT_DESC, timeoutMillis = TIMEOUT_MS)
        } catch (e: AssertionError) {
            // The button's OnClick semantic is removed while the coroutine
            // is in flight (Material3 Button drops it when enabled=false).
            // If the OnClick is still present, the first click was a no-op
            // and we can retry; otherwise rethrow.
            val hasOnClick = composeTestRule.onNodeWithContentDescription(buttonContentDesc)
                .fetchSemanticsNode()
                .config.contains(SemanticsActions.OnClick)
            if (!hasOnClick) throw e

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithContentDescription(buttonContentDesc)
                .performSemanticsAction(SemanticsActions.OnClick)
            waitForNode(ALERT_TITLE_CONTENT_DESC, timeoutMillis = TIMEOUT_MS)
        }
    }

    /**
     * Taps the "Make REST API Request" button without asserting outcome.
     * Used to trigger an automatic refresh that is expected to fail (e.g. when
     * the user's refresh token has been revoked). Dismisses the result alert
     * (which may show success or failure) so the test can proceed.
     */
    fun triggerApiRequestIgnoringResult() {
        waitForNode(REQUEST_BUTTON_CONTENT_DESC, timeoutMillis = TIMEOUT_MS)
        composeTestRule.onNodeWithContentDescription(REQUEST_BUTTON_CONTENT_DESC)
            .performSemanticsAction(SemanticsActions.OnClick)

        // The alert may or may not appear (the SDK could log the user out
        // before the dialog renders). Best-effort dismiss.
        try {
            waitForNode(ALERT_POSITIVE_BUTTON_CONTENT_DESC, timeoutMillis = TIMEOUT_MS)
            composeTestRule.onNodeWithContentDescription(ALERT_POSITIVE_BUTTON_CONTENT_DESC)
                .performClick()
            composeTestRule.waitForIdle()
        } catch (_: AssertionError) {
            // Dialog never appeared, that's OK.
        }
    }

    fun getTokens(): Tokens {
        expandUserCredentialsSection()

        return Tokens(
            getSensitiveValue(ACCESS_TOKEN),
            getSensitiveValue(REFRESH_TOKEN),
        )
    }

    fun getDpopInfo(): DpopInfo {
        expandUserCredentialsSection(targetNode = OAUTH_TOKEN_TYPE)
        return DpopInfo(
            tokenType = getText(OAUTH_TOKEN_TYPE),
            nonce = getSensitiveValue(DPOP_NONCE),
            keyThumbprint = getText(DPOP_KEY_THUMBPRINT),
        )
    }

    fun validateUser(
        knownLoginHostConfig: KnownLoginHostConfig,
        knownUserConfig: KnownUserConfig,
        usesWelcomeDiscovery: Boolean = false,
        isMultiUser: Boolean = false,
        expectAdvancedAuth: Boolean = false,
        isDpop: Boolean = false,
        expectedBMarker: String? = null,
        expectedLMarker: String? = null,
        expectedAMarker: String? = null,
        wasMigrated: Boolean = false,
        isJwt: Boolean = false,
        isBeacon: Boolean = false,
        expectedRtMarker: Boolean = false,
        assertUsername: Boolean = true,
    ) {
        val expected = testConfig.getUser(knownLoginHostConfig, knownUserConfig)

        waitForNode(CREDS_SECTION_CONTENT_DESC)

        if (assertUsername) {
            // Wait for the UI to update asynchronously after login or user switch.
            // The view may be recreated and collapsed when the current user state updates.
            try {
                composeTestRule.waitUntil(APP_LOAD_TIMEOUT_MS) {
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
            } catch (e: ComposeTimeoutException) {
                throw AssertionError("Timed out after ${APP_LOAD_TIMEOUT_MS}ms waiting for username to show \"${expected.username}\"", e)
            }
            assertEquals(expected.username, getText(USERNAME))
        }

        // Validate feature flags — UI is already settled, reuse the existing layout traversal
        expandUserCredentialsSection(targetNode = USER_AGENT_CONTENT_DESC)
        validateUserAgent(getText(USER_AGENT_CONTENT_DESC), knownLoginHostConfig, usesWelcomeDiscovery, isMultiUser, expectAdvancedAuth, expectedRtMarker = expectedRtMarker, isDpop = isDpop, expectedBMarker = expectedBMarker, expectedLMarker = expectedLMarker, expectedAMarker = expectedAMarker, wasMigrated = wasMigrated, isJwt = isJwt, isBeacon = isBeacon)
    }

    fun validateOAuthValues(knownAppConfig: KnownAppConfig, scopeSelection: ScopeSelection, useHybridAuthToken: Boolean = true, isDpop: Boolean? = null) {
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

        if (expected.isDpop) {
            val dpopInfo = getDpopInfo()
            assertEquals("DPoP", dpopInfo.tokenType)
            assert(dpopInfo.nonce.isNotEmpty()) { "Expected non-empty DPoP nonce after token exchange" }
            assert(dpopInfo.keyThumbprint.matches(Regex("[A-Za-z0-9_-]{43}"))) {
                "DPoP key thumbprint must be a 43-char base64url string; got: '${dpopInfo.keyThumbprint}'"
            }
        }

        validateSIDs(isDpop = isDpop ?: expected.isDpop, accessToken = accessToken, isJwt = expected.issuesJwt, useHybrid = useHybridAuthToken, scopeList = expected.scopeList)
    }

    private fun validateSIDs(isDpop: Boolean, accessToken: String, isJwt: Boolean, useHybrid: Boolean, scopeList: List<String>) {
        val hasContentScope = scopeList.contains("content")
        val hasLightningScope = scopeList.contains("lightning")
        val hasVisualforceScope = scopeList.contains("visualforce")

        // Expand once; individual reads handle their own scrolling via performScrollTo().
        expandUserCredentialsSection(targetNode = CONTENT_DOMAIN)
        val contentDomain = getText(CONTENT_DOMAIN).emptyIfPlaceholder()
        val contentSid = getSensitiveValue(CONTENT_SID).emptyIfPlaceholder()
        val lightningDomain = getText(LIGHTNING_DOMAIN).emptyIfPlaceholder()
        val lightningSid = getSensitiveValue(LIGHTNING_SID).emptyIfPlaceholder()
        val vfDomain = getText(VF_DOMAIN).emptyIfPlaceholder()
        val vfSid = getSensitiveValue(VF_SID).emptyIfPlaceholder()
        val parentSid = getSensitiveValue(PARENT_SID).emptyIfPlaceholder()
        val mainSid = getSensitiveValue(MAIN_SID).emptyIfPlaceholder()
        val uiSid = getSensitiveValue(UI_SID).emptyIfPlaceholder()

        assertNotEmpty(contentDomain, shouldNotBeEmpty = hasContentScope && useHybrid, "Content domain")
        assertNotEmpty(contentSid, shouldNotBeEmpty = hasContentScope && useHybrid, "Content SID")
        assertNotEmpty(lightningDomain, shouldNotBeEmpty = hasLightningScope && useHybrid, "Lightning domain")
        assertNotEmpty(lightningSid, shouldNotBeEmpty = hasLightningScope && useHybrid, "Lightning SID")
        assertNotEmpty(vfDomain, shouldNotBeEmpty = hasVisualforceScope && useHybrid, "VF domain")
        assertNotEmpty(vfSid, shouldNotBeEmpty = hasVisualforceScope && useHybrid, "VF SID")
        assertNotEmpty(parentSid, shouldNotBeEmpty = isJwt && useHybrid, "Parent SID")
        assertNotEmpty(uiSid, shouldNotBeEmpty = isDpop && useHybrid, "UI SID")

        if (useHybrid) {
            if (isDpop) {
                assertEquals("Main SID should equal UI SID in DPoP hybrid flow", uiSid, mainSid)
            } else if (isJwt) {
                assertEquals("Main SID should equal Parent SID in JWT hybrid flow", parentSid, mainSid)
            } else {
                assertEquals("Main SID should equal Access Token in opaque hybrid flow", accessToken, mainSid)
            }
        }
    }

    private fun assertNotEmpty(value: String, shouldNotBeEmpty: Boolean, name: String) {
        if (shouldNotBeEmpty) {
            assert(value.isNotEmpty()) { "$name should not be empty" }
        } else {
            assert(value.isEmpty()) { "$name should be empty" }
        }
    }

    private fun String.emptyIfPlaceholder() = if (this == EMPTY_VALUE_PLACEHOLDER) "" else this

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

    /**
     * Opens the migration bottom sheet and taps "Upgrade to DPoP" for the current user — an
     * in-place upgrade of the existing connected app (same consumer key/redirect URI/scopes),
     * independent of [SalesforceSDKManager.useDPoP]. Because the config is unchanged, no
     * approve/deny screen is expected (A-2 in the spec), but [AuthorizationPageObject
     * .tapAllowAfterMigration] is still consulted to tolerate a stray consent screen rather than
     * asserting its strict absence.
     */
    fun upgradeToDPoP() {
        // Tap "Migrate Access Token" bottom bar icon to open the sheet.
        val migrateDesc = getString(R.string.migrate_access_token)
        waitForNode(migrateDesc)
        composeTestRule.onNodeWithContentDescription(migrateDesc)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        // Tap the "Upgrade to DPoP" button.
        waitForNode(UPGRADE_TO_DPOP_BUTTON_CONTENT_DESC)
        composeTestRule.onNodeWithContentDescription(UPGRADE_TO_DPOP_BUTTON_CONTENT_DESC)
            .performSemanticsAction(SemanticsActions.OnClick)

        AuthorizationPageObject(composeTestRule).tapAllowAfterMigration()

        // Wait for migration to complete and the sheet to auto-dismiss (see migrateToNewApp
        // for the rationale behind the close-button fallback).
        val closeDesc = getString(R.string.close_content_description)
        try {
            waitForNodeGone(closeDesc)
        } catch (_: Exception) {
            composeTestRule.onNodeWithContentDescription(closeDesc)
                .performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()
            waitForNodeGone(closeDesc)
        }

        // Wait for the app UI to refresh with new token data.
        waitForAppLoad()
    }

    /**
     * Opens the migration bottom sheet and taps "Downgrade from DPoP" for the current user — an
     * in-place downgrade of the existing connected app (same consumer key/redirect URI/scopes)
     * back to Bearer, independent of [SalesforceSDKManager.useDPoP]. No consent screen is
     * expected because the consumer key, redirect URI, and scopes are unchanged, but
     * [AuthorizationPageObject.tapAllowAfterMigration] is still consulted to tolerate a stray
     * consent screen rather than asserting its strict absence. Mirrors [upgradeToDPoP] with the
     * inverse button.
     */
    fun downgradeFromDPoP() {
        // Tap "Migrate Access Token" bottom bar icon to open the sheet.
        val migrateDesc = getString(R.string.migrate_access_token)
        waitForNode(migrateDesc)
        composeTestRule.onNodeWithContentDescription(migrateDesc)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        // Tap the "Downgrade from DPoP" button.
        waitForNode(DOWNGRADE_FROM_DPOP_BUTTON_CONTENT_DESC)
        composeTestRule.onNodeWithContentDescription(DOWNGRADE_FROM_DPOP_BUTTON_CONTENT_DESC)
            .performSemanticsAction(SemanticsActions.OnClick)

        AuthorizationPageObject(composeTestRule).tapAllowAfterMigration()

        // Wait for migration to complete and the sheet to auto-dismiss (see migrateToNewApp
        // for the rationale behind the close-button fallback).
        val closeDesc = getString(R.string.close_content_description)
        try {
            waitForNodeGone(closeDesc)
        } catch (_: Exception) {
            composeTestRule.onNodeWithContentDescription(closeDesc)
                .performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()
            waitForNodeGone(closeDesc)
        }

        // Wait for the app UI to refresh with new token data.
        waitForAppLoad()
    }

    private fun expandUserCredentialsSection(targetNode: String = ACCESS_TOKEN) {
        waitForNode(CREDS_SECTION_CONTENT_DESC)

        // Wait until the target node is visible, expanding the card if needed.
        // The key() block in TesterUI may recreate UserCredentialsView (collapsing
        // the card) between expansion and access, so poll until it stabilizes.
        try {
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
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Timed out after ${TIMEOUT_MS}ms waiting to expand credentials section for node: \"$targetNode\"", e)
        }
    }

    /** Wait for a node with the given content description to exist. */
    private fun waitForNode(contentDesc: String, timeoutMillis: Long = TIMEOUT_MS) {
        try {
            composeTestRule.waitUntil(timeoutMillis) {
                try {
                    composeTestRule.onAllNodesWithContentDescription(contentDesc)
                        .fetchSemanticsNodes().isNotEmpty()
                } catch (_: IllegalStateException) {
                    false // Compose hierarchy temporarily unavailable
                }
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Timed out after ${timeoutMillis}ms waiting for node: \"$contentDesc\"", e)
        }
    }

    /** Wait for a node with the given content description to disappear. */
    private fun waitForNodeGone(contentDesc: String, timeoutMillis: Long = TIMEOUT_MS) {
        try {
            composeTestRule.waitUntil(timeoutMillis) {
                try {
                    composeTestRule.onAllNodesWithContentDescription(contentDesc)
                        .fetchSemanticsNodes().isEmpty()
                } catch (_: IllegalStateException) {
                    false
                }
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Timed out after ${timeoutMillis}ms waiting for node to disappear: \"$contentDesc\"", e)
        }
    }

    private fun getSensitiveValue(contentDescription: String): String {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        var revealMayHaveSucceeded = false
        try {
            while (System.currentTimeMillis() < deadline) {
                try {
                    val hiddenText = readTextFromFreshNode(contentDescription)
                    if (hiddenText == EMPTY_VALUE_PLACEHOLDER) return hiddenText
                    if (!hiddenText.contains("...")) {
                        revealMayHaveSucceeded = true
                        return hiddenText
                    }

                    revealMayHaveSucceeded = true
                    composeTestRule.onNodeWithContentDescription(contentDescription)
                        .performSemanticsAction(SemanticsActions.OnClick)
                    composeTestRule.waitForIdle()

                    waitForSensitiveTextState(
                        contentDescription = contentDescription,
                        shouldBeHidden = false,
                        overallDeadline = deadline,
                    )?.let { return it }
                } catch (_: AssertionError) {
                    // Retry below.
                } catch (_: IllegalStateException) {
                    // Retry below.
                }
                // The keyed credentials view can be replaced while a user switch settles. Resolve
                // a fresh node on the next pass instead of retaining a stale interaction.
                Thread.sleep(READ_RETRY_INTERVAL_MS)
            }
            throw AssertionError("Timed out revealing full value for '$contentDescription'")
        } finally {
            if (revealMayHaveSucceeded) {
                hideSensitiveValue(contentDescription)
            }
        }
    }

    private fun hideSensitiveValue(contentDescription: String) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            try {
                if (readTextFromFreshNode(contentDescription).contains("...")) return

                composeTestRule.onNodeWithContentDescription(contentDescription)
                    .performSemanticsAction(SemanticsActions.OnClick)
                composeTestRule.waitForIdle()
                val hiddenText = waitForSensitiveTextState(
                    contentDescription = contentDescription,
                    shouldBeHidden = true,
                    overallDeadline = deadline,
                )
                if (hiddenText != null) return
            } catch (_: AssertionError) {
                // Retry with a fresh node below.
            } catch (_: IllegalStateException) {
                // Retry with a fresh node below.
            }
            Thread.sleep(READ_RETRY_INTERVAL_MS)
        }
        throw AssertionError("Timed out restoring hidden value for '$contentDescription'")
    }

    /**
     * Gives a sensitive-row click time to finish recomposing before another click is attempted.
     * Without this settle window, a delayed reveal can be immediately toggled back to hidden.
     */
    private fun waitForSensitiveTextState(
        contentDescription: String,
        shouldBeHidden: Boolean,
        overallDeadline: Long,
    ): String? {
        val transitionDeadline = minOf(
            overallDeadline,
            System.currentTimeMillis() + SENSITIVE_TOGGLE_SETTLE_TIMEOUT_MS,
        )
        while (System.currentTimeMillis() < transitionDeadline) {
            try {
                val text = readTextFromFreshNode(contentDescription)
                if (text == EMPTY_VALUE_PLACEHOLDER) {
                    if (shouldBeHidden) return text
                } else if (text.contains("...") == shouldBeHidden) {
                    return text
                }
            } catch (_: AssertionError) {
                // Resolve a fresh node on the next pass.
            } catch (_: IllegalStateException) {
                // Resolve a fresh node on the next pass.
            }

            val remaining = transitionDeadline - System.currentTimeMillis()
            if (remaining > 0) {
                Thread.sleep(minOf(READ_RETRY_INTERVAL_MS, remaining))
            }
        }
        return null
    }

    private fun getText(contentDescription: String): String {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            try {
                return readTextFromFreshNode(contentDescription)
            } catch (_: AssertionError) {
                Thread.sleep(READ_RETRY_INTERVAL_MS)
            } catch (_: IllegalStateException) {
                Thread.sleep(READ_RETRY_INTERVAL_MS)
            }
        }
        throw AssertionError("Timed out reading value for '$contentDescription'")
    }

    private fun readTextFromFreshNode(contentDescription: String): String {
        val node = composeTestRule.onNodeWithContentDescription(contentDescription)
        node.performScrollTo()
        return node.fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .last().text // Value is last; first is the label
    }

    fun validateUserAgent(
        knownLoginHostConfig: KnownLoginHostConfig,
        usesWelcomeDiscovery: Boolean = false,
        isMultiUser: Boolean = false,
        expectAdvancedAuth: Boolean = false,
        expectedRtMarker: Boolean = false,
        isDpop: Boolean = false,
        expectedBMarker: String? = null,
        expectedLMarker: String? = null,
        expectedAMarker: String? = null,
        wasMigrated: Boolean = false,
        isJwt: Boolean = false,
        isBeacon: Boolean = false,
    ) {
        expandUserCredentialsSection(targetNode = USER_AGENT_CONTENT_DESC)
        validateUserAgent(getText(USER_AGENT_CONTENT_DESC), knownLoginHostConfig, usesWelcomeDiscovery, isMultiUser, expectAdvancedAuth, expectedRtMarker, isDpop, expectedBMarker, expectedLMarker, expectedAMarker, wasMigrated, isJwt, isBeacon)
    }

    fun validateLastTokenRequestUserAgent(vararg expectedMarkers: String) {
        expandUserCredentialsSection(targetNode = TOKEN_ENDPOINT_USER_AGENT_CONTENT_DESC)
        val userAgent = getText(TOKEN_ENDPOINT_USER_AGENT_CONTENT_DESC)
        val flags = userAgent.substringAfter("ftr_", missingDelimiterValue = "")
            .substringBefore(" ")
            .split(".")
            .toSet()
        expectedMarkers.forEach { expectedMarker ->
            assert(expectedMarker in flags) {
                "Expected '$expectedMarker' in the last token-request User-Agent: $userAgent"
            }
        }
    }

    fun getCapturedTokenRequestCount(): Int {
        expandUserCredentialsSection(targetNode = TOKEN_ENDPOINT_REQUEST_COUNT_CONTENT_DESC)
        return getText(TOKEN_ENDPOINT_REQUEST_COUNT_CONTENT_DESC).toInt()
    }

    private fun validateUserAgent(
        ua: String,
        knownLoginHostConfig: KnownLoginHostConfig,
        usesWelcomeDiscovery: Boolean = false,
        isMultiUser: Boolean = false,
        expectAdvancedAuth: Boolean = false,
        expectedRtMarker: Boolean = false,
        isDpop: Boolean = false,
        expectedBMarker: String? = null,
        expectedLMarker: String? = null,
        expectedAMarker: String? = null,
        wasMigrated: Boolean = false,
        isJwt: Boolean = false,
        isBeacon: Boolean = false,
    ) {
        assert(ua.contains("SalesforceMobileSDK/")) {
            "User agent missing 'SalesforceMobileSDK/' prefix: $ua"
        }
        assert(ua.contains("ftr_")) {
            "User agent missing 'ftr_' segment: $ua"
        }

        // Parse flag codes from the ftr_XXXX segment
        val ftrSegment = ua.substringAfter("ftr_").substringBefore(" ")
        val flags = ftrSegment.split(".").toSet()

        val shouldHaveBW = expectAdvancedAuth || knownLoginHostConfig == KnownLoginHostConfig.ADVANCED_AUTH
        if (shouldHaveBW) {
            assert("BW" in flags) {
                "Expected 'BW' flag for browser-based auth in: $ua"
            }
        } else {
            assert("BW" !in flags) {
                "Expected no 'BW' flag for in-app WebView auth in: $ua"
            }
        }

        if (usesWelcomeDiscovery) {
            assert("WD" in flags) {
                "Expected 'WD' flag for Welcome Discovery in: $ua"
            }
        } else {
            assert("WD" !in flags) {
                "Expected no 'WD' flag when Welcome Discovery was not used in: $ua"
            }
        }

        if (isMultiUser) {
            assert("MU" in flags) {
                "Expected 'MU' flag for multi-user in: $ua"
            }
        } else {
            assert("MU" !in flags) {
                "Expected no 'MU' flag when only one user is logged in, in: $ua"
            }
        }

        if (expectedRtMarker) {
            assert("RT" in flags) {
                "Expected 'RT' flag after Refresh Token Rotation in: $ua"
            }
        } else {
            assert("RT" !in flags) {
                "Expected no 'RT' flag when Refresh Token Rotation has not occurred in: $ua"
            }
        }

        if (isDpop) {
            assert("DP" in flags) {
                "Expected 'DP' flag for DPoP session in: $ua"
            }
        } else {
            assert("DP" !in flags) {
                "Expected no 'DP' flag for non-DPoP session in: $ua"
            }
        }

        val allBMarkers = listOf(
            Features.FEATURE_BROWSER_LOGIN_SERVER_AUTH_CONFIG,
            Features.FEATURE_BROWSER_LOGIN_FOR_ADMIN,
            Features.FEATURE_BROWSER_LOGIN_FORCE_FLAG,
        )  // B2 excluded — never registered on Android
        if (expectedBMarker != null) {
            assert(expectedBMarker in flags) {
                "Expected B-marker '$expectedBMarker' in ftr_ flags of: $ua"
            }
            allBMarkers.filter { it != expectedBMarker }.forEach { marker ->
                assert(marker !in flags) {
                    "Unexpected B-marker '$marker' in ftr_ flags of: $ua"
                }
            }
        } else {
            allBMarkers.forEach { marker ->
                assert(marker !in flags) {
                    "Unexpected B-marker '$marker' in ftr_ flags of: $ua"
                }
            }
        }

        val allLMarkers = listOf(
            Features.FEATURE_LOGIN_SERVER_PRODUCTION,
            Features.FEATURE_LOGIN_SERVER_SANDBOX,
            Features.FEATURE_LOGIN_SERVER_WELCOME_DISCOVERY,
            Features.FEATURE_LOGIN_SERVER_MY_DOMAIN,
            Features.FEATURE_LOGIN_SERVER_OTHER,
        )
        if (expectedLMarker != null) {
            assert(expectedLMarker in flags) {
                "Expected L-marker '$expectedLMarker' in ftr_ flags of: $ua"
            }
            allLMarkers.filter { it != expectedLMarker }.forEach { marker ->
                assert(marker !in flags) {
                    "Unexpected L-marker '$marker' in ftr_ flags of: $ua"
                }
            }
        }

        val allAMarkers = listOf(
            Features.FEATURE_AUTH_TYPE_WEB_SERVER_NON_HYBRID,
            Features.FEATURE_AUTH_TYPE_WEB_SERVER_HYBRID,
            Features.FEATURE_AUTH_TYPE_USER_AGENT_NON_HYBRID,
            Features.FEATURE_AUTH_TYPE_USER_AGENT_HYBRID,
            Features.FEATURE_AUTH_TYPE_NATIVE,
        )
        if (expectedAMarker != null) {
            assert(expectedAMarker in flags) {
                "Expected A-marker '$expectedAMarker' in ftr_ flags of: $ua"
            }
            allAMarkers.filter { it != expectedAMarker }.forEach { marker ->
                assert(marker !in flags) {
                    "Unexpected A-marker '$marker' in ftr_ flags of: $ua"
                }
            }
        }

        if (wasMigrated) {
            assert(Features.FEATURE_TOKEN_MIGRATION in flags) {
                "Expected 'TM' flag for token migration in: $ua"
            }
        } else {
            assert(Features.FEATURE_TOKEN_MIGRATION !in flags) {
                "Expected no 'TM' flag when not migrated in: $ua"
            }
        }

        if (isJwt) {
            assert(Features.FEATURE_TOKEN_FORMAT_JWT in flags) {
                "Expected 'JT' flag for JWT token format in: $ua"
            }
            assert(Features.FEATURE_TOKEN_FORMAT_OPAQUE !in flags) {
                "Expected no 'OT' flag for JWT token format in: $ua"
            }
        } else {
            assert(Features.FEATURE_TOKEN_FORMAT_OPAQUE in flags) {
                "Expected 'OT' flag for opaque token format in: $ua"
            }
            assert(Features.FEATURE_TOKEN_FORMAT_JWT !in flags) {
                "Expected no 'JT' flag for opaque token format in: $ua"
            }
        }

        if (isBeacon) {
            assert(Features.FEATURE_BEACON in flags) {
                "Expected 'BN' flag for beacon child app in: $ua"
            }
        } else {
            assert(Features.FEATURE_BEACON !in flags) {
                "Expected no 'BN' flag when not a beacon child app in: $ua"
            }
        }
    }
}

data class ManyRequestCounts(
    val completed: Int,
    val successes: Int,
    val failures: Int,
    val inFlight: Int,
    val queued: Int,
)
