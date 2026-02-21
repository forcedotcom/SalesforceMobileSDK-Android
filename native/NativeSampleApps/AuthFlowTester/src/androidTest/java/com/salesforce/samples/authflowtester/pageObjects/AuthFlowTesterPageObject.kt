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
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.samples.authflowtester.ALERT_POSITIVE_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.ALERT_TITLE_CONTENT_DESC
import com.salesforce.samples.authflowtester.CREDS_SECTION_CONTENT_DESC
import com.salesforce.samples.authflowtester.MIGRATE_TOKEN_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.R
import com.salesforce.samples.authflowtester.REQUEST_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.REVOKE_BUTTON_CONTENT_DESC
import com.salesforce.samples.authflowtester.components.ACCESS_TOKEN
import com.salesforce.samples.authflowtester.components.REFRESH_TOKEN
import com.salesforce.samples.authflowtester.components.SCOPES
import com.salesforce.samples.authflowtester.components.TOKEN_FORMAT

data class TokenInfo(
    val accessToken: String,
    val refreshToken: String,
    val tokenFormat: String,
    val scopes: String,
)

class AuthFlowTesterPageObject(
    private val composeTestRule: ComposeTestRule,
) {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun getString(id: Int) = context.getString(id)

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

    fun makeApiRequest() {
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

    fun getTokens(): TokenInfo {
        // Expand creds section
        waitForNode(CREDS_SECTION_CONTENT_DESC)
        composeTestRule.onNodeWithContentDescription(CREDS_SECTION_CONTENT_DESC)
            .performClick()
        composeTestRule.waitForIdle()

        // Wait for expanded content
        waitForNode(ACCESS_TOKEN)

        val tokens = TokenInfo(
            getSensitiveValue(ACCESS_TOKEN),
            getSensitiveValue(REFRESH_TOKEN),
            getText(TOKEN_FORMAT),
            getText(SCOPES),
        )

        // Collapse to guarantee consistent state
        composeTestRule.onNodeWithContentDescription(CREDS_SECTION_CONTENT_DESC)
            .performClick()
        composeTestRule.waitForIdle()

        return tokens
    }

    fun migrateToNewApp(jsonApp: String) {
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

    /** Wait for a node with the given content description to exist. */
    fun waitForNode(contentDesc: String, timeoutMillis: Long = 30_000) {
        composeTestRule.waitUntil(timeoutMillis) {
            try {
                composeTestRule.onAllNodesWithContentDescription(contentDesc)
                    .fetchSemanticsNodes().isNotEmpty()
            } catch (e: IllegalStateException) {
                false // Compose hierarchy temporarily unavailable
            }
        }
    }

    /** Wait for a node with the given content description to disappear. */
    fun waitForNodeGone(contentDesc: String, timeoutMillis: Long = 30_000) {
        composeTestRule.waitUntil(timeoutMillis) {
            try {
                composeTestRule.onAllNodesWithContentDescription(contentDesc)
                    .fetchSemanticsNodes().isEmpty()
            } catch (e: IllegalStateException) {
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