/*
 * Copyright (c) 2011, salesforce.com, inc.
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
package com.salesforce.androidsdk.rest;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;

import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.HttpAccess.Execution;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;

/**
 * RestClient allows you to send authenticated HTTP requests to a force.com server.
 */
public class RestClient {

	private final AuthTokenProvider authTokenProvider;
	private HttpAccess httpAccessor;
	private final URI baseUrl;
	private String authToken;
	private String userId;
	private String orgId;
	private String username;
	
	/** 
	 * AuthTokenProvider interface
	 * RestClient will call its authTokenProvider to refresh its authToken once it has expired. 
	 */
	public interface AuthTokenProvider {
		public String getNewAuthToken();
		public String getRefreshToken();
	}
	
    /**
     * Constructs a RestClient with the given baseUrl and authToken.
     * It uses the default httpAccess and has no AuthTokenProvider so it will not refresh the access token once it expires.
     * @param baseUrl
     * @param authToken
     */
    public RestClient(URI baseUrl, String authToken) {
		this(baseUrl, authToken, HttpAccess.DEFAULT, null, null, null, null);
	}
	
	/**
     * Constructs a RestClient with the given baseUrl, authToken, httpAccessor and authTokenProvider.
     * When it gets a 401 (not authorized) response from the server, it will ask the authTokenProvider for a new access token
     * and retry the request a second time.
	 * @param baseUrl
	 * @param authToken
	 * @param httpAccessor
	 * @param authTokenProvider
	 * @param username 
	 * @param userId 
	 * @param orgId 
	 */
	public RestClient(URI baseUrl, String authToken, HttpAccess httpAccessor, AuthTokenProvider authTokenProvider, String username, String userId, String orgId) {
		super();
		this.authToken = authToken;
		this.baseUrl = baseUrl;
		this.httpAccessor = httpAccessor;
		this.authTokenProvider = authTokenProvider;
		this.username = username;
		this.userId = userId;
		this.orgId = orgId;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("   baseUrl: ").append(baseUrl.toString()).append(",\n")
		  .append("   authToken: ").append(authToken).append("\n")
		  .append("   username: ").append(username).append("\n")
		  .append("   userId: ").append(userId).append("\n")
		  .append("   orgId: ").append(orgId).append("\n");
		return sb.toString();
	}
	
	/**
	 * @return authToken for this RestClient
	 */
	public synchronized String getAuthToken() {
		return authToken;
	}
	
	/**
	 * @return refresh token if available
	 */
	public String getRefreshToken() {
		return (authTokenProvider != null ? authTokenProvider.getRefreshToken() : null);
	}
	
	/**
	 * @return salesforce username
	 */
	public String getUsername() {
		return userId;
	}
	
	/**
	 * @return salesforce user id
	 */
	public String getUserId() {
		return userId;
	}
	
	/**
	 * @return salesforce org id
	 */
	public String getOrgId() {
		return orgId;
	}
	
	/**
	 * Change authToken for this RestClient
	 * @param newAuthToken
	 */
	private synchronized void setAuthToken(String newAuthToken) {
		authToken = newAuthToken;
	}
	
	/**
	 * @return baseUrl for this RestClient
	 */
	public URI getBaseUrl() {
		return baseUrl;
	}
	
	/**
	 * Send the given restRequest and return a RestResponse
	 * @param restRequest
	 * @return
	 * @throws IOException 
	 */
	public RestResponse send(RestRequest restRequest) throws IOException {
		return send(restRequest.getMethod(), restRequest.getPath(), restRequest.getRequestEntity());
	}

	/**
	 * Send an arbitrary HTTP request given by its method, path and httpEntity
	 * @param method
	 * @param path
	 * @param httpEntity
	 * @return
	 * @throws IOException
	 */
	public RestResponse send(RestMethod method, String path, HttpEntity httpEntity) throws IOException {
		return send(method, path, httpEntity, true);
	}
	
	private RestResponse send(RestMethod method, String path, HttpEntity httpEntity, boolean retryInvalidToken) throws IOException {
		Execution exec = null;

		// Prepare headers
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Content-Type", "application/json");
		if (getAuthToken() != null) {
			headers.put("Authorization", "OAuth " + authToken);
		}

		// Do the actual call
		switch(method) {
		case DELETE:
			exec = httpAccessor.doDelete(headers, baseUrl.resolve(path)); break;
		case GET:
			exec = httpAccessor.doGet(headers, baseUrl.resolve(path)); break;
		case HEAD:
			exec = httpAccessor.doHead(headers, baseUrl.resolve(path)); break;
		case PATCH:
			exec = httpAccessor.doPatch(headers, baseUrl.resolve(path), httpEntity); break;
		case POST:
			exec = httpAccessor.doPost(headers, baseUrl.resolve(path), httpEntity); break;
		case PUT:
			exec = httpAccessor.doPut(headers, baseUrl.resolve(path), httpEntity); break;
		}

		// Build response object
		RestResponse restResponse = new RestResponse(exec.response);
		
		int statusCode = restResponse.getStatusCode();

		// 401 bad access token *
		if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
			// If we haven't retried already and we have an accessTokeProvider
			// Then let's try to get a new authToken
			if (retryInvalidToken && authTokenProvider != null) {
				String newAuthToken = authTokenProvider.getNewAuthToken();
				if (newAuthToken != null) {
					setAuthToken(newAuthToken);
					// Retry with the new authToken
					return send(method, path, httpEntity, false);
				}
			}
		}
		
		// Done
		return restResponse;
	}
	
	/**
	 * Only used in tests
	 * @param httpAccessor
	 */
	public void setHttpAccessor(HttpAccess httpAccessor) {
		this.httpAccessor = httpAccessor; 
	}
}