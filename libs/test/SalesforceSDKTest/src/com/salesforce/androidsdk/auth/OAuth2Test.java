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
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.TestCredentials;
import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.OAuth2.IdServiceResponse;
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.ApiVersionStrings;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

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

	/**
	 * Testing getAuthorizationUrl.
     *
	 * @throws URISyntaxException
	 */
    @Test
	public void testGetAuthorizationUrl() throws URISyntaxException {
		String callbackUrl = "sfdc://callback";
		URI authorizationUrl = OAuth2.getAuthorizationUrl(new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, null, null);
		URI expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize?display=touch&response_type=token&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
				SalesforceSDKManager.getInstance().getDeviceId());
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
		authorizationUrl = OAuth2.getAuthorizationUrl(new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, "touch", null);
		expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize?display=touch&response_type=token&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId());
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
	}

	/**
	 * Testing getAuthorizationUrl with params.
     *
	 * @throws URISyntaxException
	 */
    @Test
	public void testGetAuthorizationUrlWithParams() throws URISyntaxException {
		String callbackUrl = "sfdc://callback";
		Map<String,String> params = new HashMap<>();
		params.put("param1", "val1");
		params.put("param2", "val2");
		params.put("param3", null);
		URI authorizationUrl = OAuth2.getAuthorizationUrl(new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, null, params);
        Assert.assertTrue("Wrong authorization url", authorizationUrl.getRawQuery().indexOf("&param1=val1") > 0);
        Assert.assertTrue("Wrong authorization url", authorizationUrl.getRawQuery().indexOf("&param2=val2") > 0);
        Assert.assertTrue("Wrong authorization url", authorizationUrl.getRawQuery().indexOf("&param3=") > 0);
	}

    /**
     * Testing getAuthorizationUrl with branded login path.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testGetAuthorizationUrlWithBrandedLoginPath() throws URISyntaxException {
        String callbackUrl = "sfdc://callback";
        final String brandedLoginPath = "BRAND";
        SalesforceSDKManager.getInstance().setLoginBrand(brandedLoginPath);
        URI authorizationUrl = OAuth2.getAuthorizationUrl(new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, null, null);
        URI expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize/BRAND?display=touch&response_type=token&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId());
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
        authorizationUrl = OAuth2.getAuthorizationUrl(new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, "touch", null);
        expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize/BRAND?display=touch&response_type=token&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId());
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
    }

    /**
     * Testing getAuthorizationUrl with branded login path with leading slash.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testGetAuthorizationUrlWithBrandedLoginPathWithLeadingSlash() throws URISyntaxException {
        String callbackUrl = "sfdc://callback";
        final String brandedLoginPath = "BRAND";
        SalesforceSDKManager.getInstance().setLoginBrand(brandedLoginPath);
        URI authorizationUrl = OAuth2.getAuthorizationUrl(new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, null, null);
        URI expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize/BRAND?display=touch&response_type=token&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId());
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
        authorizationUrl = OAuth2.getAuthorizationUrl(new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, "touch", null);
        expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize/BRAND?display=touch&response_type=token&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId());
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
    }

    /**
     * Testing getAuthorizationUrl with branded login path with trailing slash.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testGetAuthorizationUrlWithBrandedLoginPathWithTrailingSlash() throws URISyntaxException {
        String callbackUrl = "sfdc://callback";
        final String brandedLoginPath = "BRAND";
        SalesforceSDKManager.getInstance().setLoginBrand(brandedLoginPath);
        URI authorizationUrl = OAuth2.getAuthorizationUrl(new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, null, null);
        URI expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize/BRAND?display=touch&response_type=token&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId());
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
        authorizationUrl = OAuth2.getAuthorizationUrl(new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID, callbackUrl, null, "touch", null);
        expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL +
                "/services/oauth2/authorize/BRAND?display=touch&response_type=token&client_id=" +
                TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl + "&device_id=" +
                SalesforceSDKManager.getInstance().getDeviceId());
        Assert.assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
    }

    private void tryScopes(String[] scopes, String expectedScopeParamValue) throws URISyntaxException {
        String callbackUrl = "sfdc://callback";
        URI authorizationUrl = OAuth2.getAuthorizationUrl(new URI(TestCredentials.LOGIN_URL),
                TestCredentials.CLIENT_ID,callbackUrl, scopes, null, null);
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
	 * @throws URISyntaxException
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
	 * Testing refreshAuthToken.
	 * 
	 * Call refresh token, then try out the auth token by calling /services/data/vXX.
	 * 
	 * @throws IOException
	 * @throws URISyntaxException 
	 * @throws OAuthFailedException 
	 */
    @Test
	public void testRefreshAuthToken() throws IOException, OAuthFailedException, URISyntaxException {

		// Get an auth token using the refresh token
		TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess,
				new URI(TestCredentials.LOGIN_URL), TestCredentials.CLIENT_ID,
                TestCredentials.REFRESH_TOKEN, null);
        Assert.assertNotNull("Auth token should not be null", refreshResponse.authToken);
		
		// Let's try it out
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
	 * Testing callIdentityService.
	 * 
	 * Call refresh token then call out to identity service and check that username returned match the one in TestCredentials.
	 * 
	 * @throws IOException
	 * @throws OAuthFailedException
	 * @throws URISyntaxException
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
        Assert.assertEquals("Wrong pinLength returned", -1, id.pinLength);
        Assert.assertEquals("Wrong screenLockTimeout returned", -1, id.screenLockTimeout);
	}
}
