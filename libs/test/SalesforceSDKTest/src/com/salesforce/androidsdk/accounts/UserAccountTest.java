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
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.MapUtil;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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
@RunWith(AndroidJUnit4.class)
@SmallTest
public class UserAccountTest {

    public static final String TEST_ORG_ID = "test_org_id";
    public static final String TEST_USER_ID = "test_user_id";
    public static final String TEST_ACCOUNT_NAME = "test_accountname";
    public static final String TEST_USERNAME = "test_username";
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

    @Before
    public void setUp() throws Exception {
        final Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Application app = Instrumentation.newApplication(TestForceApp.class, targetContext);
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app);
        eq = new EventsListenerQueue();
        if (!SalesforceSDKManager.hasInstance()) {
            eq.waitForEvent(EventsObservable.EventType.AppCreateComplete, 5000);
        }
        SalesforceSDKManager.getInstance().setAdditionalOauthKeys(createAdditionalOauthKeys());
    }

    @After
    public void tearDown() throws Exception {
        if (eq != null) {
            eq.tearDown();
            eq = null;
        }
        SalesforceSDKManager.getInstance().setAdditionalOauthKeys(null);
    }

    /**
     * Tests bundle creation.
     */
    @Test
    public void testConvertAccountToBundle() {
        final UserAccount account = UserAccountBuilder.getInstance().authToken(TEST_AUTH_TOKEN).
                refreshToken(TEST_REFRESH_TOKEN).loginServer(TEST_LOGIN_URL).
                idUrl(TEST_IDENTITY_URL).instanceServer(TEST_INSTANCE_URL).
                orgId(TEST_ORG_ID).userId(TEST_USER_ID).username(TEST_USERNAME).accountName(TEST_ACCOUNT_NAME).
                communityId(TEST_COMMUNITY_ID).communityUrl(TEST_COMMUNITY_URL).firstName(TEST_FIRST_NAME).
                lastName(TEST_LAST_NAME).displayName(TEST_DISPLAY_NAME).email(TEST_EMAIL).
                photoUrl(TEST_PHOTO_URL).thumbnailUrl(TEST_THUMBNAIL_URL).
                additionalOauthValues(createAdditionalOauthValues()).build();
        final Bundle bundle = account.toBundle();
        final Bundle expectedBundle = createTestAccountBundle();
        Assert.assertTrue(equalBundles(bundle, expectedBundle));
    }

    /**
     * Tests creating an account from a bundle.
     */
    @Test
    public void testCreateAccountFromBundle() {
        final Bundle testBundle = createTestAccountBundle();
        final UserAccount account = new UserAccount(testBundle);
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
        Assert.assertEquals("Additional OAuth values should match", createAdditionalOauthValues(), account.getAdditionalOauthValues());
    }

    /**
     * Tests creating an account from JSON
     */
    @Test
    public void testCreateAccountFromJSON() throws JSONException {
        JSONObject testJSON = createTestAccountJSON();
        UserAccount account = new UserAccount(testJSON);
        Assert.assertEquals("Auth token should match", TEST_AUTH_TOKEN, account.getAuthToken());
        Assert.assertEquals("Refresh token should match", TEST_REFRESH_TOKEN, account.getRefreshToken());
        Assert.assertEquals("Login server URL should match", TEST_LOGIN_URL, account.getLoginServer());
        Assert.assertEquals("Identity URL should match", TEST_IDENTITY_URL, account.getIdUrl());
        Assert.assertEquals("Instance URL should match", TEST_INSTANCE_URL, account.getInstanceServer());
        Assert.assertEquals("Org ID should match", TEST_ORG_ID, account.getOrgId());
        Assert.assertEquals("User ID should match", TEST_USER_ID, account.getUserId());
        Assert.assertEquals("User name should match", TEST_USERNAME, account.getUsername());
        Assert.assertEquals("Community ID should match", TEST_COMMUNITY_ID, account.getCommunityId());
        Assert.assertEquals("Community URL should match", TEST_COMMUNITY_URL, account.getCommunityUrl());
        Assert.assertEquals("First name should match", TEST_FIRST_NAME, account.getFirstName());
        Assert.assertEquals("Last name should match", TEST_LAST_NAME, account.getLastName());
        Assert.assertEquals("Display name should match", TEST_DISPLAY_NAME, account.getDisplayName());
        Assert.assertEquals("Email should match", TEST_EMAIL, account.getEmail());
        Assert.assertEquals("Photo URL should match", TEST_PHOTO_URL, account.getPhotoUrl());
        Assert.assertEquals("Thumbnail URL should match", TEST_THUMBNAIL_URL, account.getThumbnailUrl());
        Assert.assertEquals("Additional OAuth values should match", createAdditionalOauthValues(), account.getAdditionalOauthValues());
        Assert.assertEquals("Account name should match",
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
            } else if (valueOne == null) {
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
