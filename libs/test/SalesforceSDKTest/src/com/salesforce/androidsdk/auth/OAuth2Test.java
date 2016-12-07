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

import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.TestCredentials;
import com.salesforce.androidsdk.auth.OAuth2.IdServiceResponse;
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.ApiVersionStrings;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Tests for OAuth2
 *
 */
public class OAuth2Test extends InstrumentationTestCase {

	private HttpAccess httpAccess;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		TestCredentials.init(getInstrumentation().getContext());
		httpAccess = new HttpAccess(null, "dummy-agent");		
	}

	/**
	 * Testing getAuthorizationUrl
	 * @throws URISyntaxException 
	 * 
	 */
	public void testGetAuthorizationUrl() throws URISyntaxException {
		String callbackUrl = "sfdc://callback";
		URI authorizationUrl = OAuth2.getAuthorizationUrl(new URI(TestCredentials.LOGIN_URL), TestCredentials.CLIENT_ID, callbackUrl, null);
		URI expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL + "/services/oauth2/authorize?display=touch&response_type=token&client_id=" + TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl);
		assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
		
		authorizationUrl = OAuth2.getAuthorizationUrl(new URI(TestCredentials.LOGIN_URL), TestCredentials.CLIENT_ID, callbackUrl, null, null, "touch");
		expectedAuthorizationUrl = new URI(TestCredentials.LOGIN_URL + "/services/oauth2/authorize?display=touch&response_type=token&client_id=" + TestCredentials.CLIENT_ID + "&redirect_uri=" + callbackUrl);
		assertEquals("Wrong authorization url", expectedAuthorizationUrl, authorizationUrl);
	}

    private void tryScopes(String[] scopes, String expectedScopeParamValue) throws URISyntaxException {
        String callbackUrl = "sfdc://callback";
        URI authorizationUrl = OAuth2.getAuthorizationUrl(new URI(TestCredentials.LOGIN_URL),TestCredentials.CLIENT_ID,callbackUrl, scopes);
        HttpUrl url = HttpUrl.get(authorizationUrl);

        boolean scopesFound = false;
        for (int i = 0, size = url.querySize(); i < size; i++) {
            if (url.queryParameterName(i).equalsIgnoreCase("scope")) {
                scopesFound = true;
                assertEquals("Wrong scopes included", expectedScopeParamValue, url.queryParameterValue(i));
                break;
            }
        }

        if (expectedScopeParamValue == null) {
            assertFalse("Scope found on empty scope", scopesFound);
        }
        else {
            assertTrue("No scope param found in query", scopesFound);
        }
    }

    /**
	 * Testing getAuthorizationUrl with scopes
	 * @throws URISyntaxException 
	 * 
	 */
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
	 * Testing refreshAuthToken
	 * 
	 * Call refresh token, then try out the auth token by calling /services/data/vXX
	 * 
	 * @throws IOException
	 * @throws URISyntaxException 
	 * @throws OAuthFailedException 
	 */
	public void testRefreshAuthToken() throws IOException, OAuthFailedException, URISyntaxException {
		// Get an auth token using the refresh token
		TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess, new URI(TestCredentials.INSTANCE_URL), TestCredentials.CLIENT_ID, TestCredentials.REFRESH_TOKEN);
		assertNotNull("Auth token should not be null", refreshResponse.authToken);
		
		// Let's try it out
		Request request = new Request.Builder()
				.addHeader("Content-Type", "application/json")
				.addHeader("Authorization", "Bearer " + refreshResponse.authToken)
				.url(TestCredentials.INSTANCE_URL + "/services/data/" + ApiVersionStrings.VERSION_NUMBER)
				.get()
				.build();

		Response resourcesResponse = httpAccess.getOkHttpClient().newCall(request).execute();

		assertEquals("HTTP response status code should have been 200 (OK)", HttpURLConnection.HTTP_OK, resourcesResponse.code());
	}
	
	/**
	 * Testing callIdentityService
	 * 
	 * Call refresh token then call out to identity service and check that username returned match the one in TestCredentials
	 * 
	 * @throws IOException
	 * @throws OAuthFailedException
	 * @throws URISyntaxException
	 */
	public void testCallIdentityService() throws IOException, OAuthFailedException, URISyntaxException {
		// Get an auth token using the refresh token
		TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess, new URI(TestCredentials.INSTANCE_URL), TestCredentials.CLIENT_ID, TestCredentials.REFRESH_TOKEN);
		assertNotNull("Auth token should not be null", refreshResponse.authToken);

		// Now let's call the identity service
		IdServiceResponse id = OAuth2.callIdentityService(httpAccess, TestCredentials.INSTANCE_URL + "/id/" + TestCredentials.ORG_ID + "/" + TestCredentials.USER_ID, refreshResponse.authToken);
		assertEquals("Wrong username returned", TestCredentials.USERNAME, id.username);
		assertEquals("Wrong pinLength returned", -1, id.pinLength);
		assertEquals("Wrong screenLockTimeout returned", -1, id.screenLockTimeout);
	}
	
}