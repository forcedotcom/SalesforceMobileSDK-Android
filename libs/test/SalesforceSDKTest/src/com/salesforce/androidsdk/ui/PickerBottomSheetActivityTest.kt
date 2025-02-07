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

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.ui.components.BACK_BUTTON_CD
import com.salesforce.androidsdk.ui.components.CLOSE_BUTTON_CD
import com.salesforce.androidsdk.ui.components.PickerStyle
import org.junit.Rule
import org.junit.Test

/**
 * These tests are separate from PickerBottomSheetTest because they check string
 * resources are correct, which requires an activity.
 */
class PickerBottomSheetActivityTest {

    @get:Rule
    val androidComposeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val closeButtonCd = hasContentDescription(CLOSE_BUTTON_CD)
    private val backButtonCd = hasContentDescription(BACK_BUTTON_CD)

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun serverPickerHeader_Navigates_AndDisplaysCorrectly() {
        androidComposeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
            )
        }

        val closeButton = androidComposeTestRule.onNode(closeButtonCd)
        val backButton = androidComposeTestRule.onNode(backButtonCd)
        val changeServerText = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(R.string.sf__pick_server)
        )
        val addConnectionButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(R.string.sf__custom_url_button)
        )
        val addConnectionText = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(R.string.sf__server_url_add_title)
        )
        val removeButton = androidComposeTestRule.onNode(customsRowCd).onChildAt(1)
        val deleteButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(R.string.sf__server_url_delete)
        )

        backButton.isNotDisplayed()
        changeServerText.assertIsDisplayed()
        closeButton.isDisplayed()
        addConnectionButton.isDisplayed()
        addConnectionText.assertDoesNotExist()

        // Click Add Connection Button
        addConnectionButton.performClick()

        // Validate Add Connection
        backButton.isDisplayed()
        changeServerText.assertDoesNotExist()
        addConnectionText.assertIsDisplayed()
        closeButton.isDisplayed()

        // Click Back
        backButton.performClick()

        // Valid Add Connection
        backButton.isNotDisplayed()
        changeServerText.assertIsDisplayed()
        closeButton.isDisplayed()
        addConnectionButton.isDisplayed()
        addConnectionText.assertDoesNotExist()

        // Validate Delete Text
        removeButton.performClick()
        deleteButton.isDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun accountPickerHeader_Displays_Correctly() {
        androidComposeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.UserAccountPicker,

            )
        }

        val closeButton = androidComposeTestRule.onNode(closeButtonCd)
        val backButton = androidComposeTestRule.onNode(backButtonCd)
        val accountSelectorText = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(R.string.sf__account_selector_text)
        )
        val addAccountButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(R.string.sf__add_new_account)
        )

        backButton.assertDoesNotExist()
        accountSelectorText.assertIsDisplayed()
        closeButton.isDisplayed()
        addAccountButton.isDisplayed()
    }
}