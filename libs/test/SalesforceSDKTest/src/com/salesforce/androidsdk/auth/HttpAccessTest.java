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

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountBuilder;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.util.test.TestCredentials;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Tests for HttpAccess.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class HttpAccessTest {

	private OkHttpClient okHttpClient;
	private Headers headers;
	private HttpUrl resourcesUrl;

	@Before
	public void setUp() throws Exception {
		TestCredentials.init(InstrumentationRegistry.getInstrumentation().getContext());
		final HttpAccess httpAccess = new HttpAccess(null, "dummy-agent");
		okHttpClient = httpAccess.getOkHttpClient();
		resourcesUrl = HttpUrl.parse(TestCredentials.INSTANCE_URL + "/services/data/" + TestCredentials.API_VERSION + "/");
		TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess,
				new URI(TestCredentials.LOGIN_URL), TestCredentials.CLIENT_ID,
                TestCredentials.REFRESH_TOKEN, null);
		headers = new Headers.Builder()
				.add("Content-Type", "application/json")
				.add("Authorization", "OAuth " + refreshResponse.authToken)
				.build();
	}

	/**
	 * Testing sending a GET request to /services/data - Check status code and response body
	 * @throws IOException 
	 * @throws URISyntaxException 
	 */
    @Test
	public void testDoGet() throws IOException {
		Response response = okHttpClient.newCall(new Request.Builder().url(resourcesUrl).headers(headers).get().build()).execute();
		checkResponse(response, HttpURLConnection.HTTP_OK, "sobjects", "identity", "recent", "search");
	}

	/**
	 * Testing sending a HEAD request to /services/data/vXX.X/ - Check status code and response body
	 * @throws IOException 
	 * @throws URISyntaxException 
	 */
    @Test
	public void testDoHead() throws IOException {
		Response response = okHttpClient.newCall(new Request.Builder().url(resourcesUrl).headers(headers).head().build()).execute();
        Assert.assertEquals("200 response expected", HttpURLConnection.HTTP_OK, response.code());
	}
	
	/**
	 * Testing sending a POST request to /services/data/vXX.X/ - Check status code and response body
	 * @throws IOException 
	 */
    @Test
	public void testSendPost() throws IOException {
		RequestBody body = RequestBody.create(RestRequest.MEDIA_TYPE_JSON, new JSONObject().toString());
		Response response = okHttpClient.newCall(new Request.Builder().url(resourcesUrl).headers(headers).post(body).build()).execute();
		checkResponse(response, HttpURLConnection.HTTP_BAD_METHOD, "'POST' not allowed");
	}

	/**
	 * Testing sending a PUT request to /services/data/vXX.X/ - Check status code and response body
	 * @throws IOException 
	 */
    @Test
	public void testSendPut() throws IOException {
		RequestBody body = RequestBody.create(RestRequest.MEDIA_TYPE_JSON, new JSONObject().toString());
		Response response = okHttpClient.newCall(new Request.Builder().url(resourcesUrl).headers(headers).put(body).build()).execute();
		checkResponse(response,  HttpURLConnection.HTTP_BAD_METHOD, "'PUT' not allowed");
	}
	
	/**
	 * Testing sending a DELETE request to /services/data/vXX.X/ - Check status code and response body
	 * @throws IOException 
	 */
    @Test
	public void testSendDelete() throws IOException {
		Response response = okHttpClient.newCall(new Request.Builder().url(resourcesUrl).headers(headers).delete().build()).execute();
		checkResponse(response,  HttpURLConnection.HTTP_BAD_METHOD, "'DELETE' not allowed");
	}

	/**
	 * Testing sending a PATCH request to /services/data/vXX.X/ - Check status code and response body
	 * @throws IOException 
	 */
    @Test
	public void testSendPatch() throws IOException {
		RequestBody body = RequestBody.create(RestRequest.MEDIA_TYPE_JSON, new JSONObject().toString());
		Response response = okHttpClient.newCall(new Request.Builder().url(resourcesUrl).headers(headers).patch(body).build()).execute();
		checkResponse(response,  HttpURLConnection.HTTP_BAD_METHOD, "'PATCH' not allowed");
	}
	
	/**
	 * Helper method to validate responses
	 * @param response
	 * @param expectedStatusCode
	 * @param stringsToMatch
	 */
	private void checkResponse(Response response, int expectedStatusCode, String... stringsToMatch) {
		// Check status code
        Assert.assertEquals(expectedStatusCode  + " response expected", expectedStatusCode, response.code());
		try {
			// Check body
			String responseAsString = new RestResponse(response).asString();
			for (String stringToMatch : stringsToMatch) {
                Assert.assertTrue("Response should contain " + stringToMatch, responseAsString.indexOf(stringToMatch) > 0);
			}
		} catch (Exception e) {
            Assert.fail("Failed to read response body");
			e.printStackTrace();
		}
	}

	/**
	 * Checks the user agent used by http access.
	 */
    @Test
	public void testUserAgentOfHttpAccess() {
		final HttpAccess http = new HttpAccess(SalesforceSDKManager.getInstance().getAppContext(),
				SalesforceSDKManager.getInstance().getUserAgent());
		final String userAgent = http.getUserAgent();
        Assert.assertTrue("User agent should start with SalesforceMobileSDK/<version>",
				userAgent.startsWith("SalesforceMobileSDK/" + SalesforceSDKManager.SDK_VERSION));
	}

	/**
	 * Verifies that UserAgentInterceptor with a UserAccount stamps per-user flags into
	 * the User-Agent header.
	 */
	@Test
	public void test_givenUserAgentInterceptorWithUser_whenIntercept_thenHeaderContainsUserFlags()
			throws Exception {
		final UserAccount user = buildMinimalUserAccount("testOrg1", "testUser1");
		SalesforceSDKManager.getInstance().registerUsedAppFeature("ZZ", user);
		try {
			final String header = captureUserAgentHeader(new HttpAccess.UserAgentInterceptor(user));
			Assert.assertTrue(
					"User-Agent header should contain per-user flag ZZ",
					header.contains("ZZ"));
			Assert.assertTrue(
					"User-Agent header should start with SalesforceMobileSDK/",
					header.startsWith("SalesforceMobileSDK/"));
		} finally {
			SalesforceSDKManager.getInstance().unregisterUsedAppFeature("ZZ", user);
		}
	}

	/**
	 * Verifies that the no-arg UserAgentInterceptor still produces a valid User-Agent header
	 * (regression guard for the original constructor path).
	 */
	@Test
	public void test_givenUserAgentInterceptorNoArgs_whenIntercept_thenHeaderStartsWithSalesforceMobileSDK()
			throws Exception {
		final String header = captureUserAgentHeader(new HttpAccess.UserAgentInterceptor());
		Assert.assertTrue(
				"User-Agent header should start with SalesforceMobileSDK/",
				header.startsWith("SalesforceMobileSDK/"));
	}

	/**
	 * Verifies that a UserAgentInterceptor for user A does NOT include flags registered
	 * for user B (per-user isolation on the wire).
	 */
	@Test
	public void test_givenTwoUsers_whenInterceptorForUserA_thenHeaderExcludesUserBFlags()
			throws Exception {
		final UserAccount userA = buildMinimalUserAccount("orgA", "userA");
		final UserAccount userB = buildMinimalUserAccount("orgB", "userB");
		SalesforceSDKManager.getInstance().registerUsedAppFeature("UA", userA);
		SalesforceSDKManager.getInstance().registerUsedAppFeature("UB", userB);
		try {
			final String header = captureUserAgentHeader(new HttpAccess.UserAgentInterceptor(userA));
			Assert.assertTrue("User-Agent should contain userA flag UA", header.contains("UA"));
			Assert.assertFalse("User-Agent should NOT contain userB flag UB", header.contains("UB"));
		} finally {
			SalesforceSDKManager.getInstance().unregisterUsedAppFeature("UA", userA);
			SalesforceSDKManager.getInstance().unregisterUsedAppFeature("UB", userB);
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Runs the given interceptor against a dummy GET request and returns the
	 * User-Agent header that the interceptor stamped on the outgoing request.
	 * Uses a capturing chain so no real network call is made.
	 */
	private static String captureUserAgentHeader(HttpAccess.UserAgentInterceptor interceptor)
			throws IOException {
		final AtomicReference<String> captured = new AtomicReference<>();
		final HttpUrl dummyUrl = new HttpUrl.Builder()
				.scheme("https").host("test.salesforce.com").build();
		final Request original = new Request.Builder().url(dummyUrl).build();

		interceptor.intercept(new Interceptor.Chain() {
			@Override
			public Request request() { return original; }

			@Override
			public Response proceed(Request request) {
				captured.set(request.header("User-Agent"));
				// Return a minimal non-null response to satisfy the chain contract.
				return new Response.Builder()
						.request(request)
						.protocol(okhttp3.Protocol.HTTP_1_1)
						.code(200)
						.message("OK")
						.build();
			}

			@Override public okhttp3.Connection connection() { return null; }
			@Override public okhttp3.Call call() { throw new UnsupportedOperationException(); }
			@Override public int connectTimeoutMillis() { return 0; }
			@Override public Interceptor.Chain withConnectTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
			@Override public int readTimeoutMillis() { return 0; }
			@Override public Interceptor.Chain withReadTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
			@Override public int writeTimeoutMillis() { return 0; }
			@Override public Interceptor.Chain withWriteTimeout(int t, java.util.concurrent.TimeUnit u) { return this; }
		});

		return captured.get();
	}

	private static UserAccount buildMinimalUserAccount(String orgId, String userId) {
		return UserAccountBuilder.getInstance()
				.authToken("tok")
				.refreshToken("rtok")
				.loginServer("https://login.salesforce.com")
				.idUrl("https://login.salesforce.com/id/" + orgId + "/" + userId)
				.instanceServer("https://cs1.salesforce.com")
				.orgId(orgId)
				.userId(userId)
				.username("user_" + userId + "@example.com")
				.accountName("user_" + userId + " (https://cs1.salesforce.com) (SalesforceSDKTest)")
				.build();
	}
}
