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

import android.os.AsyncTask;

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
	private String accountName;
	private String clientId;
	
	/** 
	 * AuthTokenProvider interface
	 * RestClient will call its authTokenProvider to refresh its authToken once it has expired. 
	 */
	public interface AuthTokenProvider {
		String getNewAuthToken();
		String getRefreshToken();
		long getLastRefreshTime();
	}
	
	/**
	 * AsyncRequestCallback interface
	 * Interface through which the result of asynchronous request is handled 
	 */
	public interface AsyncRequestCallback {
		void onSuccess(RestResponse response);
		void onError(Exception exception);
	}
	
    /**
     * Constructs a RestClient with the given baseUrl and authToken.
     * It uses the default httpAccess and has no AuthTokenProvider so it will not refresh the access token once it expires.
     * @param baseUrl
     * @param authToken
     */
    public RestClient(URI baseUrl, String authToken) {
		this(baseUrl, authToken, HttpAccess.DEFAULT, null, null, null, null, null, null);
	}

    /**
     * Constructs a RestClient with the given baseUrl, authToken, httpAccessor and authTokenProvider.
     * When it gets a 401 (not authorized) response from the server, it will ask the authTokenProvider for a new access token
     * and retry the request a second time.
	 * @param baseUrl
     * @param authToken
     * @param httpAccessor
     * @param authTokenProvider
     * @param accountName 
     * @param username 
     * @param userId 
     * @param orgId 
     * @param clientId
	 */
	public RestClient(URI baseUrl, String authToken, HttpAccess httpAccessor, AuthTokenProvider authTokenProvider, String accountName, String username, String userId, String orgId, String clientId) {
		super();
		this.authToken = authToken;
		this.baseUrl = baseUrl;
		this.httpAccessor = httpAccessor;
		this.authTokenProvider = authTokenProvider;
		this.accountName = accountName;
		this.username = username;
		this.userId = userId;
		this.orgId = orgId;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("RestClient: {\n")
		  .append("   baseUrl: ").append(baseUrl.toString()).append("\n")
		  .append("   accountName: ").append(accountName).append("\n")
		  .append("   username: ").append(username).append("\n")
		  .append("   userId: ").append(userId).append("\n")
		  .append("   orgId: ").append(orgId).append("\n")
		  // Un-comment if you must: tokens should not be printed to the log
		  // .append("   authToken: ").append(authToken).append("\n")
		  // .append("   refreshToken: ").append(getRefreshToken()).append("\n")
		  .append("   timeSinceLastRefresh: ").append(getElapsedTimeSinceLastRefresh()).append("\n")
		  .append("}\n");
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
	 * @return elapsed time (ms) since last refresh
	 */
	public long getElapsedTimeSinceLastRefresh() {
		long lastRefreshTime = (authTokenProvider != null ? authTokenProvider.getLastRefreshTime() : -1);
		if (lastRefreshTime < 0) {
			return -1;
		}
		else {
			return System.currentTimeMillis() - lastRefreshTime;
		}
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
	 * @return oauth client id
	 */
	public String getClientId() {
		return clientId;
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
	 * Send the given restRequest and process the result asynchronously with callback
	 * Note: intented to be used by code on the UI thread.
	 * @param restRequest
	 * @param callback
	 */
	public void sendAsync(RestRequest restRequest, AsyncRequestCallback callback) {
		new RestCallTask(callback).execute(restRequest);
	}

	/**
	 * Send the given restRequest synchronously and return a RestResponse
	 * Note: cannot be used by code on the UI thread (use sendAsync instead)
	 * @param restRequest
	 * @return
	 * @throws IOException 
	 */
	public RestResponse sendSync(RestRequest restRequest) throws IOException {
		return sendSync(restRequest.getMethod(), restRequest.getPath(), restRequest.getRequestEntity());
	}

	/**
	 * Send an arbitrary HTTP request synchronously given by its method, path and httpEntity
	 * Note: cannot be used by code on the UI thread (use sendAsync instead)
	 * @param method
	 * @param path
	 * @param httpEntity
	 * @return
	 * @throws IOException
	 */
	public RestResponse sendSync(RestMethod method, String path, HttpEntity httpEntity) throws IOException {
		return sendSync(method, path, httpEntity, true);
	}
	
	private RestResponse sendSync(RestMethod method, String path, HttpEntity httpEntity, boolean retryInvalidToken) throws IOException {
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
					return sendSync(method, path, httpEntity, false);
				}
			}
		}
		
		// Done
		return restResponse;
	}
	
	/**
	 * Async task used to send request asynchronously
	 */
	private class RestCallTask extends
			AsyncTask<RestRequest, Void, RestResponse> {

		private Exception exceptionThrown = null;
		private AsyncRequestCallback callback;

		public RestCallTask(AsyncRequestCallback callback) {
			this.callback = callback;
		}
		
		@Override
		protected RestResponse doInBackground(RestRequest... requests) {
			try {
				return sendSync(requests[0]);
			} catch (Exception e) {
				exceptionThrown = e;
				return null;
			}
		}

		@Override
		protected void onPostExecute(RestResponse result) {
			if (exceptionThrown != null) {
				callback.onError(exceptionThrown);
			}
			else {
				callback.onSuccess(result);
			}
		}
	}
	
	
	/**
	 * Only used in tests
	 * @param httpAccessor
	 */
	public void setHttpAccessor(HttpAccess httpAccessor) {
		this.httpAccessor = httpAccessor; 
	}
}