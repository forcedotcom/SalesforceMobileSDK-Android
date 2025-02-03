package com.salesforce.androidsdk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.salesforce.androidsdk.app.SalesforceSDKManager

private val DarkColorScheme = darkColorScheme(
    background = Color.White
)

/**
 * TODO: Update with support for light, dark and system themes: W-17687751
 */
@Composable
fun LoginWebviewTheme(
    darkTheme: Boolean = SalesforceSDKManager.getInstance().isDarkTheme,
    content: @Composable () -> Unit,
) {
    MaterialTheme(DarkColorScheme, content = content)
}
