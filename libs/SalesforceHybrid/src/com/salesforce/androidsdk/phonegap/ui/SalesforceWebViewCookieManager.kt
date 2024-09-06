/*
 * Copyright (c) 2021 Salesforce.com.
 * All Rights Reserved.
 * Company Confidential.
 */
package com.salesforce.aura

import android.net.Uri
import android.webkit.CookieManager
import androidx.annotation.VisibleForTesting
import com.salesforce.android.common.logging.LogFactory
import com.salesforce.core.dagger.CoreInjector
import com.salesforce.core.settings.FeatureManager
import com.salesforce.mobile.analytics.EventLogger
import com.salesforce.mobile.analytics.ept.SalesforceAILTNEvent
import com.salesforce.msdkabstraction.data.user.UserAccount
import io.reactivex.functions.Action
import org.json.JSONObject
import java.net.URI
import java.util.logging.Level

class BridgeCookieManager(private val userAccount: UserAccount) {
    private val logger = LogFactory.getSBILogger(BridgeCookieManager::class.java)
    private val tag = BridgeCookieManager::class.java.simpleName
    private val cookieManager = CookieManager.getInstance()
    val csrfTokenCookieName = "eikoocnekotMob"

    internal var featureManager: FeatureManager = CoreInjector.component.feature()

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
    fun areCookiesAvailable(): Boolean {
        val lightningDomain = getAdditionalOauthValues(userAccount, LIGHTNING_DOMAIN)
        val lightningSid = getAdditionalOauthValues(userAccount, LIGHTNING_SID)
        val contentDomain = getAdditionalOauthValues(userAccount, CONTENT_DOMAIN)
        val contentSid = getAdditionalOauthValues(userAccount, CONTENT_SID)
        val vfDomain = userAccount?.vfDomain
        val vfSid = userAccount?.vfSid
        val areCookiesAvailable =
            lightningDomain != null &&
                    lightningSid != null &&
                    contentDomain != null &&
                    contentSid != null &&
                    vfDomain != null &&
                    vfSid != null
        logger.logp(
            Level.WARNING,
            tag,
            "areCookiesAvailable",
            "Are cookies available $areCookiesAvailable"
        )
        return areCookiesAvailable
    }

    @Suppress("LongMethod")
    fun setCookies(
        instanceUrl: String,
        credentials: JSONObject?,
        action: Action?
    ) {
        // Get current values saved in additional oauth values from mobile sdk
        var lightningDomain = getAdditionalOauthValues(userAccount, LIGHTNING_DOMAIN)
        var lightningSid = getAdditionalOauthValues(userAccount, LIGHTNING_SID)
        var contentDomain = getAdditionalOauthValues(userAccount, CONTENT_DOMAIN)
        var contentSid = getAdditionalOauthValues(userAccount, CONTENT_SID)
        var accessToken = userAccount.authToken
        var vfDomain = userAccount?.vfDomain
        var vfSid = userAccount?.vfSid
        var clientSrc = getAdditionalOauthValues(userAccount, COOKIE_CLIENT_SRC)
        var sidClient = getAdditionalOauthValues(userAccount, COOKIE_SID_CLIENT)
        var sidCookieName = getAdditionalOauthValues(userAccount, SID_COOKIE_NAME)
        var csrfToken = getAdditionalOauthValues(userAccount, CSRFTOKEN)

        if (credentials != null) {
            // Set new values if provided
            accessToken = credentials.optString(CookieSyncHelper.CB_ACCESS_TOKEN, accessToken)
            lightningSid = credentials.optString(LIGHTNING_SID, lightningSid)
            lightningDomain =
                credentials.optString(LIGHTNING_DOMAIN, lightningDomain)
            contentSid = credentials.optString(CONTENT_SID, contentSid)
            contentDomain = credentials.optString(CONTENT_DOMAIN, contentDomain)
            vfSid = credentials.optString(VF_SID, vfSid)
            vfDomain = credentials.optString(VF_DOMAIN, vfDomain)
            clientSrc = credentials.optString(COOKIE_CLIENT_SRC, clientSrc)
            sidClient = credentials.optString(COOKIE_SID_CLIENT, sidClient)
            if (csrfToken != credentials.optString(CSRFTOKEN)) {
                logger.logp(
                    Level.WARNING,
                    tag,
                    "setCookies",
                    "csrfToken updated, using new token"
                )
            }
            csrfToken = credentials.optString(CSRFTOKEN, csrfToken)
        }

        setMainDomainCookies(
            instanceUrl,
            getDomainFromUrl(instanceUrl),
            sidCookieName,
            accessToken,
            clientSrc,
            sidClient,
            csrfToken
        )

        setLightningDomainCookies(lightningDomain, sidCookieName, lightningSid, csrfToken)

        // set content domain cookies
        if (!sidCookieName.isNullOrBlank() && !contentDomain.isNullOrBlank() && !contentSid.isNullOrBlank()) {
            setCookieValue(getHttpsUrlFromDomain(contentDomain), contentDomain, sidCookieName, contentSid)
        } else {
            logger.logp(
                Level.WARNING,
                tag,
                "setCookies",
                "Unable to set content domain sid  cookie"
            )
        }

        setVfDomainCookies(vfDomain, sidCookieName, vfSid, clientSrc, sidClient)

        CookieSyncUtil.syncCookies()

        EventLogger.getInstance()
            .endAILTNEvent(
                AuraHelper.SET_COOKIES,
                null,
                null,
                null,
                SalesforceAILTNEvent.PERFORMANCE
            )
        action?.let {
            it.run()
        }
    }

