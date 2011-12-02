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
package com.salesforce.androidsdk.auth;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;
import android.util.Log;

import com.salesforce.androidsdk.auth.HttpAccess.Execution;

/**
 * Helper methods for common OAuth2 requests.
 * 
 * The typical OAuth2 flow is;
 * 
 * <ol>
 * <li> The authorization flow is started by presenting the web-based
 * authorization screen to the user.  This will prompt him/her to login and to
 * authorize our application. The result will be a callback with an
 * authorization code, or an error.</li>
 * 
 * <li> Use the authorization code from (a) with the token end point, to get
 * access and refresh tokens, as well as other metadata.</li>
 * 
 * <li> Use the access token from (b) to call the identity service, which will let
 * us find out the user's username.</li>
 * 
 * <li> Store the username, and refresh and access tokens somewhere safe (like the
 * AccountManager).</li>
 * 
 * <li> If the access token becomes invalid, use the refresh token to get another
 * access token.</li>
 * 
 * <li> If the refresh token becomes invalid, go back to the beginning.</li>
 * </ol>
 * 
 */
public class OAuth2 {

	// Login URLs / paths
	public static final String DEFAULT_LOGIN_URL = "https://login.salesforce.com";
	public static final String SANDBOX_LOGIN_URL = "https://test.salesforce.com";
	private static final String OAUTH_AUTH_PATH = "/services/oauth2/authorize?display=mobile";
	private static final String OAUTH_TOKEN_PATH = "/services/oauth2/token";

	/**
	 * Build the URL to the authorization web page for this login server.
	 * You need not provide refresh_token, as it is provided automatically.
	 * 
	 * @param loginServer
	 *            the base protocol and server to use (e.g.
	 *            https://login.salesforce.com)
	 * @param clientId
	 *            OAuth client ID
	 * @param callbackUrl
	 *            OAuth callback url
	 * @param scopes A list of OAuth scopes to request (eg {"visualforce","api"}). If null, the default OAuth scope is provided.
	 * @return A URL to start the OAuth flow in a web browser/view.
	 * 
	 * @see <a href="https://help.salesforce.com/apex/HTViewHelpDoc?language=en&id=remoteaccess_oauth_scopes.htm">RemoteAccess OAuth Scopes</a>
	 * 
	 */
	public static URI getAuthorizationUrl(URI loginServer, String clientId,
			String callbackUrl, String[] scopes) {
	    
	    StringBuilder sb = new StringBuilder();
	    sb.append(OAUTH_AUTH_PATH);
	    sb.append("&response_type=token");
	    sb.append("&client_id=");
	    sb.append( Uri.encode(clientId) );
	    if ((null != scopes) && (scopes.length > 0)) {
	        //need to always have the refresh_token scope to reuse our refresh token
	        sb.append("&scope=refresh_token");

	        StringBuilder scopeStr = new StringBuilder();
	        for (String scope : scopes) {
	            if (!scope.equalsIgnoreCase("refresh_token")) {
	                scopeStr.append(" ").append(scope);
	            }
	        }
	        String safeScopeStr = Uri.encode(scopeStr.toString());
	        sb.append(safeScopeStr);
	    }
	    sb.append("&redirect_uri=");
	    sb.append(callbackUrl);
	    
	    String approvalUrl = sb.toString();
	    return loginServer.resolve(approvalUrl);
	}

	/**
	 * Get a new auth token using the refresh token.
	 * 
	 * @param httpAccessor
	 * @param loginServer
	 * @param clientId
	 * @param refreshToken
	 * @return
	 * @throws OAuthFailedException
	 * @throws IOException
	 */
	public static TokenEndpointResponse refreshAuthToken(
			HttpAccess httpAccessor, URI loginServer, String clientId,
			String refreshToken) throws OAuthFailedException, IOException {
		List<NameValuePair> params = makeTokenEndpointParams("refresh_token",
				clientId);
		params.add(new BasicNameValuePair("refresh_token", refreshToken));
		params.add(new BasicNameValuePair("format", "json"));
		TokenEndpointResponse tr = makeTokenEndpointRequest(httpAccessor,
				loginServer, params);
		return tr;
	}

	/**
	 * Call the identity service to determine the username of the user, given
	 * their identity service ID and an access token.
	 * 
	 * @param httpAccessor
	 * @param identityServiceIdUrl
	 * @param authToken
	 * @return
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public static final String getUsernameFromIdentityService(
			HttpAccess httpAccessor, String identityServiceIdUrl,
			String authToken) throws IOException, URISyntaxException {

		Map<String, String> idHeaders = new HashMap<String, String>();
		idHeaders.put("Authorization", "OAuth " + authToken);
		Execution exec = httpAccessor.doGet(idHeaders,
				new URI(identityServiceIdUrl));
		IdServiceResponse id = new IdServiceResponse(exec.response);
		return id.username;
	}

	/**
	 * @param httpAccessor
	 * @param loginServer
	 * @param params
	 * @return
	 * @throws OAuthFailedException
	 * @throws IOException
	 */
	private static TokenEndpointResponse makeTokenEndpointRequest(
			HttpAccess httpAccessor, URI loginServer, List<NameValuePair> params)
			throws OAuthFailedException, IOException {
		UrlEncodedFormEntity req = new UrlEncodedFormEntity(params, "UTF-8");
		try {
			// Call the token endpoint, and get tokens, instance url etc.
			Execution ex = httpAccessor.doPost(null, loginServer
							.resolve(OAUTH_TOKEN_PATH), req);
			int statusCode = ex.response.getStatusLine().getStatusCode();
			if (statusCode == 200) {
				return new TokenEndpointResponse(ex.response);
			} else {
				throw new OAuthFailedException(new TokenErrorResponse(
						ex.response), statusCode);
			}
		} catch (UnsupportedEncodingException ex1) {
			throw new RuntimeException(ex1); // should never happen
		}
	}

