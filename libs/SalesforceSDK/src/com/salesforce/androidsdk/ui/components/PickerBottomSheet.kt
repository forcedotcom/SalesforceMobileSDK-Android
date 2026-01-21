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
import android.content.res.Configuration
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
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
import com.salesforce.androidsdk.R.string.sf__account_picker_content_description
import com.salesforce.androidsdk.R.string.sf__account_selector_text
import com.salesforce.androidsdk.R.string.sf__add_new_account
import com.salesforce.androidsdk.R.string.sf__back_button_content_description
import com.salesforce.androidsdk.R.string.sf__custom_url_button
import com.salesforce.androidsdk.R.string.sf__pick_server
import com.salesforce.androidsdk.R.string.sf__server_close_button_content_description
import com.salesforce.androidsdk.R.string.sf__server_picker_content_description
import com.salesforce.androidsdk.R.string.sf__server_url_add_title
import com.salesforce.androidsdk.R.string.sf__server_url_default_custom_label
import com.salesforce.androidsdk.R.string.sf__server_url_default_custom_url
import com.salesforce.androidsdk.R.string.sf__server_url_save
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.LoginServerManager
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.ui.LoginViewModel
import com.salesforce.androidsdk.ui.theme.hintTextColor
import com.salesforce.androidsdk.ui.theme.sfDarkColors
import com.salesforce.androidsdk.ui.theme.sfLightColors
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PickerStyle {
    LoginServerPicker,
    UserAccountPicker,
}

