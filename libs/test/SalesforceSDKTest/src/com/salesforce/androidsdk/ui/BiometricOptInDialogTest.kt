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
import com.salesforce.androidsdk.ui.components.BiometricOptInDialogContent
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for the biometric opt-in dialog rendered by [BiometricOptInActivity].
 *
 * These render [BiometricOptInDialogContent] — the window-less content of the opt-in dialog — rather
 * than the full [com.salesforce.androidsdk.ui.components.BiometricOptInDialog], which wraps the same
 * title, message, and buttons in a Material3 AlertDialog.  Driving the AlertDialog through the
 * Compose test harness forces it to synchronize idleness against the dialog's separate Dialog
 * window; that second-window surface/focus sync never completes on some emulator and Firebase Test
 * Lab devices, hanging the test until the watchdog kills it.  The content composable renders in the
 * host window (a single Compose root) and shares its button-to-callback wiring with the production
 * dialog, so these tests exercise the real behavior without the flaky second window.
 *
 * A separate unit test in BiometricAuthenticationManagerTest verifies that presentOptInDialog()
 * launches the activity.
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
        composeTestRule.setContent { BiometricOptInDialogContent(onResult = {}) }

        composeTestRule.onNodeWithText(titleText).assertIsDisplayed()
        composeTestRule.onNodeWithText(messageText).assertIsDisplayed()
        composeTestRule.onNodeWithText(approveText).assertIsDisplayed()
        composeTestRule.onNodeWithText(denyText).assertIsDisplayed()
    }

    @Test
    fun optInDialog_EnableButton_ReturnsOptedInTrue() {
        var result: Boolean? = null
        composeTestRule.setContent { BiometricOptInDialogContent(onResult = { result = it }) }

        composeTestRule.onNodeWithText(approveText).performClick()

        Assert.assertEquals("Enable should report the user opted in.", true, result)
    }

    @Test
    fun optInDialog_UsePasswordButton_ReturnsOptedInFalse() {
        var result: Boolean? = null
        composeTestRule.setContent { BiometricOptInDialogContent(onResult = { result = it }) }

        composeTestRule.onNodeWithText(denyText).performClick()

        Assert.assertEquals("Use Password should report the user declined.", false, result)
    }
}
