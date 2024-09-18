/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.accounts

import com.salesforce.androidsdk.app.SalesforceSDKManager.Companion.INSTANCE
import com.salesforce.androidsdk.auth.OAuth2.IdServiceResponse
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse

/**
 * This class helps build a UserAccount object.
 */
class UserAccountBuilder private constructor() {
    private var authToken: String? = null
    private var refreshToken: String? = null
    private var loginServer: String? = null
    private var idUrl: String? = null
    private var instanceServer: String? = null
    private var orgId: String? = null
    private var userId: String? = null
    private var username: String? = null
    private var accountName: String? = null
    private var communityId: String? = null
    private var communityUrl: String? = null
    private var firstName: String? = null
    private var lastName: String? = null
    private var displayName: String? = null
    private var email: String? = null
    private var photoUrl: String? = null
    private var thumbnailUrl: String? = null
    private var lightningDomain: String? = null
    private var lightningSid: String? = null
    private var vfDomain: String? = null
    private var vfSid: String? = null
    private var contentDomain: String? = null
    private var contentSid: String? = null
    private var csrfToken: String? = null
    private var nativeLogin = false
    private var language: String? = null
    private var locale: String? = null
    private var cookieClientSrc: String? = null
    private var cookieSidClient: String? = null
    private var sidCookieName: String? = null
    private var clientId: String? = null
    private var additionalOauthValues: Map<String, String>? = null

    /**
     * Set fields from token end point response
     * @param tr token endpoint response
     * @return Instance of this class
     */
    fun populateFromTokenEndpointResponse(tr: TokenEndpointResponse?): UserAccountBuilder {
        if (tr == null) {
            return this
        }

        return authToken(tr.authToken)
            .refreshToken(tr.refreshToken)
            .instanceServer(tr.instanceUrl)
            .idUrl(tr.idUrl)
            .orgId(tr.orgId)
            .userId(tr.userId)
            .communityId(tr.communityId)
            .communityUrl(tr.communityUrl)
            .additionalOauthValues(tr.additionalOauthValues)
            .lightningDomain(tr.lightningDomain)
            .lightningSid(tr.lightningSid)
            .vfDomain(tr.vfDomain)
            .vfSid(tr.vfSid)
            .contentDomain(tr.contentDomain)
            .contentSid(tr.contentSid)
            .csrfToken(tr.csrfToken)
            .cookieClientSrc(tr.cookieClientSrc)
            .cookieSidClient(tr.cookieSidClient)
            .sidCookieName(tr.sidCookieName)
    }

    /**
     * Set fields from id service response
     * @param id id service response
     * @return Instance of this class
     */
    fun populateFromIdServiceResponse(id: IdServiceResponse?): UserAccountBuilder {
        if (id == null) {
            return this
        }

        return username(id.username)
            .firstName(id.firstName)
            .displayName(id.displayName)
            .email(id.email)
            .photoUrl(id.pictureUrl)
            .thumbnailUrl(id.thumbnailUrl)
            .language(id.language)
            .locale(id.locale)
    }

    /**
     * Set fields from an existing UserAccount
     * @param userAccount user account.
     * @return Instance of this class
     */
    fun populateFromUserAccount(userAccount: UserAccount): UserAccountBuilder {
        return this
            .authToken(userAccount.authToken)
            .refreshToken(userAccount.refreshToken)
            .loginServer(userAccount.loginServer)
            .idUrl(userAccount.idUrl)
            .instanceServer(userAccount.instanceServer)
            .orgId(userAccount.orgId)
            .userId(userAccount.userId)
            .username(userAccount.username)
            .accountName(userAccount.accountName)
            .communityId(userAccount.communityId)
            .communityUrl(userAccount.communityUrl)
            .firstName(userAccount.firstName)
            .lastName(userAccount.lastName)
            .displayName(userAccount.displayName)
            .email(userAccount.email)
            .photoUrl(userAccount.photoUrl)
            .thumbnailUrl(userAccount.thumbnailUrl)
            .additionalOauthValues(userAccount.additionalOauthValues)
            .lightningDomain(userAccount.lightningDomain)
            .lightningSid(userAccount.lightningSid)
            .vfDomain(userAccount.vfDomain)
            .vfSid(userAccount.vfSid)
            .contentDomain(userAccount.contentDomain)
            .contentSid(userAccount.contentSid)
            .csrfToken(userAccount.csrfToken)
            .nativeLogin(userAccount.nativeLogin)
            .language(userAccount.language)
            .locale(userAccount.locale)
            .cookieClientSrc(userAccount.cookieClientSrc)
            .cookieSidClient(userAccount.cookieSidClient)
            .sidCookieName(userAccount.sidCookieName)
            .clientId(userAccount.clientId)
    }

