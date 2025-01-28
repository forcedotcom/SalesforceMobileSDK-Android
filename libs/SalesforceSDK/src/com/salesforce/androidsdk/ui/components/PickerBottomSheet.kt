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

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.ui.LoginViewModel

enum class PickerStyle {
    LoginServerPicker,
    UserAccountPicker,
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerBottomSheet(pickerStyle: PickerStyle) {
    val viewModel: LoginViewModel = viewModel(factory = SalesforceSDKManager.getInstance().loginViewModelFactory)
    val sheetState = rememberModalBottomSheetState()
    val loginServerManager = SalesforceSDKManager.getInstance().loginServerManager
    val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
    val onNewLoginServerSelected = { newSelectedServer: Any? ->
        if (newSelectedServer != null && newSelectedServer is LoginServer) {
            viewModel.showServerPicker.value = false
            viewModel.loading.value = true
            viewModel.dynamicBackgroundColor.value = Color.White
            SalesforceSDKManager.getInstance().loginServerManager.selectedLoginServer = newSelectedServer
        }
    }
    val onLoginServerCancel = {
        viewModel.showServerPicker.value = false
    }
    val onUserAccountSelected = { userAccount: Any? ->
        if (userAccount != null && userAccount is UserAccount) {
            userAccountManager.switchToUser(userAccount)
        }
    }
    val onUserSwitchCancel = {
        if (userAccountManager.currentUser == null) {
            userAccountManager.switchToUser(userAccountManager.authenticatedUsers.first())
        }
    }

    when(pickerStyle) {
        PickerStyle.LoginServerPicker ->
            PickerBottomSheet(
                pickerStyle,
                sheetState,
                list = loginServerManager.loginServers,
                selectedListItem = loginServerManager.selectedLoginServer,
                onItemSelected = onNewLoginServerSelected,
                onCancel = onLoginServerCancel
            )

        PickerStyle.UserAccountPicker ->
            PickerBottomSheet(
                pickerStyle,
                sheetState,
                list = userAccountManager.authenticatedUsers,
                selectedListItem = userAccountManager.currentAccount,
                onItemSelected = onUserAccountSelected,
                onCancel = onUserSwitchCancel
            )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerBottomSheet(
    pickerStyle: PickerStyle,
    sheetState: SheetState,
    list: List<Any>,
    selectedListItem: Any?,
    onItemSelected: (Any?) -> Unit,
    onCancel: () -> Unit,
    ) {

    ModalBottomSheet(
        onDismissRequest = { onCancel() },
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(10.dp),
        containerColor = Color.White
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when(pickerStyle) {
                        PickerStyle.LoginServerPicker -> "Change Server"
                        PickerStyle.UserAccountPicker -> "Organizations"
                    },
                    color = Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(
                    onClick = { onCancel() },
                    colors = IconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF747474),  // TODO: fix color
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = Color.Transparent,
                    ),
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp)

            list.forEach { listItem ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        onClickLabel = "Login server selected.",
                        onClick = { onItemSelected(listItem) },
                    )
                ) {
                    RadioButton(
                        selected = (listItem == selectedListItem),
                        onClick = { onItemSelected(listItem) },
                    )

                    when(pickerStyle) {
                        PickerStyle.LoginServerPicker ->
                            if (listItem is LoginServer) {
                                LoginServerListItem(server =  listItem)
                            }
                        PickerStyle.UserAccountPicker -> {
                            if (listItem is UserAccount || listItem is UserAccountMock) {
                                UserAccountListItem(
                                    displayName = (listItem as UserAccountMock).displayName, // TODO: does this crash for user account?
                                    loginServer = listItem.loginServer,
                                    profilePhoto = listItem.profilePhoto?.let { painterResource(it.generationId) },
                                )
                            }
//                            if (listItem is UserAccount) {
//                                UserAccountListItem(
//                                    displayName = listItem .displayName,
//                                    loginServer = listItem.loginServer,
//                                    profilePhoto = listItem.profilePhoto?.let { painterResource(it.generationId) },
//                                )
//                            } else if (listItem is UserAccountMock) {
//                                UserAccountListItem(
//                                    displayName = listItem .displayName,
//                                    loginServer = listItem.loginServer,
//                                    profilePhoto = listItem.profilePhoto?.let { painterResource(it.generationId) },
//                                )
//                            }
                        }
                    }
                }
                HorizontalDivider(
                    thickness = Dp.Hairline,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            OutlinedButton(
                onClick = {  },
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),

                ) {
                Text(
                    text = when(pickerStyle) {
                        PickerStyle.LoginServerPicker -> "Add New Connection"
                        PickerStyle.UserAccountPicker -> "Add New Account"
                    },
                    color = Color(0xFF0B5CAB),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(0.dp),
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun PickerBottomSheetPreview(
    @PreviewParameter(PickerStylePreviewParameterProvider::class) pickerStyle: PickerStyle,
) {
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
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
                onItemSelected = {},
                onCancel = {},
            )
        PickerStyle.UserAccountPicker ->
            PickerBottomSheet(
                pickerStyle = pickerStyle,
                sheetState = sheetState,
                list = userAccountList,
                selectedListItem = userAccountList.first(),
                onItemSelected = {},
                onCancel = {},
            )
    }
}

class PickerStylePreviewParameterProvider : PreviewParameterProvider<PickerStyle> {
    override val values: Sequence<PickerStyle>
        get() = sequenceOf(PickerStyle.LoginServerPicker, PickerStyle.UserAccountPicker)
}

private class UserAccountMock(
    val displayName: String,
    val loginServer: String,
    val profilePhoto: Bitmap?,
)