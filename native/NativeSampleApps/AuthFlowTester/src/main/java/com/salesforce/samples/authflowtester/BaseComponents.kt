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
package com.salesforce.samples.authflowtester

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salesforce.androidsdk.ui.theme.sfDarkColors
import com.salesforce.androidsdk.ui.theme.sfLightColors
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport

const val SECTION_TITLE_SIZE = 16
const val ROW_FONT_SIZE = 14
const val EXPAND_SECTION_ICON_SIZE = 20
const val EXPANDED_ROTATION = 180f
const val UNEXPANDED_ROTATION = 0f
const val LABEL_WEIGHT = 0.4f
const val VALUE_WEIGHT = 1 - LABEL_WEIGHT
const val FIVE_CHARS = 5
const val VISIBILITY_ICON_SIZE = 24



@Composable
fun ExpandableCard(
    title: String,
    exportedJSON: String,
    defaultExpanded: Boolean = false, // Only used for Previews
    content: @Composable (() -> Unit),
) {
    var isExpanded by remember { mutableStateOf(defaultExpanded) }
    var showExportAlert by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding((INNER_CARD_PADDING/2).dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(CORNER_SHAPE.dp)
    ) {
        Column(
            modifier = Modifier.padding(INNER_CARD_PADDING.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isExpanded = !isExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = { showExportAlert = true }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.export_credentials),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) {
                            stringResource(R.string.collapse)
                        } else  {
                            stringResource(R.string.expand)
                        },
                        modifier = Modifier.rotate(
                            degrees = if (isExpanded) EXPANDED_ROTATION else UNEXPANDED_ROTATION
                        ),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = (INNER_CARD_PADDING/2).dp),
                    verticalArrangement = Arrangement.spacedBy((INNER_CARD_PADDING/2).dp)
                ) {
                    // Inner content here
                    content.invoke()
                }
            }
        }
    }

    if (showExportAlert) {
        val context = LocalContext.current
        @Suppress("AssignedValueIsNeverRead")
        AlertDialog(
            onDismissRequest = { showExportAlert = false },
            title = { Text(title) },
            text = { Text(
                text = exportedJSON,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) },
            confirmButton = {
                TextButton(
                    onClick = {
                        copyToClipboard(context, title = title, text = exportedJSON)
                        showExportAlert = false
                    }
                ) {
                    Text(stringResource(R.string.copy_to_clipboard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportAlert = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

@Composable
fun InfoSection(
    title: String,
    defaultExpanded: Boolean = true,
    content: @Composable @UiComposable () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(defaultExpanded) }
    val chevronRotation = remember { Animatable(UNEXPANDED_ROTATION) }

    LaunchedEffect(isExpanded) {
        chevronRotation.animateTo(
            targetValue = if (isExpanded) EXPANDED_ROTATION else UNEXPANDED_ROTATION
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(CORNER_SHAPE.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(INNER_CARD_PADDING.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = SECTION_TITLE_SIZE.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    modifier = Modifier
                        .size(EXPAND_SECTION_ICON_SIZE.dp)
                        .rotate(chevronRotation.value),
                    contentDescription = if (isExpanded) {
                        stringResource(R.string.collapse)
                    } else {
                        stringResource(R.string.expand)
                    },
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(bottom = (INNER_CARD_PADDING/2).dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun InfoRowView(
    label: String,
    value: String?,
    isSensitive: Boolean = false,
) {
    var isValueVisible by remember { mutableStateOf(!isSensitive) }
    val emptyText = stringResource(R.string.empty_placeholder)
    val displayValue = if (isSensitive && !isValueVisible && !value.isNullOrEmpty()) {
        "${value.take(FIVE_CHARS)}...${value.takeLast(FIVE_CHARS)}"
    } else {
        value
    }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = INNER_CARD_PADDING.dp, vertical = (INNER_CARD_PADDING/4).dp)
            .clickable(enabled = isSensitive && !value.isNullOrEmpty()) {
                isValueVisible = !isValueVisible
            }
            .combinedClickable(
                onClick =  {
                    if(isSensitive && !value.isNullOrEmpty()) {
                        isValueVisible = !isValueVisible
                    }
                },
                onLongClick = {
                    value?.let {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        copyToClipboard(context, label, value)
                    }
                }
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${label}:",
            fontSize = ROW_FONT_SIZE.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(LABEL_WEIGHT),
        )

        // Ensure some space between label and values.
        Spacer(modifier = Modifier.width((INNER_CARD_PADDING/4).dp))

        Text(
            text = displayValue?.ifEmpty { emptyText } ?: emptyText,
            fontSize = ROW_FONT_SIZE.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(VALUE_WEIGHT).padding(end = (INNER_CARD_PADDING/2).dp),
        )

        if (isSensitive && !value.isNullOrEmpty()) {
            if (isValueVisible) {
                Icon(
                    painter = painterResource(id = R.drawable.visibility_off),
                    contentDescription = stringResource(R.string.hide_sensitive),
                    modifier = Modifier.size(VISIBILITY_ICON_SIZE.dp),
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.visibility),
                    contentDescription = stringResource(R.string.show_sensitive),
                    modifier = Modifier.size(VISIBILITY_ICON_SIZE.dp),
                )
            }
        } else {
            // Add spacer so sensitive and non-sensitive fields remain aligned.
            Spacer(modifier = Modifier.width(VISIBILITY_ICON_SIZE.dp))
        }
    }
}

private fun copyToClipboard(context: Context, title: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val label = context.getString(R.string.clipboard_label_format, title)
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

@RequiresApi(Build.VERSION_CODES.S)
@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun InfoRowViewPreview() {
    val scheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }
    MaterialTheme(scheme) {
        InfoRowView("Label", "Value")
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun InfoRowViewSensitivePreview() {
    val scheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }
    MaterialTheme(scheme) {
        InfoRowView("Sensitive Label", "3aZ*GQ!o2^@8QPR", isSensitive = true)
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun InfoRowSectionPreview() {
    val scheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }
    MaterialTheme(scheme) {
        InfoSection("Section Title") {
            InfoRowView("Sensitive Label", "3aZ*GQ!o2^@8QPR", isSensitive = true)
        }
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun InfoRowSectionFallbackThemePreview() {
    val scheme = if (isSystemInDarkTheme()) {
        sfDarkColors()
    } else {
        sfLightColors()
    }
    MaterialTheme(scheme) {
        InfoSection("Section Title") {
            InfoRowView("Sensitive Label", "3aZ*GQ!o2^@8QPR", isSensitive = true)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun UserCredentialsViewPreview() {
    val scheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }
    MaterialTheme(scheme) {
        ExpandableCard(
            title = "Card Title",
            exportedJSON = "",
        ) { }
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun UserCredentialsViewFallbackThemePreview() {
    val scheme = if (isSystemInDarkTheme()) {
        sfDarkColors()
    } else {
        sfLightColors()
    }
    MaterialTheme(scheme) {
        ExpandableCard(
            title = "Card Title",
            exportedJSON = "",
        ) { }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun UserCredentialsViewExpandedPreview() {
    val scheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }
    MaterialTheme(scheme) {
        ExpandableCard(
            title = "Card Title",
            exportedJSON = "",
            defaultExpanded = true,
        ) {
            InfoSection("Section Title") {
                InfoRowView("Sensitive Label", "3aZ*GQ!o2^@8QPR", isSensitive = true)
            }
        }
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun UserCredentialsViewFallbackThemeExpandedPreview() {
    val scheme = if (isSystemInDarkTheme()) {
        sfDarkColors()
    } else {
        sfLightColors()
    }
    MaterialTheme(scheme) {
        ExpandableCard(
            title = "Card Title",
            exportedJSON = "",
            defaultExpanded = true,
        ) {
            InfoSection("Section Title") {
                InfoRowView("Sensitive Label", "3aZ*GQ!o2^@8QPR", isSensitive = true)
            }
        }
    }
}