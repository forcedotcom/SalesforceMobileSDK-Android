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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.ScopeParser.Companion.toScopeParser
import com.salesforce.androidsdk.ui.theme.sfDarkColors
import com.salesforce.androidsdk.ui.theme.sfLightColors
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object CredentialsLabels {
    const val USER_IDENTITY = "User Identity"
    const val OAUTH_CLIENT_CONFIGURATION = "OAuth Client Configuration"
    const val TOKENS = "Tokens"
    const val URLS = "URLs"
    const val COMMUNITY = "Community"
    const val DOMAINS_AND_SIDS = "Domains and SIDs"
    const val COOKIES_AND_SECURITY = "Cookies and Security"
    const val BEACON = "Beacon"
    const val OTHER = "Other"
    
    const val USERNAME = "Username"
    const val USER_ID_LABEL = "User ID"
    const val ORGANIZATION_ID = "Organization ID"
    
    const val CLIENT_ID = "Client ID"
    const val REDIRECT_URI = "Redirect URI"
    const val PROTOCOL_LABEL = "Protocol"
    const val DOMAIN = "Domain"
    const val IDENTIFIER = "Identifier"
    
    const val ACCESS_TOKEN = "Access Token"
    const val REFRESH_TOKEN = "Refresh Token"
    const val TOKEN_FORMAT = "Token Format"
    const val JWT = "JWT"
    const val AUTH_CODE = "Auth Code"
    const val CHALLENGE_STRING = "Challenge String"
    const val ISSUED_AT = "Issued At"
    const val SCOPES = "Scopes"
    
    const val INSTANCE_URL = "Instance URL"
    const val API_INSTANCE_URL = "API Instance URL"
    const val API_URL = "API URL"
    const val IDENTITY_URL = "Identity URL"
    
    const val COMMUNITY_ID = "Community ID"
    const val COMMUNITY_URL = "Community URL"
    
    const val LIGHTNING_DOMAIN = "Lightning Domain"
    const val LIGHTNING_SID = "Lightning SID"
    const val VF_DOMAIN = "VF Domain"
    const val VF_SID = "VF SID"
    const val CONTENT_DOMAIN = "Content Domain"
    const val CONTENT_SID = "Content SID"
    const val PARENT_SID = "Parent SID"
    const val SID_COOKIE_NAME = "SID Cookie Name"
    
    const val CSRF_TOKEN = "CSRF Token"
    const val COOKIE_CLIENT_SRC = "Cookie Client Src"
    const val COOKIE_SID_CLIENT = "Cookie SID Client"
    
    const val BEACON_CHILD_CONSUMER_KEY = "Beacon Child Consumer Key"
    const val BEACON_CHILD_CONSUMER_SECRET = "Beacon Child Consumer Secret"
    
    const val ADDITIONAL_OAUTH_FIELDS = "Additional OAuth Fields"
}

