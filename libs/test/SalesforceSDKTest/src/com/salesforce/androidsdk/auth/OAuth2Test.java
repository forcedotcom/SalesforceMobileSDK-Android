/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.auth;

import android.app.Application;
import android.app.Instrumentation;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.OAuth2.IdServiceResponse;
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.util.test.TestCredentials;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Tests for OAuth2.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class OAuth2Test {

	private HttpAccess httpAccess;

	@Before
	public void setUp() throws Exception {
		final Application app = Instrumentation.newApplication(TestForceApp.class,
                InstrumentationRegistry.getInstrumentation().getContext());
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app);
		TestCredentials.init(InstrumentationRegistry.getInstrumentation().getContext());
		httpAccess = new HttpAccess(null, "dummy-agent");		
	}

    @After
    public void tearDown() {
        SalesforceSDKManager.getInstance().setLoginBrand(null);
    }

	/**
	 * Testing getAuthorizationUrl.
     *
	 * @throws URISyntaxException See {@link URISyntaxException}.
	 */
    @Test
	public void testGetAuthorizationUrl() throws URISyntaxException {
		String callbackUrl = "sfdc://callback";
		URI authorizationUrl = OAuth2.getAuthorizationUrl(true, true, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, null, "some-challenge", null);
		URI expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize?display=touch&response_type=code&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
				SalesforceSDKManager.getInstance().getDeviceId() + "&code_challenge=some-challenge");
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
		authorizationUrl = OAuth2.getAuthorizationUrl(true, true, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, "touch", "some-challenge", null);
		expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize?display=touch&response_type=code&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId() + "&code_challenge=some-challenge");
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
	}

	/**
	 * Testing getAuthorizationUrl with params.
     *
	 * @throws URISyntaxException See {@link URISyntaxException}.
	 */
    @Test
	public void testGetAuthorizationUrlWithParams() throws URISyntaxException {
		String callbackUrl = "sfdc://callback";
		Map<String,String> params = new HashMap<>();
		params.put("param1", "val1");
		params.put("param2", "val2");
		params.put("param3", null);
		URI authorizationUrl = OAuth2.getAuthorizationUrl(true, true, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, null, "some-challenge", params);
        Assert.assertTrue("Wrong authorization url", authorizationUrl.getRawQuery().indexOf("&param1=val1") > 0);
        Assert.assertTrue("Wrong authorization url", authorizationUrl.getRawQuery().indexOf("&param2=val2") > 0);
        Assert.assertTrue("Wrong authorization url", authorizationUrl.getRawQuery().indexOf("&param3=") > 0);
	}

    /**
     * Testing getAuthorizationUrl with branded login path.
     *
     * @throws URISyntaxException See {@link URISyntaxException}.
     */
    @Test
    public void testGetAuthorizationUrlWithBrandedLoginPath() throws URISyntaxException {
        String callbackUrl = "sfdc://callback";
        final String brandedLoginPath = "BRAND";
        SalesforceSDKManager.getInstance().setLoginBrand(brandedLoginPath);
        URI authorizationUrl = OAuth2.getAuthorizationUrl(true, true, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, null, "some-challenge", null);
        URI expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize/BRAND?display=touch&response_type=code&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId() + "&code_challenge=some-challenge");
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
        authorizationUrl = OAuth2.getAuthorizationUrl(true, true, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, "touch", "some-challenge", null);
        expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize/BRAND?display=touch&response_type=code&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId() + "&code_challenge=some-challenge");
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
    }

    /**
     * Testing getAuthorizationUrl with branded login path with leading slash.
     *
     * @throws URISyntaxException See {@link URISyntaxException}.
     */
    @Test
    public void testGetAuthorizationUrlWithBrandedLoginPathWithLeadingSlash() throws URISyntaxException {
        String callbackUrl = "sfdc://callback";
        final String brandedLoginPath = "BRAND";
        SalesforceSDKManager.getInstance().setLoginBrand(brandedLoginPath);
        URI authorizationUrl = OAuth2.getAuthorizationUrl(true, true, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, null, "some-challenge", null);
        URI expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize/BRAND?display=touch&response_type=code&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId() + "&code_challenge=some-challenge");
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
        authorizationUrl = OAuth2.getAuthorizationUrl(true, true, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, "touch", "some-challenge", null);
        expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize/BRAND?display=touch&response_type=code&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId() + "&code_challenge=some-challenge");
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
    }

    /**
     * Testing getAuthorizationUrl with branded login path with trailing slash.
     *
     * @throws URISyntaxException See {@link URISyntaxException}.
     */
    @Test
    public void testGetAuthorizationUrlWithBrandedLoginPathWithTrailingSlash() throws URISyntaxException {
        String callbackUrl = "sfdc://callback";
        final String brandedLoginPath = "BRAND";
        SalesforceSDKManager.getInstance().setLoginBrand(brandedLoginPath);
        URI authorizationUrl = OAuth2.getAuthorizationUrl(true, true, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, null, "some-challenge", null);
        URI expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize/BRAND?display=touch&response_type=code&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId() + "&code_challenge=some-challenge");
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
        authorizationUrl = OAuth2.getAuthorizationUrl(true, true, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, "touch", "some-challenge", null);
        expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize/BRAND?display=touch&response_type=code&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId() + "&code_challenge=some-challenge");
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
    }

    /**
     * Testing getAuthorizationUrl with web server authentication OFF.
     *
     * @throws URISyntaxException See {@link URISyntaxException}.
     */
    @Test
    public void testGetAuthorizationUrlForUserAgentFlow() throws URISyntaxException {
        String callbackUrl = "sfdc://callback";
        URI authorizationUrl = OAuth2.getAuthorizationUrl(false, true, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, null, null, null);
        URI expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize?display=touch&response_type=hybrid_token&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId());
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
        authorizationUrl = OAuth2.getAuthorizationUrl(false, true, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, "touch", null, null);
        expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize?display=touch&response_type=hybrid_token&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId());
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
    }

    /**
     * Testing getAuthorizationUrl with web server authentication and hybrid authentication OFF.
     *
     * @throws URISyntaxException See {@link URISyntaxException}.
     */
    @Test
    public void testGetAuthorizationUrlForUserAgentFlowWithHybridAuthenticationOff() throws URISyntaxException {
        String callbackUrl = "sfdc://callback";
        URI authorizationUrl = OAuth2.getAuthorizationUrl(false, false, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, null, null, null);
        URI expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize?display=touch&response_type=token&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId());
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
        authorizationUrl = OAuth2.getAuthorizationUrl(false, false, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, "touch", null, null);
        expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize?display=touch&response_type=token&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId());
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
    }

    /**
     * Testing getAuthorizationUrl with web server authentication ON and hybrid authentication OFF.
     *
     * @throws URISyntaxException See {@link URISyntaxException}.
     */
    @Test
    public void testGetAuthorizationUrlForWebServerFlowWithHybridAuthenticationOff() throws URISyntaxException {
        String callbackUrl = "sfdc://callback";
        URI authorizationUrl = OAuth2.getAuthorizationUrl(true, false, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, null, "some-challenge", null);
        URI expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize?display=touch&response_type=code&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId() + "&code_challenge=some-challenge");
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
        authorizationUrl = OAuth2.getAuthorizationUrl(true, false, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, "touch", "some-challenge", null);
        expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize?display=touch&response_type=code&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId() + "&code_challenge=some-challenge");
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
    }

    private void tryScopes(String[] scopes, String expectedScopeParamValue) throws URISyntaxException {
        String callbackUrl = "sfdc://callback";
        URI authorizationUrl = OAuth2.getAuthorizationUrl(true, true, new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID,callbackUrl, scopes, null, "some-challenge", null);
        HttpUrl url = HttpUrl.get(authorizationUrl);
        boolean scopesFound = false;
        for (int i = 0, size = url.querySize(); i < size; i++) {
            if (url.queryParameterName(i).equalsIgnoreCase("scope")) {
                scopesFound = true;
                Assert.assertEquals("Wrong scopes included", expectedScopeParamValue, url.queryParameterValue(i));
                break;
            }
        }
        if (expectedScopeParamValue == null) {
            Assert.assertFalse("Scope found on empty scope", scopesFound);
        } else {
            Assert.assertTrue("No scope param found in query", scopesFound);
        }
    }

    /**
	 * Testing getAuthorizationUrl with scopes.
     *
	 * @throws URISyntaxException See {@link URISyntaxException}.
	 */
    @Test
	public void testGetAuthorizationUrlWithScopes() throws URISyntaxException {

        //verify basic scopes present
        tryScopes(new String[]{"foo", "bar"}, "bar foo refresh_token");

        //include a refresh_token scope even though the docs tell you not to
        tryScopes(new String[]{"foo", "bar", "refresh_token"}, "bar foo refresh_token");

        //include just one scope
        tryScopes(new String[]{"web"}, "refresh_token web");

        //empty scopes -- should not find scopes
        tryScopes(new String[] {}, null);
	}
	
	
	/**
	 * Testing refreshAuthToken. Call refresh token, then try out the auth token by calling /services/data/vXX.
	 *
	 * @throws IOException See {@link IOException}.
	 * @throws URISyntaxException See {@link URISyntaxException}.
	 * @throws OAuthFailedException See {@link OAuthFailedException}.
	 */
    @Test
	public void testRefreshAuthToken() throws IOException, OAuthFailedException, URISyntaxException {

		// Get an auth token using the refresh token.
		TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess,
				new URI(TestCredentials.LOGIN_URL), TestCredentials.CLIENT_ID,
                TestCredentials.REFRESH_TOKEN, null);
        Assert.assertNotNull("Auth token should not be null", refreshResponse.authToken);

		// Let's try it out.
		Request request = new Request.Builder()
				.addHeader("Content-Type", "application/json")
				.addHeader("Authorization", "Bearer " + refreshResponse.authToken)
				.url(TestCredentials.INSTANCE_URL + "/services/data/" + ApiVersionStrings.VERSION_NUMBER)
				.get()
				.build();
		Response resourcesResponse = httpAccess.getOkHttpClient().newCall(request).execute();
        Assert.assertEquals("HTTP response status code should have been 200 (OK)", HttpURLConnection.HTTP_OK, resourcesResponse.code());
	}
	
	/**
	 * Testing callIdentityService. Call refresh token then call out to identity service and
     * check that username returned match the one in TestCredentials.
	 *
	 * @throws IOException See {@link IOException}.
	 * @throws OAuthFailedException See {@link OAuthFailedException}.
	 * @throws URISyntaxException See {@link URISyntaxException}.
	 */
    @Test
	public void testCallIdentityService() throws IOException, OAuthFailedException, URISyntaxException {

		// Get an auth token using the refresh token
		TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess,
                new URI(TestCredentials.LOGIN_URL), TestCredentials.CLIENT_ID,
                TestCredentials.REFRESH_TOKEN, null);
        Assert.assertNotNull("Auth token should not be null", refreshResponse.authToken);

		// Now let's call the identity service
		IdServiceResponse id = OAuth2.callIdentityService(httpAccess, TestCredentials.INSTANCE_URL +
                "/id/" + TestCredentials.ORG_ID + "/" + TestCredentials.USER_ID, refreshResponse.authToken);
        Assert.assertEquals("Wrong username returned", TestCredentials.USERNAME, id.username);
        Assert.assertEquals("Wrong screenLockTimeout returned", -1, id.screenLockTimeout);
	}

    @Test
    public void testParseIdentityServiceResponse() throws JSONException {
        String responseJson = """
            {
              "id": "https://login.salesforce.com/id/00DB0000000ToZ3MAK/005B0000005V71XIAS",
              "asserted_user": true,
              "user_id": "005B0000005V71XIAS",
              "organization_id": "00DB0000000ToZ3MAK",
              "username": "wmathurin@gs0.mobilesdk.com",
              "nick_name": "wmathurin",
              "display_name": "Wolfgang Mathurin",
              "email": "wmathurin@salesforce.com",
              "email_verified": true,
              "first_name": "Wolfgang",
              "last_name": "Mathurin",
              "timezone": "America/Los_Angeles",
              "photos": {
                "picture": "https://mobilesdk.file.force.com/profilephoto/729B00000002yij/F",
                "thumbnail": "https://mobilesdk.file.force.com/profilephoto/729B00000002yij/T"
              },
              "addr_street": null,
              "addr_city": null,
              "addr_state": null,
              "addr_country": null,
              "addr_zip": null,
              "mobile_phone": null,
              "mobile_phone_verified": false,
              "is_lightning_login_user": false,
              "status": {
                "created_date": null,
                "body": null
              },
              "urls": {
                "enterprise": "https://mobilesdk.my.salesforce.com/services/Soap/c/{version}/00DB0000000ToZ3",
                "metadata": "https://mobilesdk.my.salesforce.com/services/Soap/m/{version}/00DB0000000ToZ3",
                "partner": "https://mobilesdk.my.salesforce.com/services/Soap/u/{version}/00DB0000000ToZ3",
                "rest": "https://mobilesdk.my.salesforce.com/services/data/v{version}/",
                "sobjects": "https://mobilesdk.my.salesforce.com/services/data/v{version}/sobjects/",
                "search": "https://mobilesdk.my.salesforce.com/services/data/v{version}/search/",
                "query": "https://mobilesdk.my.salesforce.com/services/data/v{version}/query/",
                "recent": "https://mobilesdk.my.salesforce.com/services/data/v{version}/recent/",
                "tooling_soap": "https://mobilesdk.my.salesforce.com/services/Soap/T/{version}/00DB0000000ToZ3",
                "tooling_rest": "https://mobilesdk.my.salesforce.com/services/data/v{version}/tooling/",
                "profile": "https://mobilesdk.my.salesforce.com/005B0000005V71XIAS",
                "feeds": "https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/feeds",
                "groups": "https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/groups",
                "users": "https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/users",
                "feed_items": "https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/feed-items",
                "feed_elements": "https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/feed-elements",
                "custom_domain": "https://mobilesdk.my.salesforce.com"
              },
              "active": true,
              "user_type": "STANDARD",
              "language": "en_US",
              "locale": "en_US",
              "utcOffset": -28800000,
              "last_modified_date": "2019-08-27T00:26:52Z",
              "is_app_installed": true
            }
            """;
        IdServiceResponse id = new IdServiceResponse(new JSONObject(responseJson));
        Assert.assertEquals("https://login.salesforce.com/id/00DB0000000ToZ3MAK/005B0000005V71XIAS", id.idUrl);
        Assert.assertEquals(true, id.assertedUser);
        Assert.assertEquals("005B0000005V71XIAS", id.userId);
        Assert.assertEquals("00DB0000000ToZ3MAK", id.orgId);
        Assert.assertEquals("wmathurin@gs0.mobilesdk.com", id.username);
        Assert.assertEquals("wmathurin", id.nickname);
        Assert.assertEquals("Wolfgang Mathurin", id.displayName);
        Assert.assertEquals("Wolfgang", id.firstName);
        Assert.assertEquals("Mathurin", id.lastName);
        Assert.assertEquals("wmathurin@salesforce.com", id.email);
        Assert.assertEquals("https://mobilesdk.file.force.com/profilephoto/729B00000002yij/F", id.pictureUrl);
        Assert.assertEquals("https://mobilesdk.file.force.com/profilephoto/729B00000002yij/T", id.thumbnailUrl);
        Assert.assertEquals("https://mobilesdk.my.salesforce.com/services/Soap/c/{version}/00DB0000000ToZ3", id.enterpriseSoapUrl);
        Assert.assertEquals("https://mobilesdk.my.salesforce.com/services/Soap/m/{version}/00DB0000000ToZ3", id.metadataSoapUrl);
        Assert.assertEquals("https://mobilesdk.my.salesforce.com/services/Soap/u/{version}/00DB0000000ToZ3", id.partnerSoapUrl);
        Assert.assertEquals("https://mobilesdk.my.salesforce.com/services/data/v{version}/", id.restUrl);
        Assert.assertEquals("https://mobilesdk.my.salesforce.com/services/data/v{version}/sobjects/", id.restSObjectsUrl);
        Assert.assertEquals("https://mobilesdk.my.salesforce.com/services/data/v{version}/search/", id.restSearchUrl);
        Assert.assertEquals("https://mobilesdk.my.salesforce.com/services/data/v{version}/query/", id.restQueryUrl);
        Assert.assertEquals("https://mobilesdk.my.salesforce.com/services/data/v{version}/recent/", id.restRecentUrl);
        Assert.assertEquals("https://mobilesdk.my.salesforce.com/005B0000005V71XIAS", id.profileUrl);
        Assert.assertEquals("https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/feeds", id.chatterFeedsUrl);
        Assert.assertEquals("https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/groups", id.chatterGroupsUrl);
        Assert.assertEquals("https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/feed-items", id.chatterFeedItemsUrl);
        Assert.assertEquals("https://mobilesdk.my.salesforce.com/services/data/v{version}/chatter/users", id.chatterUsersUrl);
        Assert.assertEquals(true, id.isActive);
        Assert.assertEquals("STANDARD", id.userType);
        Assert.assertEquals("en_US", id.language);
        Assert.assertEquals("en_US", id.locale);
        Assert.assertEquals(-28800000, id.utcOffset);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        calendar.setTime(id.lastModifiedDate);
        Assert.assertEquals(2019, calendar.get(Calendar.YEAR));
        Assert.assertEquals(8, calendar.get(Calendar.MONTH)+1);
        Assert.assertEquals(27, calendar.get(Calendar.DAY_OF_MONTH));
        Assert.assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY));
        Assert.assertEquals(26, calendar.get(Calendar.MINUTE));
        Assert.assertEquals(52, calendar.get(Calendar.SECOND));
    }

    /**
     * Testing getOpenIDToken.
     */
	@Test
	public void testGetOpenIDToken() {
        final String openIdToken = OAuth2.getOpenIDToken(TestCredentials.LOGIN_URL,
                TestCredentials.CLIENT_ID, TestCredentials.REFRESH_TOKEN);
        Assert.assertNotNull("OpenID token should not be null", openIdToken);
    }
}
