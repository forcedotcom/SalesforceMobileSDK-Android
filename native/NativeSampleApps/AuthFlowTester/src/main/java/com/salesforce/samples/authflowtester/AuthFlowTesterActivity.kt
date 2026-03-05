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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.migrateRefreshToken
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.JwtAccessToken
import com.salesforce.androidsdk.config.OAuthConfig
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.ui.SalesforceActivity
import com.salesforce.androidsdk.ui.theme.sfDarkColors
import com.salesforce.androidsdk.ui.theme.sfLightColors
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport
import com.salesforce.samples.authflowtester.components.InfoSection
import com.salesforce.samples.authflowtester.components.JwtTokenView
import com.salesforce.samples.authflowtester.components.OAuthConfigurationView
import com.salesforce.samples.authflowtester.components.UserCredentialsView
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val PADDING = 10
const val INNER_CARD_PADDING = 16
const val CORNER_SHAPE = 12
const val SPINNER_STROKE_WIDTH = 2
const val BOTTOM_BAR_SPACING = 20
const val HALF_ALPHA = 0.5f
const val RESPONSE_CARD_HEIGHT = 250
const val ICON_SIZE = 24
const val JWT = "jwt"
const val CONSUMER_JSON_KEY = "remoteConsumerKey"
const val REDIRECT_JSON_KEY = "oauthRedirectURI"
const val SCOPE_JSON_KEY = "scopes"
const val CONSUMER_KEY_LABEL = "Consumer Key"
const val REDIRECT_LABEL = "Callback URL"
const val SCOPES_LABEL = "Scopes (space-separated)"