    /**
     * Sets auth token.
     *
     * @param authToken Auth token.
     * @return Instance of this class.
     */
    fun authToken(authToken: String?): UserAccountBuilder {
        this.authToken = authToken
        return this
    }

    /**
     * Sets refresh token.
     *
     * @param refreshToken Refresh token.
     * @return Instance of this class.
     */
    fun refreshToken(refreshToken: String?): UserAccountBuilder {
        this.refreshToken = refreshToken
        return this
    }

    /**
     * Sets login server.
     *
     * @param loginServer Login server.
     * @return Instance of this class.
     */
    fun loginServer(loginServer: String?): UserAccountBuilder {
        this.loginServer = loginServer
        return this
    }

    /**
     * Sets identity URL.
     *
     * @param idUrl Identity URL.
     * @return Instance of this class.
     */
    fun idUrl(idUrl: String?): UserAccountBuilder {
        this.idUrl = idUrl
        return this
    }

    /**
     * Sets instance server.
     *
     * @param instanceServer Instance server.
     * @return Instance of this class.
     */
    fun instanceServer(instanceServer: String?): UserAccountBuilder {
        this.instanceServer = instanceServer
        return this
    }

    /**
     * Sets org ID.
     *
     * @param orgId Org ID.
     * @return Instance of this class.
     */
    fun orgId(orgId: String?): UserAccountBuilder {
        this.orgId = orgId
        return this
    }

    /**
     * Sets user ID.
     *
     * @param userId User ID.
     * @return Instance of this class.
     */
    fun userId(userId: String?): UserAccountBuilder {
        this.userId = userId
        return this
    }

    /**
     * Sets username.
     *
     * @param username Username.
     * @return Instance of this class.
     */
    fun username(username: String?): UserAccountBuilder {
        this.username = username
        return this
    }

    /**
     * Sets account name.
     *
     * @param accountName Account name.
     * @return Instance of this class.
     */
    fun accountName(accountName: String?): UserAccountBuilder {
        this.accountName = accountName
        return this
    }

    /**
     * Sets community ID.
     *
     * @param communityId Community ID.
     * @return Instance of this class.
     */
    fun communityId(communityId: String?): UserAccountBuilder {
        this.communityId = communityId
        return this
    }

    /**
     * Sets community URL.
     *
     * @param communityUrl Community URL.
     * @return Instance of this class.
     */
    fun communityUrl(communityUrl: String?): UserAccountBuilder {
        this.communityUrl = communityUrl
        return this
    }

    /**
     * Sets first name.
     *
     * @param firstName First name.
     * @return Instance of this class.
     */
    fun firstName(firstName: String?): UserAccountBuilder {
        this.firstName = firstName
        return this
    }

    /**
     * Sets last name.
     *
     * @param lastName Last name.
     * @return Instance of this class.
     */
    fun lastName(lastName: String?): UserAccountBuilder {
        this.lastName = lastName
        return this
    }

    /**
     * Sets display name.
     *
     * @param displayName Display name.
     * @return Instance of this class.
     */
    fun displayName(displayName: String?): UserAccountBuilder {
        this.displayName = displayName
        return this
    }

    /**
     * Sets email.
     *
     * @param email Email.
     * @return Instance of this class.
     */
    fun email(email: String?): UserAccountBuilder {
        this.email = email
        return this
    }

    /**
     * Sets photo URL.
     *
     * @param photoUrl Photo URL.
     * @return Instance of this class.
     */
    fun photoUrl(photoUrl: String?): UserAccountBuilder {
        this.photoUrl = photoUrl
        return this
    }

    /**
     * Sets thumbnail URL.
     *
     * @param thumbnailUrl Thumbnail URL.
     * @return Instance of this class.
     */
    fun thumbnailUrl(thumbnailUrl: String?): UserAccountBuilder {
        this.thumbnailUrl = thumbnailUrl
        return this
    }

    /**
     * Sets lightning domain.
     *
     * @param lightningDomain Lightning domain.
     * @return Instance of this class.
     */
    fun lightningDomain(lightningDomain: String?): UserAccountBuilder {
        this.lightningDomain = lightningDomain
        return this
    }

