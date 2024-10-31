/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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

import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.util.BundleTestHelper;
import com.salesforce.androidsdk.util.JSONTestHelper;
import com.salesforce.androidsdk.util.MapUtil;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link UserAccount}
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class UserAccountTest {

    // test user
    public static final String TEST_ORG_ID = "test_org_id";
    public static final String TEST_USER_ID = "test_user_id";
    public static final String TEST_ACCOUNT_NAME = "test_username (https://cs1.salesforce.com) (SalesforceSDKTest)";
    public static final String TEST_USERNAME = "test_username";
    public static final String TEST_LOGIN_URL = "https://test.salesforce.com";
    public static final String TEST_INSTANCE_URL = "https://cs1.salesforce.com";
    public static final String TEST_IDENTITY_URL = "https://test.salesforce.com/" + TEST_ORG_ID + "/" + TEST_USER_ID;
    public static final String TEST_COMMUNITY_URL = "https://mobilesdk.cs1.my.salesforce.com";
    public static final String TEST_AUTH_TOKEN = "test_auth_token";
    public static final String TEST_REFRESH_TOKEN = "test_refresh_token";
    public static final String TEST_COMMUNITY_ID = "test_community_id";
    public static final String TEST_FIRST_NAME = "test_first_name";
    public static final String TEST_LAST_NAME = "test_last_name";
    public static final String TEST_NICK_NAME = "test_nick_name";
    public static final String TEST_DISPLAY_NAME = "test_display_name";
    public static final String TEST_USER_TYPE = "test_user_type";
    public static final String TEST_LAST_MODIFIED_DATE = "2024-09-18T10:11:12Z";
    public static final String TEST_EMAIL = "test@email.com";
    public static final String TEST_PHOTO_URL = "http://some.photo.url";
    public static final String TEST_THUMBNAIL_URL = "http://some.thumbnail.url";
    public static final String TEST_CUSTOM_KEY = "test_custom_key";
    public static final String TEST_CUSTOM_VALUE = "test_custom_value";
    public static final String TEST_LANGUAGE = "en_US";
    public static final String TEST_LOCALE = "fr_FR";
    public static final String TEST_LIGHTNING_DOMAIN = "lightning-domain-value";
    public static final String TEST_LIGHTNING_SID = "lightning-sid-value";
    public static final String TEST_VF_DOMAIN = "vf-domain-value";
    public static final String TEST_VF_SID = "vf-sid-value";
    public static final String TEST_CONTENT_DOMAIN = "content-domain-value";
    public static final String TEST_CONTENT_SID = "content-sid-value";
    public static final String TEST_CSRF_TOKEN = "csrf-token-value";
    public static final Boolean TEST_NATIVE_LOGIN = false;
    public static final String TEST_COOKIE_CLIENT_SRC = "cookie-client-src-value";
    public static final String TEST_COOKIE_SID_CLIENT = "cookie-sid-client-value";
    public static final String TEST_SID_COOKIE_NAME = "sid-cookie-name";
    public static final String TEST_CLIENT_ID = "test-client-id";
    public static final String TEST_PARENT_SID = "test-parent-sid";
    public static final String TEST_TOKEN_FORMAT = "test-token-format";

    // other user
    public static final String TEST_ORG_ID_2 = "test_org_id_2";
    public static final String TEST_USER_ID_2 = "test_user_id_2";
    public static final String TEST_ACCOUNT_NAME_2 = "test_username_2 (https://cs1.salesforce.com) (SalesforceSDKTest)";
    public static final String TEST_USERNAME_2 = "test_username_2";


    private EventsListenerQueue eq;

    /**
     * Tests user account to bundle conversion.
     */
    @Test
    public void testConvertAccountToBundle() {
        final UserAccount account = createTestAccount();
        final Bundle actual = account.toBundle(createAdditionalOauthKeys());
        final Bundle expected = createTestAccountBundle();
        BundleTestHelper.checkSameBundle("UserAccount bundles do not match", expected, actual);
    }

    /**
     * Tests user account to json conversion.
     */
    @Test
    public void testConvertAccountToJSON() throws JSONException {
        final UserAccount account = createTestAccount();
        final JSONObject actual = account.toJson(createAdditionalOauthKeys());
        final JSONObject expected = createTestAccountJSON();
        JSONTestHelper.assertSameJSONObject("UserAccount JSONs do not match", expected, actual);
    }

    /**
     * Tests creating an account from a bundle.
     */
    @Test
    public void testCreateAccountFromBundle() {
        final Bundle testBundle = createTestAccountBundle();
        final UserAccount account = new UserAccount(testBundle, createAdditionalOauthKeys());
        checkTestAccount(account);
    }

    /**
     * Tests creating an account from JSON
     */
    @Test
    public void testCreateAccountFromJSON() throws JSONException {
        JSONObject testJSON = createTestAccountJSON();
        UserAccount account = new UserAccount(testJSON, "SalesforceSDKTest", createAdditionalOauthKeys());
        checkTestAccount(account);
    }

    /**
     * Tests populating account from token end point response and id response
     */
    @Test
    public void testPopulateFromTokenEndpointAndIdService() throws JSONException {
        OAuth2.TokenEndpointResponse tr = createTokenEndpointResponse();
        OAuth2.IdServiceResponse id = createIdServiceResponse();
        UserAccount account = UserAccountBuilder.getInstance()
                .populateFromTokenEndpointResponse(tr)
                .populateFromIdServiceResponse(id)
                .accountName(TEST_ACCOUNT_NAME)
                .loginServer(TEST_LOGIN_URL)
                .nativeLogin(TEST_NATIVE_LOGIN)
                .clientId(TEST_CLIENT_ID)
                .build();
        checkTestAccount(account);
    }

    /**
     * Tests populating account from another user account
     */
    @Test
    public void testPopulateFromUserAccount() {
        UserAccount otherUserAccount = UserAccountBuilder.getInstance()
                .populateFromUserAccount(createTestAccount())
                .userId(TEST_USER_ID_2)
                .orgId(TEST_ORG_ID_2)
                .username(TEST_USERNAME_2)
                .accountName(TEST_ACCOUNT_NAME_2)
                .build();
        checkOtherTestAccount(otherUserAccount);
    }

    /**
     * Tests that allowUnset behaves as expected
     */
    @Test
    public void testAllowUnset() {
        // allow unset true (default)
        Assert.assertEquals("login-server-1", UserAccountBuilder.getInstance()
                .loginServer("login-server-1")
                .build().getLoginServer());

        Assert.assertEquals("login-server-2", UserAccountBuilder.getInstance()
                .loginServer("login-server-1")
                .loginServer("login-server-2")
                .build().getLoginServer());

        Assert.assertEquals(null, UserAccountBuilder.getInstance()
                .loginServer("login-server-1")
                .loginServer("login-server-2")
                .loginServer(null)
                .build().getLoginServer());

        Assert.assertEquals("login-server-3", UserAccountBuilder.getInstance()
                .loginServer("login-server-1")
                .loginServer("login-server-2")
                .loginServer(null)
                .loginServer("login-server-3")
                .build().getLoginServer());

        // allow unset false
        Assert.assertEquals("login-server-1", UserAccountBuilder.getInstance()
                .allowUnset(false)
                .loginServer("login-server-1")
                .build().getLoginServer());

        Assert.assertEquals("login-server-2", UserAccountBuilder.getInstance()
                .allowUnset(false)
                .loginServer("login-server-1")
                .loginServer("login-server-2")
                .build().getLoginServer());

        Assert.assertEquals("login-server-2", UserAccountBuilder.getInstance()
                .allowUnset(false)
                .loginServer("login-server-1")
                .loginServer("login-server-2")
                .loginServer(null)
                .build().getLoginServer());

        Assert.assertEquals("login-server-3", UserAccountBuilder.getInstance()
                .allowUnset(false)
                .loginServer("login-server-1")
                .loginServer("login-server-2")
                .loginServer(null)
                .loginServer("login-server-3")
                .build().getLoginServer());
    }

    /**
     * Tests that allowUnset behaves as expected
     */
    @Test
    public void testAllowUnsetForAdditionalOauthValues() {
        Map<String, String> addtional1 = new HashMap<>() {{
            put("custom-1", "value-1");
        }};

        Map<String, String> addtional1upd = new HashMap<>() {{
            put("custom-1", "value-1-upd");
        }};

        Map<String, String> addtional2 = new HashMap<>() {{
            put("custom-2", "value-2");
        }};

        Map<String, String> addtionalMerge = new HashMap<>() {{
            put("custom-1", "value-1");
            put("custom-2", "value-2");
        }};

        Map<String, String> addtionalMergeUpd = new HashMap<>() {{
            put("custom-1", "value-1-upd");
            put("custom-2", "value-2");
        }};


        // allow unset true (default)
        Assert.assertEquals(addtional1, UserAccountBuilder.getInstance()
                .additionalOauthValues(addtional1)
                .build().getAdditionalOauthValues());

        Assert.assertEquals(addtional2, UserAccountBuilder.getInstance()
                .additionalOauthValues(addtional1)
                .additionalOauthValues(addtional2)
                .build().getAdditionalOauthValues());

        Assert.assertEquals(null, UserAccountBuilder.getInstance()
                .additionalOauthValues(addtional1)
                .additionalOauthValues(addtional2)
                .additionalOauthValues(null)
                .build().getAdditionalOauthValues());

        Assert.assertEquals(addtional1upd, UserAccountBuilder.getInstance()
                .additionalOauthValues(addtional1)
                .additionalOauthValues(addtional2)
                .additionalOauthValues(null)
                .additionalOauthValues(addtional1upd)
                .build().getAdditionalOauthValues());

        // allow unset false - null won't write over - maps are merged
        Assert.assertEquals(addtional1, UserAccountBuilder.getInstance()
                .allowUnset(false)
                .additionalOauthValues(addtional1)
                .build().getAdditionalOauthValues());

        Assert.assertEquals(addtionalMerge, UserAccountBuilder.getInstance()
                .allowUnset(false)
                .additionalOauthValues(addtional1)
                .additionalOauthValues(addtional2)
                .build().getAdditionalOauthValues());

        Assert.assertEquals(addtionalMerge, UserAccountBuilder.getInstance()
                .allowUnset(false)
                .additionalOauthValues(addtional1)
                .additionalOauthValues(addtional2)
                .additionalOauthValues(null)
                .build().getAdditionalOauthValues());

        Assert.assertEquals(addtionalMergeUpd, UserAccountBuilder.getInstance()
                .allowUnset(false)
                .additionalOauthValues(addtional1)
                .additionalOauthValues(addtional2)
                .additionalOauthValues(null)
                .additionalOauthValues(addtional1upd)
                .build().getAdditionalOauthValues());

    }


    /**
     * Creates a test {@link JSONObject} with all {@link UserAccount} fields populated
     *
     * @return {@link JSONObject}
     */
    private JSONObject createTestAccountJSON() throws JSONException{
        JSONObject object = new JSONObject();
        object.put(UserAccount.AUTH_TOKEN, TEST_AUTH_TOKEN);
        object.put(UserAccount.REFRESH_TOKEN, TEST_REFRESH_TOKEN);
        object.put(UserAccount.LOGIN_SERVER, TEST_LOGIN_URL);
        object.put(UserAccount.ID_URL, TEST_IDENTITY_URL);
        object.put(UserAccount.INSTANCE_SERVER, TEST_INSTANCE_URL);
        object.put(UserAccount.ORG_ID, TEST_ORG_ID);
        object.put(UserAccount.USER_ID, TEST_USER_ID);
        object.put(UserAccount.USERNAME, TEST_USERNAME);
        object.put(UserAccount.COMMUNITY_ID, TEST_COMMUNITY_ID);
        object.put(UserAccount.COMMUNITY_URL, TEST_COMMUNITY_URL);
        object.put(UserAccount.FIRST_NAME, TEST_FIRST_NAME);
        object.put(UserAccount.LAST_NAME, TEST_LAST_NAME);
        object.put(UserAccount.DISPLAY_NAME, TEST_DISPLAY_NAME);
        object.put(UserAccount.EMAIL, TEST_EMAIL);
        object.put(UserAccount.LANGUAGE, TEST_LANGUAGE);
        object.put(UserAccount.LOCALE, TEST_LOCALE);
        object.put(UserAccount.PHOTO_URL, TEST_PHOTO_URL);
        object.put(UserAccount.THUMBNAIL_URL, TEST_THUMBNAIL_URL);
        object.put(UserAccount.LIGHTNING_DOMAIN, TEST_LIGHTNING_DOMAIN);
        object.put(UserAccount.LIGHTNING_SID, TEST_LIGHTNING_SID);
        object.put(UserAccount.VF_DOMAIN, TEST_VF_DOMAIN);
        object.put(UserAccount.VF_SID, TEST_VF_SID);
        object.put(UserAccount.CONTENT_DOMAIN, TEST_CONTENT_DOMAIN);
        object.put(UserAccount.CONTENT_SID, TEST_CONTENT_SID);
        object.put(UserAccount.CSRF_TOKEN, TEST_CSRF_TOKEN);
        object.put(UserAccount.NATIVE_LOGIN, TEST_NATIVE_LOGIN);
        object.put(UserAccount.COOKIE_CLIENT_SRC, TEST_COOKIE_CLIENT_SRC);
        object.put(UserAccount.COOKIE_SID_CLIENT, TEST_COOKIE_SID_CLIENT);
        object.put(UserAccount.SID_COOKIE_NAME, TEST_SID_COOKIE_NAME);
        object.put(UserAccount.PARENT_SID, TEST_PARENT_SID);
        object.put(UserAccount.TOKEN_FORMAT, TEST_TOKEN_FORMAT);
        object = MapUtil.addMapToJSONObject(createAdditionalOauthValues(), createAdditionalOauthKeys(), object);
        return object;
    }

    /**
     * Creates a test {@link Bundle} with all {@link UserAccount} fields populated
     *
     * @return {@link Bundle}
     */
    private Bundle createTestAccountBundle() {
        Bundle object = new Bundle();
        object.putString(UserAccount.AUTH_TOKEN, TEST_AUTH_TOKEN);
        object.putString(UserAccount.REFRESH_TOKEN, TEST_REFRESH_TOKEN);
        object.putString(UserAccount.LOGIN_SERVER, TEST_LOGIN_URL);
        object.putString(UserAccount.ID_URL, TEST_IDENTITY_URL);
        object.putString(UserAccount.INSTANCE_SERVER, TEST_INSTANCE_URL);
        object.putString(UserAccount.ORG_ID, TEST_ORG_ID);
        object.putString(UserAccount.USER_ID, TEST_USER_ID);
        object.putString(UserAccount.USERNAME, TEST_USERNAME);
        object.putString(UserAccount.ACCOUNT_NAME, TEST_ACCOUNT_NAME);
        object.putString(UserAccount.COMMUNITY_ID, TEST_COMMUNITY_ID);
        object.putString(UserAccount.COMMUNITY_URL, TEST_COMMUNITY_URL);
        object.putString(UserAccount.FIRST_NAME, TEST_FIRST_NAME);
        object.putString(UserAccount.LAST_NAME, TEST_LAST_NAME);
        object.putString(UserAccount.DISPLAY_NAME, TEST_DISPLAY_NAME);
        object.putString(UserAccount.EMAIL, TEST_EMAIL);
        object.putString(UserAccount.LANGUAGE, TEST_LANGUAGE);
        object.putString(UserAccount.LOCALE, TEST_LOCALE);
        object.putString(UserAccount.PHOTO_URL, TEST_PHOTO_URL);
        object.putString(UserAccount.THUMBNAIL_URL, TEST_THUMBNAIL_URL);
        object.putString(UserAccount.LIGHTNING_DOMAIN, TEST_LIGHTNING_DOMAIN);
        object.putString(UserAccount.LIGHTNING_SID, TEST_LIGHTNING_SID);
        object.putString(UserAccount.VF_DOMAIN, TEST_VF_DOMAIN);
        object.putString(UserAccount.VF_SID, TEST_VF_SID);
        object.putString(UserAccount.CONTENT_DOMAIN, TEST_CONTENT_DOMAIN);
        object.putString(UserAccount.CONTENT_SID, TEST_CONTENT_SID);
        object.putString(UserAccount.CSRF_TOKEN, TEST_CSRF_TOKEN);
        object.putBoolean(UserAccount.NATIVE_LOGIN, TEST_NATIVE_LOGIN);
        object.putString(UserAccount.COOKIE_CLIENT_SRC, TEST_COOKIE_CLIENT_SRC);
        object.putString(UserAccount.COOKIE_SID_CLIENT, TEST_COOKIE_SID_CLIENT);
        object.putString(UserAccount.SID_COOKIE_NAME, TEST_SID_COOKIE_NAME);
        object.putString(UserAccount.CLIENT_ID, TEST_CLIENT_ID);
        object.putString(UserAccount.PARENT_SID, TEST_PARENT_SID);
        object.putString(UserAccount.TOKEN_FORMAT, TEST_TOKEN_FORMAT);
        object = MapUtil.addMapToBundle(createAdditionalOauthValues(), createAdditionalOauthKeys(), object);
        return object;
    }

    /**
     * Create test account
     */
    public static UserAccount createTestAccount() {
        return UserAccountBuilder.getInstance()
                .authToken(TEST_AUTH_TOKEN)
                .refreshToken(TEST_REFRESH_TOKEN)
                .loginServer(TEST_LOGIN_URL)
                .idUrl(TEST_IDENTITY_URL)
                .instanceServer(TEST_INSTANCE_URL)
                .orgId(TEST_ORG_ID)
                .userId(TEST_USER_ID)
                .username(TEST_USERNAME)
                .accountName(TEST_ACCOUNT_NAME)
                .communityId(TEST_COMMUNITY_ID)
                .communityUrl(TEST_COMMUNITY_URL)
                .firstName(TEST_FIRST_NAME)
                .lastName(TEST_LAST_NAME)
                .displayName(TEST_DISPLAY_NAME)
                .email(TEST_EMAIL)
                .photoUrl(TEST_PHOTO_URL)
                .thumbnailUrl(TEST_THUMBNAIL_URL)
                .lightningDomain(TEST_LIGHTNING_DOMAIN)
                .lightningSid(TEST_LIGHTNING_SID)
                .vfDomain(TEST_VF_DOMAIN)
                .vfSid(TEST_VF_SID)
                .contentDomain(TEST_CONTENT_DOMAIN)
                .contentSid(TEST_CONTENT_SID)
                .csrfToken(TEST_CSRF_TOKEN)
                .nativeLogin(TEST_NATIVE_LOGIN)
                .language(TEST_LANGUAGE)
                .locale(TEST_LOCALE)
                .cookieClientSrc(TEST_COOKIE_CLIENT_SRC)
                .cookieSidClient(TEST_COOKIE_SID_CLIENT)
                .sidCookieName(TEST_SID_COOKIE_NAME)
                .clientId(TEST_CLIENT_ID)
                .parentSid(TEST_PARENT_SID)
                .tokenFormat(TEST_TOKEN_FORMAT)
                .additionalOauthValues(createAdditionalOauthValues())
                .build();
    }

    /**
     * Create test account
     */
    public static UserAccount createOtherTestAccount() {
        return UserAccountBuilder.getInstance()
                .populateFromUserAccount(createTestAccount())
                .userId(TEST_USER_ID_2)
                .orgId(TEST_ORG_ID_2)
                .username(TEST_USERNAME_2)
                .accountName(TEST_ACCOUNT_NAME_2)
                .build();
    }

    /**
     * Check that the account passed has the test values
     * @param account
     */
    void checkTestAccount(UserAccount account) {
        Assert.assertEquals("Auth token should match", TEST_AUTH_TOKEN, account.getAuthToken());
        Assert.assertEquals("Refresh token should match", TEST_REFRESH_TOKEN, account.getRefreshToken());
        Assert.assertEquals("Login server URL should match", TEST_LOGIN_URL, account.getLoginServer());
        Assert.assertEquals("Identity URL should match", TEST_IDENTITY_URL, account.getIdUrl());
        Assert.assertEquals("Instance URL should match", TEST_INSTANCE_URL, account.getInstanceServer());
        Assert.assertEquals("Org ID should match", TEST_ORG_ID, account.getOrgId());
        Assert.assertEquals("User ID should match", TEST_USER_ID, account.getUserId());
        Assert.assertEquals("User name should match", TEST_USERNAME, account.getUsername());
        Assert.assertEquals("Account name should match", TEST_ACCOUNT_NAME, account.getAccountName());
        Assert.assertEquals("Community ID should match", TEST_COMMUNITY_ID, account.getCommunityId());
        Assert.assertEquals("Community URL should match", TEST_COMMUNITY_URL, account.getCommunityUrl());
        Assert.assertEquals("First name should match", TEST_FIRST_NAME, account.getFirstName());
        Assert.assertEquals("Last name should match", TEST_LAST_NAME, account.getLastName());
        Assert.assertEquals("Display name should match", TEST_DISPLAY_NAME, account.getDisplayName());
        Assert.assertEquals("Email should match", TEST_EMAIL, account.getEmail());
        Assert.assertEquals("Photo URL should match", TEST_PHOTO_URL, account.getPhotoUrl());
        Assert.assertEquals("Thumbnail URL should match", TEST_THUMBNAIL_URL, account.getThumbnailUrl());
        Assert.assertEquals("Language should match", TEST_LANGUAGE, account.getLanguage());
        Assert.assertEquals("Locale should match", TEST_LOCALE, account.getLocale());
        Assert.assertEquals("Lightning domain should match", TEST_LIGHTNING_DOMAIN, account.getLightningDomain());
        Assert.assertEquals("Lightning sid should match", TEST_LIGHTNING_SID, account.getLightningSid());
        Assert.assertEquals("Content domain should match", TEST_CONTENT_DOMAIN, account.getContentDomain());
        Assert.assertEquals("Content sid should match", TEST_CONTENT_SID, account.getContentSid());
        Assert.assertEquals("Vf domain should match", TEST_VF_DOMAIN, account.getVFDomain());
        Assert.assertEquals("Vf sid should match", TEST_VF_SID, account.getVFSid());
        Assert.assertEquals("Native login should match", TEST_NATIVE_LOGIN, account.getNativeLogin());
        Assert.assertEquals("Cookie client src should match", TEST_COOKIE_CLIENT_SRC, account.getCookieClientSrc());
        Assert.assertEquals("Cookie sid client should match", TEST_COOKIE_SID_CLIENT, account.getCookieSidClient());
        Assert.assertEquals("Sid cookie name should match", TEST_SID_COOKIE_NAME, account.getSidCookieName());
        Assert.assertEquals("Parent sid should match", TEST_PARENT_SID, account.getParentSid());
        Assert.assertEquals("Token format should match", TEST_TOKEN_FORMAT, account.getTokenFormat());

        Assert.assertEquals("Additional OAuth values should match", createAdditionalOauthValues(), account.getAdditionalOauthValues());
    }

    /**
     * Check that the account passed has the test values
     * @param account
     */
    void checkOtherTestAccount(UserAccount account) {
        Assert.assertEquals("Auth token should match", TEST_AUTH_TOKEN, account.getAuthToken());
        Assert.assertEquals("Refresh token should match", TEST_REFRESH_TOKEN, account.getRefreshToken());
        Assert.assertEquals("Login server URL should match", TEST_LOGIN_URL, account.getLoginServer());
        Assert.assertEquals("Identity URL should match", TEST_IDENTITY_URL, account.getIdUrl());
        Assert.assertEquals("Instance URL should match", TEST_INSTANCE_URL, account.getInstanceServer());
        Assert.assertEquals("Org ID should match", TEST_ORG_ID_2, account.getOrgId());
        Assert.assertEquals("User ID should match", TEST_USER_ID_2, account.getUserId());
        Assert.assertEquals("User name should match", TEST_USERNAME_2, account.getUsername());
        Assert.assertEquals("Account name should match", TEST_ACCOUNT_NAME_2, account.getAccountName());
        Assert.assertEquals("Community ID should match", TEST_COMMUNITY_ID, account.getCommunityId());
        Assert.assertEquals("Community URL should match", TEST_COMMUNITY_URL, account.getCommunityUrl());
        Assert.assertEquals("First name should match", TEST_FIRST_NAME, account.getFirstName());
        Assert.assertEquals("Last name should match", TEST_LAST_NAME, account.getLastName());
        Assert.assertEquals("Display name should match", TEST_DISPLAY_NAME, account.getDisplayName());
        Assert.assertEquals("Email should match", TEST_EMAIL, account.getEmail());
        Assert.assertEquals("Photo URL should match", TEST_PHOTO_URL, account.getPhotoUrl());
        Assert.assertEquals("Thumbnail URL should match", TEST_THUMBNAIL_URL, account.getThumbnailUrl());
        Assert.assertEquals("Language should match", TEST_LANGUAGE, account.getLanguage());
        Assert.assertEquals("Locale should match", TEST_LOCALE, account.getLocale());
        Assert.assertEquals("Lightning domain should match", TEST_LIGHTNING_DOMAIN, account.getLightningDomain());
        Assert.assertEquals("Lightning sid should match", TEST_LIGHTNING_SID, account.getLightningSid());
        Assert.assertEquals("Content domain should match", TEST_CONTENT_DOMAIN, account.getContentDomain());
        Assert.assertEquals("Content sid should match", TEST_CONTENT_SID, account.getContentSid());
        Assert.assertEquals("Vf domain should match", TEST_VF_DOMAIN, account.getVFDomain());
        Assert.assertEquals("Vf sid should match", TEST_VF_SID, account.getVFSid());
        Assert.assertEquals("Native login should match", TEST_NATIVE_LOGIN, account.getNativeLogin());
        Assert.assertEquals("Cookie client src should match", TEST_COOKIE_CLIENT_SRC, account.getCookieClientSrc());
        Assert.assertEquals("Cookie sid client should match", TEST_COOKIE_SID_CLIENT, account.getCookieSidClient());
        Assert.assertEquals("Sid cookie name should match", TEST_SID_COOKIE_NAME, account.getSidCookieName());
        Assert.assertEquals("Parent sid should match", TEST_PARENT_SID, account.getParentSid());
        Assert.assertEquals("Token format should match", TEST_TOKEN_FORMAT, account.getTokenFormat());
        Assert.assertEquals("Additional OAuth values should match", createAdditionalOauthValues(), account.getAdditionalOauthValues());
    }

    static private Map<String, String> createAdditionalOauthValues() {
        return new HashMap<>() {{
            put(TEST_CUSTOM_KEY, TEST_CUSTOM_VALUE);
        }};
    }

    private List<String> createAdditionalOauthKeys() {
        return new ArrayList<>(Collections.singletonList(TEST_CUSTOM_KEY));
    }

    private OAuth2.TokenEndpointResponse createTokenEndpointResponse() {
        Map<String, String> params = new HashMap<>();

        params.put("access_token", TEST_AUTH_TOKEN);
        params.put("refresh_token", TEST_REFRESH_TOKEN);
        params.put("instance_url", TEST_INSTANCE_URL);
        params.put("id", TEST_IDENTITY_URL);
        params.put("sfdc_community_id", TEST_COMMUNITY_ID);
        params.put("sfdc_community_url", TEST_COMMUNITY_URL);
        params.putAll(createAdditionalOauthValues());
        params.put("lightning_domain", TEST_LIGHTNING_DOMAIN);
        params.put("lightning_sid", TEST_LIGHTNING_SID);
        params.put("visualforce_domain", TEST_VF_DOMAIN);
        params.put("visualforce_sid", TEST_VF_SID);
        params.put("content_domain", TEST_CONTENT_DOMAIN);
        params.put("content_sid", TEST_CONTENT_SID);
        params.put("csrf_token", TEST_CSRF_TOKEN);
        params.put("cookie-clientSrc", TEST_COOKIE_CLIENT_SRC);
        params.put("cookie-sid_Client", TEST_COOKIE_SID_CLIENT);
        params.put("sidCookieName", TEST_SID_COOKIE_NAME);
        params.put("parent_sid", TEST_PARENT_SID);
        params.put("token_format", TEST_TOKEN_FORMAT);

        return new OAuth2.TokenEndpointResponse(params, createAdditionalOauthKeys());
    }

    private OAuth2.IdServiceResponse createIdServiceResponse() throws JSONException {
        JSONObject response = new JSONObject();

        response.put("id", TEST_IDENTITY_URL);
        response.put("username", TEST_USERNAME);
        response.put("email", TEST_EMAIL);
        response.put("first_name", TEST_FIRST_NAME);
        response.put("last_name", TEST_LAST_NAME);
        response.put("nick_name", TEST_NICK_NAME);
        response.put("user_type", TEST_USER_TYPE);
        response.put("display_name", TEST_DISPLAY_NAME);
        response.put("last_modified_date", TEST_LAST_MODIFIED_DATE);
        response.put("user_id", TEST_USER_ID);
        response.put("organization_id", TEST_ORG_ID);
        JSONObject photos = new JSONObject();
        photos.put("picture", TEST_PHOTO_URL);
        photos.put("thumbnail", TEST_THUMBNAIL_URL);
        response.put("photos", photos);
        response.put("language", TEST_LANGUAGE);
        response.put("locale", TEST_LOCALE);
        return new OAuth2.IdServiceResponse(response);
    }

    /**
     * Check the user accounts are the same
     * @param expected Expected UserAccount
     * @param actual Actual UserAccount
     */
    public static void checkSameUserAccount(UserAccount expected, UserAccount actual) {
        // NB We are comparing every fields (UserAccount's equals method only looks at userId and orgId)
        BundleTestHelper.checkSameBundle("Not the expected user account", expected.toBundle(), actual.toBundle());
    }
}