// For UI Tests
const val TITLE_CONTENT_DESC = "app_title"
const val REVOKE_BUTTON_CONTENT_DESC = "revoke_button"
const val REQUEST_BUTTON_CONTENT_DESC = "request_button"
const val CREDS_SECTION_CONTENT_DESC = "user_creds_section"
const val JWT_SECTION_CONTENT_DESC = "jwt_section"
const val OAUTH_SECTION_CONTENT_DESC = "oauth_config_section"
const val MIGRATE_TOKEN_BUTTON_CONTENT_DESC = "migrate_refresh_token_button"
const val MIGRATE_USER_RADIO_CONTENT_DESC = "migrate_user_radio"
const val ALERT_TITLE_CONTENT_DESC = "alert_title"
const val ALERT_POSITIVE_BUTTON_CONTENT_DESC = "alert_positive"
const val SCROLL_CONTAINER_CONTENT_DESC = "scroll_container"

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
        var showLogoutDialog by remember { mutableStateOf(false) }
        var showMigrateBottomSheet by remember { mutableStateOf(false) }
        val isPreview = LocalInspectionMode.current
        val context = LocalContext.current
        val currentUser = remember {
            mutableStateOf(
                value = if (isPreview) null else UserAccountManager.getInstance().currentUser,
                // UserAccount's equals() function only compares userId and orgId.
                policy = neverEqualPolicy(),
            )
        }
        val jwtTokenInUse by remember { derivedStateOf { currentUser.value?.tokenFormat == JWT } }

        // Set current user when it changes to update UI.
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    currentUser.value = UserAccountManager.getInstance().currentUser
                }
            }
            val filter = IntentFilter(UserAccountManager.USER_SWITCH_INTENT_ACTION)
            filter.addAction(ClientManager.ACCESS_TOKEN_REFRESH_INTENT)
            filter.addAction(ClientManager.INSTANCE_URL_UPDATE_INTENT)
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            onDispose {
                context.unregisterReceiver(receiver)
            }
        }

        Scaffold(
            topBar = { TesterUITopBar(topScrollBehavior) },
            bottomBar = {
                if (isPreview) {
                    TesterUIBottomBar(bottomScrollBehavior, {}, {}, {})
                } else {
                    with(SalesforceSDKManager.getInstance()) {
                        return@with TesterUIBottomBar(
                            bottomScrollBehavior,
                            tokenMigrationAction = { showMigrateBottomSheet = true },
                            switchUserAction = {
                                appContext.startActivity(Intent(
                                    appContext,
                                    accountSwitcherActivityClass
                                ).apply {
                                    flags = FLAG_ACTIVITY_NEW_TASK
                                })
                            },
                            logoutAction = { showLogoutDialog = true },
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
                    .semantics { contentDescription = SCROLL_CONTAINER_CONTENT_DESC }
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Spacer(Modifier.height(innerPadding.calculateTopPadding()))
                RevokeButtonCard()
                RequestButtonCard()

                // Use key() to force recomposition when tokens change (UserAccount.equals only compares userId/orgId)
                key(currentUser.value?.authToken, currentUser.value?.refreshToken) {
                    UserCredentialsView(currentUser.value)

                    if (jwtTokenInUse) {
                        currentUser.value?.authToken?.let { token ->
                            JwtTokenView(jwtToken = JwtAccessToken(token))
                        }
                    }
                }

                OAuthConfigurationView()

                Spacer(Modifier.height(innerPadding.calculateBottomPadding()))
            }
        }

        if (showLogoutDialog) {
            @Suppress("AssignedValueIsNeverRead")
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text(stringResource(R.string.logout)) },
                text = { Text(stringResource(R.string.logout_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            SalesforceSDKManager.getInstance().logout(null)
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.logout),
                            color = colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showLogoutDialog = false },
                        modifier = Modifier.semantics { contentDescription = ALERT_POSITIVE_BUTTON_CONTENT_DESC },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        if (showMigrateBottomSheet) {
            @Suppress("AssignedValueIsNeverRead")
            MigrateAppBottomSheet(
                onDismiss = {
                    Log.i("bptest", "Migration complete \n -> currentUser.value: ${currentUser.value!!.username}" +
                            "\n -> Mgr Current User: ${UserAccountManager.getInstance().currentUser.username}")
                    currentUser.value = UserAccountManager.getInstance().currentUser
                    showMigrateBottomSheet = false
                }
            )
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

        Card(
            modifier = Modifier.padding((INNER_CARD_PADDING/2).dp),
            shape = RoundedCornerShape(CORNER_SHAPE.dp),
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        revokeInProgress = true
                        val res = revokeAccessTokenAction(client)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            response = res
                            revokeInProgress = false
                            showAlertDialog = true
                        }
                    }
                },
                enabled = !revokeInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PADDING.dp)
                    .semantics{ contentDescription = REVOKE_BUTTON_CONTENT_DESC },
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                shape = RoundedCornerShape(CORNER_SHAPE.dp),
            ) {
                if (revokeInProgress) {
                    Row {
                        CircularProgressIndicator(
                            modifier = Modifier.size(INNER_CARD_PADDING.dp),
                            strokeWidth = SPINNER_STROKE_WIDTH.dp,
                        )
                        Spacer(Modifier.width(INNER_CARD_PADDING.dp))
                        Text(
                            text = stringResource(R.string.revoking),
                            color = colorScheme.onSurface,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.revoke_access_token),
                        color = colorScheme.onError,
                    )
                }
            }
        }

        if (showAlertDialog) {
            val title = when {
                response == null -> stringResource(R.string.no_response)
                response?.success == true -> stringResource(R.string.revoke_successful)
                else -> stringResource(R.string.revoke_failed)
            }

            @Suppress("AssignedValueIsNeverRead")
            AlertDialog(
                onDismissRequest = { showAlertDialog = false },
                title = {
                    Text(
                        text = title,
                        modifier = Modifier.semantics { contentDescription = ALERT_TITLE_CONTENT_DESC },
                    )
                },
                text = { Text(
                    text = response?.displayValue ?: "",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) },
                confirmButton = {
                    TextButton(
                        onClick = { showAlertDialog = false },
                        modifier = Modifier.semantics { contentDescription = ALERT_POSITIVE_BUTTON_CONTENT_DESC },
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                },
                shape = RoundedCornerShape(CORNER_SHAPE.dp),
            )
        }
    }

    @Composable
    fun RequestButtonCard(
        initialProgressState: Boolean = false, // Only used for UI Previews
        initialResponse: RequestResult? = null, // Only used for UI Previews
    ) {
        val coroutineScope = rememberCoroutineScope()
        var requestInProgress by remember { mutableStateOf(initialProgressState) }
        var response: RequestResult? by remember { mutableStateOf(initialResponse) }
        var showAlertDialog by remember { mutableStateOf(false) }

        Card(modifier = Modifier.padding((INNER_CARD_PADDING/2).dp)) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        response = null
                        requestInProgress = true
                        val res = makeRestRequest(client, ApiVersionStrings.VERSION_NUMBER)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            response = res
                            requestInProgress = false
                            showAlertDialog = true
                        }
                    }
                },
                enabled = !requestInProgress,
                modifier = Modifier.fillMaxWidth()
                    .padding(PADDING.dp)
                    .semantics{ contentDescription = REQUEST_BUTTON_CONTENT_DESC },
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.tertiary),
                shape = RoundedCornerShape(CORNER_SHAPE.dp),
            ) {
                if (requestInProgress) {
                    Row {
                        CircularProgressIndicator(
                            modifier = Modifier.size(INNER_CARD_PADDING.dp),
                            strokeWidth = SPINNER_STROKE_WIDTH.dp,
                        )
                        Spacer(Modifier.width(INNER_CARD_PADDING.dp))
                        Text(
                            text = stringResource(R.string.making_request),
                            color = colorScheme.onSurface,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.make_rest_api_request),
                        color = colorScheme.onTertiary,
                    )
                }
            }

            if (response != null) {
                Column(modifier = Modifier.padding(INNER_CARD_PADDING.dp)) {
                    val noResponseString = stringResource(R.string.no_response)
                    InfoSection(title = stringResource(R.string.response_details), defaultExpanded = false) {
                        val textColor = colorScheme.onSurface.toArgb()
                        // This is necessary to prevent scrolling from affecting Top/BottomAppBar behavior.
                        AndroidView(
                            modifier = Modifier
                                .padding(INNER_CARD_PADDING.dp)
                                .height(RESPONSE_CARD_HEIGHT.dp)
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
                                    text = response?.response ?: noResponseString
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showAlertDialog) {
            val title = when {
                response == null -> stringResource(R.string.no_response)
                response?.success == true -> stringResource(R.string.request_successful)
                else -> stringResource(R.string.request_failed)
            }

            @Suppress("AssignedValueIsNeverRead")
            AlertDialog(
                onDismissRequest = { showAlertDialog = false },
                title = {
                    Text(
                        text = title,
                        modifier = Modifier.semantics { contentDescription = ALERT_TITLE_CONTENT_DESC },
                    )
                },
                text = { Text(
                    text = response?.displayValue ?: "",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) },
                confirmButton = {
                    TextButton(onClick = { showAlertDialog = false }) {
                        Text(
                            stringResource(R.string.ok),
                            modifier = Modifier.semantics { contentDescription = ALERT_POSITIVE_BUTTON_CONTENT_DESC },
                        )
                    }
                },
                shape = RoundedCornerShape(CORNER_SHAPE.dp),
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MigrateAppBottomSheet(onDismiss: () -> Unit) {
        var consumerKey by remember { mutableStateOf("") }
        var callbackUrl by remember { mutableStateOf("") }
        var scopes by remember { mutableStateOf("") }
        val validInput = consumerKey.isNotBlank() && callbackUrl.isNotBlank()
        var showJsonImportDialog by remember { mutableStateOf(false) }
        var migrationInProgress by remember { mutableStateOf(false) }
        var migrationError: String? by remember { mutableStateOf(null) }
        val clipboard = LocalClipboard.current
        val context = LocalContext.current
        val isPreview = LocalInspectionMode.current
        val userAccountManager = if (isPreview) null else UserAccountManager.getInstance()
        var selectedUser by remember { mutableStateOf(userAccountManager?.currentUser) }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState =  rememberStandardBottomSheetState(
                initialValue = SheetValue.Expanded,
                skipHiddenState = false,
            ),
            dragHandle = null,
            shape = RoundedCornerShape(CORNER_SHAPE.dp),
            containerColor = colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(INNER_CARD_PADDING.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(PADDING.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(ICON_SIZE.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.close_content_description),
                        )
                    }

                    Text(
                        text = stringResource(R.string.migrate_app_title),
                        fontSize = 20.sp,
                    )

                    IconButton(
                        onClick = { showJsonImportDialog = true },
                        modifier = Modifier.size(ICON_SIZE.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.json),
                            contentDescription = stringResource(R.string.json_content_description),
                        )
                    }
                }

                userAccountManager?.authenticatedUsers?.takeIf { it.size > 1 }?.let { authenticatedUsers ->
                    authenticatedUsers.forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedUser = user }
                                .padding(horizontal = PADDING.dp, vertical = (PADDING / 2).dp)
                                .semantics {
                                    contentDescription = MIGRATE_USER_RADIO_CONTENT_DESC + user.username
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedUser == user,
                                onClick = { selectedUser = user },
                            )
                            Spacer(Modifier.width((PADDING / 2).dp))
                            Column {
                                Text(
                                    text = user.displayName ?: user.username ?: "",
                                    fontWeight = FontWeight.Medium,
                                )
                                user.username?.let {
                                    Text(
                                        text = it,
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = consumerKey,
                    onValueChange = {consumerKey = it},
                    label = { Text(CONSUMER_KEY_LABEL) },
                    singleLine = true,
                    shape = RoundedCornerShape(CORNER_SHAPE.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = (INNER_CARD_PADDING/2).dp),
                )

                OutlinedTextField(
                    value = callbackUrl,
                    onValueChange = {callbackUrl = it},
                    label = { Text(REDIRECT_LABEL) },
                    singleLine = true,
                    shape = RoundedCornerShape(CORNER_SHAPE.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = (INNER_CARD_PADDING/2).dp),
                )

                OutlinedTextField(
                    value = scopes,
                    onValueChange = {scopes = it},
                    label = { Text(SCOPES_LABEL) },
                    singleLine = true,
                    shape = RoundedCornerShape(CORNER_SHAPE.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = (INNER_CARD_PADDING/2).dp),
                )

                Button(
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = (INNER_CARD_PADDING/2).dp)
                        .semantics { contentDescription = MIGRATE_TOKEN_BUTTON_CONTENT_DESC },
                    shape = RoundedCornerShape(CORNER_SHAPE.dp),
                    enabled = validInput && !migrationInProgress,
                    onClick = {
                        migrationInProgress = true

                        userAccountManager?.migrateRefreshToken(
                            userAccount = selectedUser,
                            appConfig = OAuthConfig(
                                consumerKey = consumerKey,
                                redirectUri = callbackUrl,
                            ),
                            onMigrationSuccess = {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@AuthFlowTesterActivity,
                                        resources.getString(R.string.migration_success),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    onDismiss.invoke()
                                }
                            },
                            onMigrationError = { error, errorDesc, e ->
                                runOnUiThread {
                                    migrationInProgress = false
                                    migrationError = error +
                                            (errorDesc?.let { " \n\nDesc: $it" } ?: "") +
                                            (e?.let { "\n\nThrowable: $it" } ?: "")
                                }
                            },
                        )
                    },
                ) {
                    if (migrationInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(INNER_CARD_PADDING.dp),
                            strokeWidth = SPINNER_STROKE_WIDTH.dp,
                        )
                        Spacer(Modifier.width(INNER_CARD_PADDING.dp))
                        Text(
                            text = stringResource(R.string.migration_in_progress),
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onErrorContainer,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.migrate_button),
                            fontWeight = if (validInput) FontWeight.Normal else FontWeight.Medium,
                            color = if (validInput) colorScheme.onPrimary else colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        if (showJsonImportDialog) {
            var jsonInput by remember { mutableStateOf(
                // Default to clipboard text, if available.
                value = clipboard.nativeClipboard.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(/* index = */ 0)
                    ?.coerceToText(context)
                    ?.toString() ?: ""
            ) }
            val alertBody = context.getString(
                /* resId = */ R.string.json_import,
                /* ...formatArgs = */ CONSUMER_JSON_KEY, REDIRECT_JSON_KEY, SCOPE_JSON_KEY,
            )

            @Suppress("AssignedValueIsNeverRead")
            AlertDialog(
                onDismissRequest = { showJsonImportDialog = false },
                title = { Text(stringResource(R.string.import_config)) },
                text = {
                    Column {
                        Text(text = alertBody)
                        Spacer(modifier = Modifier.height(INNER_CARD_PADDING.dp))
                        OutlinedTextField(
                            value = jsonInput,
                            onValueChange = { jsonInput = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            try {
                                val jsonObject = Json.parseToJsonElement(jsonInput).jsonObject
                                jsonObject[CONSUMER_JSON_KEY]?.jsonPrimitive?.content?.let {
                                    consumerKey = it
                                }
                                jsonObject[REDIRECT_JSON_KEY]?.jsonPrimitive?.content?.let {
                                    callbackUrl = it
                                }
                                jsonObject[SCOPE_JSON_KEY]?.jsonPrimitive?.content?.let {
                                    scopes = it
                                }
                            } catch (_: Exception) { }
                            showJsonImportDialog = false
                        },
                        modifier = Modifier.semantics { contentDescription = ALERT_POSITIVE_BUTTON_CONTENT_DESC },
                    ) {
                        Text(stringResource(R.string.import_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showJsonImportDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                shape = RoundedCornerShape(CORNER_SHAPE.dp),
            )
        }

        migrationError?.let { errorMessage ->
            @Suppress("AssignedValueIsNeverRead")
            AlertDialog(
                onDismissRequest = { migrationError = null },
                title = { Text(stringResource(R.string.migration_failure)) },
                text = { Text(text = errorMessage) },
                confirmButton = {
                    TextButton(onClick = { migrationError = null }) {
                        Text("Ok")
                    }
                },
                shape = RoundedCornerShape(CORNER_SHAPE.dp),
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TesterUITopBar(scrollBehavior: TopAppBarScrollBehavior) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.semantics{ contentDescription = TITLE_CONTENT_DESC }
                )
            },
            colors = TopAppBarColors(
                containerColor = colorScheme.background,
                scrolledContainerColor = colorScheme.background.copy(alpha = HALF_ALPHA),
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
                    modifier = Modifier.fillMaxWidth().padding(BOTTOM_BAR_SPACING.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = tokenMigrationAction) {
                        Icon(
                            painterResource(R.drawable.key),
                            contentDescription = stringResource(R.string.migrate_access_token),
                        )
                    }
                    IconButton(onClick = switchUserAction) {
                        Icon(
                            painterResource(R.drawable.person_add),
                            contentDescription = stringResource(R.string.switch_user),
                        )
                    }
                    IconButton(onClick = logoutAction) {
                        Icon(
                            painterResource(R.drawable.logout),
                            contentDescription = stringResource(R.string.logout),
                        )
                    }
                }
            },
            containerColor = colorScheme.background.copy(alpha = HALF_ALPHA),
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

    @RequiresApi(Build.VERSION_CODES.S)
    @ExcludeFromJacocoGeneratedReport
    @Preview
    @Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun MigrateAppPreview() {
        val scheme = if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            dynamicLightColorScheme(LocalContext.current)
        }
        MaterialTheme(scheme) {
            MigrateAppBottomSheet {  }
        }
    }

    @ExcludeFromJacocoGeneratedReport
    @Preview
    @Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun MigrateAppFallbackThemePreview() {
        val scheme = if (isSystemInDarkTheme()) {
            sfDarkColors()
        } else {
            sfLightColors()
        }
        MaterialTheme(scheme) {
            MigrateAppBottomSheet {  }
        }
    }
}