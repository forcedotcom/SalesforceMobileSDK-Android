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
    fun subtextColor(context: Context) = ContextCompat.getColor(context, R.color.sf__subtext_color)

    @ColorInt
    fun api35StatusBarColor(context: Context) = ContextCompat.getColor(context, R.color.sf__api_35_status_bar_color)
}