	/**
	 * @param grantType
	 * @param clientId
	 * @return
	 */
	private static List<NameValuePair> makeTokenEndpointParams(
			String grantType, String clientId) {
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("grant_type", grantType));
		params.add(new BasicNameValuePair("client_id", clientId));
		return params;
	}

	/**
	 * Exception thrown when refresh fails.
	 */
	public static class OAuthFailedException extends Exception {

		OAuthFailedException(TokenErrorResponse err, int httpStatusCode) {
			super(err.toString());
			this.response = err;
			this.httpStatusCode = httpStatusCode;
		}

		final TokenErrorResponse response;
		final int httpStatusCode;

		boolean isRefreshTokenInvalid() {
			return httpStatusCode == 401
					|| httpStatusCode == 403
					|| (httpStatusCode == 400 && response.error
							.equals("invalid_grant"));
		}

		private static final long serialVersionUID = 1L;
	}

	/**************************************************************************************************
	 * 
	 * Helper classes to parse responses
	 * 
	 **************************************************************************************************/

	public static class AbstractResponse {

		protected JSONObject parseResponse(HttpResponse httpResponse)
				throws IOException, JSONException {
			String responseAsString = EntityUtils.toString(httpResponse
					.getEntity(), HTTP.UTF_8);
			JSONObject parsedResponse = new JSONObject(responseAsString);
			return parsedResponse;
		}

	}

	/**
	 * Helper class to parse an identity service response.
	 */
	public static class IdServiceResponse extends AbstractResponse {
		public String username;

		public IdServiceResponse(HttpResponse httpResponse) {
			try {
				JSONObject parsedResponse = parseResponse(httpResponse);
				username = parsedResponse.getString("username");
			} catch (Exception e) {
				Log.w("IdServiceResponse:contructor", "", e);
			}
		}
	}

	/**
	 * Helper class to parse a token refresh error response.
	 */
	public static class TokenErrorResponse extends AbstractResponse {
		public String error;
		public String errorDescription;

		public TokenErrorResponse(HttpResponse httpResponse) {
			try {
				JSONObject parsedResponse = parseResponse(httpResponse);
				error = parsedResponse.getString("error");
				errorDescription = parsedResponse
						.getString("error_description");
			} catch (Exception e) {
				Log.w("TokenErrorResponse:contructor", "", e);
			}
		}

		@Override
		public String toString() {
			return error + ":" + errorDescription;
		}
	}

	/**
	 * Helper class to parse a token refresh response.
	 */
	public static class TokenEndpointResponse extends AbstractResponse {
		public String authToken;
		public String refreshToken;
		public String instanceUrl;
		public String idUrl;
		public String idUrlWithInstance;
		public String orgId;
		public String userId;

		/**
		 * Constructor used during login flow
		 * @param callbackUrlParams
		 */
		public TokenEndpointResponse(Map<String, String> callbackUrlParams) {
			try {
				authToken = callbackUrlParams.get("access_token");
				refreshToken = callbackUrlParams.get("refresh_token");
				instanceUrl = callbackUrlParams.get("instance_url");
				idUrl = callbackUrlParams.get("id");
				computeOtherFields();
			} catch (Exception e) {
				Log.w("TokenEndpointResponse:contructor", "", e);
			}
		}

		/**
		 * Constructor used during refresh flow
		 * @param httpResponse
		 */
		public TokenEndpointResponse(HttpResponse httpResponse) {
			try {
				JSONObject parsedResponse = parseResponse(httpResponse);
				Log.i("TokenEndpointResponse", "parsedResponse->" + parsedResponse.toString());
				authToken = parsedResponse.getString("access_token");
				instanceUrl = parsedResponse.getString("instance_url");
				idUrl  = parsedResponse.getString("id");
			} catch (Exception e) {
				Log.w("TokenEndpointResponse:contructor", "", e);
			}
		}
		
		private void computeOtherFields() throws URISyntaxException {
			idUrlWithInstance = idUrl.replace(new URI(idUrl).getHost(), new URI(instanceUrl).getHost());
			String[] idUrlFragments = idUrl.split("/");
			userId = idUrlFragments[idUrlFragments.length - 1];
			orgId = idUrlFragments[idUrlFragments.length - 2];
		}
	}
}
