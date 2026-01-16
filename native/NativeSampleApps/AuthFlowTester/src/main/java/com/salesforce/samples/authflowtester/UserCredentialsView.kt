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

import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
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

private const val CARD_TITLE = "User Credentials"

// Section titles
private const val USER_IDENTITY = "User Identity"
private const val OAUTH_CLIENT_CONFIGURATION = "OAuth Client Configuration"
private const val TOKENS = "Tokens"
private const val URLS = "URLs"
private const val COMMUNITY = "Community"
private const val DOMAINS_AND_SIDS = "Domains and SIDs"
private const val COOKIES_AND_SECURITY = "Cookies and Security"
private const val BEACON = "Beacon"
private const val OTHER = "Other"

// User Identity fields
private const val USERNAME = "Username"
private const val USER_ID_LABEL = "User ID"
private const val ORGANIZATION_ID = "Organization ID"

// OAuth Client Configuration fields
private const val CLIENT_ID = "Client ID"
private const val DOMAIN = "Domain"

// Tokens fields
private const val ACCESS_TOKEN = "Access Token"
private const val REFRESH_TOKEN = "Refresh Token"
private const val TOKEN_FORMAT = "Token Format"
private const val SCOPES = "Scopes"

// URLs fields
private const val INSTANCE_URL = "Instance URL"
private const val API_INSTANCE_URL = "API Instance URL"
private const val IDENTITY_URL = "Identity URL"

// Community fields
private const val COMMUNITY_ID = "Community ID"
private const val COMMUNITY_URL = "Community URL"

// Domains and SIDs fields
private const val LIGHTNING_DOMAIN = "Lightning Domain"
private const val LIGHTNING_SID = "Lightning SID"
private const val VF_DOMAIN = "VF Domain"
private const val VF_SID = "VF SID"
private const val CONTENT_DOMAIN = "Content Domain"
private const val CONTENT_SID = "Content SID"
private const val PARENT_SID = "Parent SID"
private const val SID_COOKIE_NAME = "SID Cookie Name"

// Cookies and Security fields
private const val CSRF_TOKEN = "CSRF Token"
private const val COOKIE_CLIENT_SRC = "Cookie Client Src"
private const val COOKIE_SID_CLIENT = "Cookie SID Client"

// Beacon fields
private const val BEACON_CHILD_CONSUMER_KEY = "Beacon Child Consumer Key"
private const val BEACON_CHILD_CONSUMER_SECRET = "Beacon Child Consumer Secret"

// Other fields
private const val ADDITIONAL_OAUTH_FIELDS = "Additional OAuth Fields"

@Composable
fun UserCredentialsView(currentUser: UserAccount?) {
    ExpandableCard(
        title = CARD_TITLE,
        exportedJSON = generateCredentialsJSON(currentUser),
    ) {
        InfoSection(title = USER_IDENTITY) {
            InfoRowView(label = USERNAME, value = currentUser?.username)
            InfoRowView(label = USER_ID_LABEL, value = currentUser?.userId)
            InfoRowView(label = ORGANIZATION_ID, value = currentUser?.orgId)
        }

        InfoSection(title = OAUTH_CLIENT_CONFIGURATION) {
            InfoRowView(label = CLIENT_ID, value = currentUser?.clientId, isSensitive = true)
            InfoRowView(label = DOMAIN, value = currentUser?.loginServer)
        }

        InfoSection(title = TOKENS) {
            InfoRowView(label = ACCESS_TOKEN, value = currentUser?.authToken, isSensitive = true)
            InfoRowView(label = REFRESH_TOKEN, value = currentUser?.refreshToken, isSensitive = true)
            InfoRowView(label = TOKEN_FORMAT, value = currentUser?.tokenFormat)
            InfoRowView(label = SCOPES, value = formatScopes(currentUser))
        }

        InfoSection(title = URLS) {
            InfoRowView(label = INSTANCE_URL, value = currentUser?.instanceServer)
            InfoRowView(label = API_INSTANCE_URL, value = currentUser?.apiInstanceServer)
            InfoRowView(label = IDENTITY_URL, value = currentUser?.idUrl)
        }

        InfoSection(title = COMMUNITY) {
            InfoRowView(label = COMMUNITY_ID, value = currentUser?.communityId)
            InfoRowView(label = COMMUNITY_URL, value = currentUser?.communityUrl)
        }

        InfoSection(title = DOMAINS_AND_SIDS) {
            InfoRowView(label = LIGHTNING_DOMAIN, value = currentUser?.lightningDomain)
            InfoRowView(label = LIGHTNING_SID, value = currentUser?.lightningSid, isSensitive = true)
            InfoRowView(label = VF_DOMAIN, value = currentUser?.vfDomain)
            InfoRowView(label = VF_SID, value = currentUser?.vfSid, isSensitive = true)
            InfoRowView(label = CONTENT_DOMAIN, value = currentUser?.contentDomain)
            InfoRowView(label = CONTENT_SID, value = currentUser?.contentSid, isSensitive = true)
            InfoRowView(label = PARENT_SID, value = currentUser?.parentSid, isSensitive = true)
            InfoRowView(label = SID_COOKIE_NAME, value = currentUser?.sidCookieName)
        }

        InfoSection(title = COOKIES_AND_SECURITY) {
            InfoRowView(label = CSRF_TOKEN, value = currentUser?.csrfToken, isSensitive = true)
            InfoRowView(label = COOKIE_CLIENT_SRC, value = currentUser?.cookieClientSrc)
            InfoRowView(label = COOKIE_SID_CLIENT, value = currentUser?.cookieSidClient, isSensitive = true)
        }

        InfoSection(title = BEACON) {
            InfoRowView(label = BEACON_CHILD_CONSUMER_KEY, value = currentUser?.beaconChildConsumerKey)
            InfoRowView(label = BEACON_CHILD_CONSUMER_SECRET, value = currentUser?.beaconChildConsumerSecret, isSensitive = true)
        }

        InfoSection(title = OTHER) {
            InfoRowView(label = ADDITIONAL_OAUTH_FIELDS, value = formatAdditionalOAuthFields(currentUser))
        }
    }
}

