package com.salesforce.androidsdk.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelectable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.ui.components.AddConnection
import com.salesforce.androidsdk.ui.components.PickerBottomSheet
import com.salesforce.androidsdk.ui.components.PickerStyle
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

private const val NAME_FIELD_IDENTIFIER = "Name"
private const val URL_FIELD_IDENTIFIER = "URL"
private const val SAVE_BUTTON_IDENTIFIER = "Save"
private const val TEST_NAME = "Production"
private const val VALID_URL = "login.salesforce.com"
private const val INVALID_URL = "invalid"
private const val WITH_VALIDATION = "_with_validation"
private const val LOGIN_SERVER_DESC = "Login Server List Item"

class PickerBottomSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

//    @get:Rule
//    val androidComponentRule = createAndroidComposeRule<ComponentActivity>()

    private val prodServer = LoginServer(TEST_NAME, VALID_URL, false)
    private val sandboxServer = LoginServer("Sandbox", "test.salesforce.com", false)
    private val customServer = LoginServer("Custom", "mobilesdk.my.salesforce.com", true)
    private val prodRow = (hasContentDescription(LOGIN_SERVER_DESC)
            and hasText(prodServer.name) and hasText(prodServer.url))
    private val sandboxRow = (hasContentDescription(LOGIN_SERVER_DESC)
            and hasText(sandboxServer.name) and hasText(sandboxServer.url))
    private val customsRow = (hasContentDescription(LOGIN_SERVER_DESC)
            and hasText(customServer.name) and hasText(customServer.url))


    @Test
    fun testAddServerValidation() {
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
    fun testAddServer() {
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun testSwitchServer() {
        val serverList = listOf(prodServer, sandboxServer, customServer)
        var selectedServer = serverList.first()
        val onServerSelected = { server: Any?, _: Boolean ->
            selectedServer = server as LoginServer
        }

        composeTestRule.setContent {
            PickerBottomSheet(
                pickerStyle = PickerStyle.LoginServerPicker,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                list = serverList,
                selectedListItem = selectedServer,
                onItemSelected = onServerSelected,
                onCancel = { },
                getValidServer = { _ -> "" },
                addNewLoginServer = { _,_ -> },
                removeLoginServer = { },
                addNewAccount = { },
            )
        }
        composeTestRule.onAllNodes(isRoot()).printToLog("", 10)

        val prodListItem = composeTestRule.onNode(prodRow)
        val sandboxListItem = composeTestRule.onNode(sandboxRow)
        val customListItem = composeTestRule.onNode(customsRow)

        prodListItem.assertExists()
        prodListItem.assertHasClickAction()
        prodListItem.onChild().assertIsSelectable()
        prodListItem.onChild().assertIsSelected()
        sandboxListItem.assertExists()
        sandboxListItem.assertHasClickAction()
        sandboxListItem.onChild().assertIsSelectable()
        sandboxListItem.onChild().assertIsNotSelected()
        customListItem.assertExists()
        customListItem.assertHasClickAction()
//        customListItem.onChild().assertIsSelectable()
//        customListItem.onChild().assertIsNotSelected()
        Assert.assertEquals(prodServer, selectedServer)

        // Change server
        sandboxListItem.onChild().performClick()

        Assert.assertEquals(sandboxServer, selectedServer)
        // Assert change
//        prodListItem.onChild().assertIsNotSelected()
//        sandboxListItem.onChild().assertIsSelected()
//        customListItem.onChild().assertIsNotSelected()
    }
}