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

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ClientManagerTest;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.MapUtil;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link UserAccount}
 *
 * @author aghoneim
 */
public class UserAccountTest extends InstrumentationTestCase {

    public static final String TEST_ORG_ID = "test_org_id";
    public static final String TEST_USER_ID = "test_user_id";
    public static final String TEST_ACCOUNT_NAME = "test_accountname";
    public static final String TEST_USERNAME = "test_username";
    public static final String TEST_CLIENT_ID = "test_client_d";
    public static final String TEST_LOGIN_URL = "https://test.salesforce.com";
    public static final String TEST_INSTANCE_URL = "https://cs1.salesforce.com";
    public static final String TEST_IDENTITY_URL = "https://test.salesforce.com";
    public static final String TEST_COMMUNITY_URL = "https://mobilesdk.cs1.my.salesforce.com";
    public static final String TEST_AUTH_TOKEN = "test_auth_token";
    public static final String TEST_REFRESH_TOKEN = "test_refresh_token";
    public static final String TEST_COMMUNITY_ID = "test_community_id";
    public static final String TEST_FIRST_NAME = "firstName";
    public static final String TEST_LAST_NAME = "lastName";
    public static final String TEST_DISPLAY_NAME = "displayName";
    public static final String TEST_EMAIL = "test@email.com";
    public static final String TEST_PHOTO_URL = "http://some.photo.url";
    public static final String TEST_THUMBNAIL_URL = "http://some.thumbnail.url";
    public static final String TEST_CUSTOM_KEY = "test_custom_key";
    public static final String TEST_CUSTOM_VALUE = "test_custom_value";

