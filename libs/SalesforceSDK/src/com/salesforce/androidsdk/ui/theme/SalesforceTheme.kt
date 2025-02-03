/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class SalesforceThemeType {
    Light, Dark, DarkLogin
}

@Composable
fun SalesforceTheme(
    themeType: SalesforceThemeType = SalesforceThemeType.Light,
    content: @Composable () -> Unit
) {
    val colors = when (themeType) {
        SalesforceThemeType.Light -> lightColors()
        SalesforceThemeType.Dark -> darkColors()
        SalesforceThemeType.DarkLogin -> darkLoginColors()
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

@Composable
fun lightColors(): ColorScheme {
    val context = LocalContext.current
    return lightColorScheme(
        primary = Color(SFColors.primaryColor(context)),
        primaryContainer = Color(SFColors.primaryColorDark(context)),
        secondary = Color(SFColors.secondaryColor(context)),
        background = Color(SFColors.background(context)),
        surface = Color(SFColors.layoutBackground(context)),
        onPrimary = Color(SFColors.secondaryColor(context)),
        onSecondary = Color(SFColors.textColor(context)),
        onBackground = Color(SFColors.textColor(context)),
        onSurface = Color(SFColors.textColor(context)),
        inverseSurface = Color(SFColors.background(context)), // Login background
        inverseOnSurface = Color(SFColors.background(context)) // Login navigation bar
    )
}

@Composable
fun darkColors(): ColorScheme {
    val context = LocalContext.current
    return darkColorScheme(
        primary = Color(SFColors.primaryColor(context)),
        primaryContainer = Color(SFColors.primaryColorDark(context)),
        secondary = Color(SFColors.secondaryColorDark(context)),
        background = Color(SFColors.backgroundDark(context)),
        surface = Color(SFColors.layoutBackgroundDark(context)),
        onPrimary = Color(SFColors.secondaryColorDark(context)),
        onSecondary = Color(SFColors.textColorDark(context)),
        onBackground = Color(SFColors.textColorDark(context)),
        onSurface = Color(SFColors.textColorDark(context)),
        inverseSurface = Color(SFColors.backgroundDark(context)), // Default dark background
        inverseOnSurface = Color(SFColors.backgroundDark(context)) // Default dark navigation bar
    )
}

@Composable
fun darkLoginColors(): ColorScheme {
    val context = LocalContext.current
    return darkColorScheme(
        primary = Color(SFColors.primaryColor(context)),
        primaryContainer = Color(SFColors.primaryColorDark(context)),
        secondary = Color(SFColors.secondaryColorDark(context)),
        background = Color(SFColors.background(context)), // Overriding with light mode background
        surface = Color(SFColors.layoutBackground(context)), // Overriding with light mode layout
        onPrimary = Color(SFColors.secondaryColorDark(context)),
        onSecondary = Color(SFColors.textColorDark(context)),
        onBackground = Color(SFColors.textColorDark(context)),
        onSurface = Color(SFColors.textColorDark(context)),
        inverseSurface = Color(SFColors.background(context)), // Login-specific background
        inverseOnSurface = Color(SFColors.background(context)) // Login-specific navigation bar
    )
}
