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

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salesforce.androidsdk.R.string.sf__server_delete_content_description
import com.salesforce.androidsdk.R.string.sf__server_picker_click_label
import com.salesforce.androidsdk.R.string.sf__server_remove_content_description
import com.salesforce.androidsdk.R.string.sf__server_url_delete
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.ui.theme.sfDarkColors
import com.salesforce.androidsdk.ui.theme.sfLightColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DELETE_BUTTON_SIZE = 80
private const val DELETE_ANIMATION_TIME = 300

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginServerListItem(
    server: LoginServer,
    selected: Boolean,
    onItemSelected: (Any?, Boolean) -> Unit,
    previewDeleting: Boolean = false,
    removeServer: (LoginServer) -> Unit,
) {
    var deleting by remember { mutableStateOf(previewDeleting) }
    val density = LocalDensity.current
    val deleteButtonPixels = with(density) { DELETE_BUTTON_SIZE.dp.roundToPx() }
    val offset by animateIntOffsetAsState(
        targetValue = if (deleting) {
            IntOffset(-deleteButtonPixels, IntOffset.Zero.y)
        } else {
            IntOffset.Zero
        },
        label = "offset"
    )
    var rowSizePixels by remember { mutableStateOf(IntSize(0, 0)) }
    val rowHeightDp = remember {
        derivedStateOf { with(density) { rowSizePixels.height.toDp() } }
    }
    val confirmDeleteFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { /* Necessary for accessibility */ }

    Box {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(intrinsicSize = IntrinsicSize.Max)
                .onSizeChanged { size -> rowSizePixels = size }
                .clickable(
                    onClickLabel = stringResource(sf__server_picker_click_label),
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = colorScheme.onSecondary),
                    onClick = {
                        if (deleting) {
                            deleting = false
                        } else {
                            onItemSelected(server, true)
                        }
                    },
                ),
        ) {
            RadioButton(
                selected = selected,
                onClick = { onItemSelected(server, true) },
                colors = RadioButtonDefaults.colors(
                    selectedColor = colorScheme.tertiary,
                    unselectedColor = colorScheme.secondary
                ),
                modifier = Modifier.offset { offset },
            )
            Column(modifier = Modifier
                .weight(1f)
                .padding(PADDING_SIZE.dp)
                .offset { offset }) {
                Text(
                    server.name,
                    fontSize = TEXT_SIZE.sp,
                    color = colorScheme.onSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    server.url,
                    fontSize = TEXT_SIZE.sp,
                    color = colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (server.isCustom) {
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    val scope = rememberCoroutineScope()
                    IconButton(
                        onClick = {
                            deleting = true
                            scope.launch {
                                delay(DELETE_ANIMATION_TIME.toLong())
                                confirmDeleteFocus.requestFocus()
                            }
                        },
                        enabled = !deleting,
                        interactionSource = null,
                        modifier = Modifier
                            .padding(end = PADDING_SIZE.dp)
                            .size(ICON_SIZE.dp)
                            .offset { offset },
                    ) {
                        Icon(
                            Icons.TwoTone.Delete,
                            contentDescription = stringResource(sf__server_remove_content_description),
                            tint = colorScheme.secondary.copy(
                                alpha = if (deleting) 0f else 1f
                            ),
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(modifier = Modifier)
            AnimatedVisibility(
                visible = deleting,
                enter = slideInHorizontally(tween(DELETE_ANIMATION_TIME)) { deleteButtonPixels },
                exit = shrinkHorizontally { -deleteButtonPixels }
            ) {
                Box(
                    modifier = Modifier
                        .background(colorScheme.error)
                        .width(DELETE_BUTTON_SIZE.dp)
                        .height(rowHeightDp.value)
                        .clickable { removeServer(server) },
                ) {
                    val deleteContentDescription = stringResource(sf__server_delete_content_description)
                    Text(
                        text = stringResource(sf__server_url_delete),
                        color = colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .semantics {
                                contentDescription = deleteContentDescription
                                role = Role.Button
                            }
                            .focusRequester(confirmDeleteFocus)
                            .focusable(),
                    )
                }
            }
        }
    }
}

@Preview("Default Server", showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun DefaultServerPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        LoginServerListItem(
            server = LoginServer("Production", "https://login.salesforce.com", false),
            selected = true,
            onItemSelected = { _, _ -> },
            removeServer = { },
        )
    }
}

@Preview("Very Long Default Server", showBackground = true)
@Composable
private fun LongDefaultServerPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        LoginServerListItem(
            server = LoginServer(
                "Production Production Production Production",
                "https://login.salesforce.comhttps://login.salesforce.comhttps://login.salesforce.comr",
                false
            ),
            selected = true,
            onItemSelected = { _, _ -> },
            removeServer = { },
        )
    }
}

@Preview("Custom Server", showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun CustomServerPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        LoginServerListItem(
            server = LoginServer("Custom", "https://mobilesdk.my.salesforce.com", true),
            selected = false,
            onItemSelected = { _, _ -> },
            removeServer = { },
        )
    }
}

@Preview("Deleting", showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun DeletingLoginServer() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        LoginServerListItem(
            server = LoginServer("Custom", "https://mobilesdk.my.salesforce.com", true),
            selected = false,
            previewDeleting = true,
            onItemSelected = { _, _ -> },
            removeServer = { },
        )
    }
}

@Preview("Very Long Custom Server", showBackground = true)
@Composable
private fun LongServerPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        LoginServerListItem(
            server = LoginServer(
                "Custom Long Custom Long Custom Long Custom Long Custom Long ",
                "https://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.com",
                true,
            ),
            selected = false,
            onItemSelected = { _, _ -> },
            removeServer = { },
        )
    }
}

@Preview("Very Long Custom Server Deleting", showBackground = true)
@Composable
private fun LongServerDeletingPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        LoginServerListItem(
            server = LoginServer(
                "Custom Long Custom Long Custom Long Custom Long Custom Long ",
                "https://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.comhttps://mobilesdk.my.salesforce.com",
                true,
            ),
            selected = false,
            previewDeleting = true,
            onItemSelected = { _, _ -> },
            removeServer = { },
        )
    }
}
