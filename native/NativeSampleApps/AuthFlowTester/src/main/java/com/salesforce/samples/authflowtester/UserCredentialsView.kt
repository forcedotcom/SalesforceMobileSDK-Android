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

private const val USER_IDENTITY = "User Identity"
private const val OAUTH_CLIENT_CONFIGURATION = "OAuth Client Configuration"
private const val TOKENS = "Tokens"
private const val URLS = "URLs"
private const val COMMUNITY = "Community"
private const val DOMAINS_AND_SIDS = "Domains and SIDs"
private const val COOKIES_AND_SECURITY = "Cookies and Security"
private const val BEACON = "Beacon"
private const val OTHER = "Other"

private const val USERNAME = "Username"
private const val USER_ID_LABEL = "User ID"
private const val ORGANIZATION_ID = "Organization ID"

private const val CLIENT_ID = "Client ID"
private const val REDIRECT_URI = "Redirect URI"
private const val PROTOCOL_LABEL = "Protocol"
private const val DOMAIN = "Domain"
private const val IDENTIFIER = "Identifier"

private const val ACCESS_TOKEN = "Access Token"
private const val REFRESH_TOKEN = "Refresh Token"
private const val TOKEN_FORMAT = "Token Format"
private const val JWT = "JWT"
private const val AUTH_CODE = "Auth Code"
private const val CHALLENGE_STRING = "Challenge String"
private const val ISSUED_AT = "Issued At"
private const val SCOPES = "Scopes"

private const val INSTANCE_URL = "Instance URL"
private const val API_INSTANCE_URL = "API Instance URL"
private const val API_URL = "API URL"
private const val IDENTITY_URL = "Identity URL"

private const val COMMUNITY_ID = "Community ID"
private const val COMMUNITY_URL = "Community URL"

private const val LIGHTNING_DOMAIN = "Lightning Domain"
private const val LIGHTNING_SID = "Lightning SID"
private const val VF_DOMAIN = "VF Domain"
private const val VF_SID = "VF SID"
private const val CONTENT_DOMAIN = "Content Domain"
private const val CONTENT_SID = "Content SID"
private const val PARENT_SID = "Parent SID"
private const val SID_COOKIE_NAME = "SID Cookie Name"

private const val CSRF_TOKEN = "CSRF Token"
private const val COOKIE_CLIENT_SRC = "Cookie Client Src"
private const val COOKIE_SID_CLIENT = "Cookie SID Client"

private const val BEACON_CHILD_CONSUMER_KEY = "Beacon Child Consumer Key"
private const val BEACON_CHILD_CONSUMER_SECRET = "Beacon Child Consumer Secret"

private const val ADDITIONAL_OAUTH_FIELDS = "Additional OAuth Fields"

