package com.salesforce.samples.authflowtester.pageObjects

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.R

/**
 * Page object for the SDK's Login Options screen.
 * The login options UI is fully Compose (LoginOptionsActivity),
 * so all interactions use the Compose Test API via [ComposeTestRule].
 */
class LoginOptionsPageObject(
    private val composeTestRule: ComposeTestRule,
) {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun getString(id: Int) = context.getString(id)

    fun openLoginOptions() {
        // Tap "More Options" three-dot menu (Compose IconButton)
        composeTestRule.onNodeWithContentDescription(getString(R.string.sf__more_options))
            .performClick()
        composeTestRule.waitForIdle()

        // Tap "Developer Support" dropdown menu item
        composeTestRule.onNodeWithText(getString(R.string.sf__dev_support_title_menu_item))
            .performClick()
        composeTestRule.waitForIdle()

        // Tap "Login Options" in the native AlertDialog (not Compose)
        onView(withText(getString(R.string.sf__dev_support_login_options_title)))
            .inRoot(isDialog())
            .perform(click())
        composeTestRule.waitForIdle()
    }

    fun enableWebServerFlow() = toggleIfOff(
        getString(R.string.sf__login_options_webserver_toggle_content_description)
    )

    fun disableWebServerFlow() = toggleIfOn(
        getString(R.string.sf__login_options_webserver_toggle_content_description)
    )

    fun enableHybridAuthToken() = toggleIfOff(
        getString(R.string.sf__login_options_hybrid_toggle_content_description)
    )

    fun disableHybridAuthToken() = toggleIfOn(
        getString(R.string.sf__login_options_hybrid_toggle_content_description)
    )

    fun enableOverrideBootConfig() = toggleIfOff(
        getString(R.string.sf__login_options_dynamic_config_toggle_content_description)
    )

    fun disableOverrideBootConfig() = toggleIfOn(
        getString(R.string.sf__login_options_dynamic_config_toggle_content_description)
    )

    fun saveOverrideBootConfig() {
        closeSoftKeyboard()
        composeTestRule.onNodeWithContentDescription(
            getString(R.string.sf__login_options_save_button_content_description)
        ).performClick()
        composeTestRule.waitForIdle()
    }

    fun setOverrideBootConfig(consumerKey: String, redirectUri: String, scopes: String? = null) {
        openLoginOptions()
        enableOverrideBootConfig()

        composeTestRule.onNodeWithContentDescription(
            getString(R.string.sf__login_options_consumer_key_field_content_description)
        ).performTextReplacement(consumerKey)

        composeTestRule.onNodeWithContentDescription(
            getString(R.string.sf__login_options_redirect_uri_field_content_description)
        ).performTextReplacement(redirectUri)

        scopes?.let {
            composeTestRule.onNodeWithContentDescription(
                getString(R.string.sf__login_options_scopes_field_content_description)
            ).performTextReplacement(it)
        }

        saveOverrideBootConfig()
    }

    /** Clicks the toggle only if it is currently off. */
    private fun toggleIfOff(contentDescription: String) {
        val node = composeTestRule.onNodeWithContentDescription(contentDescription)
        try {
            node.assertIsOff()
            node.performClick()
            composeTestRule.waitForIdle()
        } catch (_: AssertionError) {
            // Already on
        }
    }

    /** Clicks the toggle only if it is currently on. */
    private fun toggleIfOn(contentDescription: String) {
        val node = composeTestRule.onNodeWithContentDescription(contentDescription)
        try {
            node.assertIsOn()
            node.performClick()
            composeTestRule.waitForIdle()
        } catch (_: AssertionError) {
            // Already off
        }
    }
}