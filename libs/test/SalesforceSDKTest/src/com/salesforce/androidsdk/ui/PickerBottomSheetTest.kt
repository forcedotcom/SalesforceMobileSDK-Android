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

import androidx.annotation.VisibleForTesting
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelectable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.ui.components.ADD_NEW_BUTTON_CD
import com.salesforce.androidsdk.ui.components.AddConnection
import com.salesforce.androidsdk.ui.components.CLOSE_BUTTON_CD
import com.salesforce.androidsdk.ui.components.DELETE_BUTTON_CD
import com.salesforce.androidsdk.ui.components.PICKER_CD
import com.salesforce.androidsdk.ui.components.PickerBottomSheet
import com.salesforce.androidsdk.ui.components.PickerStyle
import com.salesforce.androidsdk.ui.components.USER_ACCOUNT_CD
import com.salesforce.androidsdk.ui.components.UserAccountMock
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

private const val NAME_FIELD_IDENTIFIER = "Name"
private const val URL_FIELD_IDENTIFIER = "URL"
private const val SAVE_BUTTON_IDENTIFIER = "Save"
private const val TEST_NAME = "Production"
private const val VALID_URL = "https://login.salesforce.com"
private const val INVALID_URL = "invalid"
private const val WITH_VALIDATION = "_with_validation"
private const val LOGIN_SERVER_DESC = "Login Server List Item"

private val prodServer = LoginServer(TEST_NAME, VALID_URL, false)
private val sandboxServer = LoginServer("Sandbox", "https://test.salesforce.com", false)
private val customServer = LoginServer("Custom", "https://mobilesdk.my.salesforce.com", true)
private val serverList = listOf(prodServer, sandboxServer, customServer)

private val user1 = UserAccountMock("user1", VALID_URL)
private val user2 = UserAccountMock("user2", sandboxServer.url)
private val userList = listOf(user1, user2)

@VisibleForTesting
internal val customsRowCd = (hasContentDescription(LOGIN_SERVER_DESC)
        and hasText(customServer.name) and hasText(customServer.url))

class PickerBottomSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /* This call will print the semantic tree: composeTestRule.onAllNodes(isRoot()).printToLog("", 10) */

    private val pickerCd = hasContentDescription(PICKER_CD)
    private val closeButtonCd = hasContentDescription(CLOSE_BUTTON_CD)
    private val prodRowCd = (hasContentDescription(LOGIN_SERVER_DESC)
            and hasText(prodServer.name) and hasText(prodServer.url))
    private val sandboxRowCd = (hasContentDescription(LOGIN_SERVER_DESC)
            and hasText(sandboxServer.name) and hasText(sandboxServer.url))
    private val customsRowCd = (hasContentDescription(LOGIN_SERVER_DESC)
            and hasText(customServer.name) and hasText(customServer.url))
    private val user1RowCd = (hasContentDescription(USER_ACCOUNT_CD)
            and hasText(user1.displayName) and hasText(user1.loginServer))
    private val user2RowCd = (hasContentDescription(USER_ACCOUNT_CD)
            and hasText(user2.displayName) and hasText(user2.loginServer))

    // Google's recommended naming scheme for test is "thingUnderTest_TriggerOfTest_ResultOfTest"

    // region Add Connection Tests
    @Test
    fun saveButton_RespondsTo_InputValidation() {
        val serverValidator = { server: String ->
            when (server == VALID_URL) {
                true -> server
                false -> null
            }
        }

        // Start the app
        composeTestRule.setContent {
            AddConnection(getValidServer = serverValidator)
        }

        val nameField = composeTestRule.onNodeWithText(NAME_FIELD_IDENTIFIER)
        val urlField = composeTestRule.onNodeWithText(URL_FIELD_IDENTIFIER)
        val saveButton = composeTestRule.onNodeWithText(SAVE_BUTTON_IDENTIFIER)
        saveButton.assertIsDisplayed()
        saveButton.assertIsNotEnabled()

        nameField.performTextInput(TEST_NAME)
        saveButton.assertIsNotEnabled()

        urlField.performTextInput(INVALID_URL)
        saveButton.assertIsNotEnabled()

        urlField.performTextClearance()
        urlField.performTextInput(VALID_URL)
        saveButton.assertIsEnabled()

        nameField.performTextClearance()
        saveButton.assertIsNotEnabled()
    }

    @Test
    fun newSever_Input_IsValidatedAndAddedToList() {
        val alwaysValid = { server: String -> "${server}${WITH_VALIDATION}" }
        val list = mutableListOf<LoginServer>()
        val mockAddServer = { name: String, url: String -> list.add(LoginServer(name, url, true)); Unit }

        composeTestRule.setContent {
            AddConnection(
                getValidServer = alwaysValid,
                addNewLoginServer = mockAddServer,
            )
        }

        val nameField = composeTestRule.onNodeWithText(NAME_FIELD_IDENTIFIER)
        val urlField = composeTestRule.onNodeWithText(URL_FIELD_IDENTIFIER)
        val saveButton = composeTestRule.onNodeWithText(SAVE_BUTTON_IDENTIFIER)

        nameField.performTextInput(TEST_NAME)
        urlField.performTextInput(VALID_URL)
        Assert.assertTrue(list.isEmpty())

        saveButton.performClick()
        Assert.assertTrue(list.isNotEmpty())
        Assert.assertEquals(1, list.size)
        Assert.assertEquals(TEST_NAME, list.first().name)
        Assert.assertEquals("${VALID_URL}${WITH_VALIDATION}", list.first().url)
    }

    // endregion
    // region Login Server Picker Tests

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun serverPicker_CloseButtonClick_CallsCancel() {
        var cancelCalled = false
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                onCancel = { cancelCalled = true },
            )
        }

        val closeButton = composeTestRule.onNode(closeButtonCd)

        // Close Picker
        closeButton.performClick()

        // Assert Cancel Called
        Assert.assertTrue("onCancel not called.", cancelCalled)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun serverList_Displays_FirstServerSelected() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                selectedListItem = serverList.first(),
            )
        }

        val picker = composeTestRule.onNode(pickerCd)
        val prodListItem = composeTestRule.onNode(prodRowCd)
        val sandboxListItem = composeTestRule.onNode(sandboxRowCd)
        val customListItem = composeTestRule.onNode(customsRowCd)

        picker.assertIsDisplayed()
        prodListItem.assertIsDisplayed()
        prodListItem.onChild().assertIsSelectable()
        prodListItem.onChild().assertIsSelected()
        sandboxListItem.assertIsDisplayed()
        sandboxListItem.onChild().assertIsSelectable()
        sandboxListItem.onChild().assertIsNotSelected()
        customListItem.assertIsDisplayed()
        customListItem.onChildAt(0).assertIsSelectable()
        customListItem.onChildAt(0).assertIsNotSelected()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun serverList_Displays_SecondServerSelected() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                selectedListItem = serverList[1],
            )
        }

        val prodListItem = composeTestRule.onNode(prodRowCd)
        val sandboxListItem = composeTestRule.onNode(sandboxRowCd)
        val customListItem = composeTestRule.onNode(customsRowCd)

        prodListItem.assertIsDisplayed()
        prodListItem.onChild().assertIsSelectable()
        prodListItem.onChild().assertIsNotSelected()
        sandboxListItem.assertIsDisplayed()
        sandboxListItem.onChild().assertIsSelectable()
        sandboxListItem.onChild().assertIsSelected()
        customListItem.assertIsDisplayed()
        customListItem.onChildAt(0).assertIsSelectable()
        customListItem.onChildAt(0).assertIsNotSelected()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun serverList_Displays_CustomServerSelected() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                selectedListItem = serverList[2],
            )
        }

        val prodListItem = composeTestRule.onNode(prodRowCd)
        val sandboxListItem = composeTestRule.onNode(sandboxRowCd)
        val customListItem = composeTestRule.onNode(customsRowCd)

        prodListItem.assertIsDisplayed()
        prodListItem.onChild().assertIsSelectable()
        prodListItem.onChild().assertIsNotSelected()
        sandboxListItem.assertIsDisplayed()
        sandboxListItem.onChild().assertIsSelectable()
        sandboxListItem.onChild().assertIsNotSelected()
        customListItem.assertIsDisplayed()
        customListItem.onChildAt(0).assertIsSelectable()
        customListItem.onChildAt(0).assertIsSelected()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun selectedServer_UpdatesOn_UIServerSelection() {
        var selectedServer = serverList.first()
        val onServerSelected = { server: Any?, _: Boolean ->
            selectedServer = server as LoginServer
        }

        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                selectedListItem = selectedServer,
                onItemSelected = onServerSelected,
            )
        }

        val prodListItem = composeTestRule.onNode(prodRowCd)
        val sandboxListItem = composeTestRule.onNode(sandboxRowCd)

        prodListItem.assertIsDisplayed()
        prodListItem.assertHasClickAction()
        prodListItem.onChild().assertIsSelectable()
        prodListItem.onChild().assertIsSelected()
        sandboxListItem.assertIsDisplayed()
        sandboxListItem.assertHasClickAction()
        sandboxListItem.onChild().assertIsSelectable()
        sandboxListItem.onChild().assertIsNotSelected()
        Assert.assertEquals(prodServer, selectedServer)

        // Change server
        sandboxListItem.onChild().performClick()

        // Assert data source change
        Assert.assertEquals(sandboxServer, selectedServer)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun serverList_UpdatesOn_ServerDeletion() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                selectedListItem = serverList[1],
            )
        }

        val prodListItem = composeTestRule.onNode(prodRowCd)
        val sandboxListItem = composeTestRule.onNode(sandboxRowCd)
        val customListItem = composeTestRule.onNode(customsRowCd)
        val customListItemRadioButton = customListItem.onChildAt(0)
        val customListItemRemoveButton = customListItem.onChildAt(1)
        val customListItemDeleteButton = composeTestRule.onNode(hasContentDescription(DELETE_BUTTON_CD))

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
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                selectedListItem = serverList[1],
                removeLoginServer = removeServer,
            )
        }

        val customListItem = composeTestRule.onNode(customsRowCd)
        val customListItemRemoveButton = customListItem.onChildAt(1)
        val customListItemDeleteButton = composeTestRule.onNode(hasContentDescription(DELETE_BUTTON_CD))

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
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                list = complexServerList,
                selectedListItem = complexServerList[4],
            )
        }

        val prodListItem = composeTestRule.onNode(prodRowCd)
        val sandboxListItem = composeTestRule.onNode(sandboxRowCd)
        val customListItem = composeTestRule.onNode(customsRowCd)
        val customListItemRadioButton = customListItem.onChildAt(0)
        val customListItemRemoveButton = customListItem.onChildAt(1)
        val custom2ListItem = composeTestRule.onNode((hasContentDescription(LOGIN_SERVER_DESC)
                and hasText(customServer2.name) and hasText(customServer2.url)))
        val custom2ListItemRadioButton = custom2ListItem.onChildAt(0)
        val custom2ListItemRemoveButton = custom2ListItem.onChildAt(1)
        val custom2ListItemDeleteButton = composeTestRule.onNode(hasContentDescription(DELETE_BUTTON_CD))
        val custom3ListItem = composeTestRule.onNode((hasContentDescription(LOGIN_SERVER_DESC)
                and hasText(customServer3.name) and hasText(customServer3.url)))
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
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                selectedListItem = serverList[2],
            )
        }

        val prodListItem = composeTestRule.onNode(prodRowCd)
        val sandboxListItem = composeTestRule.onNode(sandboxRowCd)
        val customListItem = composeTestRule.onNode(customsRowCd)
        val customListItemRadioButton = customListItem.onChildAt(0)
        val customListItemRemoveButton = customListItem.onChildAt(1)
        val customListItemDeleteButton = composeTestRule.onNode(hasContentDescription(DELETE_BUTTON_CD))

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
    fun userList_Displays_FirstAccountIsCurrent() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.UserAccountPicker,
                selectedListItem = userList.first(),
            )
        }

        val picker = composeTestRule.onNode(pickerCd)
        val user1ListItem = composeTestRule.onNode(user1RowCd)
        val user2ListItem = composeTestRule.onNode(user2RowCd)

        picker.assertIsDisplayed()
        user1ListItem.assertIsDisplayed()
        user1ListItem.onChild().assertIsSelectable()
        user1ListItem.onChild().assertIsSelected()
        user2ListItem.assertIsDisplayed()
        user2ListItem.onChild().assertIsSelectable()
        user2ListItem.onChild().assertIsNotSelected()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun userList_Displays_SecondAccountIsCurrent() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.UserAccountPicker,
                selectedListItem = userList[1],
            )
        }

        val user1ListItem = composeTestRule.onNode(user1RowCd)
        val user2ListItem = composeTestRule.onNode(user2RowCd)

        user1ListItem.assertIsDisplayed()
        user1ListItem.onChild().assertIsSelectable()
        user1ListItem.onChild().assertIsNotSelected()
        user2ListItem.assertIsDisplayed()
        user2ListItem.onChild().assertIsSelectable()
        user2ListItem.onChild().assertIsSelected()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun userList_Selection_SwitchesUser() {
        var currentUser = userList.first()
        val onUserSelected = { user: Any?, _: Boolean ->
            currentUser = user as UserAccountMock
        }

        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.UserAccountPicker,
                selectedListItem = currentUser,
                onItemSelected = onUserSelected,
            )
        }

        val user1ListItem = composeTestRule.onNode(user1RowCd)
        val user2ListItem = composeTestRule.onNode(user2RowCd)

        user1ListItem.assertIsDisplayed()
        user1ListItem.assertHasClickAction()
        user1ListItem.onChild().assertIsSelectable()
        user1ListItem.onChild().assertIsSelected()
        user2ListItem.assertIsDisplayed()
        user2ListItem.assertHasClickAction()
        user2ListItem.onChild().assertIsSelectable()
        user2ListItem.onChild().assertIsNotSelected()
        Assert.assertEquals(user1, currentUser)

        // Switcher User
        user2ListItem.performClick()

        // Assert data source change
        Assert.assertEquals(user2, currentUser)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun userPicker_CloseButtonClick_CallsCancel() {
        var cancelCalled = false
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.UserAccountPicker,
                onCancel = { cancelCalled = true },
            )
        }

        val closeButton = composeTestRule.onNode(closeButtonCd)

        // Close Picker
        closeButton.performClick()

        // Assert Cancel Called
        Assert.assertTrue("onCancel not called.", cancelCalled)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun userPicker_NewAccountButtonClick_CallsAddNewAccount() {
        var addAccountCalled = false
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.UserAccountPicker,
                addNewAccount = { addAccountCalled = true },
            )
        }

        val addNewAccountButton = composeTestRule.onNode(hasContentDescription(ADD_NEW_BUTTON_CD))

        // Close Picker
        addNewAccountButton.performClick()

        // Assert Cancel Called
        Assert.assertTrue("addNewAccount not called.", addAccountCalled)
    }

    // endregion
}

/**
 * This wrapper makes most component input optional so it is more clear what the test is actually doing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerBottomSheetTestWrapper(
    pickerStyle: PickerStyle,
    sheetState: SheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Expanded,
        skipHiddenState = false,
    ),
    list: List<Any> = when(pickerStyle) {
        PickerStyle.LoginServerPicker -> serverList
        PickerStyle.UserAccountPicker -> userList
    },
    selectedListItem: Any = list.first(),
    onItemSelected: (Any?, Boolean) -> Unit = { _,_ -> },
    onCancel: () -> Unit = { },
    getValidServer: ((String) -> String?)? = { _ -> "" },
    addNewLoginServer: ((String, String) -> Unit)? = { _,_ -> },
    removeLoginServer: ((LoginServer) -> Unit)? = { },
    addNewAccount: (() -> Unit)? = { },
) {
    PickerBottomSheet(pickerStyle, sheetState, list, selectedListItem, onItemSelected, onCancel, getValidServer,
        addNewLoginServer, removeLoginServer, addNewAccount)
}