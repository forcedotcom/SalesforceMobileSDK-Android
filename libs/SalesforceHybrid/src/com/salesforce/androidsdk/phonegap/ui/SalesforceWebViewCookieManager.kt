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
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger.w
import java.net.URI

class SalesforceWebViewCookieManager {
    private val cookieManager = CookieManager.getInstance()

    private fun getAdditionalOauthValues(
        userAccount: UserAccount?,
        key: String?
    ): String? {
        userAccount?.additionalOauthValues?.let {
            return it[key]
        }
        return null
    }

    /**
     * Helper method to check if current account has available cookies
     *
     * @return return true if account provides; otherwise, false
     */
    fun areCookiesAvailable(userAccount: UserAccount): Boolean {
        val lightningDomain = userAccount.lightningDomain
        val lightningSid = userAccount.lightningSid
        val contentDomain = userAccount.contentDomain
        val contentSid = userAccount.contentSid
        val vfDomain = userAccount.vfDomain
        val vfSid = userAccount.vfSid
        val areCookiesAvailable =
            lightningDomain != null &&
                    lightningSid != null &&
                    contentDomain != null &&
                    contentSid != null &&
                    vfDomain != null &&
                    vfSid != null
        w(TAG, "areCookiesAvailable - Are cookies available $areCookiesAvailable")
        return areCookiesAvailable
    }

    @Suppress("LongMethod")
    fun setCookies(userAccount:UserAccount) {
        val instanceUrl = userAccount.instanceServer
        val lightningDomain = userAccount.lightningDomain
        val lightningSid = userAccount.lightningSid
        val contentDomain = userAccount.contentDomain
        val contentSid = userAccount.contentSid
        val accessToken = userAccount.authToken
        val vfDomain = userAccount.vfDomain
        val vfSid = userAccount.vfSid
        val clientSrc = getAdditionalOauthValues(userAccount, COOKIE_CLIENT_SRC)
        val sidClient = getAdditionalOauthValues(userAccount, COOKIE_SID_CLIENT)
        val sidCookieName = getAdditionalOauthValues(userAccount, SID_COOKIE_NAME)
        val csrfToken = userAccount.csrfToken
        val orgId = userAccount.orgId
        val communityUrl = userAccount.communityUrl
        val isCommunity = !communityUrl.isNullOrBlank()

        setMainDomainCookies(
            instanceUrl,
            getDomainFromUrl(instanceUrl),
            isCommunity,
            sidCookieName,
            accessToken,
            clientSrc,
            sidClient,
            csrfToken,
            orgId
        )

        setLightningDomainCookies(lightningDomain, isCommunity, sidCookieName, lightningSid, csrfToken)

        // set content domain cookies
        if (!sidCookieName.isNullOrBlank() && !contentDomain.isNullOrBlank() && !contentSid.isNullOrBlank()) {
            setCookieValue(getHttpsUrlFromDomain(contentDomain), contentDomain, isCommunity, sidCookieName, contentSid)
        } else {
            w(TAG, "setCookies - Unable to set content domain sid  cookie")
        }

        setVfDomainCookies(vfDomain, isCommunity, sidCookieName, vfSid, clientSrc, sidClient, orgId)

        syncCookies()
    }

    private fun setMainDomainCookies(
        url: String,
        domain: String?,
        isCommunity: Boolean,
        sidCookieName: String?,
        accessToken: String?,
        clientSrc: String?,
        sidClient: String?,
        csrfToken: String?,
        orgId: String?
    ) {
        // set main sid cookie
        if (!domain.isNullOrBlank()) {
            if (!sidCookieName.isNullOrBlank() && !accessToken.isNullOrBlank()) {
                setCookieValue(url, domain, isCommunity, sidCookieName, accessToken)
            } else {
                w(TAG, "setMainDomainCookies - Unable to set main sid cookie")
            }
            // set clientSrc cookie
            setCookieValue(url, domain, isCommunity, CLIENT_SRC, clientSrc)
            // set sid_Client cookie
            setCookieValue(url, domain, isCommunity, SID_CLIENT, sidClient)
            // set oid cookie
            if (!orgId.isNullOrBlank()) {
                setCookieValue(url, domain, isCommunity, ORG_ID, orgId.toString())
            } else {
                w(TAG, "setMainDomainCookies - Unable to set oid in main Domain")
            }
            // set csrf cookie
            setCookieValue(url, domain, isCommunity, csrfTokenCookieName, csrfToken)
            if (csrfToken.isNullOrBlank()) {
                w(TAG, "setMainDomainCookies - csrfToken is empty or null when setting on main domain")
            }
        } else {
            w(TAG, "setMainDomainCookies - Unable to set main domain cookies, domain is null or empty")
        }
    }

