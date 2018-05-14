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
package com.salesforce.androidsdk.accounts;

import java.util.Map;

/**
 * This class helps build a UserAccount object.
 *
 * @author bhariharan
 */
public class UserAccountBuilder {

    private String authToken;
    private String refreshToken;
    private String loginServer;
    private String idUrl;
    private String instanceServer;
    private String orgId;
    private String userId;
    private String username;
    private String accountName;
    private String communityId;
    private String communityUrl;
    private String firstName;
    private String lastName;
    private String displayName;
    private String email;
    private String photoUrl;
    private String thumbnailUrl;
    private Map<String, String> additionalOauthValues;

    /**
     * Returns an instance of this class.
     *
     * @return Instance of this class.
     */
    public static final UserAccountBuilder getInstance() {
        return new UserAccountBuilder();
    }

    private UserAccountBuilder() {
        super();
    }

    /**
     * Sets auth token.
     *
     * @param authToken Auth token.
     * @return Instance of this class.
     */
    public UserAccountBuilder authToken(String authToken) {
        this.authToken = authToken;
        return this;
    }

    /**
     * Sets refresh token.
     *
     * @param refreshToken Refresh token.
     * @return Instance of this class.
     */
    public UserAccountBuilder refreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
        return this;
    }

    /**
     * Sets login server.
     *
     * @param loginServer Login server.
     * @return Instance of this class.
     */
    public UserAccountBuilder loginServer(String loginServer) {
        this.loginServer = loginServer;
        return this;
    }

    /**
     * Sets identity URL.
     *
     * @param idUrl Identity URL.
     * @return Instance of this class.
     */
    public UserAccountBuilder idUrl(String idUrl) {
        this.idUrl = idUrl;
        return this;
    }

    /**
     * Sets instance server.
     *
     * @param instanceServer Instance server.
     * @return Instance of this class.
     */
    public UserAccountBuilder instanceServer(String instanceServer) {
        this.instanceServer = instanceServer;
        return this;
    }

    /**
     * Sets org ID.
     *
     * @param orgId Org ID.
     * @return Instance of this class.
     */
    public UserAccountBuilder orgId(String orgId) {
        this.orgId = orgId;
        return this;
    }

    /**
     * Sets user ID.
     *
     * @param userId User ID.
     * @return Instance of this class.
     */
    public UserAccountBuilder userId(String userId) {
        this.userId = userId;
        return this;
    }

    /**
     * Sets username.
     *
     * @param username Username.
     * @return Instance of this class.
     */
    public UserAccountBuilder username(String username) {
        this.username = username;
        return this;
    }

    /**
     * Sets account name.
     *
     * @param accountName Account name.
     * @return Instance of this class.
     */
    public UserAccountBuilder accountName(String accountName) {
        this.accountName = accountName;
        return this;
    }

    /**
     * Sets community ID.
     *
     * @param communityId Community ID.
     * @return Instance of this class.
     */
    public UserAccountBuilder communityId(String communityId) {
        this.communityId = communityId;
        return this;
    }

    /**
     * Sets community URL.
     *
     * @param communityUrl Community URL.
     * @return Instance of this class.
     */
    public UserAccountBuilder communityUrl(String communityUrl) {
        this.communityUrl = communityUrl;
        return this;
    }

    /**
     * Sets first name.
     *
     * @param firstName First name.
     * @return Instance of this class.
     */
    public UserAccountBuilder firstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    /**
     * Sets last name.
     *
     * @param lastName Last name.
     * @return Instance of this class.
     */
    public UserAccountBuilder lastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    /**
     * Sets display name.
     *
     * @param displayName Display name.
     * @return Instance of this class.
     */
    public UserAccountBuilder displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * Sets email.
     *
     * @param email Email.
     * @return Instance of this class.
     */
    public UserAccountBuilder email(String email) {
        this.email = email;
        return this;
    }

    /**
     * Sets photo URL.
     *
     * @param photoUrl Photo URL.
     * @return Instance of this class.
     */
    public UserAccountBuilder photoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
        return this;
    }

    /**
     * Sets thumbnail URL.
     *
     * @param thumbnailUrl Thumbnail URL.
     * @return Instance of this class.
     */
    public UserAccountBuilder thumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
        return this;
    }

    /**
     * Sets additional OAuth values.
     *
     * @param additionalOauthValues Additional OAuth values.
     * @return Instance of this class.
     */
    public UserAccountBuilder additionalOauthValues(Map<String, String> additionalOauthValues) {
        this.additionalOauthValues = additionalOauthValues;
        return this;
    }

    /**
     * Builds and returns a UserAccount object.
     *
     * @return UserAccount object.
     */
    public UserAccount build() {
        return new UserAccount(authToken, refreshToken, loginServer, idUrl, instanceServer, orgId,
                userId, username, accountName, communityId, communityUrl, firstName, lastName,
                displayName, email, photoUrl, thumbnailUrl, additionalOauthValues);
    }
}
