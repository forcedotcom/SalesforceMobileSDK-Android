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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginServerBottomSheet() {
    val viewModel: LoginViewModel = viewModel(factory = SalesforceSDKManager.getInstance().loginViewModelFactory)
    val sheetState = rememberModalBottomSheetState()
    val loginServerManager = SalesforceSDKManager.getInstance().loginServerManager

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.showServerPicker.value = false
        },
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        var editing by remember { mutableStateOf(false) }

        // Sheet content
        if (editing) {
            var name by remember { mutableStateOf("") }
            var url by remember { mutableStateOf("") }

            IconButton(onClick = { editing = false }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier =  Modifier
                    .padding(start = 15.dp, end = 15.dp, top = 15.dp)
                    .fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Url") },
                modifier =  Modifier
                    .padding(start = 15.dp, end = 15.dp, bottom = 15.dp)
                    .fillMaxWidth(),
            )
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val serverUrl = viewModel.getValidServerUrl(url)
                        if (serverUrl == null) {
                            // TODO: error invalid url
                        } else {
                            loginServerManager.addCustomLoginServer(serverUrl, url)
                            editing = false
                        }
                    } else {
                        // TODO: error invalid label
                    }
                },
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                colors = ButtonColors(
                    containerColor = Color.Black,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.Black,
                    disabledContentColor = Color.Black
                )
            ) {
                Text(text = "Save", color = Color.White)
            }
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Change Server",
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.padding(10.dp))
                loginServerManager.loginServers.forEach { server ->
                    HorizontalDivider(thickness = 1.dp)
                    LoginServerListItem(server)
                }

                TextButton(
                    modifier = Modifier.padding(bottom = 20.dp),
                    onClick = {
                        editing = true
                    }
                ) {
                    Text(
                        text = "Add New Connection",
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}