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
package com.salesforce.androidsdk.ui

import android.content.ClipData
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.developer.support.DevSupportInfo
import com.salesforce.androidsdk.ui.components.ICON_SIZE
import com.salesforce.androidsdk.ui.components.PADDING_SIZE
import com.salesforce.androidsdk.ui.components.TEXT_SIZE
import com.salesforce.androidsdk.ui.theme.sfDarkColors
import com.salesforce.androidsdk.ui.theme.sfLightColors
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class DevInfoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val devSupportInfo = SalesforceSDKManager.getInstance().devSupportInfo

        setContent {
            MaterialTheme(colorScheme = SalesforceSDKManager.getInstance().colorScheme()) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text(stringResource(id = R.string.sf__dev_support_title)) }
                        )
                    }
                ) { innerPadding ->
                    DevInfoScreen(innerPadding, devSupportInfo)
                }
            }
        }
    }
}

@Composable
fun DevInfoScreen(
    paddingValues: PaddingValues,
    devSupportInfo: DevSupportInfo,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
    ) {
        // Basic Info List
        devSupportInfo.basicInfo?.let {
            items(it) { (name, value) ->
                DevInfoItem(name, value)
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Auth Config Section
        devSupportInfo.authConfigSection?.let { (title, items) ->
            item {
                CollapsibleSection(title, items)
            }
        }

        // Boot Config Section
        devSupportInfo.bootConfigSection?.let { (title, items) ->
            item {
                CollapsibleSection(title, items)
            }
        }

        // Current User Section
        devSupportInfo.currentUserSection?.let { (title, items) ->
            item {
                CollapsibleSection(title, items)
            }
        }

        // Runtime Config Section
        devSupportInfo.runtimeConfigSection?.let { (title, items) ->
            item {
                CollapsibleSection(title, items)
            }
        }

        // Additional Sections
        devSupportInfo.additionalSections.forEach { (title, items) ->
            item {
                CollapsibleSection(title, items)
            }
        }
    }
}

@Composable
fun DevInfoItem(name: String, value: String?) {
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PADDING_SIZE.dp, end = PADDING_SIZE.dp, top = PADDING_SIZE.dp)
            .clickable {
                // Copy non-null and non-boolean values to clipboard.
                value?.let {
                    if (it.toBooleanStrictOrNull() == null) {
                        val clipData = ClipData.newPlainText(name, it)
                        coroutineScope.launch {
                            clipboard.setClipEntry(ClipEntry(clipData))
                        }
                    }
                }
            }
    ) {
        Text(
            text = name,
            fontSize = TEXT_SIZE.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSecondary,
        )
        Text(
            text = value ?: "",
            fontSize = TEXT_SIZE.sp,
            color = colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(top = PADDING_SIZE.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(top = PADDING_SIZE.dp))
    }
}

@Composable
fun CollapsibleSection(
    title: String,
    items: List<Pair<String, String>>,
    defaultExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    val chevronRotation = remember { Animatable(0f) }

    LaunchedEffect(expanded) {
        chevronRotation.animateTo(targetValue = if (expanded) 180f else 0f)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = PADDING_SIZE.dp, vertical = (PADDING_SIZE / 2).dp)
    ) {
        Column {
            // Header with click handler
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(PADDING_SIZE.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = (TEXT_SIZE + 2).sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    modifier = Modifier.size(ICON_SIZE.dp)
                        .rotate(chevronRotation.value),
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    items.forEach { (name, value) ->
                        DevInfoItem(name, value)
                    }
                }
            }
        }
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun DevInfoItemPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DevInfoItem("SDK Version", SalesforceSDKManager.SDK_VERSION)
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun DevInfoItemLongPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        DevInfoItem("User Agent","SalesforceMobileSDK/13.2.0.dev android mobile/15 (sdk_gphone64_arm64) " +
                "RestExplorer/1.0(1) Native uid_adc6e133bd0ac338 ftr_AI.SP.UA SecurityPatch/2024-09-05")
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun CollapsibleSectionPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        CollapsibleSection(
            "Collapsed",
            listOf("User Agent" to "SalesforceMobileSDK/13.2.0.dev android mobile/15 (sdk_gphone64_arm64) " +
                "RestExplorer/1.0(1) Native uid_adc6e133bd0ac338 ftr_AI.SP.UA SecurityPatch/2024-09-05"),
        )
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun CollapsibleSectionExpandedPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) sfDarkColors() else sfLightColors()) {
        CollapsibleSection(
            "Expanded",
            listOf(
                "SDK Version" to SalesforceSDKManager.SDK_VERSION,
                "User Agent" to "SalesforceMobileSDK/13.2.0.dev android mobile/15 (sdk_gphone64_arm64) " +
                    "RestExplorer/1.0(1) Native uid_adc6e133bd0ac338 ftr_AI.SP.UA SecurityPatch/2024-09-05"
            ),
            defaultExpanded = true,
        )
    }
}