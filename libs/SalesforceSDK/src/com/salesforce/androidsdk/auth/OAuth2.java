/*
 * Copyright (c) 2014, salesforce.com, inc.
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

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.auth.HttpAccess.Execution;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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

    // Misc constants: strings appearing in requests or responses
    private static final String ACCESS_TOKEN = "access_token";
    private static final String CLIENT_ID = "client_id";
    private static final String ERROR = "error";
    private static final String ERROR_DESCRIPTION = "error_description";
    private static final String FORMAT = "format";
    private static final String GRANT_TYPE = "grant_type";
    private static final String ID = "id";
    private static final String INSTANCE_URL = "instance_url";
    private static final String JSON = "json";
    private static final String MOBILE_POLICY = "mobile_policy";
    private static final String PIN_LENGTH = "pin_length";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String RESPONSE_TYPE = "response_type";
    private static final String SCOPE = "scope";
    private static final String REDIRECT_URI = "redirect_uri";
    private static final String SCREEN_LOCK = "screen_lock";
    private static final String TOKEN = "token";
    private static final String USERNAME = "username";
    private static final String CODE = "code";
    private static final String ACTIVATED_CLIENT_CODE = "activated_client_code";
    private static final String CUSTOM_ATTRIBUTES = "custom_attributes";
    private static final String CUSTOM_PERMISSIONS = "custom_permissions";
    private static final String SFDC_COMMUNITY_ID = "sfdc_community_id";
    private static final String SFDC_COMMUNITY_URL = "sfdc_community_url";
    private static final String AND = "&";
    private static final String EQUAL = "=";
    private static final String TOUCH = "touch";

    // Login paths
    private static final String OAUTH_AUTH_PATH = "/services/oauth2/authorize?display=";
    private static final String OAUTH_TOKEN_PATH = "/services/oauth2/token";
    private static final String OAUTH_REVOKE_PATH = "/services/oauth2/revoke?token=";

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
       return getAuthorizationUrl(loginServer, clientId, callbackUrl, scopes, null, null);
    }

    public static URI getAuthorizationUrl(URI loginServer, String clientId,
            String callbackUrl, String[] scopes, String clientSecret) {
    	return getAuthorizationUrl(loginServer, clientId, callbackUrl, scopes, clientSecret, null);
    }
    
    public static URI getAuthorizationUrl(URI loginServer, String clientId,
            String callbackUrl, String[] scopes, String clientSecret, String displayType) {
        final StringBuilder sb = new StringBuilder(loginServer.toString());
        sb.append(OAUTH_AUTH_PATH).append(displayType == null ? TOUCH : displayType);
        sb.append(AND).append(RESPONSE_TYPE).append(EQUAL).append(clientSecret == null ? TOKEN : ACTIVATED_CLIENT_CODE);
        sb.append(AND).append(CLIENT_ID).append(EQUAL).append(Uri.encode(clientId));
        if (scopes != null && scopes.length > 0) sb.append(AND).append(SCOPE).append(EQUAL).append(Uri.encode(computeScopeParameter(scopes)));
        sb.append(AND).append(REDIRECT_URI).append(EQUAL).append(callbackUrl);
        return URI.create(sb.toString());
    }

    private static String computeScopeParameter(String[] scopes) {
        final List<String> scopesList = Arrays.asList(scopes == null ? new String[]{} : scopes);
        Set<String> scopesSet = new TreeSet<String>(scopesList); // sorted set to make tests easier
        scopesSet.add(REFRESH_TOKEN);
        return TextUtils.join(" ", scopesSet.toArray(new String[]{}));
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
        return refreshAuthToken(httpAccessor, loginServer, clientId, refreshToken, null);
    }

    /**
     * Get a new auth token using the refresh token.
     *
     * @param httpAccessor
     * @param loginServer
     * @param clientId
     * @param refreshToken
     * @param clientSecret
     * @return
     * @throws OAuthFailedException
     * @throws IOException
     */
    public static TokenEndpointResponse refreshAuthToken(
            HttpAccess httpAccessor, URI loginServer, String clientId,
            String refreshToken, String clientSecret) throws OAuthFailedException, IOException {
        List<NameValuePair> params = makeTokenEndpointParams(REFRESH_TOKEN,
                clientId, clientSecret);
        params.add(new BasicNameValuePair(REFRESH_TOKEN, refreshToken));
        params.add(new BasicNameValuePair(FORMAT, JSON));
        TokenEndpointResponse tr = makeTokenEndpointRequest(httpAccessor,
                loginServer, params);
        return tr;
    }

    /**
     * Revokes the existing refresh token.
     *
     * @param httpAccessor
     * @param loginServer
     * @param clientId
     * @param refreshToken
     * @throws OAuthFailedException
     * @throws IOException
     */
    public static void revokeRefreshToken(HttpAccess httpAccessor, URI loginServer, String clientId, String refreshToken) {
        try {
            final StringBuilder sb = new StringBuilder(loginServer.toString());
            sb.append(OAUTH_REVOKE_PATH);
            sb.append(Uri.encode(refreshToken));
            httpAccessor.doGet(null, URI.create(sb.toString()));
        } catch (IOException e) {
        	Log.w("OAuth2:revokeRefreshToken", e);
        }
    }

     /** @returns a TokenEndointResponse from the give authorization code, this is typically the first step after
     * receiving an authorization code from the oAuth authorization UI flow.
     * In addition, this will also call the Identity service to fetch & populate the username field.
     *
     * @param loginServerUrl  the protocol & host (e.g. https://login.salesforce.com) that the authCode was generated from
     * @param clientSecret the client secret if there is one (e.g. for IP/IC bypass)
     * @param authCode     the authorization code issued by the oauth authorization flow.
     *
     * @throws IOException
     * @throws URISyntaxException
     * @throws OAuthFailedException
     *
     */
    public static TokenEndpointResponse swapAuthCodeForTokens(HttpAccess httpAccessor, URI loginServerUrl, String clientSecret,
            String authCode, String clientId, String callbackUrl) throws IOException, URISyntaxException, OAuthFailedException {
        // call the token endpoint, and swap our authorization code for a refresh & access tokens.
        List<NameValuePair> params = makeTokenEndpointParams("authorization_code", clientId, clientSecret);
        params.add(new BasicNameValuePair("code", authCode));
        params.add(new BasicNameValuePair("redirect_uri", callbackUrl));
        TokenEndpointResponse tr = makeTokenEndpointRequest(httpAccessor, loginServerUrl, params);
        return tr;
    }

    /**
     * Call the identity service to determine the username of the user and the mobile policy, given
     * their identity service ID and an access token.
     *
     * @param httpAccessor
     * @param identityServiceIdUrl
     * @param authToken
     * @return
     * @throws IOException
     * @throws URISyntaxException
     */
    public static final IdServiceResponse callIdentityService(
            HttpAccess httpAccessor, String identityServiceIdUrl,
            String authToken) throws IOException, URISyntaxException {
        Map<String, String> idHeaders = new HashMap<String, String>();
        idHeaders.put("Authorization", "Bearer " + authToken);
        Execution exec = httpAccessor.doGet(idHeaders, new URI(identityServiceIdUrl));
        return new IdServiceResponse(exec.response);
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
            final String refreshPath = loginServer.toString() + OAUTH_TOKEN_PATH;
            Execution ex = httpAccessor.doPost(null, new URI(refreshPath), req);
            int statusCode = ex.response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                return new TokenEndpointResponse(ex.response);
            } else {
                throw new OAuthFailedException(new TokenErrorResponse(
                        ex.response), statusCode);
            }
        } catch (UnsupportedEncodingException ex1) {
            throw new RuntimeException(ex1); // should never happen
        } catch (URISyntaxException ex1) {
            throw new RuntimeException(ex1); // should never happen
        }
    }

    /**
     * @param grantType
     * @param clientId
     * @return
     */
    private static List<NameValuePair> makeTokenEndpointParams(
            String grantType, String clientId, String clientSecret) {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair(GRANT_TYPE, grantType));
        params.add(new BasicNameValuePair(CLIENT_ID, clientId));
        if (clientSecret != null) {
            params.add(new BasicNameValuePair("client_secret", clientSecret));
        }
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
            return httpStatusCode == HttpStatus.SC_UNAUTHORIZED
                    || httpStatusCode == HttpStatus.SC_FORBIDDEN
                    || httpStatusCode == HttpStatus.SC_BAD_REQUEST;
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
        public int pinLength = -1;
        public int screenLockTimeout = -1;
        public JSONObject customAttributes;
        public JSONObject customPermissions;


        public IdServiceResponse(HttpResponse httpResponse) {
            try {
                JSONObject parsedResponse = parseResponse(httpResponse);
                username = parsedResponse.getString(USERNAME);
                customAttributes = parsedResponse.optJSONObject(CUSTOM_ATTRIBUTES);
                customPermissions = parsedResponse.optJSONObject(CUSTOM_PERMISSIONS);

                // With connected apps (pilot in Summer '12), the server can specify a policy.
                if (parsedResponse.has(MOBILE_POLICY)) {
                    pinLength = parsedResponse.getJSONObject(MOBILE_POLICY).getInt(PIN_LENGTH);
                    screenLockTimeout = parsedResponse.getJSONObject(MOBILE_POLICY).getInt(SCREEN_LOCK);
                }
            } catch (Exception e) {
                Log.w("IdServiceResponse:constructor", "", e);
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
                error = parsedResponse.getString(ERROR);
                errorDescription = parsedResponse
                        .getString(ERROR_DESCRIPTION);
            } catch (Exception e) {
                Log.w("TokenErrorResponse:constructor", "", e);
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
        public String code;
        public String communityId;
        public String communityUrl;

        /**
         * Constructor used during login flow
         * @param callbackUrlParams
         */
        public TokenEndpointResponse(Map<String, String> callbackUrlParams) {
            try {
                authToken = callbackUrlParams.get(ACCESS_TOKEN);
                refreshToken = callbackUrlParams.get(REFRESH_TOKEN);
                instanceUrl = callbackUrlParams.get(INSTANCE_URL);
                idUrl = callbackUrlParams.get(ID);
                code = callbackUrlParams.get(CODE);
                computeOtherFields();
                communityId = callbackUrlParams.get(SFDC_COMMUNITY_ID);
                communityUrl = callbackUrlParams.get(SFDC_COMMUNITY_URL);
            } catch (Exception e) {
                Log.w("TokenEndpointResponse:constructor", "", e);
            }
        }

        /**
         * Constructor used during refresh flow
         * @param httpResponse
         */
        public TokenEndpointResponse(HttpResponse httpResponse) {
            try {
                JSONObject parsedResponse = parseResponse(httpResponse);
                authToken = parsedResponse.getString(ACCESS_TOKEN);
                instanceUrl = parsedResponse.getString(INSTANCE_URL);
                idUrl  = parsedResponse.getString(ID);
                computeOtherFields();
                if (parsedResponse.has(REFRESH_TOKEN)) {
                    refreshToken = parsedResponse.getString(REFRESH_TOKEN);
                }
                if (parsedResponse.has(SFDC_COMMUNITY_ID)) {
                	communityId = parsedResponse.getString(SFDC_COMMUNITY_ID);
                }
                if (parsedResponse.has(SFDC_COMMUNITY_URL)) {
                	communityUrl = parsedResponse.getString(SFDC_COMMUNITY_URL);
                }
            } catch (Exception e) {
                Log.w("TokenEndpointResponse:constructor", "", e);
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