    private fun setMainDomainCookies(
        url: String,
        domain: String?,
        sidCookieName: String?,
        accessToken: String?,
        clientSrc: String?,
        sidClient: String?,
        csrfToken: String?
    ) {
        // set main sid cookie
        if (!domain.isNullOrBlank()) {
            if (!sidCookieName.isNullOrBlank() && !accessToken.isNullOrBlank()) {
                setCookie(url, sidCookieName, accessToken, domain)
            } else {
                logger.logp(
                    Level.WARNING,
                    tag,
                    "setSidCookies",
                    "Unable to set main sid  cookie"
                )
            }
            // set clientSrc cookie
            setCookieValue(url, domain, CLIENT_SRC, clientSrc)
            // set sid_Client cookie
            setCookieValue(url, domain, SID_CLIENT, sidClient)
            // set oid cookie
            if (!userAccount.orgId.isNullOrBlank()) {
                setCookieValue(url, domain, ORG_ID, userAccount.orgId.toString())
            } else {
                logger.logp(
                    Level.WARNING,
                    tag,
                    "setSidCookies",
                    "Unable to set oid in main Domain"
                )
            }
            // set csrf cookie
            setCookieValue(url, domain, csrfTokenCookieName, csrfToken)
            if (csrfToken.isNullOrBlank()) {
                logger.logp(
                    Level.WARNING,
                    tag,
                    "setSidCookies",
                    "csrfToken is empty or null when setting on main domain"
                )
            }
        } else {
            logger.logp(
                Level.WARNING,
                tag,
                "setSidCookies",
                "Unable to set main domain cookies, domain is null or empty"
            )
        }
    }

    private fun setLightningDomainCookies(
        domain: String?,
        sidCookieName: String?,
        lightningSid: String?,
        csrfToken: String?
    ) {
        // set lightning domain cookies
        if (!sidCookieName.isNullOrBlank() && !domain.isNullOrBlank() && !lightningSid.isNullOrBlank()) {
            setCookieValue(getHttpsUrlFromDomain(domain), domain, sidCookieName, lightningSid)
        } else {
            logger.logp(
                Level.WARNING,
                tag,
                "setLightningDomainCookies",
                "Unable to set lightning sid  cookie"
            )
        }

        if (!csrfToken.isNullOrBlank() && !domain.isNullOrBlank()) {
            setCookieValue(getHttpsUrlFromDomain(domain), domain, csrfTokenCookieName, csrfToken)
        } else {
            logger.logp(
                Level.WARNING,
                tag,
                "setSidCookies",
                "Unable to set csrfToken in Lightning Domain"
            )
        }
    }

