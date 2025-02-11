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
package com.salesforce.androidsdk.ui.theme

import android.content.Context
import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.salesforce.androidsdk.R

object SFColors {
    @ColorInt
    fun background(context: Context) = ContextCompat.getColor(context, R.color.sf__background)

    @ColorInt
    fun backgroundDark(context: Context) = ContextCompat.getColor(context, R.color.sf__background_dark)

    @ColorInt
    fun layoutBackground(context: Context) = ContextCompat.getColor(context, R.color.sf__layout_background)

    @ColorInt
    fun layoutBackgroundStatusBar(context: Context) = ContextCompat.getColor(context, R.color.sf__layout_background_status_bar)

    @ColorInt
    fun layoutBackgroundDark(context: Context) = ContextCompat.getColor(context, R.color.sf__layout_background_dark)

    @ColorInt
    fun layoutBackgroundDarkStatusBar(context: Context) = ContextCompat.getColor(context, R.color.sf__layout_background_dark_status_bar)

    @ColorInt
    fun textColor(context: Context) = ContextCompat.getColor(context, R.color.sf__text_color)

    @ColorInt
    fun textColorDark(context: Context) = ContextCompat.getColor(context, R.color.sf__text_color_dark)

    @ColorInt
    fun borderColor(context: Context) = ContextCompat.getColor(context, R.color.sf__border_color)

    @ColorInt
    fun borderColorDark(context: Context) = ContextCompat.getColor(context, R.color.sf__border_color_dark)

    @ColorInt
    fun primaryColor(context: Context) = ContextCompat.getColor(context, R.color.sf__primary_color)

    @ColorInt
    fun primaryColorDark(context: Context) = ContextCompat.getColor(context, R.color.sf__primary_color_dark)

    @ColorInt
    fun onPrimaryColor(context: Context) = ContextCompat.getColor(context, R.color.sf__on_primary)

    @ColorInt
    fun onPrimaryColorDark(context: Context) = ContextCompat.getColor(context, R.color.sf__on_primary)

    @ColorInt
    fun secondaryColor(context: Context) = ContextCompat.getColor(context, R.color.sf__secondary_color)

    @ColorInt
    fun secondaryColorDark(context: Context) = ContextCompat.getColor(context, R.color.sf__secondary_color_dark)

    @ColorInt
    fun hintColor(context: Context) = ContextCompat.getColor(context, R.color.sf__hint_color)

    @ColorInt
    fun hintColorDark(context: Context) = ContextCompat.getColor(context, R.color.sf__hint_color_dark)

    @ColorInt
    fun accessibilityNavColor(context: Context) = ContextCompat.getColor(context, R.color.sf__accessibility_nav_color)

    @ColorInt
    fun subTextColor(context: Context) = ContextCompat.getColor(context, R.color.sf__subtext_color)

    @ColorInt
    fun subTextColorDark(context: Context) = ContextCompat.getColor(context, R.color.sf__disabled_text_dark)

    @ColorInt
    fun outlineColor(context: Context) = ContextCompat.getColor(context, R.color.sf__outline)

    @ColorInt
    fun outlineColorDark(context: Context) = ContextCompat.getColor(context, R.color.sf__outline_dark)

    @ColorInt
    fun tertiaryColor(context: Context) = ContextCompat.getColor(context, R.color.sf__tertiary)

    @ColorInt
    fun tertiaryColorDark(context: Context) = ContextCompat.getColor(context, R.color.sf__tertiary_dark)

    @ColorInt
    fun errorColor(context: Context) = ContextCompat.getColor(context, R.color.sf__error)

    @ColorInt
    fun disabledText(context: Context) = ContextCompat.getColor(context, R.color.sf__disabled_text)

    @ColorInt
    fun disabledTextDark(context: Context) = ContextCompat.getColor(context, R.color.sf__disabled_text_dark)
}

@Composable
fun sfLightColors(): ColorScheme {
    val context = LocalContext.current
    return lightColorScheme(
        primary = Color(SFColors.primaryColor(context)),
        primaryContainer = Color(SFColors.layoutBackground(context)),
        secondary = Color(SFColors.secondaryColor(context)),
        background = Color(SFColors.background(context)),
        surface = Color(SFColors.layoutBackground(context)),
        onPrimary = Color(SFColors.onPrimaryColor(context)),
        onSecondary = Color(SFColors.textColor(context)),
        onBackground = Color(SFColors.textColor(context)),
        onSurface = Color(SFColors.textColor(context)),
        inverseSurface = Color(SFColors.background(context)), // Login background
        inverseOnSurface = Color(SFColors.background(context)), // Login navigation bar
        surfaceVariant = Color(SFColors.secondaryColorDark(context)),
        outline = Color(SFColors.outlineColor(context)),
        tertiary = Color(SFColors.tertiaryColor(context)),
        error = Color(SFColors.errorColor(context)),
        onErrorContainer = Color(SFColors.disabledText(context)),
        onSecondaryContainer = Color(SFColors.subTextColor(context)), // Used for SubText Color
    )
}

@Composable
fun sfDarkColors(): ColorScheme {
    val context = LocalContext.current
    return darkColorScheme(
        primary = Color(SFColors.primaryColorDark(context)),
        primaryContainer = Color(SFColors.layoutBackgroundDark(context)),
        secondary = Color(SFColors.secondaryColorDark(context)),
        background = Color(SFColors.backgroundDark(context)),
        surface = Color(SFColors.layoutBackgroundDark(context)),
        onPrimary = Color(SFColors.onPrimaryColorDark(context)),
        onSecondary = Color(SFColors.textColorDark(context)),
        onBackground = Color(SFColors.textColorDark(context)),
        onSurface = Color(SFColors.textColorDark(context)),
        inverseSurface = Color(SFColors.backgroundDark(context)), // Default dark background
        inverseOnSurface = Color(SFColors.backgroundDark(context)), // Default dark navigation bar
        surfaceVariant = Color(SFColors.subTextColor(context)),
        outline = Color(SFColors.outlineColorDark(context)),
        tertiary = Color(SFColors.tertiaryColorDark(context)),
        error = Color(SFColors.errorColor(context)),
        onErrorContainer = Color(SFColors.disabledTextDark(context)),
        onSecondaryContainer = Color(SFColors.subTextColorDark(context)), // Used for SubText Color
    )
}

@Composable
fun sfDarkLoginColors(): ColorScheme {
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

val ColorScheme.hintTextColor: Color
    @Composable
    get() = Color(SFColors.hintColor(LocalContext.current))