    private fun setLightningDomainCookies(
        domain: String?,
        isCommunity: Boolean,
        sidCookieName: String?,
        lightningSid: String?,
        csrfToken: String?
    ) {
        // set lightning domain cookies
        if (!sidCookieName.isNullOrBlank() && !domain.isNullOrBlank() && !lightningSid.isNullOrBlank()) {
            setCookieValue(getHttpsUrlFromDomain(domain), domain, isCommunity, sidCookieName, lightningSid)
        } else {
            w(TAG, "setLightningDomainCookies - Unable to set lightning sid cookie")
        }

        if (!csrfToken.isNullOrBlank() && !domain.isNullOrBlank()) {
            setCookieValue(getHttpsUrlFromDomain(domain), domain, isCommunity, csrfTokenCookieName, csrfToken)
        } else {
            w(TAG, "setLightningDomainCookies - Unable to set csrfToken in Lightning Domain")
        }
    }

    private fun setVfDomainCookies(
        domain: String?,
        isCommunity: Boolean,
        sidCookieName: String?,
        vfSid: String?,
        clientSrc: String?,
        sidClient: String?,
        orgId: String?
    ) {
        // set visualforce domain cookies
        if (!domain.isNullOrBlank()) {
            val url = getHttpsUrlFromDomain(domain)
            // set sid cookie
            if (!sidCookieName.isNullOrBlank() && !vfSid.isNullOrBlank()) {
                setCookieValue(url, domain, isCommunity, sidCookieName, vfSid)
            } else {
                w(TAG, "setVfDomainCookies - Unable to set vf sid cookie")
            }
            // set clientSrc cookie
            setCookieValue(url, domain, isCommunity, CLIENT_SRC, clientSrc)
            // set sid_Client cookie
            setCookieValue(url, domain, isCommunity, SID_CLIENT, sidClient)
            // set oid cookie
            if (!orgId.isNullOrBlank()) {
                setCookieValue(url, domain, isCommunity, ORG_ID, orgId.toString())
            } else {
                w(TAG, "setVfDomainCookies - Unable to set oid in vfDomain")
            }
        } else {
            w(TAG, "setVfDomainCookies - Unable to set vf cookies, domain is null or empty")
        }
    }

    private fun setCookieValue(
        url: String,
        domain: String,
        isCommunity: Boolean,
        name: String,
        value: String?,

    ) {
        if (!value.isNullOrBlank()) {

            val builder = StringBuilder()
            builder.append("$name=$value")

            // Setup domain for community to avoid dupe cookies
            // See https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#define_where_cookies_are_sent
            if (domain.isNotBlank() && isCommunity) {
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

        } else {
            w(TAG, "setCookieValue - Unable to set $name in $domain")
        }
    }

    /** Syncs the cookies for webview  */
    fun syncCookies() {
        cookieManager.flush()
        // Give enough time to yield
        SystemClock.sleep(SLEEP_TIME_SYNC)
    }

    companion object {
        const val COOKIE_CLIENT_SRC = "cookie-clientSrc"
        const val COOKIE_SID_CLIENT = "cookie-sid_Client"
        const val SID_COOKIE_NAME = "sidCookieName"
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

    fun getHttpsUrlFromDomain(domain: String): String {
        // Prepend with https:// if it doesn't have a protocol
        var url = domain
        if (domain != null && !domain.contains("://")) {
            val builder = Uri.Builder()
            url = builder.scheme("https").authority(domain).build().toString()
        }
        return url
    }

    fun getDomainFromUrl(url: String): String {
        val uri = URI(url)
        return uri.host
    }
}