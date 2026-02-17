/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight.Companion.Normal
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salesforce.androidsdk.R.drawable.sf__salesforce_logo
import com.salesforce.androidsdk.R.string.sf__application_icon
import com.salesforce.androidsdk.R.string.sf__logout
import com.salesforce.androidsdk.R.string.sf__screen_lock_setup_button
import com.salesforce.androidsdk.R.string.sf__screen_lock_setup_required
import com.salesforce.androidsdk.ui.CORNER_RADIUS
import com.salesforce.androidsdk.ui.PADDING_SIZE
import com.salesforce.androidsdk.ui.ScreenLockViewModel
import com.salesforce.androidsdk.ui.noOp
import com.salesforce.androidsdk.ui.theme.hintTextColor
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport

/**
 * The set up view when screen lock is required and not enrolled.
 */
@Composable
internal fun ScreenLockView(
    appIcon: Painter,
    appName: String,
    innerPadding: PaddingValues,
    logoutAction: () -> Unit,
    viewModel: ScreenLockViewModel = viewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {

        // Log out button.
        Button(
            colors = ButtonColors(
                containerColor = Transparent,
                contentColor = colorScheme.hintTextColor,
                disabledContainerColor = Transparent,
                disabledContentColor = colorScheme.surfaceVariant,
            ),
            contentPadding = PaddingValues(PADDING_SIZE.dp),
            enabled = true,
            modifier = Modifier.padding(PADDING_SIZE.dp),
            onClick = logoutAction,
            shape = RoundedCornerShape(CORNER_RADIUS.dp),
        ) { Text(color = colorScheme.primary, fontWeight = Normal, fontSize = 17.sp, text = stringResource(sf__logout)) }

        Spacer(modifier = Modifier.weight(1f))

        // Application icon.
        Image(
            modifier = Modifier
                .align(CenterHorizontally)
                .size(150.dp),
            contentDescription = stringResource(sf__application_icon),
            painter = appIcon,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Setup message text.
        Text(
            fontSize = 14.sp,
            modifier = Modifier.align(CenterHorizontally),
            text = viewModel.setupMessageText.value ?: stringResource(sf__screen_lock_setup_required, appName),
        )

        // Setup action button.
        if (viewModel.setupButtonVisible.value) {
            Button(
                colors = ButtonColors(
                    containerColor = colorScheme.tertiary,
                    contentColor = colorScheme.tertiary,
                    disabledContainerColor = colorScheme.surfaceVariant,
                    disabledContentColor = colorScheme.surfaceVariant,
                ),
                contentPadding = PaddingValues(PADDING_SIZE.dp),
                enabled = true,
                modifier = Modifier
                    .align(CenterHorizontally)
                    .padding(PADDING_SIZE.dp),
                onClick = {
                    viewModel.setupButtonAction.value()
                },
                shape = RoundedCornerShape(CORNER_RADIUS.dp),
            ) { Text(color = colorScheme.onPrimary, fontSize = 14.sp, fontWeight = Normal, text = viewModel.setupButtonLabel.value ?: stringResource(sf__screen_lock_setup_button)) }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
fun ScreenLockViewPreview() {
    ScreenLockView(
        appName = "App",
        innerPadding = PaddingValues.Absolute(0.dp),
        appIcon = painterResource(id = sf__salesforce_logo),
        viewModel = ScreenLockViewModel(),
        logoutAction = ::noOp,
    )
}
