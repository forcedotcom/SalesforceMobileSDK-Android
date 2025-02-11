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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.ui.components.PickerStyle
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

/**
 * These tests are separate from PickerBottomSheetTest because they check string
 * resources are correct, which requires an activity.
 */
class PickerBottomSheetActivityTest {

    @get:Rule
    val androidComposeTestRule = createAndroidComposeRule<ComponentActivity>()

    // region Login Server Picker Tests

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun serverPickerHeader_Navigates_AndDisplaysCorrectly() {
        androidComposeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
            )
        }

        val picker = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__server_picker_content_description)
        )
        val closeButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__server_close_button_content_description)
        )
        val backButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__back_button_content_description)
        )
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
            androidComposeTestRule.activity.getString(R.string.sf__server_delete_content_description)
        )

        picker.assertIsDisplayed()
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
    fun serverList_UpdatesOn_ServerDeletion() {
        androidComposeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                selectedListItem = serverList[1],
            )
        }

        val prodListItem = androidComposeTestRule.onNode(prodRowCd)
        val sandboxListItem = androidComposeTestRule.onNode(sandboxRowCd)
        val customListItem = androidComposeTestRule.onNode(customsRowCd)
        val customListItemRadioButton = customListItem.onChildAt(0)
        val customListItemRemoveButton = customListItem.onChildAt(1)
        val customListItemDeleteButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__server_delete_content_description)
        )

        prodListItem.onChild().assertIsNotSelected()
        sandboxListItem.onChild().assertIsSelected()
        customListItemRadioButton.assertIsNotSelected()

        // Check only custom server has remove button
        prodListItem.onChildAt(1).assertDoesNotExist()
        sandboxListItem.onChildAt(1).assertDoesNotExist()
        customListItemRemoveButton.assertExists()
        customListItemRemoveButton.assertIsDisplayed()
        customListItemDeleteButton.assertDoesNotExist()

        // Click Remove
        customListItemRemoveButton.performClick()
        customListItemDeleteButton.assertExists()
        customListItemDeleteButton.assertIsDisplayed()

        // Click Delete
        customListItemDeleteButton.performClick()

        // Assert Visuals
        prodListItem.assertIsDisplayed()
        prodListItem.onChild().assertIsNotSelected()
        prodListItem.assertIsDisplayed()
        sandboxListItem.onChild().assertIsSelected()
        customListItem.assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun picker_DeleteServer_CallsSeverDeletion() {
        var correctServerRemoved = false
        val removeServer = { server: LoginServer -> if (server == customServer) correctServerRemoved = true }
        androidComposeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                selectedListItem = serverList[1],
                removeLoginServer = removeServer,
            )
        }

        val customListItem = androidComposeTestRule.onNode(customsRowCd)
        val customListItemRemoveButton = customListItem.onChildAt(1)
        val customListItemDeleteButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__server_delete_content_description)
        )

        // Click Remove
        customListItemRemoveButton.performClick()
        // Click Delete
        customListItemDeleteButton.performClick()

        // Assert Model Called
        Assert.assertTrue("Remove server fun not called.", correctServerRemoved)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun complexServerList_UpdatesOn_ServerDeletion() {
        val customServer2 = LoginServer("Custom2", "2mobilesdk.my.salesforce.com", true)
        val customServer3 = LoginServer("Custom3", "3mobilesdk.my.salesforce.com", true)
        val complexServerList = listOf(prodServer, sandboxServer, customServer, customServer2, customServer3)
        androidComposeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                list = complexServerList,
                selectedListItem = complexServerList[4],
            )
        }

        val prodListItem = androidComposeTestRule.onNode(prodRowCd)
        val sandboxListItem = androidComposeTestRule.onNode(sandboxRowCd)
        val customListItem = androidComposeTestRule.onNode(customsRowCd)
        val customListItemRadioButton = customListItem.onChildAt(0)
        val customListItemRemoveButton = customListItem.onChildAt(1)
        val custom2ListItem = androidComposeTestRule.onNode(hasText(customServer2.name) and hasText(customServer2.url))
        val custom2ListItemRadioButton = custom2ListItem.onChildAt(0)
        val custom2ListItemRemoveButton = custom2ListItem.onChildAt(1)
        val custom2ListItemDeleteButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__server_delete_content_description)
        )
        val custom3ListItem = androidComposeTestRule.onNode(hasText(customServer3.name) and hasText(customServer3.url))
        val custom3ListItemRadioButton = custom3ListItem.onChildAt(0)
        val custom3ListItemRemoveButton = custom3ListItem.onChildAt(1)

        prodListItem.onChild().assertIsNotSelected()
        sandboxListItem.onChild().assertIsNotSelected()
        customListItemRadioButton.assertIsNotSelected()
        custom2ListItemRadioButton.assertIsNotSelected()
        custom3ListItemRadioButton.assertIsSelected()

        // Check only custom servers have remove button
        prodListItem.onChildAt(1).assertDoesNotExist()
        sandboxListItem.onChildAt(1).assertDoesNotExist()
        customListItemRemoveButton.assertIsDisplayed()
        custom2ListItemRemoveButton.assertIsDisplayed()
        custom2ListItemDeleteButton.assertDoesNotExist()
        custom3ListItemRemoveButton.assertIsDisplayed()

        // Click Remove
        custom2ListItemRemoveButton.performClick()
        custom2ListItemDeleteButton.assertExists()
        custom2ListItemDeleteButton.assertIsDisplayed()

        // Click Delete
        custom2ListItemDeleteButton.performClick()

        // Assert Visuals
        prodListItem.assertIsDisplayed()
        prodListItem.assertIsDisplayed()
        customListItem.assertIsDisplayed()
        custom2ListItem.assertDoesNotExist()
        custom3ListItem.assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun serverList_UpdatesOn_SelectedServerDeletion() {
        androidComposeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                selectedListItem = serverList[2],
            )
        }

        val prodListItem = androidComposeTestRule.onNode(prodRowCd)
        val sandboxListItem = androidComposeTestRule.onNode(sandboxRowCd)
        val customListItem = androidComposeTestRule.onNode(customsRowCd)
        val customListItemRadioButton = customListItem.onChildAt(0)
        val customListItemRemoveButton = customListItem.onChildAt(1)
        val customListItemDeleteButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__server_delete_content_description)
        )

        prodListItem.onChild().assertIsNotSelected()
        sandboxListItem.onChild().assertIsNotSelected()
        customListItemRadioButton.assertIsSelected()

        // Click Remove
        customListItemRemoveButton.performClick()
        customListItemDeleteButton.assertExists()
        customListItemDeleteButton.assertIsDisplayed()

        // Click Delete
        customListItemDeleteButton.performClick()

        // Assert Visuals - First server should now be selected.
        prodListItem.assertIsDisplayed()
        prodListItem.onChild().assertIsSelected()
        prodListItem.assertIsDisplayed()
        sandboxListItem.onChild().assertIsNotSelected()
        customListItem.assertDoesNotExist()
    }

    // endregion
    // region Account Picker Tests

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun accountPickerHeader_Displays_Correctly() {
        androidComposeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.UserAccountPicker,

            )
        }

        val picker = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__account_picker_content_description)
        )
        val closeButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__server_close_button_content_description)
        )
        val backButton = androidComposeTestRule.onNodeWithContentDescription(
            androidComposeTestRule.activity.getString(R.string.sf__back_button_content_description)
        )
        val accountSelectorText = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(R.string.sf__account_selector_text)
        )
        val addAccountButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(R.string.sf__add_new_account)
        )

        picker.assertIsDisplayed()
        backButton.assertDoesNotExist()
        accountSelectorText.assertIsDisplayed()
        closeButton.isDisplayed()
        addAccountButton.isDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun userPicker_NewAccountButtonClick_CallsAddNewAccount() {
        var addAccountCalled = false
        androidComposeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.UserAccountPicker,
                addNewAccount = { addAccountCalled = true },
            )
        }

        val addNewAccountButton = androidComposeTestRule.onNodeWithText(
            androidComposeTestRule.activity.getString(R.string.sf__add_new_account)
        )

        // Close Picker
        addNewAccountButton.performClick()

        // Assert Cancel Called
        Assert.assertTrue("addNewAccount not called.", addAccountCalled)
    }

    // endregion
}