@Composable
fun UserCredentialsView(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    currentUser: UserAccount?,
) {
    val context = LocalContext.current
    var showExportAlert by remember { mutableStateOf(false) }
    var exportedJSON by remember { mutableStateOf("") }
    
    var userIdentityExpanded by remember { mutableStateOf(false) }
    var oauthConfigExpanded by remember { mutableStateOf(false) }
    var tokensExpanded by remember { mutableStateOf(false) }
    var urlsExpanded by remember { mutableStateOf(false) }
    var communityExpanded by remember { mutableStateOf(false) }
    var domainsAndSidsExpanded by remember { mutableStateOf(false) }
    var cookiesAndSecurityExpanded by remember { mutableStateOf(false) }
    var beaconExpanded by remember { mutableStateOf(false) }
    var otherExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onExpandedChange(!isExpanded) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "User Credentials",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(
                    onClick = {
                        exportedJSON = generateCredentialsJSON(currentUser)
                        showExportAlert = true
                    }
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Export Credentials",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(
                    onClick = { onExpandedChange(!isExpanded) }
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(if (isExpanded) 180f else 0f),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            if (isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoSection(
                        title = CredentialsLabels.USER_IDENTITY,
                        isExpanded = userIdentityExpanded,
                        onExpandedChange = { userIdentityExpanded = it }
                    ) {
                        InfoRowView("${CredentialsLabels.USERNAME}:", currentUser?.username ?: "")
                        InfoRowView("${CredentialsLabels.USER_ID_LABEL}:", currentUser?.userId ?: "")
                        InfoRowView("${CredentialsLabels.ORGANIZATION_ID}:", currentUser?.orgId ?: "")
                    }
                    
                    InfoSection(
                        title = CredentialsLabels.OAUTH_CLIENT_CONFIGURATION,
                        isExpanded = oauthConfigExpanded,
                        onExpandedChange = { oauthConfigExpanded = it }
                    ) {
                        InfoRowView("${CredentialsLabels.CLIENT_ID}:", currentUser?.clientId ?: "", isSensitive = true)
                        InfoRowView("${CredentialsLabels.REDIRECT_URI}:", "")
                        InfoRowView("${CredentialsLabels.PROTOCOL_LABEL}:", "")
                        InfoRowView("${CredentialsLabels.DOMAIN}:", currentUser?.loginServer ?: "")
                        InfoRowView("${CredentialsLabels.IDENTIFIER}:", currentUser?.accountName ?: "")
                    }
                    
                    InfoSection(
                        title = CredentialsLabels.TOKENS,
                        isExpanded = tokensExpanded,
                        onExpandedChange = { tokensExpanded = it }
                    ) {
                        InfoRowView("${CredentialsLabels.ACCESS_TOKEN}:", currentUser?.authToken ?: "", isSensitive = true)
                        InfoRowView("${CredentialsLabels.REFRESH_TOKEN}:", currentUser?.refreshToken ?: "", isSensitive = true)
                        InfoRowView("${CredentialsLabels.TOKEN_FORMAT}:", currentUser?.tokenFormat ?: "")
                        InfoRowView("${CredentialsLabels.JWT}:", "", isSensitive = true)
                        InfoRowView("${CredentialsLabels.AUTH_CODE}:", "", isSensitive = true)
                        InfoRowView("${CredentialsLabels.CHALLENGE_STRING}:", "", isSensitive = true)
                        InfoRowView("${CredentialsLabels.ISSUED_AT}:", "")
                        InfoRowView("${CredentialsLabels.SCOPES}:", formatScopes(currentUser))
                    }
                    
                    InfoSection(
                        title = CredentialsLabels.URLS,
                        isExpanded = urlsExpanded,
                        onExpandedChange = { urlsExpanded = it }
                    ) {
                        InfoRowView("${CredentialsLabels.INSTANCE_URL}:", currentUser?.instanceServer ?: "")
                        InfoRowView("${CredentialsLabels.API_INSTANCE_URL}:", currentUser?.apiInstanceServer ?: "")
                        InfoRowView("${CredentialsLabels.API_URL}:", "")
                        InfoRowView("${CredentialsLabels.IDENTITY_URL}:", currentUser?.idUrl ?: "")
                    }
                    
                    InfoSection(
                        title = CredentialsLabels.COMMUNITY,
                        isExpanded = communityExpanded,
                        onExpandedChange = { communityExpanded = it }
                    ) {
                        InfoRowView("${CredentialsLabels.COMMUNITY_ID}:", currentUser?.communityId ?: "")
                        InfoRowView("${CredentialsLabels.COMMUNITY_URL}:", currentUser?.communityUrl ?: "")
                    }
                    
                    InfoSection(
                        title = CredentialsLabels.DOMAINS_AND_SIDS,
                        isExpanded = domainsAndSidsExpanded,
                        onExpandedChange = { domainsAndSidsExpanded = it }
                    ) {
                        InfoRowView("${CredentialsLabels.LIGHTNING_DOMAIN}:", currentUser?.lightningDomain ?: "")
                        InfoRowView("${CredentialsLabels.LIGHTNING_SID}:", currentUser?.lightningSid ?: "", isSensitive = true)
                        InfoRowView("${CredentialsLabels.VF_DOMAIN}:", currentUser?.vfDomain ?: "")
                        InfoRowView("${CredentialsLabels.VF_SID}:", currentUser?.vfSid ?: "", isSensitive = true)
                        InfoRowView("${CredentialsLabels.CONTENT_DOMAIN}:", currentUser?.contentDomain ?: "")
                        InfoRowView("${CredentialsLabels.CONTENT_SID}:", currentUser?.contentSid ?: "", isSensitive = true)
                        InfoRowView("${CredentialsLabels.PARENT_SID}:", currentUser?.parentSid ?: "", isSensitive = true)
                        InfoRowView("${CredentialsLabels.SID_COOKIE_NAME}:", currentUser?.sidCookieName ?: "")
                    }
                    
                    InfoSection(
                        title = CredentialsLabels.COOKIES_AND_SECURITY,
                        isExpanded = cookiesAndSecurityExpanded,
                        onExpandedChange = { cookiesAndSecurityExpanded = it }
                    ) {
                        InfoRowView("${CredentialsLabels.CSRF_TOKEN}:", currentUser?.csrfToken ?: "", isSensitive = true)
                        InfoRowView("${CredentialsLabels.COOKIE_CLIENT_SRC}:", currentUser?.cookieClientSrc ?: "")
                        InfoRowView("${CredentialsLabels.COOKIE_SID_CLIENT}:", currentUser?.cookieSidClient ?: "", isSensitive = true)
                    }
                    
                    InfoSection(
                        title = CredentialsLabels.BEACON,
                        isExpanded = beaconExpanded,
                        onExpandedChange = { beaconExpanded = it }
                    ) {
                        InfoRowView("${CredentialsLabels.BEACON_CHILD_CONSUMER_KEY}:", currentUser?.beaconChildConsumerKey ?: "")
                        InfoRowView("${CredentialsLabels.BEACON_CHILD_CONSUMER_SECRET}:", currentUser?.beaconChildConsumerSecret ?: "", isSensitive = true)
                    }
                    
                    InfoSection(
                        title = CredentialsLabels.OTHER,
                        isExpanded = otherExpanded,
                        onExpandedChange = { otherExpanded = it }
                    ) {
                        InfoRowView("${CredentialsLabels.ADDITIONAL_OAUTH_FIELDS}:", formatAdditionalOAuthFields(currentUser))
                    }
                }
            }
        }
    }
    
    if (showExportAlert) {
        AlertDialog(
            onDismissRequest = { showExportAlert = false },
            title = { Text("Credentials JSON") },
            text = { Text(
                text = exportedJSON,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) },
            confirmButton = {
                TextButton(
                    onClick = {
                        copyToClipboard(context, exportedJSON)
                        showExportAlert = false
                    }
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportAlert = false }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
fun InfoSection(
    title: String,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val chevronRotation = remember { Animatable(0f) }
    
    LaunchedEffect(isExpanded) {
        chevronRotation.animateTo(if (isExpanded) 180f else 0f)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!isExpanded) }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation.value),
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun InfoRowView(
    label: String,
    value: String,
    isSensitive: Boolean = false,
) {
    var isValueVisible by remember { mutableStateOf(!isSensitive) }
    val displayValue = if (isSensitive && !isValueVisible && value.isNotEmpty()) {
        "${value.take(5)}...${value.takeLast(5)}"
    } else {
        value
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(enabled = isSensitive && value.isNotEmpty()) {
                isValueVisible = !isValueVisible
            },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.4f),
        )
        
        Text(
            text = displayValue,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.6f).padding(end = 12.dp),
        )

        if (isSensitive && value.isNotEmpty()) {
            if (isValueVisible) {
                Icon(
                    painter = painterResource(id = R.drawable.visibility_off),
                    contentDescription = "Hide sensitive content.",
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.visibility),
                    contentDescription = "Show sensitive content.",
                )
            }
        } else {
            // Add spacer so sensitive and non-sensitive fields remain aligned.
            Spacer(modifier = Modifier.width(24.dp))
        }
    }
}

