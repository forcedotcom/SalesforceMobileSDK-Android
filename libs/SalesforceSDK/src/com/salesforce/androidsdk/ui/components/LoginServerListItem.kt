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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer

@Composable
fun LoginServerListItem(
    server: LoginServer,
    removeServer: (LoginServer) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(10f)) {
            Text(
                server.name,
                fontSize = 16.sp,
                color = Color(0xFF181818),
                maxLines = 1,
            )
            Text(
                server.url,
                fontSize = 16.sp,
                color = Color(0xFF444444),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.weight(1f),
        ) {
            if (server.isCustom) {
                IconButton(
                    onClick = {
                        // TODO: implement final UX
                        removeServer(server)
                    },
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Remove Server",
                    )
                }
            }
        }
    }
}

@Preview("Default Server", showBackground = true)
@Composable
private fun DefaultServerPreview() {
    LoginServerListItem(LoginServer("Production", "https://login.salesforce.com", false)) {}
}

@Preview("Custom Server", showBackground = true)
@Composable
private fun CustomServerPreview() {
    LoginServerListItem(LoginServer("Custom", "https://mobilesdk.my.salesforce.com", true)) {}

}

@Preview("Very Long Custom Server", showBackground = true)
@Composable
private fun LongServerPreview() {
    LoginServerListItem(
        LoginServer(
            "Custom Long Custom Long Custom Long Custom Long Custom Long ",
            "https://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.com",
            true,
        )
    ) {}

}

