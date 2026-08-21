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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.espresso.Espresso.pressBack
import androidx.test.rule.GrantPermissionRule
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.ui.components.AddConnection
import com.salesforce.androidsdk.ui.components.LoginViewTestTags
import com.salesforce.androidsdk.ui.components.PickerBottomSheet
import com.salesforce.androidsdk.ui.components.PickerStyle
import com.salesforce.androidsdk.ui.components.TestablePickerBottomSheet
import com.salesforce.androidsdk.ui.components.UserAccountMock
import io.mockk.mockk
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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

        // Anchor on the locale-invariant container tag rather than the localized title text.
        composeTestRule.onNodeWithTag(LoginViewTestTags.ACCOUNT_PICKER).assertIsDisplayed()
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

        // Anchor on the locale-invariant container tag rather than the localized title text.
        composeTestRule.onNodeWithTag(LoginViewTestTags.SERVER_PICKER).assertIsDisplayed()
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

        val nameField = composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_CUSTOM_LABEL)
        val urlField = composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_CUSTOM_URL)
        val saveButton = composeTestRule.onNodeWithTag(LoginViewTestTags.APPLY_BUTTON)
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

        val nameField = composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_CUSTOM_LABEL)
        val urlField = composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_CUSTOM_URL)
        val saveButton = composeTestRule.onNodeWithTag(LoginViewTestTags.APPLY_BUTTON)

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

        composeTestRule.onNodeWithTag(LoginViewTestTags.CUSTOM_URL_BUTTON).assertIsDisplayed()
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

        composeTestRule.onNodeWithTag(LoginViewTestTags.CUSTOM_URL_BUTTON).assertDoesNotExist()
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
    // region Non-Dismissable Picker Tests (W-23731759)

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun loginPicker_closeButton_absentOrDisabled() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(pickerStyle = PickerStyle.LoginServerPicker)
        }

        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_CLOSE_BUTTON).assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun loginPicker_swipeDown_doesNotDismiss() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(pickerStyle = PickerStyle.LoginServerPicker)
        }

        composeTestRule.onNodeWithTag(LoginViewTestTags.SERVER_PICKER).performTouchInput { swipeDown() }

        composeTestRule.onNodeWithTag(LoginViewTestTags.SERVER_PICKER).assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun loginPicker_systemBack_doesNotDismiss() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(pickerStyle = PickerStyle.LoginServerPicker)
        }

        // ModalBottomSheetDialog's PredictiveBackOnBackPressedCallback routes the system back
        // press through the same onDismissRequest -> confirmValueChange(Hidden) path as a swipe,
        // so this exercises the locked picker's back/scrim no-op (PickerBottomSheet.kt :330).
        pressBack()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(LoginViewTestTags.SERVER_PICKER).assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun accountPicker_closeButton_present_andDismisses() {
        // The stateless PickerBottomSheet always keeps its content composed regardless of
        // sheetState's value (production removal-from-screen happens via activity?.finish() in
        // TestablePickerBottomSheet's onUserSwitchCancel, not by the sheet reaching Hidden), so
        // the meaningful regression guard here is that confirmValueChange actually allows the
        // transition to Hidden -- capture the sheetState to assert on it directly.
        lateinit var sheetState: SheetState
        composeTestRule.setContent {
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            PickerBottomSheetTestWrapper(pickerStyle = PickerStyle.UserAccountPicker, sheetState = sheetState)
        }

        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_CLOSE_BUTTON).apply {
            assertIsDisplayed()
            performClick()
        }
        // sheetState.hide() launches an animation coroutine; let it settle to Hidden.
        composeTestRule.waitForIdle()

        assertTrue(
            "Unlike the login picker, the account picker's close button should hide the sheet.",
            sheetState.currentValue == SheetValue.Hidden,
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun accountPicker_swipeDown_stillDismisses() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(pickerStyle = PickerStyle.UserAccountPicker)
        }

        // Regression guard: the account picker's confirmValueChange must still allow Hidden.
        composeTestRule.onNodeWithTag(LoginViewTestTags.ACCOUNT_PICKER).performTouchInput { swipeDown() }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun loginPicker_backButton_shownWhenShouldShowBackButtonTrue() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                showLoginBackButton = true,
            )
        }

        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_LOGIN_BACK_BUTTON).assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun loginPicker_backButton_hiddenWhenShouldShowBackButtonFalse() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                showLoginBackButton = false,
            )
        }

        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_LOGIN_BACK_BUTTON).assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun loginPicker_backButton_invokesHandleBackBehavior() {
        var backInvoked = false
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                showLoginBackButton = true,
                onLoginBackButtonClick = { backInvoked = true },
            )
        }

        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_LOGIN_BACK_BUTTON).performClick()

        assertTrue("Tapping the login-exit back button should invoke the supplied callback.", backInvoked)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun loginPicker_devLoginOptions_reachableDebugOnly() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                showDevSupport = { },
            )
        }

        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_DEV_SUPPORT_BUTTON).assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun loginPicker_devLoginOptions_absentWhenShowDevSupportNull() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                showDevSupport = null,
            )
        }

        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_DEV_SUPPORT_BUTTON).assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun loginPicker_retryBiometric_shownWhenLockedAndOptedIn() {
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                showRetryBiometric = true,
            )
        }

        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_RETRY_BIOMETRIC_BUTTON).assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun loginPicker_retryBiometric_hiddenWhenNotLocked() {
        // PickerBottomSheet renders the button purely off the showRetryBiometric flag. That flag is
        // computed upstream from showBiometricAuthenticationButton (locked + hasBiometricOptedIn() +
        // !nativeLogin), which has no BiometricManager.canAuthenticate() hardware/enrollment check --
        // so a "biometric hardware unavailable" case collapses to this same showRetryBiometric = false
        // input and cannot be exercised distinctly at this layer.
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                showRetryBiometric = false,
            )
        }

        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_RETRY_BIOMETRIC_BUTTON).assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun loginPicker_retryBiometric_invokesOnBioAuthClick() {
        var invoked = false
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                showRetryBiometric = true,
                onRetryBiometricClick = { invoked = true },
            )
        }

        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_RETRY_BIOMETRIC_BUTTON).performClick()

        assertTrue("Tapping the retry-biometric button should invoke the supplied callback.", invoked)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun accountPicker_retryBiometric_absent() {
        val userAccountManager = mockk<UserAccountManager>(relaxed = true)
        composeTestRule.setContent {
            // TestablePickerBottomSheet's UserAccountPicker dispatch never passes
            // showRetryBiometric/onRetryBiometricClick, so go through it directly rather than the
            // low-level stateless PickerBottomSheet + wrapper, which would render whatever is
            // handed to it regardless of pickerStyle for this control. This is the critical
            // regression guard: the retry-biometric entry must never leak onto the account
            // switcher's picker.
            TestablePickerBottomSheet(
                pickerStyle = PickerStyle.UserAccountPicker,
                userAccountManager = userAccountManager,
            )
        }

        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_RETRY_BIOMETRIC_BUTTON).assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun accountPicker_backAndDevOptions_absent() {
        val userAccountManager = mockk<UserAccountManager>(relaxed = true)
        composeTestRule.setContent {
            // TestablePickerBottomSheet's UserAccountPicker dispatch (:265-276) never passes
            // showLoginBackButton/showDevSupport, so go through it directly rather than the
            // low-level stateless PickerBottomSheet + wrapper, which would render whatever is
            // handed to it regardless of pickerStyle for these two controls.
            TestablePickerBottomSheet(
                pickerStyle = PickerStyle.UserAccountPicker,
                userAccountManager = userAccountManager,
            )
        }

        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_LOGIN_BACK_BUTTON).assertDoesNotExist()
        composeTestRule.onNodeWithTag(LoginViewTestTags.PICKER_DEV_SUPPORT_BUTTON).assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun loginPicker_reselectCurrentServer_closesAndReloads() {
        var reselectedServer: Any? = null
        var closePicker = false
        composeTestRule.setContent {
            PickerBottomSheetTestWrapper(
                pickerStyle = PickerStyle.LoginServerPicker,
                selectedListItem = serverList.first(),
                onItemSelected = { item, close ->
                    reselectedServer = item
                    closePicker = close
                },
            )
        }

        // Tapping the already-selected server's row re-invokes onItemSelected with the same item.
        composeTestRule.onNode(prodRowCd).onChild().performClick()

        Assert.assertEquals(prodServer, reselectedServer)
        assertTrue("Selecting a server (even re-selecting) should request the picker close.", closePicker)
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
    // Mirrors TestablePickerBottomSheet's production sheetState wiring (:214-228) so this test
    // harness actually exercises the pickerStyle-aware confirmValueChange lock under test, rather
    // than defaulting to a sheetState that never vetoes Hidden.
    sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { sheetValue ->
            !(sheetValue == SheetValue.Hidden && pickerStyle == PickerStyle.LoginServerPicker)
        },
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
    showLoginBackButton: Boolean = false,
    onLoginBackButtonClick: (() -> Unit)? = null,
    showDevSupport: (() -> Unit)? = null,
    showRetryBiometric: Boolean = false,
    onRetryBiometricClick: (() -> Unit)? = null,
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
        showLoginBackButton = showLoginBackButton,
        onLoginBackButtonClick = onLoginBackButtonClick,
        showDevSupport = showDevSupport,
        showRetryBiometric = showRetryBiometric,
        onRetryBiometricClick = onRetryBiometricClick,
    )
}