private fun formatScopes(user: UserAccount?): String {
    return user?.scope?.toScopeParser()?.scopesAsString ?: ""
}

private fun formatAdditionalOAuthFields(user: UserAccount?): String {
    val fields = user?.additionalOauthValues ?: return ""
    return try {
        val json = buildJsonObject {
            fields.forEach { (key, value) ->
                put(key, value)
            }
        }
        Json { prettyPrint = true }.encodeToString(json)
    } catch (_: Exception) {
        ""
    }
}

private fun generateCredentialsJSON(user: UserAccount?): String {
    if (user == null) return "{}"

    try {
        val result = buildJsonObject {
            putJsonObject(CredentialsLabels.USER_IDENTITY) {
                put(CredentialsLabels.USERNAME, user.username ?: "")
                put(CredentialsLabels.USER_ID_LABEL, user.userId ?: "")
                put(CredentialsLabels.ORGANIZATION_ID, user.orgId ?: "")
            }

            putJsonObject(CredentialsLabels.OAUTH_CLIENT_CONFIGURATION) {
                put(CredentialsLabels.CLIENT_ID, user.clientId ?: "")
                put(CredentialsLabels.REDIRECT_URI, "")
                put(CredentialsLabels.PROTOCOL_LABEL, "")
                put(CredentialsLabels.DOMAIN, user.loginServer ?: "")
                put(CredentialsLabels.IDENTIFIER, user.accountName ?: "")
            }

            putJsonObject(CredentialsLabels.TOKENS) {
                put(CredentialsLabels.ACCESS_TOKEN, user.authToken ?: "")
                put(CredentialsLabels.REFRESH_TOKEN, user.refreshToken ?: "")
                put(CredentialsLabels.TOKEN_FORMAT, user.tokenFormat ?: "")
                put(CredentialsLabels.JWT, "")
                put(CredentialsLabels.AUTH_CODE, "")
                put(CredentialsLabels.CHALLENGE_STRING, "")
                put(CredentialsLabels.ISSUED_AT, "")
                put(CredentialsLabels.SCOPES, formatScopes(user))
            }

            putJsonObject(CredentialsLabels.URLS) {
                put(CredentialsLabels.INSTANCE_URL, user.instanceServer ?: "")
                put(CredentialsLabels.API_INSTANCE_URL, user.apiInstanceServer ?: "")
                put(CredentialsLabels.API_URL, "")
                put(CredentialsLabels.IDENTITY_URL, user.idUrl ?: "")
            }

            putJsonObject(CredentialsLabels.COMMUNITY) {
                put(CredentialsLabels.COMMUNITY_ID, user.communityId ?: "")
                put(CredentialsLabels.COMMUNITY_URL, user.communityUrl ?: "")
            }

            putJsonObject(CredentialsLabels.DOMAINS_AND_SIDS) {
                put(CredentialsLabels.LIGHTNING_DOMAIN, user.lightningDomain ?: "")
                put(CredentialsLabels.LIGHTNING_SID, user.lightningSid ?: "")
                put(CredentialsLabels.VF_DOMAIN, user.vfDomain ?: "")
                put(CredentialsLabels.VF_SID, user.vfSid ?: "")
                put(CredentialsLabels.CONTENT_DOMAIN, user.contentDomain ?: "")
                put(CredentialsLabels.CONTENT_SID, user.contentSid ?: "")
                put(CredentialsLabels.PARENT_SID, user.parentSid ?: "")
                put(CredentialsLabels.SID_COOKIE_NAME, user.sidCookieName ?: "")
            }

            putJsonObject(CredentialsLabels.COOKIES_AND_SECURITY) {
                put(CredentialsLabels.CSRF_TOKEN, user.csrfToken ?: "")
                put(CredentialsLabels.COOKIE_CLIENT_SRC, user.cookieClientSrc ?: "")
                put(CredentialsLabels.COOKIE_SID_CLIENT, user.cookieSidClient ?: "")
            }

            putJsonObject(CredentialsLabels.BEACON) {
                put(CredentialsLabels.BEACON_CHILD_CONSUMER_KEY, user.beaconChildConsumerKey ?: "")
                put(CredentialsLabels.BEACON_CHILD_CONSUMER_SECRET, user.beaconChildConsumerSecret ?: "")
            }

            putJsonObject(CredentialsLabels.OTHER) {
                put(CredentialsLabels.ADDITIONAL_OAUTH_FIELDS, formatAdditionalOAuthFields(user))
            }
        }

        return Json { prettyPrint = true }.encodeToString(result)
    } catch (_: Exception) {
        return "{}"
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Credentials JSON", text)
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
        InfoSection("Section Title", isExpanded = false, onExpandedChange = {}) {
            InfoRowView("Sensitive Label", "3aZ*GQ!o2^@8QPR", isSensitive = true)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun InfoRowSectionExpandedPreview() {
    val scheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }
    MaterialTheme(scheme) {
        InfoSection("Expanded Section Title", isExpanded = true, onExpandedChange = {}) {
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
        InfoSection("Expanded Section Title", isExpanded = true, onExpandedChange = {}) {
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
        UserCredentialsView(isExpanded = false, onExpandedChange = {}, currentUser = null)
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
        UserCredentialsView(isExpanded = true, onExpandedChange = {}, currentUser = null)
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
        UserCredentialsView(isExpanded = true, onExpandedChange = {}, currentUser = null)
    }
}