    /**
     * Sets lightning SID.
     *
     * @param lightningSid Lightning SID.
     * @return Instance of this class.
     */
    fun lightningSid(lightningSid: String?): UserAccountBuilder {
        this.lightningSid = lightningSid
        return this
    }

    /**
     * Sets VF domain.
     *
     * @param vfDomain VF domain.
     * @return Instance of this class.
     */
    fun vfDomain(vfDomain: String?): UserAccountBuilder {
        this.vfDomain = vfDomain
        return this
    }

    /**
     * Sets VF SID.
     *
     * @param vfSid VF SID.
     * @return Instance of this class.
     */
    fun vfSid(vfSid: String?): UserAccountBuilder {
        this.vfSid = vfSid
        return this
    }

    /**
     * Sets content domain.
     *
     * @param contentDomain Content domain.
     * @return Instance of this class.
     */
    fun contentDomain(contentDomain: String?): UserAccountBuilder {
        this.contentDomain = contentDomain
        return this
    }

    /**
     * Sets content SID.
     *
     * @param contentSid Content SID.
     * @return Instance of this class.
     */
    fun contentSid(contentSid: String?): UserAccountBuilder {
        this.contentSid = contentSid
        return this
    }

    /**
     * Sets CSRF token.
     *
     * @param csrfToken CSRF token.
     * @return Instance of this class.
     */
    fun csrfToken(csrfToken: String?): UserAccountBuilder {
        this.csrfToken = csrfToken
        return this
    }

    /**
     * Sets if the user authenticated with native login.
     *
     * @param isNativeLogin if the user authenticated with native login.
     * @return Instance of this class.
     */
    fun nativeLogin(isNativeLogin: Boolean): UserAccountBuilder {
        this.nativeLogin = isNativeLogin
        return this
    }

    /**
     * Sets language.
     *
     * @param language User's language.
     * @return Instance of this class.
     */
    fun language(language: String?): UserAccountBuilder {
        this.language = language
        return this
    }

    /**
     * Sets locale.
     *
     * @param locale User's locale.
     * @return Instance of this class.
     */
    fun locale(locale: String?): UserAccountBuilder {
        this.locale = locale
        return this
    }


    /**
     * Sets cookie client src
     *
     * @param cookieClientSrc cookie client src.
     * @return Instance of this class.
     */
    fun cookieClientSrc(cookieClientSrc: String?): UserAccountBuilder {
        this.cookieClientSrc = cookieClientSrc
        return this
    }

    /**
     * Sets cookie sid client
     *
     * @param cookieSidClient cookie sid client.
     * @return Instance of this class.
     */
    fun cookieSidClient(cookieSidClient: String?): UserAccountBuilder {
        this.cookieSidClient = cookieSidClient
        return this
    }

    /**
     * Sets sid cookie name
     *
     * @param sidCookieName sid cookie name.
     * @return Instance of this class.
     */
    fun sidCookieName(sidCookieName: String?): UserAccountBuilder {
        this.sidCookieName = sidCookieName
        return this
    }

    /**
     * Sets oauth client id
     *
     * @param clientId sid cookie name.
     * @return Instance of this class.
     */
    fun clientId(clientId: String?): UserAccountBuilder {
        this.clientId = clientId
        return this
    }

    /**
     * Sets additional OAuth values.
     *
     * @param additionalOauthValues Additional OAuth values.
     * @return Instance of this class.
     */
    fun additionalOauthValues(additionalOauthValues: Map<String, String>?): UserAccountBuilder {
        this.additionalOauthValues = additionalOauthValues
        return this
    }

    /**
     * Builds and returns a UserAccount object.
     *
     * @return UserAccount object.
     */
    fun build(): UserAccount {
        return UserAccount(
            authToken, refreshToken, loginServer, idUrl, instanceServer, orgId,
            userId, username, accountName, communityId, communityUrl, firstName, lastName,
            displayName, email, photoUrl, thumbnailUrl, additionalOauthValues, lightningDomain,
            lightningSid, vfDomain, vfSid, contentDomain, contentSid, csrfToken, nativeLogin,
            language, locale, cookieClientSrc, cookieSidClient, sidCookieName, clientId
        )
    }

    companion object {
        @JvmStatic // This removes the `Companion` reference in in Java code
        fun getInstance() = UserAccountBuilder()
    }
}