@Composable
fun UserCredentialsView(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    currentUser: UserAccount?,
) {
    val context = LocalContext.current
    var showExportAlert by remember { mutableStateOf(false) }
    var exportedJSON by remember { mutableStateOf("") }
    
    var userIdentityExpanded by remember { mutableStateOf(true) }
    var oauthConfigExpanded by remember { mutableStateOf(true) }
    var tokensExpanded by remember { mutableStateOf(true) }
    var urlsExpanded by remember { mutableStateOf(true) }
    var communityExpanded by remember { mutableStateOf(true) }
    var domainsAndSidsExpanded by remember { mutableStateOf(true) }
    var cookiesAndSecurityExpanded by remember { mutableStateOf(true) }
    var beaconExpanded by remember { mutableStateOf(true) }
    var otherExpanded by remember { mutableStateOf(true) }
    
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
                        title = USER_IDENTITY,
                        isExpanded = userIdentityExpanded,
                        onExpandedChange = { userIdentityExpanded = it }
                    ) {
                        InfoRowView("${USERNAME}:", currentUser?.username ?: "")
                        InfoRowView("${USER_ID_LABEL}:", currentUser?.userId ?: "")
                        InfoRowView("${ORGANIZATION_ID}:", currentUser?.orgId ?: "")
                    }
                    
                    InfoSection(
                        title = OAUTH_CLIENT_CONFIGURATION,
                        isExpanded = oauthConfigExpanded,
                        onExpandedChange = { oauthConfigExpanded = it }
                    ) {
                        InfoRowView("${CLIENT_ID}:", currentUser?.clientId ?: "", isSensitive = true)
                        InfoRowView("${REDIRECT_URI}:", "")
                        InfoRowView("${PROTOCOL_LABEL}:", "")
                        InfoRowView("${DOMAIN}:", currentUser?.loginServer ?: "")
                        InfoRowView("${IDENTIFIER}:", currentUser?.accountName ?: "")
                    }
                    
                    InfoSection(
                        title = TOKENS,
                        isExpanded = tokensExpanded,
                        onExpandedChange = { tokensExpanded = it }
                    ) {
                        InfoRowView("${ACCESS_TOKEN}:", currentUser?.authToken ?: "", isSensitive = true)
                        InfoRowView("${REFRESH_TOKEN}:", currentUser?.refreshToken ?: "", isSensitive = true)
                        InfoRowView("${TOKEN_FORMAT}:", currentUser?.tokenFormat ?: "")
                        InfoRowView("${JWT}:", "", isSensitive = true)
                        InfoRowView("${AUTH_CODE}:", "", isSensitive = true)
                        InfoRowView("${CHALLENGE_STRING}:", "", isSensitive = true)
                        InfoRowView("${ISSUED_AT}:", "")
                        InfoRowView("${SCOPES}:", formatScopes(currentUser))
                    }
                    
                    InfoSection(
                        title = URLS,
                        isExpanded = urlsExpanded,
                        onExpandedChange = { urlsExpanded = it }
                    ) {
                        InfoRowView("${INSTANCE_URL}:", currentUser?.instanceServer ?: "")
                        InfoRowView("${API_INSTANCE_URL}:", currentUser?.apiInstanceServer ?: "")
                        InfoRowView("${API_URL}:", "")
                        InfoRowView("${IDENTITY_URL}:", currentUser?.idUrl ?: "")
                    }
                    
                    InfoSection(
                        title = COMMUNITY,
                        isExpanded = communityExpanded,
                        onExpandedChange = { communityExpanded = it }
                    ) {
                        InfoRowView("${COMMUNITY_ID}:", currentUser?.communityId ?: "")
                        InfoRowView("${COMMUNITY_URL}:", currentUser?.communityUrl ?: "")
                    }
                    
                    InfoSection(
                        title = DOMAINS_AND_SIDS,
                        isExpanded = domainsAndSidsExpanded,
                        onExpandedChange = { domainsAndSidsExpanded = it }
                    ) {
                        InfoRowView("${LIGHTNING_DOMAIN}:", currentUser?.lightningDomain ?: "")
                        InfoRowView("${LIGHTNING_SID}:", currentUser?.lightningSid ?: "", isSensitive = true)
                        InfoRowView("${VF_DOMAIN}:", currentUser?.vfDomain ?: "")
                        InfoRowView("${VF_SID}:", currentUser?.vfSid ?: "", isSensitive = true)
                        InfoRowView("${CONTENT_DOMAIN}:", currentUser?.contentDomain ?: "")
                        InfoRowView("${CONTENT_SID}:", currentUser?.contentSid ?: "", isSensitive = true)
                        InfoRowView("${PARENT_SID}:", currentUser?.parentSid ?: "", isSensitive = true)
                        InfoRowView("${SID_COOKIE_NAME}:", currentUser?.sidCookieName ?: "")
                    }
                    
                    InfoSection(
                        title = COOKIES_AND_SECURITY,
                        isExpanded = cookiesAndSecurityExpanded,
                        onExpandedChange = { cookiesAndSecurityExpanded = it }
                    ) {
                        InfoRowView("${CSRF_TOKEN}:", currentUser?.csrfToken ?: "", isSensitive = true)
                        InfoRowView("${COOKIE_CLIENT_SRC}:", currentUser?.cookieClientSrc ?: "")
                        InfoRowView("${COOKIE_SID_CLIENT}:", currentUser?.cookieSidClient ?: "", isSensitive = true)
                    }
                    
                    InfoSection(
                        title = BEACON,
                        isExpanded = beaconExpanded,
                        onExpandedChange = { beaconExpanded = it }
                    ) {
                        InfoRowView("${BEACON_CHILD_CONSUMER_KEY}:", currentUser?.beaconChildConsumerKey ?: "")
                        InfoRowView("${BEACON_CHILD_CONSUMER_SECRET}:", currentUser?.beaconChildConsumerSecret ?: "", isSensitive = true)
                    }
                    
                    InfoSection(
                        title = OTHER,
                        isExpanded = otherExpanded,
                        onExpandedChange = { otherExpanded = it }
                    ) {
                        InfoRowView("${ADDITIONAL_OAUTH_FIELDS}:", formatAdditionalOAuthFields(currentUser))
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
            putJsonObject(USER_IDENTITY) {
                put(USERNAME, user.username ?: "")
                put(USER_ID_LABEL, user.userId ?: "")
                put(ORGANIZATION_ID, user.orgId ?: "")
            }

            putJsonObject(OAUTH_CLIENT_CONFIGURATION) {
                put(CLIENT_ID, user.clientId ?: "")
                put(REDIRECT_URI, "")
                put(PROTOCOL_LABEL, "")
                put(DOMAIN, user.loginServer ?: "")
                put(IDENTIFIER, user.accountName ?: "")
            }

            putJsonObject(TOKENS) {
                put(ACCESS_TOKEN, user.authToken ?: "")
                put(REFRESH_TOKEN, user.refreshToken ?: "")
                put(TOKEN_FORMAT, user.tokenFormat ?: "")
                put(JWT, "")
                put(AUTH_CODE, "")
                put(CHALLENGE_STRING, "")
                put(ISSUED_AT, "")
                put(SCOPES, formatScopes(user))
            }

            putJsonObject(URLS) {
                put(INSTANCE_URL, user.instanceServer ?: "")
                put(API_INSTANCE_URL, user.apiInstanceServer ?: "")
                put(API_URL, "")
                put(IDENTITY_URL, user.idUrl ?: "")
            }

            putJsonObject(COMMUNITY) {
                put(COMMUNITY_ID, user.communityId ?: "")
                put(COMMUNITY_URL, user.communityUrl ?: "")
            }

            putJsonObject(DOMAINS_AND_SIDS) {
                put(LIGHTNING_DOMAIN, user.lightningDomain ?: "")
                put(LIGHTNING_SID, user.lightningSid ?: "")
                put(VF_DOMAIN, user.vfDomain ?: "")
                put(VF_SID, user.vfSid ?: "")
                put(CONTENT_DOMAIN, user.contentDomain ?: "")
                put(CONTENT_SID, user.contentSid ?: "")
                put(PARENT_SID, user.parentSid ?: "")
                put(SID_COOKIE_NAME, user.sidCookieName ?: "")
            }

            putJsonObject(COOKIES_AND_SECURITY) {
                put(CSRF_TOKEN, user.csrfToken ?: "")
                put(COOKIE_CLIENT_SRC, user.cookieClientSrc ?: "")
                put(COOKIE_SID_CLIENT, user.cookieSidClient ?: "")
            }

            putJsonObject(BEACON) {
                put(BEACON_CHILD_CONSUMER_KEY, user.beaconChildConsumerKey ?: "")
                put(BEACON_CHILD_CONSUMER_SECRET, user.beaconChildConsumerSecret ?: "")
            }

            putJsonObject(OTHER) {
                put(ADDITIONAL_OAUTH_FIELDS, formatAdditionalOAuthFields(user))
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