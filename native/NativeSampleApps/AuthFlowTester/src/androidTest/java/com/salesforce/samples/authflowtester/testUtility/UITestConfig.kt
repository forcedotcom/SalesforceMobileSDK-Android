package com.salesforce.samples.authflowtester.testUtility

import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.util.ResourceReaderHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ScopeSelection {

}


val testConfig: UITestConfig by lazy {
    Json.decodeFromString(
        string = ResourceReaderHelper.readAssetFile(
            InstrumentationRegistry.getInstrumentation().targetContext,
            /* assetFilePath = */ "ui_test_config.json",
        ) ?: throw Exception("ui_test_config.json file not found.")
    )
}

@Serializable
data class UITestConfig(val loginHosts: List<LoginHost>, val apps: List<AppConfig>)

@Serializable
data class LoginHost(
    val name: String,
    val url: String,
    val users: List<User>,
)

@Serializable
data class User(val username: String, val password: String)

@Serializable
data class AppConfig(
    val name: String,
    val consumerKey: String,
    val redirectUri: String,
    val scopes: String,
) {
    val issuesJwt = name.contains("_jwt")
    val scopeList = scopes.split(" ")
}