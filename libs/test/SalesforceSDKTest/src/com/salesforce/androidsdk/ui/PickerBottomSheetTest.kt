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

import android.Manifest
import android.os.Build
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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.rule.GrantPermissionRule
import com.salesforce.androidsdk.R.string.sf__account_selector_text
import com.salesforce.androidsdk.R.string.sf__custom_url_button
import com.salesforce.androidsdk.R.string.sf__pick_server
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.ui.components.AddConnection
import com.salesforce.androidsdk.ui.components.PickerBottomSheet
import com.salesforce.androidsdk.ui.components.PickerStyle
import com.salesforce.androidsdk.ui.components.TestablePickerBottomSheet
import com.salesforce.androidsdk.ui.components.UserAccountMock
import io.mockk.mockk
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

@VisibleForTesting
internal val prodServer = LoginServer(TEST_NAME, VALID_URL, false)
@VisibleForTesting
internal val sandboxServer = LoginServer("Sandbox", "https://test.salesforce.com", false)
@VisibleForTesting
internal val customServer = LoginServer("Custom", "https://mobilesdk.my.salesforce.com", true)
@VisibleForTesting
internal val serverList = listOf(prodServer, sandboxServer, customServer)
@VisibleForTesting
internal val prodRowCd = (hasText(prodServer.name) and hasText(prodServer.url))
@VisibleForTesting
internal val sandboxRowCd = (hasText(sandboxServer.name) and hasText(sandboxServer.url))

private val user1 = UserAccountMock("user1", VALID_URL)
private val user2 = UserAccountMock("user2", sandboxServer.url)
private val userList = listOf(user1, user2)

@VisibleForTesting
internal val customsRowCd = (hasText(customServer.name) and hasText(customServer.url))

class PickerBottomSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // TODO: Remove if when min SDK version is 33
    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    /* This call will print the semantic tree: composeTestRule.onAllNodes(isRoot()).printToLog("", 10) */

    private val customsRowCd = (hasText(customServer.name) and hasText(customServer.url))
    private val user1RowCd = (hasText(user1.displayName) and hasText(user1.loginServer))
    private val user2RowCd = (hasText(user2.displayName) and hasText(user2.loginServer))

    // Google's recommended naming scheme for test is "thingUnderTest_TriggerOfTest_ResultOfTest"

    // region Public API Tests

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun pickerBottomSheet_publicApiUserAccountPicker_displaysUserAccountPicker() {
        val userAccountManager = mockk<UserAccountManager>(relaxed = true)
        composeTestRule.setContent {
            TestablePickerBottomSheet(
                pickerStyle = PickerStyle.UserAccountPicker,
                userAccountManager = userAccountManager
            )
        }

        val context = getInstrumentation().targetContext
        val button = composeTestRule.onNode(hasText(context.getString(sf__account_selector_text)))

        button.assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun pickerBottomSheet_publicApiLoginServerPicker_displaysLoginServerPicker() {
        val userAccountManager = mockk<UserAccountManager>(relaxed = true)
        composeTestRule.setContent {
            TestablePickerBottomSheet(
                pickerStyle = PickerStyle.LoginServerPicker,
                userAccountManager = userAccountManager
            )
        }

        val context = getInstrumentation().targetContext
        val button = composeTestRule.onNode(hasText(context.getString(sf__pick_server)))

        button.assertIsDisplayed()
    }

    // endregion

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
    fun serverList_Displays_FirstServerSelected() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                selectedListItem = serverList.first(),
            )
        }

        val prodListItem = composeTestRule.onNode(prodRowCd)
        val sandboxListItem = composeTestRule.onNode(sandboxRowCd)
        val customListItem = composeTestRule.onNode(customsRowCd)

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
    fun serverList_AddButtonVisibleTrue_DisplaysAddNewConnectionButton() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                addButtonVisible = true,
            )
        }

        val context = getInstrumentation().targetContext
        val button = composeTestRule.onNode(hasText(context.getString(sf__custom_url_button)))

        button.assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun serverList_AddButtonVisibleFalse_HidesAddNewConnectionButton() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                addButtonVisible = false,
            )
        }

        val context = getInstrumentation().targetContext
        val button = composeTestRule.onNode(hasText(context.getString(sf__custom_url_button)))

        button.assertDoesNotExist()
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

        val user1ListItem = composeTestRule.onNode(user1RowCd)
        val user2ListItem = composeTestRule.onNode(user2RowCd)

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
    list: List<Any> = when (pickerStyle) {
        PickerStyle.LoginServerPicker -> serverList
        PickerStyle.UserAccountPicker -> userList
    },
    selectedListItem: Any = list.first(),
    addButtonVisible: Boolean = true,
    onItemSelected: (Any?, Boolean) -> Unit = { _, _ -> },
    getValidServer: ((String) -> String?)? = { _ -> "" },
    addNewLoginServer: ((String, String) -> Unit)? = { _, _ -> },
    removeLoginServer: ((LoginServer) -> Unit)? = { },
    addNewAccount: (() -> Unit)? = { },
) {
    PickerBottomSheet(
        pickerStyle = pickerStyle,
        sheetState = sheetState,
        list = list,
        selectedListItem = selectedListItem,
        addButtonVisible = addButtonVisible,
        onItemSelected = onItemSelected,
        getValidServer = getValidServer,
        addNewLoginServer = addNewLoginServer,
        removeLoginServer = removeLoginServer,
        addNewAccount = addNewAccount,
    )
}
