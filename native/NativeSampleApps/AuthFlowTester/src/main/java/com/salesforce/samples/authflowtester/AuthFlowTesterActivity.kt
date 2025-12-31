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
package com.salesforce.samples.authflowtester

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.ui.CollapsibleSection
import com.salesforce.androidsdk.ui.SalesforceActivity
import com.salesforce.androidsdk.ui.theme.sfDarkColors
import com.salesforce.androidsdk.ui.theme.sfLightColors

const val PADDING = 10
const val CORNER_PERCENTAGE = 25

class AuthFlowTesterActivity : SalesforceActivity() {
    private var client: RestClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TesterUI()
        }
    }

    override fun onResume(client: RestClient?) {
        // Keeping reference to rest client
        this.client = client
    }

    @Preview
    @Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun TesterUI(
        revokeAction: suspend (client: RestClient) -> RequestResult = { RequestResult(false, "") },
        apiRequestAction: () -> Unit = {},
        credentialsDataList: List<Pair<String, String>> = emptyList(),
        authConfigDataList: List<Pair<String, String>> = emptyList(),
    ) {
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
            Scaffold(
                topBar = { TesterUITopBar() },
                bottomBar = { TesterUIBottomBar() },
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding)
                        .padding(start = PADDING.dp, end = PADDING.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    Card(modifier = Modifier.padding(PADDING.dp)) {
                        Button(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().padding(PADDING.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                            shape = RoundedCornerShape(percent = CORNER_PERCENTAGE),
                        ) {
                            Text("Revoke Access Token")
                        }
                    }

                    Card(modifier = Modifier.padding(PADDING.dp)) {
                        Button(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().padding(PADDING.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.tertiary),
                            shape = RoundedCornerShape(percent = CORNER_PERCENTAGE),
                        ) {
                            Text("Make REST API Request")
                        }
                    }

                    CollapsibleSection("User Credentials", credentialsDataList)

                    Spacer(Modifier.height(PADDING.dp))

                    CollapsibleSection("OAuth Configuration", authConfigDataList)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Preview
    @Composable
    fun TesterUITopBar() {
        CenterAlignedTopAppBar(
            title = { Text("AuthFlowTester") },
        )
    }

    @Preview
    @Composable
    fun TesterUIBottomBar() {
        BottomAppBar(
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = "migrate",
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Default.AccountBox,
                            contentDescription = "switch user",
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "logout",
                        )
                    }
                }
            },
            containerColor = Color(android.graphics.Color.TRANSPARENT),
        )
    }
}