    private fun setVfDomainCookies(
        domain: String?,
        sidCookieName: String?,
        vfSid: String?,
        clientSrc: String?,
        sidClient: String?
    ) {
        // set visualforce domain cookies
        if (!domain.isNullOrBlank()) {
            val url = getHttpsUrlFromDomain(domain)
            // set sid cookie
            if (!sidCookieName.isNullOrBlank() && !vfSid.isNullOrBlank()) {
                setCookieValue(url, domain, sidCookieName, vfSid)
            } else {
                logger.logp(
                    Level.WARNING,
                    tag,
                    "setSidCookies",
                    "Unable to set vf sid cookie"
                )
            }
            // set clientSrc cookie
            setCookieValue(url, domain, CLIENT_SRC, clientSrc)
            // set sid_Client cookie
            setCookieValue(url, domain, SID_CLIENT, sidClient)
            // set oid cookie
            if (!userAccount.orgId.isNullOrBlank()) {
                setCookieValue(url, domain, ORG_ID, userAccount.orgId.toString())
            } else {
                logger.logp(
                    Level.WARNING,
                    tag,
                    "setSidCookies",
                    "Unable to set oid in vfDomain"
                )
            }
        } else {
            logger.logp(
                Level.WARNING,
                tag,
                "setSidCookies",
                "Unable to set vf cookies, domain is null or empty"
            )
        }
    }

    private fun setCookieValue(
        url: String,
        domain: String,
        name: String,
        value: String?
    ) {
        if (!value.isNullOrBlank()) {
            setCookie(url, name, value, domain)
        } else {
            logger.logp(
                Level.WARNING,
                tag,
                "setCookieValue",
                "Unable to set $name in $domain"
            )
        }
    }

    @VisibleForTesting
    fun setCookie(
        url: String,
        name: String,
        value: String,
        domain: String
    ) {
        val builder = StringBuilder()
        builder.append("$name=$value")

        // Setup domain for community to avoid dupe cookies
        // See https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#define_where_cookies_are_sent
        if (domain.isNotBlank() && !userAccount.communityUrl.isNullOrBlank()) {
            builder.append("$DOMAIN_PREFIX$domain")
        }

        // Set same-site param only when chrome version is >= 76. This feature is available as of
        // Chrome 76 by enabling the same-site-by-default-cookies flag
        // (https://chromestatus.com/feature/5088147346030592)
        // Certain older version of chrome(51-66), UC Browser on Android prior to version 12.13.2
        // will reject a cookie with 'SameSite=None'
        // (https://www.chromium.org/updates/same-site/incompatible-clients/).
        // So lets apply the 'SameSite=None' param to only webview version 76 and above.
        val webviewVer =
            AuraHelper.getWebviewMajorVersion(
                AuraHelper.getWebViewVersionFromUserAgent(AuraHelper.getUserAgent())
            )
        if (webviewVer >= AuraHelper.CHROME_SAMESITE_MIN_WEBVIEW_VERSION) {
            builder.append(PATH)
            // We need to set secure param when setting SameSite=None
            builder.append(SECURE)
            builder.append(SAME_SITE)
        }
        cookieManager.setCookie(url, builder.toString())
    }

    companion object {
        const val LIGHTNING_DOMAIN = "lightning_domain"
        const val LIGHTNING_SID = "lightning_sid"
        const val CONTENT_DOMAIN = "content_domain"
        const val CONTENT_SID = "content_sid"
        const val VF_DOMAIN = "visualforce_domain"
        const val VF_SID = "visualforce_sid"
        const val COOKIE_CLIENT_SRC = "cookie-clientSrc"
        const val COOKIE_SID_CLIENT = "cookie-sid_Client"
        const val SID_COOKIE_NAME = "sidCookieName"
        const val CLIENT_SRC = "clientSrc"
        const val SID_CLIENT = "sid_Client"
        const val ORG_ID = "oid"
        const val CSRFTOKEN = "csrfToken"
        private const val PATH = "; path=/"
        private const val SECURE = "; secure"
        private const val SAME_SITE = "; SameSite=None"
        private const val DOMAIN_PREFIX = "; domain="
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