    private EventsListenerQueue eq;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        final Context targetContext = getInstrumentation().getTargetContext();
        final Application app = Instrumentation.newApplication(TestForceApp.class, targetContext);
        getInstrumentation().callApplicationOnCreate(app);
        eq = new EventsListenerQueue();
        if (!SalesforceSDKManager.hasInstance()) {
            eq.waitForEvent(EventsObservable.EventType.AppCreateComplete, 5000);
        }
        SalesforceSDKManager.getInstance().getPasscodeManager().setPasscodeHash(ClientManagerTest.TEST_PASSCODE_HASH);
        SalesforceSDKManager.getInstance().setAdditionalOauthKeys(createAdditionalOauthKeys());
    }

    @Override
    public void tearDown() throws Exception {
        if (eq != null) {
            eq.tearDown();
            eq = null;
        }
        SalesforceSDKManager.getInstance().setAdditionalOauthKeys(null);
        super.tearDown();
    }

    /**
     * Tests bundle creation.
     */
    public void testConvertAccountToBundle() {
        final UserAccount account = new UserAccount(TEST_AUTH_TOKEN,
                TEST_REFRESH_TOKEN, TEST_LOGIN_URL, TEST_IDENTITY_URL, TEST_INSTANCE_URL,
                TEST_ORG_ID, TEST_USER_ID, TEST_USERNAME, TEST_ACCOUNT_NAME,
                TEST_CLIENT_ID, TEST_COMMUNITY_ID, TEST_COMMUNITY_URL, TEST_FIRST_NAME,
                TEST_LAST_NAME, TEST_DISPLAY_NAME, TEST_EMAIL, TEST_PHOTO_URL, TEST_THUMBNAIL_URL,
                createAdditionalOauthValues());
        final Bundle bundle = account.toBundle();
        final Bundle expectedBundle = createTestAccountBundle();
        assertTrue(equalBundles(bundle, expectedBundle));
    }

    /**
     * Tests creating an account from a bundle.
     */
    public void testCreateAccountFromBundle() {
        final Bundle testBundle = createTestAccountBundle();
        final UserAccount account = new UserAccount(testBundle);
        assertEquals("Auth token should match", TEST_AUTH_TOKEN, account.getAuthToken());
        assertEquals("Refresh token should match", TEST_REFRESH_TOKEN, account.getRefreshToken());
        assertEquals("Login server URL should match", TEST_LOGIN_URL, account.getLoginServer());
        assertEquals("Identity URL should match", TEST_IDENTITY_URL, account.getIdUrl());
        assertEquals("Instance URL should match", TEST_INSTANCE_URL, account.getInstanceServer());
        assertEquals("Org ID should match", TEST_ORG_ID, account.getOrgId());
        assertEquals("User ID should match", TEST_USER_ID, account.getUserId());
        assertEquals("User name should match", TEST_USERNAME, account.getUsername());
        assertEquals("Client ID should match", TEST_CLIENT_ID, account.getClientId());
        assertEquals("Account name should match", TEST_ACCOUNT_NAME, account.getAccountName());
        assertEquals("Community ID should match", TEST_COMMUNITY_ID, account.getCommunityId());
        assertEquals("Community URL should match", TEST_COMMUNITY_URL, account.getCommunityUrl());
        assertEquals("First name should match", TEST_FIRST_NAME, account.getFirstName());
        assertEquals("Last name should match", TEST_LAST_NAME, account.getLastName());
        assertEquals("Display name should match", TEST_DISPLAY_NAME, account.getDisplayName());
        assertEquals("Email should match", TEST_EMAIL, account.getEmail());
        assertEquals("Photo URL should match", TEST_PHOTO_URL, account.getPhotoUrl());
        assertEquals("Thumbnail URL should match", TEST_THUMBNAIL_URL, account.getThumbnailUrl());
        assertEquals("Additional OAuth values should match", createAdditionalOauthValues(), account.getAdditionalOauthValues());
    }

    /**
     * Tests creating an account from JSON
     */
    public void testCreateAccountFromJSON() throws JSONException {
        JSONObject testJSON = createTestAccountJSON();
        UserAccount account = new UserAccount(testJSON);
        assertEquals("Auth token should match", TEST_AUTH_TOKEN, account.getAuthToken());
        assertEquals("Refresh token should match", TEST_REFRESH_TOKEN, account.getRefreshToken());
        assertEquals("Login server URL should match", TEST_LOGIN_URL, account.getLoginServer());
        assertEquals("Identity URL should match", TEST_IDENTITY_URL, account.getIdUrl());
        assertEquals("Instance URL should match", TEST_INSTANCE_URL, account.getInstanceServer());
        assertEquals("Org ID should match", TEST_ORG_ID, account.getOrgId());
        assertEquals("User ID should match", TEST_USER_ID, account.getUserId());
        assertEquals("User name should match", TEST_USERNAME, account.getUsername());
        assertEquals("Client ID should match", TEST_CLIENT_ID, account.getClientId());
        assertEquals("Community ID should match", TEST_COMMUNITY_ID, account.getCommunityId());
        assertEquals("Community URL should match", TEST_COMMUNITY_URL, account.getCommunityUrl());
        assertEquals("First name should match", TEST_FIRST_NAME, account.getFirstName());
        assertEquals("Last name should match", TEST_LAST_NAME, account.getLastName());
        assertEquals("Display name should match", TEST_DISPLAY_NAME, account.getDisplayName());
        assertEquals("Email should match", TEST_EMAIL, account.getEmail());
        assertEquals("Photo URL should match", TEST_PHOTO_URL, account.getPhotoUrl());
        assertEquals("Thumbnail URL should match", TEST_THUMBNAIL_URL, account.getThumbnailUrl());
        assertEquals("Additional OAuth values should match", createAdditionalOauthValues(), account.getAdditionalOauthValues());
        assertEquals("Account name should match",
                String.format("%s (%s) (%s)", TEST_USERNAME, TEST_INSTANCE_URL, SalesforceSDKManager.getInstance().getApplicationName()),
                account.getAccountName());
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
        object.put(UserAccount.CLIENT_ID, TEST_CLIENT_ID);
        object.put(UserAccount.ACCOUNT_NAME, TEST_ACCOUNT_NAME);
        object.put(UserAccount.COMMUNITY_ID, TEST_COMMUNITY_ID);
        object.put(UserAccount.COMMUNITY_URL, TEST_COMMUNITY_URL);
        object.put(UserAccount.FIRST_NAME, TEST_FIRST_NAME);
        object.put(UserAccount.LAST_NAME, TEST_LAST_NAME);
        object.put(UserAccount.DISPLAY_NAME, TEST_DISPLAY_NAME);
        object.put(UserAccount.EMAIL, TEST_EMAIL);
        object.put(UserAccount.PHOTO_URL, TEST_PHOTO_URL);
        object.put(UserAccount.THUMBNAIL_URL, TEST_THUMBNAIL_URL);
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
        object.putString(UserAccount.CLIENT_ID, TEST_CLIENT_ID);
        object.putString(UserAccount.ACCOUNT_NAME, TEST_ACCOUNT_NAME);
        object.putString(UserAccount.COMMUNITY_ID, TEST_COMMUNITY_ID);
        object.putString(UserAccount.COMMUNITY_URL, TEST_COMMUNITY_URL);
        object.putString(UserAccount.FIRST_NAME, TEST_FIRST_NAME);
        object.putString(UserAccount.LAST_NAME, TEST_LAST_NAME);
        object.putString(UserAccount.DISPLAY_NAME, TEST_DISPLAY_NAME);
        object.putString(UserAccount.EMAIL, TEST_EMAIL);
        object.putString(UserAccount.PHOTO_URL, TEST_PHOTO_URL);
        object.putString(UserAccount.THUMBNAIL_URL, TEST_THUMBNAIL_URL);
        object = MapUtil.addMapToBundle(createAdditionalOauthValues(), createAdditionalOauthKeys(), object);
        return object;
    }

    /**
     * Check for equality of two bundles.
     *
     * @param one the first bundle
     * @param two the second bundle
     * @return true if the keys/values match
     *         false otherwise
     */
    public boolean equalBundles(Bundle one, Bundle two) {
        if (one.size() != two.size()) {
            return false;
        }
        Set<String> setOne = one.keySet();
        Object valueOne;
        Object valueTwo;
        for (String key : setOne) {
            valueOne = one.get(key);
            valueTwo = two.get(key);
            if (valueOne instanceof Bundle && valueTwo instanceof Bundle &&
                    !equalBundles((Bundle) valueOne, (Bundle) valueTwo)) {
                return false;
            } else if(valueOne == null) {
                if (valueTwo != null || !two.containsKey(key))
                    return false;
            } else if (!valueOne.equals(valueTwo))
                return false;
        }
        return true;
    }

    private Map<String, String> createAdditionalOauthValues() {
        final Map<String, String> testOauthValues = new HashMap<>();
        testOauthValues.put(TEST_CUSTOM_KEY, TEST_CUSTOM_VALUE);
        return testOauthValues;
    }

    private List<String> createAdditionalOauthKeys() {
        final List<String> testOauthValues = new ArrayList<>();
        testOauthValues.add(TEST_CUSTOM_KEY);
        return testOauthValues;
    }
}