private fun formatScopes(user: UserAccount?): String? {
    return user?.scope?.toScopeParser()?.scopesAsString
}

private fun formatAdditionalOAuthFields(user: UserAccount?): String? {
    val fields = user?.additionalOauthValues ?: return null
    return try {
        val json = buildJsonObject {
            fields.forEach { (key, value) ->
                put(key, value)
            }
        }
        Json { prettyPrint = true }.encodeToString(json)
    } catch (_: Exception) {
        null
    }
}

private fun generateCredentialsJSON(user: UserAccount?): String {
    if (user == null) return "{}"

    try {
        val result = buildJsonObject {
            putJsonObject(USER_IDENTITY) {
                put(USERNAME, user.username)
                put(USER_ID_LABEL, user.userId)
                put(ORGANIZATION_ID, user.orgId)
            }

            putJsonObject(OAUTH_CLIENT_CONFIGURATION) {
                put(CLIENT_ID, user.clientId)
                put(DOMAIN, user.loginServer)
            }

            putJsonObject(TOKENS) {
                put(ACCESS_TOKEN, user.authToken)
                put(REFRESH_TOKEN, user.refreshToken)
                put(TOKEN_FORMAT, user.tokenFormat)
                put(SCOPES, formatScopes(user))
            }

            putJsonObject(URLS) {
                put(INSTANCE_URL, user.instanceServer)
                put(API_INSTANCE_URL, user.apiInstanceServer)
                put(IDENTITY_URL, user.idUrl)
            }

            putJsonObject(COMMUNITY) {
                put(COMMUNITY_ID, user.communityId)
                put(COMMUNITY_URL, user.communityUrl)
            }

            putJsonObject(DOMAINS_AND_SIDS) {
                put(LIGHTNING_DOMAIN, user.lightningDomain)
                put(LIGHTNING_SID, user.lightningSid)
                put(VF_DOMAIN, user.vfDomain)
                put(VF_SID, user.vfSid)
                put(CONTENT_DOMAIN, user.contentDomain)
                put(CONTENT_SID, user.contentSid)
                put(PARENT_SID, user.parentSid)
                put(SID_COOKIE_NAME, user.sidCookieName)
            }

            putJsonObject(COOKIES_AND_SECURITY) {
                put(CSRF_TOKEN, user.csrfToken)
                put(COOKIE_CLIENT_SRC, user.cookieClientSrc)
                put(COOKIE_SID_CLIENT, user.cookieSidClient)
            }

            putJsonObject(BEACON) {
                put(BEACON_CHILD_CONSUMER_KEY, user.beaconChildConsumerKey)
                put(BEACON_CHILD_CONSUMER_SECRET, user.beaconChildConsumerSecret)
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
        UserCredentialsView(currentUser = null)
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
        UserCredentialsView(currentUser = null)
    }
}