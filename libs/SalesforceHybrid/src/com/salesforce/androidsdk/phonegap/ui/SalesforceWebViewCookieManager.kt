/*
 * Copyright (c) 2024-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap.ui

import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger.d
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger.w
import java.net.URI

class SalesforceWebViewCookieManager {
    private val cookieManager = CookieManager.getInstance()

    fun setCookies(userAccount: UserAccount) {
        d(TAG, "setCookies for userAccount:${userAccount.toJson()}")

        val instanceUrl = userAccount.instanceServer
        val lightningDomain = userAccount.lightningDomain
        val lightningSid = userAccount.lightningSid
        val contentDomain = userAccount.contentDomain
        val contentSid = userAccount.contentSid
        val mainSid = if (userAccount.tokenFormat == "jwt") userAccount.parentSid else userAccount.authToken
        val vfDomain = userAccount.vfDomain
        val vfSid = userAccount.vfSid
        val clientSrc = userAccount.cookieClientSrc
        val sidClient = userAccount.cookieSidClient
        val sidCookieName = userAccount.sidCookieName
        val csrfToken = userAccount.csrfToken
        val orgId = userAccount.orgId
        val communityUrl = userAccount.communityUrl
        val mainDomain = getDomainFromUrl(instanceUrl)

        // Setup domain for community to avoid dupe cookies
        // See https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#define_where_cookies_are_sent
        val setDomain = !communityUrl.isNullOrBlank()

        // Main domain cookies
        setCookieValue("sid for main", mainDomain, setDomain, sidCookieName, mainSid)
        setCookieValue(CLIENT_SRC, mainDomain, setDomain, CLIENT_SRC, clientSrc)
        setCookieValue(SID_CLIENT, mainDomain, setDomain, SID_CLIENT, sidClient)
        setCookieValue(ORG_ID, mainDomain, setDomain, ORG_ID, orgId)
        setCookieValue(csrfTokenCookieName, mainDomain, setDomain, csrfTokenCookieName, csrfToken)

        // Lightning domain cookies
        setCookieValue("sid for lightning", lightningDomain, setDomain, sidCookieName, lightningSid)
        setCookieValue(csrfTokenCookieName, lightningDomain, setDomain, csrfTokenCookieName, csrfToken)

        // Content domain cookies
        setCookieValue("sid for content", contentDomain, setDomain, sidCookieName, contentSid)

        // Vf domain cookies
        setCookieValue("sid for vf", vfDomain, setDomain, sidCookieName, vfSid)
        setCookieValue(CLIENT_SRC, vfDomain, setDomain, CLIENT_SRC, clientSrc)
        setCookieValue(SID_CLIENT, vfDomain, setDomain, SID_CLIENT, sidClient)
        setCookieValue(ORG_ID, vfDomain, setDomain, ORG_ID, orgId)

        syncCookies()
    }

    private fun setCookieValue(
        cookieType: String, domain: String?, setDomain: Boolean, name: String?, value: String?
    ) {
        if (!domain.isNullOrBlank() && !name.isNullOrBlank() && !value.isNullOrBlank()) {
            val url = getHttpsUrlFromDomain(domain)

            val builder = StringBuilder()
            builder.append("$name=$value")

            if (setDomain) {
                builder.append("$DOMAIN_PREFIX$domain")
            }

            // Set 'SameSite=None' param.
            // NB will NOT work on old versions of chrome
            // (https://www.chromium.org/updates/same-site/incompatible-clients/).
            builder.append(PATH)
            // We need to set secure param when setting SameSite=None
            builder.append(SECURE)
            builder.append(SAME_SITE)
            cookieManager.setCookie(url, builder.toString())

            d(TAG, "setCookieValue - Set $cookieType in domain:$domain with value:$value")
        } else {
            w(TAG, "setCookieValue - Unable to set $cookieType in domain:$domain")
        }
    }

    /** Syncs the cookies for webview  */
    private fun syncCookies() {
        cookieManager.flush()
        // Give enough time to yield
        SystemClock.sleep(SLEEP_TIME_SYNC)
    }

    companion object {
        const val CLIENT_SRC = "clientSrc"
        const val SID_CLIENT = "sid_Client"
        const val ORG_ID = "oid"
        private const val PATH = "; path=/"
        private const val SECURE = "; secure"
        private const val SAME_SITE = "; SameSite=None"
        private const val DOMAIN_PREFIX = "; domain="
        const val SLEEP_TIME_SYNC: Long = 15
        private const val TAG = "SalesforceWebViewCookieManager"
        private const val csrfTokenCookieName = "eikoocnekotMob"
    }

    private fun getHttpsUrlFromDomain(domain: String): String {
        // Prepend with https:// if it doesn't have a protocol
        var url = domain
        if (domain != null && !domain.contains("://")) {
            val builder = Uri.Builder()
            url = builder.scheme("https").authority(domain).build().toString()
        }
        return url
    }

    private fun getDomainFromUrl(url: String): String {
        val uri = URI(url)
        return uri.host
    }
}