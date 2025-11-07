package com.salesforce.androidsdk.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.ui.components.PADDING_SIZE
import com.salesforce.androidsdk.ui.theme.hintTextColor

var dynamicConsumerKey = MutableLiveData("")
var dynamicRedirectUri = MutableLiveData("")
var dynamicScopes = MutableLiveData("")

class LoginOptionsActivity: ComponentActivity() {
    val useWebServer = MutableLiveData(SalesforceSDKManager.getInstance().useWebServerAuthentication)
    val useHybridToken = MediatorLiveData(SalesforceSDKManager.getInstance().useHybridAuthentication)

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
                        TopAppBar(
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
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // TODO: Set Dynamic Boot Config
    }
}

@Composable
fun OptionToggle(
    title: String,
    useWebServer: MutableLiveData<Boolean>,
) {
    val checked by useWebServer.observeAsState(initial = false)
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            modifier = Modifier.height(50.dp).wrapContentHeight(align = Alignment.CenterVertically),
        )
        Switch(
            checked = checked,
            onCheckedChange = { useWebServer.value = it },
        )
    }
}

@Composable
fun BootConfigView() {
    Column {
        OutlinedTextField(
            value = dynamicConsumerKey.value ?: "",
            onValueChange = { dynamicConsumerKey.value = it },
            label = { Text("Consumer Key") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(PADDING_SIZE.dp),
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
            value = dynamicRedirectUri.value ?: "",
            onValueChange = { dynamicRedirectUri.value },
            label = { Text("Redirect URI") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(PADDING_SIZE.dp),
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
            value = dynamicScopes.value ?: "",
            onValueChange = { dynamicScopes.value = it },
            label = { Text("Scopes (comma separated)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(PADDING_SIZE.dp),
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
}

@Composable
fun BootConfigItem(name: String, value: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 10.dp)
    ) {
        Text(text = name)
        Text(
            text = value ?: "",
            color = Color.Gray,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
fun LoginOptionsScreen(
    innerPadding: PaddingValues,
    useWebServer: MutableLiveData<Boolean>,
    useHybridToken: MutableLiveData<Boolean>,
    bootConfig: BootConfig = BootConfig.getBootConfig(LocalContext.current),
) {
    var useDynamicConfig by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(innerPadding)) {
        OptionToggle("Use Web Server Flow", useWebServer)
        OptionToggle("Use Hybrid Auth Token", useHybridToken)

        HorizontalDivider()

        Text(
            text = "Boot Config File",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(10.dp)
        )

        LazyColumn {
            val scopes = bootConfig.oauthScopes ?: emptyArray<String>()
            val bootConfigList = listOf(
                "Consumer Key" to bootConfig.remoteAccessConsumerKey,
                "Redirect URI" to bootConfig.oauthRedirectURI,
                "Scopes" to scopes.joinToString(separator = ", "),
            )
            items(bootConfigList) { (name, value) ->
                BootConfigItem(name, value)
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Use Dynamic Boot Config",
                modifier = Modifier.height(50.dp).wrapContentHeight(align = Alignment.CenterVertically),
            )
            Switch(
                checked = useDynamicConfig,
                onCheckedChange = { useDynamicConfig = it },
            )
        }

        if (useDynamicConfig) {
            BootConfigView()
        }
    }
}

val previewBootConfig = object : BootConfig() {
    override fun getRemoteAccessConsumerKey() = "3MVG98dostKihXN53TYStBIiS8FC2a3tE3XhGId0hQ37iQjF0xe4fxMSb2mFaWZn9e3GiLs1q67TNlyRji.Xw"
    override fun getOauthRedirectURI() = "testsfdc:///mobilesdk/detect/oauth/done"
}

@Preview(showBackground = true)
@Composable
fun OptionsTogglePreview() {
    Column {
        OptionToggle("Test Toggle", MutableLiveData(false))
    }
}

@Preview(showBackground = true)
@Composable
fun BootConfigViewPreview() {
    BootConfigView()
}

@Preview(showBackground = true)
@Composable
fun LoginOptionsScreenPreview() {
    LoginOptionsScreen(
        innerPadding = PaddingValues(0.dp),
        useWebServer = MutableLiveData(true),
        useHybridToken = MutableLiveData(false),
        bootConfig = previewBootConfig,
    )
}


