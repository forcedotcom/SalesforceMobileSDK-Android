package com.salesforce.androidsdk.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun SalesforceTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

// Light theme colors
private val LightColors = lightColorScheme(
    primary = sfPrimaryColor,
    secondary = sfSecondaryColor,
    background = sfBackground,
    surface = sfBackground,
    onPrimary = sfTextColor,
    onSecondary = sfTextColor,
    onBackground = sfTextColor,
    onSurface = sfTextColor,
    error = Color.Red,
    onError = Color.White,
    surfaceVariant = sfBackground
)

// Dark theme colors
private val DarkColors = darkColorScheme(
    primary = sfPrimaryColor,
    secondary = sfSecondaryColorDark,
    background = sfBackgroundDark,
    surface = sfBackgroundDark,
    onPrimary = sfTextColorDark,
    onSecondary = sfTextColorDark,
    onBackground = sfTextColorDark,
    onSurface = sfTextColorDark,
    error = Color.Red,
    onError = Color.White,
    surfaceVariant = sfBackgroundDark
)
