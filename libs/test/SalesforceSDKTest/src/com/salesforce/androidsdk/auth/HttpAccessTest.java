/*
 * Copyright (c) 2011-2014, salesforce.com, inc.
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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;

import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.TestCredentials;
import com.salesforce.androidsdk.auth.HttpAccess.Execution;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;

/**
 * Tests for HttpAccess.
 */
public class HttpAccessTest extends InstrumentationTestCase {

	private HttpAccess httpAccess;
	Map<String, String> headers;
	private URI resourcesUri;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		TestCredentials.init(getInstrumentation().getContext());
		httpAccess = new HttpAccess(null, "dummy-agent");
		TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess, new URI(TestCredentials.INSTANCE_URL), TestCredentials.CLIENT_ID, TestCredentials.REFRESH_TOKEN);
		headers = new HashMap<String, String>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "OAuth " + refreshResponse.authToken);
		resourcesUri = new URI(TestCredentials.INSTANCE_URL + "/services/data/" + TestCredentials.API_VERSION + "/");
	}

	/**
	 * Testing sending a GET request to /services/data - Check status code and response body
	 * @throws IOException 
	 * @throws URISyntaxException 
	 */
	public void testDoGet() throws IOException, URISyntaxException {
		Execution execution = httpAccess.doGet(headers, resourcesUri);
		checkResponse(execution, HttpStatus.SC_OK, "sobjects", "identity", "recent", "search");
	}

	/**
	 * Testing sending a HEAD request to /services/data/vXX.X/ - Check status code and response body
	 * @throws IOException 
	 * @throws URISyntaxException 
	 */
	public void testDoHead() throws IOException, URISyntaxException {
		Execution execution = httpAccess.doHead(headers, resourcesUri);
		assertEquals("200 response expected", HttpStatus.SC_OK, execution.response.getStatusLine().getStatusCode());
	}
	
	/**
	 * Testing sending a POST request to /services/data/vXX.X/ - Check status code and response body
	 * @throws IOException 
	 */
	public void testSendPost() throws IOException {
		Execution execution = httpAccess.doPost(headers, resourcesUri, null);		
		checkResponse(execution,  HttpStatus.SC_METHOD_NOT_ALLOWED, "'POST' not allowed");
	}

	/**
	 * Testing sending a PUT request to /services/data/vXX.X/ - Check status code and response body
	 * @throws IOException 
	 */
	public void testSendPut() throws IOException {
		Execution execution = httpAccess.doPut(headers, resourcesUri, null);
		checkResponse(execution,  HttpStatus.SC_METHOD_NOT_ALLOWED, "'PUT' not allowed");
	}
	
	/**
	 * Testing sending a DELETE request to /services/data/vXX.X/ - Check status code and response body
	 * @throws IOException 
	 */
	public void testSendDelete() throws IOException {
		Execution execution = httpAccess.doDelete(headers, resourcesUri);
		checkResponse(execution,  HttpStatus.SC_METHOD_NOT_ALLOWED, "'DELETE' not allowed");
	}

	/**
	 * Testing sending a PATCH request to /services/data/vXX.X/ - Check status code and response body
	 * @throws IOException 
	 */
	public void testSendPatch() throws IOException {
		Execution execution = httpAccess.doPatch(headers, resourcesUri, null);
		checkResponse(execution,  HttpStatus.SC_METHOD_NOT_ALLOWED, "'PATCH' not allowed");
	}
	
	/**
	 * Helper method to validate responses
	 * @param execution
	 * @param expectedStatusCode
	 * @param stringsToMatch
	 */
	private void checkResponse(Execution execution, int expectedStatusCode, String... stringsToMatch) {
		HttpResponse response = execution.response;
		// Check status code
		assertEquals(expectedStatusCode  + " response expected", expectedStatusCode, response.getStatusLine().getStatusCode());
		try {
			// Check body
			String responseAsString = EntityUtils.toString(response.getEntity());
			for (String stringToMatch : stringsToMatch) {
				assertTrue("Response should contain " + stringToMatch, responseAsString.indexOf(stringToMatch) > 0);
			}
		} 
		catch (Exception e) {
			fail("Failed to read response body");
			e.printStackTrace();
		}
	}

	
}