internal const val ICON_SIZE = 32
internal const val HEADER_PADDING_SIZE = 16
internal const val TEXT_SELECTION_ALPHA = 0.2f


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerBottomSheet(pickerStyle: PickerStyle) {
    TestablePickerBottomSheet(
        pickerStyle = pickerStyle,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@VisibleForTesting
internal fun TestablePickerBottomSheet(
    pickerStyle: PickerStyle,
    viewModel: LoginViewModel = viewModel(factory = SalesforceSDKManager.getInstance().loginViewModelFactory),
    loginServerManager: LoginServerManager = SalesforceSDKManager.getInstance().loginServerManager,
    userAccountManager: UserAccountManager = SalesforceSDKManager.getInstance().userAccountManager,
    activity: FragmentActivity? = LocalContext.current.getActivity(),
) {
    val onNewLoginServerSelected = { newSelectedServer: Any?, closePicker: Boolean ->
        if (newSelectedServer != null && newSelectedServer is LoginServer) {
            viewModel.showServerPicker.value = !closePicker
            if (newSelectedServer != SalesforceSDKManager.getInstance().loginServerManager.selectedLoginServer) {
                viewModel.loading.value = true
                SalesforceSDKManager.getInstance().loginServerManager.selectedLoginServer = newSelectedServer
            }
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
    }

    when (pickerStyle) {
        PickerStyle.LoginServerPicker ->
            PickerBottomSheet(
                addButtonVisible = viewModel.serverPickerAddConnectionButtonVisible,
                pickerStyle = pickerStyle,
                sheetState = sheetState,
                list = loginServerManager.loginServers,
                selectedListItem = loginServerManager.selectedLoginServer,
                onItemSelected = onNewLoginServerSelected,
                getValidServer = { serverUrl: String -> viewModel.getValidServerUrl(serverUrl) },
                addNewLoginServer = addNewLoginServer,
                removeLoginServer = { server: LoginServer -> loginServerManager.removeServer(server) }
            )

        PickerStyle.UserAccountPicker ->
            PickerBottomSheet(
                pickerStyle = pickerStyle,
                sheetState = sheetState,
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
    addButtonVisible: Boolean = true,
    onItemSelected: (Any?, Boolean) -> Unit,
    getValidServer: ((String) -> String?)? = null,
    addNewLoginServer: ((String, String) -> Unit)? = null,
    removeLoginServer: ((LoginServer) -> Unit)? = null,
    addNewAccount: (() -> Unit)? = null,
) {
    val pickerFocus = remember { FocusRequester() }
    val containerContentDescription = when (pickerStyle) {
        PickerStyle.LoginServerPicker -> stringResource(sf__server_picker_content_description)
        PickerStyle.UserAccountPicker -> stringResource(sf__account_picker_content_description)
    }
    val themeRippleConfiguration = RippleConfiguration(color = colorScheme.onSecondary)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            delay(SLOW_ANIMATION_MS.toLong())
            pickerFocus.requestFocus()
        }
    }

    CompositionLocalProvider(LocalRippleConfiguration provides themeRippleConfiguration) {
        ModalBottomSheet(
            onDismissRequest = { /* Do nothing */ },
            sheetState = sheetState,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = CORNER_RADIUS.dp, topEnd = CORNER_RADIUS.dp),
            containerColor = colorScheme.primaryContainer,
        ) {
            var addingNewServer by remember { mutableStateOf(false) }
            val mutableList = remember { list.pickerDistinctBy().toMutableStateList() }
            var mutableSelectedListItem = selectedListItem

            Column(
                modifier = Modifier
                    .animateContentSize()
                    .semantics { contentDescription = containerContentDescription }
                    .focusRequester(pickerFocus)
                    .focusable(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(HEADER_PADDING_SIZE.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Add Connection Back Arrow
                    AnimatedVisibility(
                        visible = addingNewServer,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        ToolTipWrapper(sf__back_button_content_description) { backButtonDescription ->
                            IconButton(
                                onClick = { addingNewServer = false },
                                colors = IconButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = colorScheme.secondary,
                                    disabledContainerColor = Color.Transparent,
                                    disabledContentColor = Color.Transparent,
                                ),
                                modifier = Modifier.size(ICON_SIZE.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = backButtonDescription,
                                )
                            }
                        }
                    }
                    // Picker Title Text
                    Text(
                        text = when (pickerStyle) {
                            PickerStyle.LoginServerPicker -> {
                                if (addingNewServer) {
                                    stringResource(sf__server_url_add_title)
                                } else {
                                    stringResource(sf__pick_server)
                                }
                            }

                            PickerStyle.UserAccountPicker -> stringResource(sf__account_selector_text)
                        },
                        color = colorScheme.onSecondary,
                        fontSize = HEADER_TEXT_SIZE.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // Close Button
                    ToolTipWrapper(sf__server_close_button_content_description) { closeButtonDescription ->
                        IconButton(
                            onClick = { coroutineScope.launch { sheetState.hide() } },
                            colors = IconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = colorScheme.secondary,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = Color.Transparent,
                            ),
                            modifier = Modifier.size(ICON_SIZE.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = closeButtonDescription,
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = STROKE_WIDTH.dp, color = colorScheme.surfaceVariant)

                Crossfade(
                    targetState = addingNewServer,
                    animationSpec = tween(
                        durationMillis = SLOW_ANIMATION_MS,
                        easing = LinearEasing
                    ),
                ) { showAddConnection ->
                    when (showAddConnection) {
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
                                                fadeOutSpec = tween(SLOW_ANIMATION_MS),
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
                                                            loginServer = listItem.communityUrl ?: listItem.instanceServer,
                                                            selected = selected,
                                                            onItemSelected = { onItemSelected(listItem, true) },
                                                            profilePhoto = listItem.profilePhoto?.let { bitmap ->
                                                                BitmapPainter(bitmap.asImageBitmap())
                                                            },
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
                                                            profilePhoto = listItem.profilePhoto?.let { bitmap ->
                                                                BitmapPainter(bitmap.asImageBitmap())
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        HorizontalDivider(
                                            thickness = STROKE_WIDTH.dp,
                                            modifier = Modifier.padding(horizontal = PADDING_SIZE.dp),
                                            color = colorScheme.surfaceVariant,
                                        )

                                        // Add New Connection/Account Button
                                        if (listItem == mutableList.last() && addButtonVisible) {
                                            OutlinedButton(
                                                onClick = {
                                                    when (pickerStyle) {
                                                        PickerStyle.LoginServerPicker -> addingNewServer = true
                                                        PickerStyle.UserAccountPicker -> addNewAccount?.invoke()
                                                    }
                                                },
                                                modifier = Modifier
                                                    .padding(PADDING_SIZE.dp)
                                                    .fillMaxWidth(),
                                                shape = RoundedCornerShape(CORNER_RADIUS.dp),
                                                contentPadding = PaddingValues(PADDING_SIZE.dp),
                                                border = BorderStroke(STROKE_WIDTH.dp, colorScheme.outline),
                                            ) {
                                                Text(
                                                    text = when (pickerStyle) {
                                                        PickerStyle.LoginServerPicker -> stringResource(
                                                            sf__custom_url_button
                                                        )

                                                        PickerStyle.UserAccountPicker -> stringResource(
                                                            sf__add_new_account
                                                        )
                                                    },
                                                    color = colorScheme.primary,
                                                    fontSize = TEXT_SIZE.sp,
                                                    fontWeight = FontWeight.Medium,
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
        handleColor = colorScheme.tertiary,
        backgroundColor = colorScheme.tertiary.copy(alpha = TEXT_SELECTION_ALPHA),
    )

    Column {
        CompositionLocalProvider(LocalTextSelectionColors provides sfTextSection) {
            // Name input field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(sf__server_url_default_custom_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PADDING_SIZE.dp)
                    .focusRequester(focusRequester),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = colorScheme.tertiary,
                    focusedLabelColor = colorScheme.tertiary,
                    focusedTextColor = colorScheme.onSecondary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = colorScheme.hintTextColor,
                    unfocusedLabelColor = colorScheme.hintTextColor,
                    unfocusedContainerColor = Color.Transparent,
                    unfocusedTextColor = colorScheme.onSecondary,
                    cursorColor = colorScheme.tertiary,
                ),
            )
            // Url input field
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(sf__server_url_default_custom_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = PADDING_SIZE.dp, end = PADDING_SIZE.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = colorScheme.tertiary,
                    focusedLabelColor = colorScheme.tertiary,
                    focusedTextColor = colorScheme.onSecondary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = colorScheme.hintTextColor,
                    unfocusedLabelColor = colorScheme.hintTextColor,
                    unfocusedContainerColor = Color.Transparent,
                    unfocusedTextColor = colorScheme.onSecondary,
                    cursorColor = colorScheme.tertiary,
                ),
            )
        }

        val trimmedName = name.trim()
        val trimmedUrl = url.trim()
        val serverUrl = getValidServer?.let { it(trimmedUrl) }
        val validInput = trimmedName.isNotBlank() && serverUrl != null
        Button(
            modifier = Modifier
                .padding(PADDING_SIZE.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(CORNER_RADIUS.dp),
            contentPadding = PaddingValues(PADDING_SIZE.dp),
            colors = ButtonColors(
                containerColor = colorScheme.tertiary,
                contentColor = colorScheme.tertiary,
                disabledContainerColor = colorScheme.surfaceVariant,
                disabledContentColor = colorScheme.surfaceVariant,
            ),
            enabled = validInput,
            onClick = { addNewLoginServer?.let { it(trimmedName, serverUrl!!) } },
        ) {
            Text(
                text = stringResource(sf__server_url_save),
                fontWeight = if (validInput) FontWeight.Normal else FontWeight.Medium,
                color = if (validInput) colorScheme.onPrimary else colorScheme.onErrorContainer,
            )
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

// Ensure no duplicates in the list because they are not allowed by lazy column.
fun List<Any>.pickerDistinctBy(): List<Any> {
    return distinctBy { listItem ->
        when (listItem) {
            is LoginServer -> with(listItem) { "$name$url" }
            else -> listItem.toString()
        }
    }
}

// Get access to host activity from within Compose.  tail rec makes this safe.
private tailrec fun Context.getActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

@ExcludeFromJacocoGeneratedReport
@Preview("Default", showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun AddConnectionPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        AddConnection()
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview("Values", showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun AddConnectionValuesPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        AddConnection(
            getValidServer = { server: String -> server },
            previewName = "New Server",
            previewUrl = "https://login.salesforce.com"
        )
    }
}


@ExcludeFromJacocoGeneratedReport
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@PreviewScreenSizes
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

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        when (pickerStyle) {
            PickerStyle.LoginServerPicker ->
                PickerBottomSheet(
                    pickerStyle = pickerStyle,
                    sheetState = sheetState,
                    list = serverList,
                    selectedListItem = serverList[1],
                    onItemSelected = { _, _ -> },
                )

            PickerStyle.UserAccountPicker ->
                PickerBottomSheet(
                    pickerStyle = pickerStyle,
                    sheetState = sheetState,
                    list = userAccountList,
                    selectedListItem = userAccountList.first(),
                    onItemSelected = { _, _ -> },
                )
        }
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
