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
package com.salesforce.androidsdk.ui.components

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salesforce.androidsdk.R

@VisibleForTesting
internal const val USER_ACCOUNT_CD = "User Account List Item"

@Composable
fun UserAccountListItem(
    displayName: String,
    loginServer: String,
    selected: Boolean,
    onItemSelected: () -> Unit,
    profilePhoto: Painter?,
) {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .semantics { contentDescription = USER_ACCOUNT_CD }
            .clickable(
                onClickLabel = "Login server selected.",
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color(0xFF181818)),
            ) {
                onItemSelected()
            },
    ) {
        RadioButton(
            selected = selected,
            onClick = { onItemSelected() },
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFF0176D3),
                unselectedColor = Color(0xFF747474)
            ),
            modifier = Modifier.semantics { contentDescription = RADIO_BUTTON_CD },
        )
        Column(
            horizontalAlignment = Alignment.Start,
        ) {
            Image(
                profilePhoto ?: painterResource(R.drawable.sf__android_astro),
                contentDescription = "Profile Photo",
                modifier = Modifier.requiredHeight(32.dp)
            )
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                displayName,
                fontSize = 16.sp,
                color = Color(0xFF181818),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                loginServer,
                fontSize = 16.sp,
                color = Color(0xFF444444),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview("", showBackground = true, heightDp = 60)
@Composable
private fun UserAccountPreview() {
    UserAccountListItem(
        "Test User",
        "https://login.salesforce.com",
        selected = false,
        onItemSelected = { },
        painterResource(R.drawable.sf__salesforce_logo),
    )
}

@Preview("Selected", showBackground = true, heightDp = 60)
@Composable
private fun UserAccountSelectedPreview() {
    UserAccountListItem(
        "Test User",
        "https://login.salesforce.com",
        selected = true,
        onItemSelected = { },
        painterResource(R.drawable.sf__salesforce_logo),
    )
}

@Preview (name = "User account without provided profile picture.", showBackground = true, heightDp = 60)
@Composable
private fun UserAccountPreviewNoPic() {
    UserAccountListItem(
        "Another Test User",
        "https://mobilesdk.my.salesforce.com",
        selected = false,
        onItemSelected = { },
        profilePhoto = null,
    )
}

@Preview ("User Account with very long username and server url.", showBackground = true, heightDp = 60)
@Composable
private fun UserAccountPreviewLong() {
    UserAccountListItem(
        "Looooooooooooooong Naaaaaaaaaaaaaaaaaaaammmmmmeeeee",
        "https://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.com",
        selected = false,
        onItemSelected = { },
        profilePhoto = null,
    )
}

