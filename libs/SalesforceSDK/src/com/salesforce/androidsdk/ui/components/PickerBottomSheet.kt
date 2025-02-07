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
package com.salesforce.androidsdk.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.ui.LoginViewModel
import kotlinx.coroutines.launch

enum class PickerStyle {
    LoginServerPicker,
    UserAccountPicker,
}

@VisibleForTesting
internal const val PICKER_CD = "Picker"
@VisibleForTesting
internal const val CLOSE_BUTTON_CD = "Close"
@VisibleForTesting
internal const val BACK_BUTTON_CD = "Back"
@VisibleForTesting
internal const val ADD_NEW_BUTTON_CD = "Add"


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerBottomSheet(pickerStyle: PickerStyle) {
    val viewModel: LoginViewModel = viewModel(factory = SalesforceSDKManager.getInstance().loginViewModelFactory)
    val loginServerManager = SalesforceSDKManager.getInstance().loginServerManager
    val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
    val activity = LocalContext.current.getActivity()
    val onNewLoginServerSelected = { newSelectedServer: Any?, closePicker: Boolean ->
        if (newSelectedServer != null && newSelectedServer is LoginServer) {
            viewModel.showServerPicker.value = !closePicker
            viewModel.loading.value = true
            viewModel.dynamicBackgroundColor.value = Color.White
            SalesforceSDKManager.getInstance().loginServerManager.selectedLoginServer = newSelectedServer
        }
    }
    val onLoginServerCancel = { viewModel.showServerPicker.value = false }
    val onUserAccountSelected = { userAccount: Any?, _: Boolean ->
        if (userAccount != null && userAccount is UserAccount) {
            activity?.finish()
            userAccountManager.switchToUser(userAccount)
        }
    }
    val onUserSwitchCancel = {
        activity?.finish()
        if (userAccountManager.currentUser == null) {
            userAccountManager.switchToUser(userAccountManager.authenticatedUsers.first())
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { sheetValue ->
            if (sheetValue == SheetValue.Hidden) {
                when (pickerStyle) {
                    PickerStyle.LoginServerPicker -> onLoginServerCancel()
                    PickerStyle.UserAccountPicker -> onUserSwitchCancel()
                }
            }

            true
        }
    )
    val addNewLoginServer = { name: String, url: String ->
        loginServerManager.addCustomLoginServer(name, url)
        viewModel.showServerPicker.value = false
        viewModel.loading.value = true
        viewModel.dynamicBackgroundColor.value = Color.White
    }

    when(pickerStyle) {
        PickerStyle.LoginServerPicker ->
            PickerBottomSheet(
                pickerStyle,
                sheetState,
                list = loginServerManager.loginServers,
                selectedListItem = loginServerManager.selectedLoginServer,
                onItemSelected = onNewLoginServerSelected,
                getValidServer = { serverUrl: String -> viewModel.getValidServerUrl(serverUrl) },
                addNewLoginServer = addNewLoginServer,
                removeLoginServer = { server: LoginServer -> loginServerManager.removeServer(server) }
            )

        PickerStyle.UserAccountPicker ->
            PickerBottomSheet(
                pickerStyle,
                sheetState,
                list = userAccountManager.authenticatedUsers,
                selectedListItem = userAccountManager.currentUser,
                onItemSelected = onUserAccountSelected,
                addNewAccount = {
                    userAccountManager.switchToNewUser()
                    activity?.finish()
                },
            )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@VisibleForTesting
internal fun PickerBottomSheet(
    pickerStyle: PickerStyle,
    sheetState: SheetState,
    list: List<Any>,
    selectedListItem: Any?,
    onItemSelected: (Any?, Boolean) -> Unit,
    getValidServer: ((String) -> String?)? = null,
    addNewLoginServer: ((String, String) -> Unit)? = null,
    removeLoginServer: ((LoginServer) -> Unit)? = null,
    addNewAccount: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = { /* Do nothing */ },
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(9.dp),
        containerColor = Color(0xFFFFFFFF),
    ) {
        var addingNewServer by remember { mutableStateOf(false) }
        val sfRipple = RippleConfiguration(color = Color(0xFF0B5CAB))
        val mutableList = remember { list.toMutableStateList() }
        var mutableSelectedListItem = selectedListItem

        Column(modifier = Modifier.animateContentSize().semantics { contentDescription = PICKER_CD }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Add Connection Back Arrow
                AnimatedVisibility(
                    visible = addingNewServer,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(
                        onClick = { addingNewServer = false },
                        colors = IconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFF747474),
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = Color.Transparent,
                        ),
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = BACK_BUTTON_CD,
                        )
                    }
                }
                // Picker Title Text
                Text(
                    text = when (pickerStyle) {
                        PickerStyle.LoginServerPicker -> {
                            if (addingNewServer) {
                                stringResource(R.string.sf__server_url_add_title)
                            } else {
                                stringResource(R.string.sf__pick_server)
                            }
                        }
                        PickerStyle.UserAccountPicker -> stringResource(R.string.sf__account_selector_text)
                    },
                    color = Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                // Close Button
                val coroutineScope = rememberCoroutineScope()
                IconButton(
                    onClick = { coroutineScope.launch { sheetState.hide() } },
                    colors = IconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF747474),
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = Color.Transparent,
                    ),
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = CLOSE_BUTTON_CD,
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = Color(0xFFE5E5E5))

            Crossfade(
                targetState = addingNewServer,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = LinearEasing
                ),
            ) { showAddConnection ->
                when(showAddConnection) {
                    // Login Server Add Connection
                    true -> {
                        AddConnection(
                            getValidServer = getValidServer,
                            addNewLoginServer = { newServerName: String, newServerUrl: String ->
                                addingNewServer = false
                                addNewLoginServer?.invoke(newServerName, newServerUrl)
                            }
                        )
                    }
                    // Login Server or User Account List
                    false -> {
                        Column(
                            modifier = Modifier.scrollable(
                                state = rememberScrollState(),
                                orientation = Orientation.Vertical,
                            ),
                        ) {
                            LazyColumn(modifier = Modifier.animateContentSize()) {
                                items(items = mutableList, key = { it.toString() }) { listItem ->
                                    val selected = (listItem == mutableSelectedListItem)
                                    Row(
                                        modifier = Modifier.animateItem(
                                            placementSpec = tween(),
                                            fadeOutSpec = tween(500),
                                        )
                                    ) {
                                        when (pickerStyle) {
                                            PickerStyle.LoginServerPicker ->
                                                if (listItem is LoginServer) {
                                                    LoginServerListItem(
                                                        server = listItem,
                                                        selected = selected,
                                                        onItemSelected = onItemSelected,
                                                        removeServer = { server: LoginServer ->
                                                            if (selected) {
                                                                mutableSelectedListItem = list.first()
                                                                onItemSelected(list.first(), false)
                                                            }

                                                            mutableList.remove(listItem)
                                                            removeLoginServer?.let { it(server) }
                                                        }
                                                    )
                                                }

                                            PickerStyle.UserAccountPicker -> {
                                                if (listItem is UserAccount) {
                                                    UserAccountListItem(
                                                        displayName = listItem.displayName,
                                                        loginServer = listItem.loginServer,
                                                        selected = selected,
                                                        onItemSelected = { onItemSelected(listItem, true) },
                                                        profilePhoto = listItem.profilePhoto?.let { painterResource(it.generationId) },
                                                    )
                                                /*
                                                 TODO: Remove this mock when a UserAccount can be created in without
                                                 SalesforceSDKManger (for previews).  This would be trivial with an
                                                 internal constructor if the class was converted to Kotlin.
                                                */
                                                } else if (listItem is UserAccountMock) {
                                                    UserAccountListItem(
                                                        displayName = listItem.displayName,
                                                        loginServer = listItem.loginServer,
                                                        selected = selected,
                                                        onItemSelected = { onItemSelected(listItem, true) },
                                                        profilePhoto = listItem.profilePhoto?.let { painterResource(it.generationId) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        color = Color(0xFFE5E5E5),
                                    )

                                    // Add New Connection/Account Button
                                    if (listItem == mutableList.last()) {
                                        CompositionLocalProvider(LocalRippleConfiguration provides sfRipple) {
                                            OutlinedButton(
                                                onClick = {
                                                    when (pickerStyle) {
                                                        PickerStyle.LoginServerPicker -> addingNewServer = true
                                                        PickerStyle.UserAccountPicker -> addNewAccount?.invoke()
                                                    }
                                                },
                                                modifier = Modifier
                                                    .padding(12.dp)
                                                    .fillMaxWidth()
                                                    .semantics { contentDescription = ADD_NEW_BUTTON_CD },
                                                shape = RoundedCornerShape(9.dp),
                                                border = BorderStroke(1.dp, Color(0xFFc9c9c9)),
                                            ) {
                                                Text(
                                                    text = when (pickerStyle) {
                                                        PickerStyle.LoginServerPicker -> stringResource(R.string.sf__custom_url_button)
                                                        PickerStyle.UserAccountPicker -> stringResource(R.string.sf__add_new_account)
                                                    },
                                                    color = Color(0xFF0B5CAB),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@VisibleForTesting
internal fun AddConnection(
    getValidServer: ((String) -> String?)? = null,
    addNewLoginServer: ((String, String) -> Unit)? = null,
    previewName: String = "",
    previewUrl: String = "",
) {
    var name by remember { mutableStateOf(previewName) }
    var url by remember { mutableStateOf(previewUrl) }
    val focusRequester = remember { FocusRequester() }
    val sfTextSection = TextSelectionColors(
        handleColor = Color(0xFF0176D3),
        backgroundColor = Color(0xFF0176D3).copy(alpha = 0.2f),
    )

    Column {
        CompositionLocalProvider(LocalTextSelectionColors provides sfTextSection) {
            // Name input field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.sf__server_url_default_custom_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp)
                    .focusRequester(focusRequester),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color(0xFF0176D3),
                    focusedLabelColor = Color(0xFF0176D3),
                    focusedTextColor = Color(0xFF181818),
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color(0xFF939393),
                    unfocusedLabelColor = Color(0xFF939393),
                    unfocusedContainerColor = Color.Transparent,
                    unfocusedTextColor = Color(0xFF747474),
                    cursorColor = Color(0xFF0176D3),
                ),
            )
            // Url input field
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.sf__server_url_default_custom_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color(0xFF0176D3),
                    focusedLabelColor = Color(0xFF0176D3),
                    focusedTextColor = Color(0xFF181818),
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color(0xFF939393),
                    unfocusedLabelColor = Color(0xFF939393),
                    unfocusedContainerColor = Color.Transparent,
                    unfocusedTextColor = Color(0xFF747474),
                    cursorColor = Color(0xFF0176D3),
                ),
            )
        }

        val trimmedName = name.trim()
        val trimmedUrl = url.trim()
        val serverUrl = getValidServer?.let { it(trimmedUrl) }
        val validInput = trimmedName.isNotBlank() && serverUrl != null
        Button(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(9.dp),
            colors = ButtonColors(
                containerColor = Color(0xFF0176D3),
                contentColor = Color(0xFF0176D3),
                disabledContainerColor = Color(0xFFE5E5E5),
                disabledContentColor = Color(0xFFE5E5E5),
            ),
            enabled = validInput,
            onClick = { addNewLoginServer?.let { it(trimmedName, serverUrl!!) } },
        ) {
            Text(
                text = stringResource(R.string.sf__server_url_save),
                fontWeight = if (validInput) FontWeight.Normal else FontWeight.Medium,
                color = if (validInput) Color(0xFFFFFFFF) else Color(0xFF747474),
                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
            )
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

// Get access to host activity from within Compose.  tail rec makes this safe.
private tailrec fun Context.getActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

@Preview("Default", showBackground = true)
@Composable
private fun AddConnectionPreview() {
    Column {
        AddConnection()
    }
}

@Preview("Values", showBackground = true)
@Composable
private fun AddConnectionValuesPreview() {
    Column {
        AddConnection(
            getValidServer = { server: String -> server },
            previewName = "New Server",
            previewUrl = "https://login.salesforce.com"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun PickerBottomSheetLongListPreview(
    @PreviewParameter(PickerStylePreviewParameterProvider::class) pickerStyle: PickerStyle,
) {
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Expanded, skipHiddenState = true)
    val serverList = listOf(
        LoginServer("Production", "https://login.salesforce.com", false),
        LoginServer("Sandbox", "https://test.salesforce.com", false),
        LoginServer("Custom", "https://mobilesdk.my.salesforce.com", true),
        LoginServer("Production2", "https://login.salesforce.com", false),
        LoginServer("Sandbox2", "https://test.salesforce.com", false),
        LoginServer("Custom2", "https://mobilesdk.my.salesforce.com", true),
        LoginServer("Production3", "https://login.salesforce.com", false),
        LoginServer("Sandbox3", "https://test.salesforce.com", false),
        LoginServer("Custom3", "https://mobilesdk.my.salesforce.com", true),
    )
    val userAccountList = listOf(
        UserAccountMock("Test User", "https://login.salesforce.com", null),
        UserAccountMock("Second User", "https://mobilesdk.my.salesforce.com", null),
        UserAccountMock("Third User", "https://login.salesforce.com", null),
        UserAccountMock("Forth User", "https://mobilesdk.my.salesforce.com", null),
        UserAccountMock("Fifth User", "https://login.salesforce.com", null),
        UserAccountMock("Sixth User", "https://mobilesdk.my.salesforce.com", null),
        UserAccountMock("Seventh User", "https://login.salesforce.com", null),
        UserAccountMock("Eighth User", "https://mobilesdk.my.salesforce.com", null),
    )

    when(pickerStyle) {
        PickerStyle.LoginServerPicker ->
            PickerBottomSheet(
                pickerStyle = pickerStyle,
                sheetState = sheetState,
                list = serverList,
                selectedListItem = serverList[1],
                onItemSelected = { _,_ -> },
            )
        PickerStyle.UserAccountPicker ->
            PickerBottomSheet(
                pickerStyle = pickerStyle,
                sheetState = sheetState,
                list = userAccountList,
                selectedListItem = userAccountList.first(),
                onItemSelected = { _,_ -> },
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreenSizes
@Preview
@Composable
private fun PickerBottomSheetPreview(
    @PreviewParameter(PickerStylePreviewParameterProvider::class) pickerStyle: PickerStyle,
) {
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Expanded, skipHiddenState = true)
    val serverList = listOf(
        LoginServer("Production", "https://login.salesforce.com", false),
        LoginServer("Sandbox", "https://test.salesforce.com", false),
        LoginServer("Custom", "https://mobilesdk.my.salesforce.com", true),
    )
    val userAccountList = listOf(
        UserAccountMock("Test User", "https://login.salesforce.com", null),
        UserAccountMock("Second User", "https://mobilesdk.my.salesforce.com", null),
    )

    when(pickerStyle) {
        PickerStyle.LoginServerPicker ->
            PickerBottomSheet(
                pickerStyle = pickerStyle,
                sheetState = sheetState,
                list = serverList,
                selectedListItem = serverList[1],
                onItemSelected = { _,_ -> },
            )
        PickerStyle.UserAccountPicker ->
            PickerBottomSheet(
                pickerStyle = pickerStyle,
                sheetState = sheetState,
                list = userAccountList,
                selectedListItem = userAccountList.first(),
                onItemSelected = { _,_ -> },
            )
    }
}

class PickerStylePreviewParameterProvider : PreviewParameterProvider<PickerStyle> {
    override val values: Sequence<PickerStyle>
        get() = sequenceOf(PickerStyle.LoginServerPicker, PickerStyle.UserAccountPicker)
}

internal class UserAccountMock(
    val displayName: String,
    val loginServer: String,
    val profilePhoto: Bitmap? = null,
)