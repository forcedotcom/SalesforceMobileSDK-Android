package com.salesforce.androidsdk.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.R.string.sf__server_url_save
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.config.OAuthConfig
import com.salesforce.androidsdk.ui.components.CORNER_RADIUS
import com.salesforce.androidsdk.ui.components.PADDING_SIZE
import com.salesforce.androidsdk.ui.components.TEXT_SIZE
import com.salesforce.androidsdk.ui.theme.hintTextColor
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport

class LoginOptionsActivity: ComponentActivity() {
    val useWebServer = MutableLiveData(SalesforceSDKManager.getInstance().useWebServerAuthentication)
    val useHybridToken = MutableLiveData(SalesforceSDKManager.getInstance().useHybridAuthentication)

    @OptIn(ExperimentalMaterial3Api::class)
    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        useWebServer.observe(
            /* owner = */ this,
            Observer<Boolean> {
                // onChanged lambda
                value -> SalesforceSDKManager.getInstance().useWebServerAuthentication = value
            },
        )
        useHybridToken.observe(
            /* owner = */ this,
            Observer<Boolean> {
                // onChanged lambda
                value -> SalesforceSDKManager.getInstance().useHybridAuthentication = value
            },
        )

        setContent {
            MaterialTheme(colorScheme = SalesforceSDKManager.getInstance().colorScheme()) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(stringResource(R.string.sf__dev_support_login_options_title))
                            }
                        )
                    }
                ) { innerPadding ->
                    LoginOptionsScreen(
                        innerPadding,
                        useWebServer,
                        useHybridToken,
                        SalesforceSDKManager.getInstance().debugOverrideAppConfig,
                    )
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        SalesforceSDKManager.getInstance().loginDevMenuReload = true
    }
}

@Composable
fun OptionToggle(
    title: String,
    contentDescription: String,
    optionData: MutableLiveData<Boolean>,
) {
    val checked by optionData.observeAsState(initial = false)

    Row(
        modifier = Modifier.fillMaxWidth().padding(PADDING_SIZE.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            modifier = Modifier.height(50.dp).wrapContentHeight(align = Alignment.CenterVertically),
        )
        Switch(
            checked = checked,
            onCheckedChange = { optionData.value = it },
            modifier = Modifier.semantics {
                this.contentDescription = contentDescription
            }
        )
    }
}

