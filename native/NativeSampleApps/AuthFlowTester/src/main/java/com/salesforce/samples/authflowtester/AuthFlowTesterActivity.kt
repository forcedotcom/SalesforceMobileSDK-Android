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

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.ui.SalesforceActivity
import com.salesforce.androidsdk.ui.theme.sfDarkColors
import com.salesforce.androidsdk.ui.theme.sfLightColors
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport
import kotlinx.coroutines.launch

const val PADDING = 10
const val CORNER_PERCENTAGE = 25

class AuthFlowTesterActivity : SalesforceActivity() {
    private var client: RestClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(colorScheme = getColorScheme()) {
                TesterUI()
            }
        }
    }

    override fun onResume(client: RestClient?) {
        // Keeping reference to rest client
        this.client = client
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TesterUI() {
        val topScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val bottomScrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()
        var credentialsExpanded by remember { mutableStateOf(false) }
        var oauthConfigExpanded by remember { mutableStateOf(false) }
        val isPreview = LocalInspectionMode.current
        var currentUser by remember { mutableStateOf(
            value = if (isPreview) {
                null
            } else {
                UserAccountManager.getInstance().currentUser
            }
        ) }

        Scaffold(
            topBar = { TesterUITopBar(topScrollBehavior) },
            bottomBar = {
                if (isPreview) {
                    TesterUIBottomBar(bottomScrollBehavior, {}, {}, {})
                } else {
                    with(SalesforceSDKManager.getInstance()) {
                        return@with TesterUIBottomBar(
                            bottomScrollBehavior,
                            tokenMigrationAction = {},
                            switchUserAction = {
                                appContext.startActivity(Intent(
                                    appContext,
                                    accountSwitcherActivityClass
                                ).apply {
                                    flags = FLAG_ACTIVITY_NEW_TASK
                                })
                            },
                            logoutAction = { logout(null) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(start = PADDING.dp, end = PADDING.dp)
                    .nestedScroll(topScrollBehavior.nestedScrollConnection)
                    .nestedScroll(bottomScrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Spacer(Modifier.height(innerPadding.calculateTopPadding()))
                RevokeButtonCard()
                RequestButtonCard()

                UserCredentialsView(
                    isExpanded = credentialsExpanded,
                    onExpandedChange = { credentialsExpanded = it },
                    currentUser = currentUser
                )

                Spacer(Modifier.height(PADDING.dp))

                // TODO:  Add JWT section

                OAuthConfigurationView(
                    isExpanded = oauthConfigExpanded,
                    onExpandedChange = { oauthConfigExpanded = it },
                )

                Spacer(Modifier.height(innerPadding.calculateBottomPadding()))
            }
        }
    }

    @Composable
    fun RevokeButtonCard(
        initialProgressState: Boolean = false, // Only used for UI Previews
    ) {
        val coroutineScope = rememberCoroutineScope()
        var revokeInProgress by remember { mutableStateOf(initialProgressState) }
        var showAlertDialog by remember { mutableStateOf(false) }
        var response: RequestResult? by remember { mutableStateOf(null) }

        Card(modifier = Modifier.padding(PADDING.dp)) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        revokeInProgress = true
                        response = revokeAccessTokenAction(client)
                        revokeInProgress = false
                        showAlertDialog = true
                    }
                },
                enabled = !revokeInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PADDING.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                shape = RoundedCornerShape(percent = CORNER_PERCENTAGE),
            ) {
                if (revokeInProgress) {
                    Row {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Revoking...",
                            color = colorScheme.onSurface,
                        )
                    }
                } else {
                    Text(
                        text = "Revoke Access Token",
                        color = colorScheme.onError,
                    )
                }
            }
        }

        if (showAlertDialog) {
            val title = when {
                response == null -> "No Response"
                response?.success == true -> "Revoke Successful"
                else -> "Revoke Failed"
            }

            AlertDialog(
                onDismissRequest = { showAlertDialog = false },
                title = { Text(title) },
                text = { Text(
                    text = response?.displayValue ?: "",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) },
                confirmButton = {
                    TextButton(onClick = { showAlertDialog = false }) {
                        Text("OK")
                    }
                },
            )
        }
    }

    @Composable
    fun RequestButtonCard(
        initialProgressState: Boolean = false, // Only used for UI Previews
        initialResponse: RequestResult? = null, // Only used for UI Previews
        initialResponseExpanded: Boolean = false, // Only used for UI Previews
    ) {
        val coroutineScope = rememberCoroutineScope()
        var requestInProgress by remember { mutableStateOf(initialProgressState) }
        var response: RequestResult? by remember { mutableStateOf(initialResponse) }
        var responseExpanded by remember { mutableStateOf(initialResponseExpanded) }
        var showAlertDialog by remember { mutableStateOf(false) }

        Card(modifier = Modifier.padding(PADDING.dp)) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        response = null
                        requestInProgress = true
                        response = makeRestRequest(client, ApiVersionStrings.VERSION_NUMBER)
                        requestInProgress = false
                        showAlertDialog = true
                    }
                },
                enabled = !requestInProgress,
                modifier = Modifier.fillMaxWidth().padding(PADDING.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.tertiary),
                shape = RoundedCornerShape(percent = CORNER_PERCENTAGE),
            ) {
                if (requestInProgress) {
                    Row {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Making Request...",
                            color = colorScheme.onSurface,
                        )
                    }
                } else {
                    Text(
                        text = "Make REST API Request",
                        color = colorScheme.onTertiary,
                    )
                }
            }

            if (response != null) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoSection(
                        title = "Response Details",
                        isExpanded = responseExpanded,
                        onExpandedChange = { change -> responseExpanded = change }
                    ) {
                        val textColor = colorScheme.onSurface.toArgb()
                        // This is necessary to prevent scrolling from affecting Top/BottomAppBar behavior.
                        AndroidView(
                            modifier = Modifier
                                .padding(16.dp)
                                .height(250.dp)
                                .clipToBounds(),
                            factory = { context ->
                                ScrollView(context).apply {
                                    addView(TextView(context).apply {
                                        setTextColor(textColor)
                                    })
                                }
                            },
                            update = { view ->
                                (view.getChildAt(0) as TextView).apply {
                                    text = response?.response ?: "No Response"
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showAlertDialog) {
            val title = when {
                response == null -> "No Response"
                response?.success == true -> "Request Successful"
                else -> "Request Failed"
            }

            AlertDialog(
                onDismissRequest = { showAlertDialog = false },
                title = { Text(title) },
                text = { Text(
                    text = response?.displayValue ?: "",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) },
                confirmButton = {
                    TextButton(onClick = { showAlertDialog = false }) {
                        Text("OK")
                    }
                },
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TesterUITopBar(scrollBehavior: TopAppBarScrollBehavior) {
        CenterAlignedTopAppBar(
            title = { Text("AuthFlowTester") },
            colors = TopAppBarColors(
                containerColor = colorScheme.background,
                scrolledContainerColor = colorScheme.background.copy(alpha = 0.5f),
                navigationIconContentColor = colorScheme.onBackground,
                titleContentColor = colorScheme.onBackground,
                actionIconContentColor = colorScheme.onBackground,
            ),
            scrollBehavior = scrollBehavior,
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TesterUIBottomBar(
        scrollBehavior: BottomAppBarScrollBehavior,
        tokenMigrationAction: () -> Unit,
        switchUserAction: () -> Unit,
        logoutAction: () -> Unit,
    ) {
        BottomAppBar(
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = tokenMigrationAction) {
                        Icon(
                            painterResource(R.drawable.key),
                            contentDescription = "migrate access token",
                        )
                    }
                    IconButton(onClick = switchUserAction) {
                        Icon(
                            painterResource(R.drawable.groups),
                            contentDescription = "switch user",
                        )
                    }
                    IconButton(onClick = logoutAction) {
                        Icon(
                            painterResource(R.drawable.logout),
                            contentDescription = "logout",
                        )
                    }
                }
            },
            containerColor = colorScheme.background.copy(alpha = 0.5f),
            scrollBehavior = scrollBehavior,
        )
    }

    @Composable
    fun getColorScheme(): ColorScheme {
        return with(SalesforceSDKManager.getInstance()) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (isDarkTheme) {
                        dynamicDarkColorScheme(this@AuthFlowTesterActivity)
                    } else {
                        dynamicLightColorScheme(this@AuthFlowTesterActivity)
                    }
                }
                else -> {
                    if (isDarkTheme) sfDarkColors() else sfLightColors()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @ExcludeFromJacocoGeneratedReport
    @Preview
    @Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun RevokeButtonCardActivePreview() {
        val scheme = if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            dynamicLightColorScheme(LocalContext.current)
        }
        MaterialTheme(scheme) {
            RevokeButtonCard(initialProgressState = true)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @ExcludeFromJacocoGeneratedReport
    @Preview
    @Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun RequestButtonCardActivePreview() {
        val scheme = if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            dynamicLightColorScheme(LocalContext.current)
        }
        MaterialTheme(scheme) {
            RequestButtonCard(
                initialProgressState = true,
                initialResponse = RequestResult(
                    success = true,
                    displayValue = "",
                    response = "Preview Request Response!",
                ),
                initialResponseExpanded = true,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @ExcludeFromJacocoGeneratedReport
    @Preview
    @Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun TesterUIPreview() {
        val scheme = if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            dynamicLightColorScheme(LocalContext.current)
        }
        MaterialTheme(scheme) {
            TesterUI()
        }
    }

    @ExcludeFromJacocoGeneratedReport
    @Preview
    @Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun RevokeButtonCardFallbackThemePreview() {
        val scheme = if (isSystemInDarkTheme()) {
            sfDarkColors()
        } else {
            sfLightColors()
        }
        MaterialTheme(scheme) {
            RevokeButtonCard(initialProgressState = true)
        }
    }

    @ExcludeFromJacocoGeneratedReport
    @Preview
    @Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun RequestButtonCardFallbackThemePreview() {
        val scheme = if (isSystemInDarkTheme()) {
            sfDarkColors()
        } else {
            sfLightColors()
        }
        MaterialTheme(scheme) {
            RequestButtonCard(
                initialProgressState = true,
                initialResponse = RequestResult(
                    success = true,
                    displayValue = "",
                    response = "Preview Request Response!",
                ),
                initialResponseExpanded = true,
            )
        }
    }

    @ExcludeFromJacocoGeneratedReport
    @Preview
    @Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun TesterUIFallbackThemePreview() {
        val scheme = if (isSystemInDarkTheme()) {
            sfDarkColors()
        } else {
            sfLightColors()
        }
        MaterialTheme(scheme) {
            TesterUI()
        }
    }
}