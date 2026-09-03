/*
 * Copyright (c) 2025-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.ui.components.BiometricOptInDialog
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for the biometric opt-in dialog rendered by [BiometricOptInActivity].
 *
 * These test the dialog composable directly (rather than launching the activity) so the user's
 * choice can be captured without touching the current user's persisted preferences.  A separate
 * unit test in BiometricAuthenticationManagerTest verifies that presentOptInDialog() launches the
 * activity.
 */
class BiometricOptInDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val titleText get() = context.getString(R.string.sf__biometric_opt_in_title)
    private val messageText get() = context.getString(R.string.sf__biometric_opt_in_message)
    private val approveText get() = context.getString(R.string.sf__biometric_opt_in_approve)
    private val denyText get() = context.getString(R.string.sf__biometric_opt_in_deny)

    @Test
    fun optInDialog_DisplaysTitleMessageAndButtons() {
        composeTestRule.setContent {
            BiometricOptInDialog(onResult = {})
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(titleText).assertIsDisplayed()
        composeTestRule.onNodeWithText(messageText).assertIsDisplayed()
        composeTestRule.onNodeWithText(approveText).assertIsDisplayed()
        composeTestRule.onNodeWithText(denyText).assertIsDisplayed()
    }

    @Test
    fun optInDialog_EnableButton_ReturnsOptedInTrue() {
        var result: Boolean? = null
        composeTestRule.setContent {
            BiometricOptInDialog(onResult = { result = it })
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(approveText).performClick()
        composeTestRule.waitForIdle()

        Assert.assertEquals("Enable should report the user opted in.", true, result)
    }

    @Test
    fun optInDialog_UsePasswordButton_ReturnsOptedInFalse() {
        var result: Boolean? = null
        composeTestRule.setContent {
            BiometricOptInDialog(onResult = { result = it })
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(denyText).performClick()
        composeTestRule.waitForIdle()

        Assert.assertEquals("Use Password should report the user declined.", false, result)
    }
}