@Composable
fun BootConfigView(config: OAuthConfig? = null) {
    var overrideConsumerKey by remember { mutableStateOf(config?.consumerKey ?: "") }
    var overrideRedirectUri by remember { mutableStateOf(config?.redirectUri ?: "") }
    var overrideScopes by remember { mutableStateOf(config?.scopesString ?: "") }
    val consumerKeyFieldDesc = stringResource(R.string.sf__login_options_consumer_key_field_content_description)
    val redirectFieldDesc = stringResource(R.string.sf__login_options_redirect_uri_field_content_description)
    val scopesFieldDesc = stringResource(R.string.sf__login_options_scopes_field_content_description)
    val validInput = overrideConsumerKey.isNotBlank() && overrideRedirectUri.isNotBlank()
    val activity = LocalActivity.current

    Column {
        OutlinedTextField(
            value = overrideConsumerKey,
            onValueChange = { overrideConsumerKey = it },
            label = { Text("Consumer Key") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(PADDING_SIZE.dp)
                .semantics { contentDescription = consumerKeyFieldDesc },
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

        OutlinedTextField(
            value = overrideRedirectUri,
            onValueChange = { overrideRedirectUri = it },
            label = { Text("Redirect URI") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(PADDING_SIZE.dp)
                .semantics { contentDescription = redirectFieldDesc },
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

        OutlinedTextField(
            value = overrideScopes,
            onValueChange = { overrideScopes = it },
            label = { Text("Scopes") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(PADDING_SIZE.dp)
                .semantics { contentDescription = scopesFieldDesc },
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

        Button(
            modifier = Modifier.padding(PADDING_SIZE.dp).fillMaxWidth(),
            shape = RoundedCornerShape(CORNER_RADIUS.dp),
            contentPadding = PaddingValues(PADDING_SIZE.dp),
            colors = ButtonColors(
                containerColor = colorScheme.tertiary,
                contentColor = colorScheme.tertiary,
                disabledContainerColor = colorScheme.surfaceVariant,
                disabledContentColor = colorScheme.surfaceVariant,
            ),
            enabled = validInput,
            onClick = {
                SalesforceSDKManager.getInstance().debugOverrideAppConfig = OAuthConfig(
                    overrideConsumerKey,
                    overrideRedirectUri,
                    overrideScopes,
                )
                activity?.finish()
            },
        ) {
            Text(
                text = stringResource(sf__server_url_save),
                fontWeight = if (validInput) FontWeight.Normal else FontWeight.Medium,
                color = if (validInput) colorScheme.onPrimary else colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
fun BootConfigItem(name: String, value: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PADDING_SIZE.dp, end = PADDING_SIZE.dp, top = PADDING_SIZE.dp)
    ) {
        Text(
            text = name,
            fontSize = TEXT_SIZE.sp,
            color = colorScheme.onSecondary,
        )
        Text(
            text = value ?: "",
            fontSize = TEXT_SIZE.sp,
            color = colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(top = PADDING_SIZE.dp),
        )
    }
}

@Composable
fun LoginOptionsScreen(
    innerPadding: PaddingValues,
    useWebServer: MutableLiveData<Boolean>,
    useHybridToken: MutableLiveData<Boolean>,
    overrideConfig: OAuthConfig?,
    bootConfig: BootConfig = BootConfig.getBootConfig(LocalContext.current),
) {
    var useDynamicConfig by remember { mutableStateOf(overrideConfig != null) }

    Column(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        OptionToggle(
            "Use Web Server Flow",
            stringResource(R.string.sf__login_options_webserver_toggle_content_description),
            useWebServer,
        )
        OptionToggle(
            "Use Hybrid Auth Token",
            stringResource(R.string.sf__login_options_hybrid_toggle_content_description),
            useHybridToken,
        )

        HorizontalDivider()

        Text(
            text = "Boot Config File",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(PADDING_SIZE.dp)
        )

        val scopes = bootConfig.oauthScopes ?: emptyArray<String>()
        val bootConfigList = listOf(
            "Consumer Key" to bootConfig.remoteAccessConsumerKey,
            "Redirect URI" to bootConfig.oauthRedirectURI,
            "Scopes" to scopes.joinToString(separator = ", "),
        )
        bootConfigList.forEach { (name, value) ->
            BootConfigItem(name, value)
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth().padding(PADDING_SIZE.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Override Boot Config",
                modifier = Modifier.height( 50.dp).wrapContentHeight(align = Alignment.CenterVertically),
            )
            val dynamicConfigToggleDesc = stringResource(R.string.sf__login_options_dynamic_config_toggle_content_description)
            Switch(
                checked = useDynamicConfig,
                onCheckedChange = {
                    useDynamicConfig = it
                    // Reset the stored value on uncheck so it is not used.
                    if (!useDynamicConfig) {
                        SalesforceSDKManager.getInstance().debugOverrideAppConfig = null
                    }
                },
                modifier = Modifier.semantics {
                    contentDescription = dynamicConfigToggleDesc
                }
            )
        }

        if (useDynamicConfig) {
            BootConfigView(overrideConfig)
        }
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
fun OptionsTogglePreview() {
    Column {
        OptionToggle("Test Toggle", "", MutableLiveData(false))
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
fun BootConfigViewPreview() {
    BootConfigView()
}

@ExcludeFromJacocoGeneratedReport
@Preview
@Composable
fun BootConfigViewFilledPreview() {
    val config = OAuthConfig(
        stringResource(R.string.remoteAccessConsumerKey),
        stringResource(R.string.oauthRedirectURI),
        listOf("web", "api"),
    )

    BootConfigView(config)
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
fun LoginOptionsScreenPreview() {
    val consumerKey = stringResource(R.string.remoteAccessConsumerKey)
    val redirect = stringResource(R.string.oauthRedirectURI)

    LoginOptionsScreen(
        innerPadding = PaddingValues(0.dp),
        useWebServer = MutableLiveData(true),
        useHybridToken = MutableLiveData(false),
        overrideConfig = null,
        bootConfig = object : BootConfig() {
            override fun getRemoteAccessConsumerKey() = consumerKey
            override fun getOauthRedirectURI() = redirect
        },
    )
}