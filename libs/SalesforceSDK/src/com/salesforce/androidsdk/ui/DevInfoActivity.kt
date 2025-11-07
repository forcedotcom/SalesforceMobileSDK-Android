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
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.components.PADDING_SIZE
import com.salesforce.androidsdk.ui.components.TEXT_SIZE
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class DevInfoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val devInfoList = prepareListData(SalesforceSDKManager.getInstance().devSupportInfos)

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
                    DevInfoScreen(innerPadding, devInfoList)
                }
            }
        }
    }

    private fun prepareListData(rawData: List<String>): List<Pair<String, String>> {
        return rawData.chunked(2).map { it[0] to it[1] }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevInfoScreen(
    paddingValues: PaddingValues,
    devInfoList: List<Pair<String, String>>,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        items(devInfoList) { (name, value) ->
            DevInfoItem(name, value)
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

@Preview(showBackground = true)
@Composable
private fun DevInfoItemPreview() {
    DevInfoItem("Item Name", "Item Value")
}

@Preview(showBackground = true)
@Composable
private fun DevInfoScreenPreview() {
    DevInfoScreen(
        PaddingValues(0.dp),
        devInfoList = listOf(
            "SDK Version" to SalesforceSDKManager.SDK_VERSION,
            "User Agent" to "SalesforceMobileSDK/13.2.0.dev android mobile/15 (sdk_gphone64_arm64) " +
                    "RestExplorer/1.0(1) Native uid_adc6e133bd0ac338 ftr_AI.SP.UA SecurityPatch/2024-09-05",
        ),
    )
}