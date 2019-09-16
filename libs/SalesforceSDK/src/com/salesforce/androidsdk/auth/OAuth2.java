/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Helper methods for common OAuth2 requests.
 *
 * The typical OAuth2 flow is:
 *
 * <ol>
 * <li> The authorization flow is started by presenting the web-based
 * authorization screen to the user. This will prompt him/her to login and to
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
 */
public class OAuth2 {

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
    private static final String BIOMETRIC_UNLOCK = "biometric_unlock";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String RESPONSE_TYPE = "response_type";
    private static final String SCOPE = "scope";
    private static final String REDIRECT_URI = "redirect_uri";
    private static final String DEVICE_ID = "device_id";
    private static final String SCREEN_LOCK = "screen_lock";
    private static final String TOKEN = "token";
    private static final String USERNAME = "username";
    private static final String EMAIL = "email";
    private static final String FIRST_NAME = "first_name";
    private static final String LAST_NAME = "last_name";
    private static final String DISPLAY_NAME = "display_name";
    private static final String PHOTOS = "photos";
    private static final String PICTURE = "picture";
    private static final String THUMBNAIL = "thumbnail";
    private static final String AUTHORIZATION_CODE = "authorization_code";
    private static final String CODE = "code";
    private static final String CODE_CHALLENGE = "code_challenge";
    private static final String CODE_VERIFIER = "code_verifier";
    private static final String CUSTOM_ATTRIBUTES = "custom_attributes";
    private static final String CUSTOM_PERMISSIONS = "custom_permissions";
    private static final String SFDC_COMMUNITY_ID = "sfdc_community_id";
    private static final String SFDC_COMMUNITY_URL = "sfdc_community_url";
    private static final String ID_TOKEN = "id_token";
    private static final String AND = "&";
    private static final String EQUAL = "=";
    private static final String QUESTION = "?";
    private static final String TOUCH = "touch";
    private static final String FRONTDOOR = "/secur/frontdoor.jsp?";
    private static final String SID = "sid";
    private static final String RETURL = "retURL";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String ASSERTION = "assertion";
    private static final String JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final String OAUTH_AUTH_PATH = "/services/oauth2/authorize";
    private static final String OAUTH_DISPLAY_PARAM = "?display=";
    private static final String OAUTH_TOKEN_PATH = "/services/oauth2/token";
    private static final String OAUTH_REVOKE_PATH = "/services/oauth2/revoke?token=";
    private static final String EMPTY_STRING = "";
    private static final String FORWARD_SLASH = "/";
    private static final String SINGLE_SPACE = " ";
    private static final String TAG = "OAuth2";

    /**
     * Builds the URL to the authorization web page for this login server.
     * You need not provide the 'refresh_token' scope, as it is provided automatically.
     *
     * @param loginServer Base protocol and server to use (e.g. https://login.salesforce.com).
     * @param clientId OAuth client ID.
     * @param callbackUrl OAuth callback URL or redirect URL.
     * @param scopes A list of OAuth scopes to request (e.g. {"visualforce", "api"}). If null,
     *               the default OAuth scope is provided.
     * @param displayType OAuth display type. If null, the default of 'touch' is used.
     * @param addlParams Any additional parameters that may be added to the request.
     * @return A URL to start the OAuth flow in a web browser/view.
     *
     * @see <a href="https://help.salesforce.com/apex/HTViewHelpDoc?language=en&id=remoteaccess_oauth_scopes.htm">RemoteAccess OAuth Scopes</a>
     */
    public static URI getAuthorizationUrl(URI loginServer, String clientId, String callbackUrl,
                                          String[] scopes, String displayType,
                                          Map<String,String> addlParams) {
        final StringBuilder sb = new StringBuilder(loginServer.toString());
        sb.append(OAUTH_AUTH_PATH).append(getBrandedLoginPath());
        sb.append(OAUTH_DISPLAY_PARAM).append(displayType == null ? TOUCH : displayType);
        sb.append(AND).append(RESPONSE_TYPE).append(EQUAL).append(TOKEN);
        sb.append(AND).append(CLIENT_ID).append(EQUAL).append(Uri.encode(clientId));
        if (scopes != null && scopes.length > 0) {
            sb.append(AND).append(SCOPE).append(EQUAL).append(Uri.encode(computeScopeParameter(scopes)));
        }
        sb.append(AND).append(REDIRECT_URI).append(EQUAL).append(callbackUrl);
        sb.append(AND).append(DEVICE_ID).append(EQUAL).append(SalesforceSDKManager.getInstance().getDeviceId());
        if (addlParams != null && addlParams.size() > 0) {
            for (final Map.Entry<String,String> entry : addlParams.entrySet()) {
                final String value = entry.getValue() == null ? EMPTY_STRING : entry.getValue();
                sb.append(AND).append(entry.getKey()).append(EQUAL).append(Uri.encode(value));
            }
        }
        return URI.create(sb.toString());
    }

    private static String getBrandedLoginPath() {
        String brandedLoginPath = SalesforceSDKManager.getInstance().getLoginBrand();
        if (brandedLoginPath == null || brandedLoginPath.trim().isEmpty()) {
            brandedLoginPath = EMPTY_STRING;
        } else {
            if (!brandedLoginPath.startsWith(FORWARD_SLASH)) {
                brandedLoginPath = FORWARD_SLASH + brandedLoginPath;
            }
            if (brandedLoginPath.endsWith(FORWARD_SLASH)) {
                brandedLoginPath = brandedLoginPath.substring(0, brandedLoginPath.length() - 1);
            }
        }
        return brandedLoginPath;
    }

    /**
     * Builds the URL to the authorization web page for this login server.
     * You need not provide the 'refresh_token' scope, as it is provided automatically.
     *
     * @param loginServer Base protocol and server to use (e.g. https://login.salesforce.com).
     * @param clientId OAuth client ID.
     * @param callbackUrl OAuth callback URL or redirect URL.
     * @param scopes A list of OAuth scopes to request (e.g. {"visualforce", "api"}). If null,
     *               the default OAuth scope is provided.
     * @param displayType OAuth display type. If null, the default of 'touch' is used.
     * @param accessToken Access token.
     * @param instanceURL Instance URL.
     * @param addlParams Any additional parameters that may be added to the request.
     * @return A URL to start the OAuth flow in a web browser/view.
     *
     * @see <a href="https://help.salesforce.com/apex/HTViewHelpDoc?language=en&id=remoteaccess_oauth_scopes.htm">RemoteAccess OAuth Scopes</a>
     */
    public static URI getAuthorizationUrl(URI loginServer, String clientId, String callbackUrl,
                                          String[] scopes, String displayType, String accessToken,
                                          String instanceURL, Map<String, String> addlParams) {
        if (accessToken == null || instanceURL == null) {
            return getAuthorizationUrl(loginServer, clientId, callbackUrl, scopes,
                    displayType, addlParams);
        }
        final StringBuilder sb = new StringBuilder(instanceURL);
        sb.append(FRONTDOOR);
        sb.append(SID).append(EQUAL).append(accessToken);
        sb.append(AND).append(RETURL).append(EQUAL).append(Uri.encode(getAuthorizationUrl(loginServer,
                clientId, callbackUrl, scopes, displayType, null).toString()));
        if (addlParams != null && addlParams.size() > 0) {
            for (final Map.Entry<String,String> entry : addlParams.entrySet()) {
                final String value = entry.getValue() == null ? EMPTY_STRING : entry.getValue();
                sb.append(AND).append(entry.getKey()).append(EQUAL).append(Uri.encode(value));
            }
        }
        return URI.create(sb.toString());
    }

    /**
     * Returns an IDP 'frontdoor' URL configured with the SP's configuration.
     *
     * @param instanceUrl IDP's instance URL.
     * @param accessToken IDP's access token.
     * @param loginUrl SP's login URL.
     * @param displayType IDP's display type.
     * @param clientId SP's client ID.
     * @param callbackUrl SP's callback URL.
     * @param scopes SP's scopes.
     * @param codeChallenge SP's code challenge.
     * @return A 'frontdoor' URL that the IDP can load in a WebView.
     */
    public static URI getIDPFrontdoorUrl(String instanceUrl, String accessToken, String loginUrl,
                                            String displayType, String clientId, String callbackUrl,
                                            String[] scopes, String codeChallenge) {
        final StringBuilder sb = new StringBuilder(instanceUrl);
        sb.append(FRONTDOOR);
        sb.append(SID).append(EQUAL).append(accessToken);
        sb.append(AND).append(RETURL).append(EQUAL).append(Uri.encode(getIDPApprovalUrl(loginUrl,
                displayType, clientId, callbackUrl, scopes, codeChallenge).toString()));
        return URI.create(sb.toString());
    }

    /**
     * Returns an approval URL configured with the SP's configuration.
     *
     * @param loginUrl SP's login URL.
     * @param displayType IDP's display type.
     * @param clientId SP's client ID.
     * @param callbackUrl SP's callback URL.
     * @param scopes SP's scopes.
     * @param codeChallenge SP's code challenge.
     * @return An approval URL that the IDP can use to construct its 'frontdoor' URL.
     */
    public static URI getIDPApprovalUrl(String loginUrl, String displayType,
                                           String clientId, String callbackUrl,
                                           String[] scopes, String codeChallenge) {
        final StringBuilder sb = new StringBuilder(loginUrl);
        sb.append(OAUTH_AUTH_PATH).append(getBrandedLoginPath());
        sb.append(OAUTH_DISPLAY_PARAM).append(displayType == null ? TOUCH : displayType);
        sb.append(AND).append(RESPONSE_TYPE).append(EQUAL).append(CODE);
        sb.append(AND).append(CLIENT_ID).append(EQUAL).append(Uri.encode(clientId));
        if (scopes != null && scopes.length > 0) {
            sb.append(AND).append(SCOPE).append(EQUAL).append(Uri.encode(computeScopeParameter(scopes)));
        }
        sb.append(AND).append(REDIRECT_URI).append(EQUAL).append(callbackUrl);
        sb.append(AND).append(DEVICE_ID).append(EQUAL).append(SalesforceSDKManager.getInstance().getDeviceId());
        sb.append(AND).append(CODE_CHALLENGE).append(EQUAL).append(Uri.encode(codeChallenge));
        return URI.create(sb.toString());
    }

    /**
     * Computes the scope parameter from an array of scopes. Also adds
     * the 'refresh_token' scope if it hasn't already been added.
     *
     * @param scopes Array of scopes.
     * @return Scope parameter.
     */
    public static String computeScopeParameter(String[] scopes) {
        final List<String> scopesList = Arrays.asList(scopes == null ? new String[]{} : scopes);
        final Set<String> scopesSet = new TreeSet<>(scopesList);
        scopesSet.add(REFRESH_TOKEN);
        return TextUtils.join(SINGLE_SPACE, scopesSet.toArray(new String[]{}));
    }

    /**
     * Returns a full set of credentials for the SP app based on the code generated
     * from the IDP app.
     *
     * @param httpAccessor HTTPAccess instance.
     * @param loginServer Login server.
     * @param clientId Client ID.
     * @param code Code returned from the IDP.
     * @param codeVerifier Code verifier used by the SP to generate 'code_challenge'.
     * @param callbackUrl Callback URL.
     * @return Full set of credentials.
     *
     * @throws OAuthFailedException See {@link OAuthFailedException}.
     * @throws IOException See {@link IOException}.
     */
    public static TokenEndpointResponse getSPCredentials(HttpAccess httpAccessor, URI loginServer,
                                                         String clientId, String code, String codeVerifier,
                                                         String callbackUrl)
            throws OAuthFailedException, IOException {
        final FormBody.Builder builder = new FormBody.Builder();
        builder.add(GRANT_TYPE, AUTHORIZATION_CODE);
        builder.add(CLIENT_ID, clientId);
        builder.add(FORMAT, JSON);
        builder.add(CODE, code);
        builder.add(CODE_VERIFIER, codeVerifier);
        builder.add(REDIRECT_URI, callbackUrl);
        return makeTokenEndpointRequest(httpAccessor, loginServer, builder);
    }

    /**
     * Gets a new auth token using the refresh token.
     *
     * @param httpAccessor HttpAccess instance.
     * @param loginServer Login server.
     * @param clientId Client ID.
     * @param refreshToken Refresh token.
     * @param addlParams Additional parameters.
     * @return Token response.
     *
     * @throws OAuthFailedException See {@link OAuthFailedException}.
     * @throws IOException See {@link IOException}.
     */
    public static TokenEndpointResponse refreshAuthToken(HttpAccess httpAccessor, URI loginServer,
                                                         String clientId, String refreshToken,
                                                         Map<String,String> addlParams)
            throws OAuthFailedException, IOException {
        final FormBody.Builder builder = new FormBody.Builder();
        builder.add(GRANT_TYPE, REFRESH_TOKEN);
        builder.add(CLIENT_ID, clientId);
        builder.add(REFRESH_TOKEN, refreshToken);
        builder.add(FORMAT, JSON);
        if (addlParams != null ) {
            for (final Map.Entry<String,String> entry : addlParams.entrySet()) {
                builder.add(entry.getKey(),entry.getValue());
            }
        }
        return makeTokenEndpointRequest(httpAccessor, loginServer, builder);
    }

    /**
     * Revokes the existing refresh token.
     *
     * @param httpAccessor HttpAccess instance.
     * @param loginServer Login server.
     * @param refreshToken Refresh token.
     */
    public static void revokeRefreshToken(HttpAccess httpAccessor, URI loginServer, String refreshToken) {
        final StringBuilder sb = new StringBuilder(loginServer.toString());
        sb.append(OAUTH_REVOKE_PATH);
        sb.append(Uri.encode(refreshToken));
        final Request request = new Request.Builder().url(sb.toString()).get().build();
        try {
            httpAccessor.getOkHttpClient().newCall(request).execute();
        } catch (IOException e) {
            SalesforceSDKLogger.w(TAG, "Exception thrown while revoking refresh token", e);
        }
    }

    /**
     * Swaps a JWT for regular OAuth tokens. This is typically the first step after
     * receiving a JWT from a link. In addition, this will also call the identity
     * service to populate the username field.
     *
     * @param httpAccessor HttpAccess instance.
     * @param loginServerUrl The server (e.g. https://login.salesforce.com) that
     *                       the auth code was generated from.
     * @param jwt JWT issued by the OAuth authorization flow.
     *
     * @throws IOException See {@link IOException}.
     * @throws OAuthFailedException See {@link OAuthFailedException}.
     */
    public static TokenEndpointResponse swapJWTForTokens(HttpAccess httpAccessor, URI loginServerUrl,
                                                         String jwt) throws IOException, OAuthFailedException {
        final FormBody.Builder formBodyBuilder = new FormBody.Builder().add(GRANT_TYPE, JWT_BEARER)
                .add(ASSERTION, jwt);
        return makeTokenEndpointRequest(httpAccessor, loginServerUrl, formBodyBuilder);
    }

    /**
     * Calls the identity service to determine the username of the user and the mobile policy, given
     * their identity service ID and an access token.
     *
     * @param httpAccessor HttpAccessor instance.
     * @param identityServiceIdUrl Identity service URL.
     * @param authToken Access token.
     * @return IdServiceResponse instance.
     *
     * @throws IOException See {@link IOException}.
     */
    public static final IdServiceResponse callIdentityService(HttpAccess httpAccessor,
                                                              String identityServiceIdUrl,
                                                              String authToken)
            throws IOException {
        final Request.Builder builder = new Request.Builder().url(identityServiceIdUrl).get();
        addAuthorizationHeader(builder, authToken);
        final Request request = builder.build();
        final Response response = httpAccessor.getOkHttpClient().newCall(request).execute();
        return new IdServiceResponse(response);
    }

    /**
     * Adds the authorization header to request builder.
     *
     * @param builder Builder instance.
     * @param authToken Access token.
     */
    public static final Request.Builder addAuthorizationHeader(Request.Builder builder, String authToken) {
        return builder.header(AUTHORIZATION, BEARER + authToken);
    }

    private static TokenEndpointResponse makeTokenEndpointRequest(HttpAccess httpAccessor,
                                                                  URI loginServer,
                                                                  FormBody.Builder formBodyBuilder)
            throws OAuthFailedException, IOException {
        final StringBuilder sb = new StringBuilder(loginServer.toString());
        sb.append(OAUTH_TOKEN_PATH);
        sb.append(QUESTION).append(DEVICE_ID).append(EQUAL).append(SalesforceSDKManager.getInstance().getDeviceId());
        final String refreshPath = sb.toString();
        final RequestBody body = formBodyBuilder.build();
        final Request request = new Request.Builder().url(refreshPath).post(body).build();
        final Response response = httpAccessor.getOkHttpClient().newCall(request).execute();
        if (response.isSuccessful()) {
            return new TokenEndpointResponse(response);
        } else {
            throw new OAuthFailedException(new TokenErrorResponse(response), response.code());
        }
    }

    /**
     * Fetches an OpenID token from the Salesforce backend. This requires an OpenID token to be
     * configured on the Salesforce connected app in the backend. It also requires the "openid"
     * scope to be added on the client side through bootconfig and on the connected app.
     *
     * @param loginServer Login server.
     * @param clientId Client ID.
     * @param refreshToken Refresh token.
     * @return OpenID token.
     */
    public static String getOpenIDToken(String loginServer, String clientId, String refreshToken) {
        String idToken = null;
        try {
            final TokenEndpointResponse tr = refreshAuthToken(HttpAccess.DEFAULT,
                    new URI(loginServer), clientId, refreshToken, null);
            idToken = tr.idToken;
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while fetching OpenID token", e);
        }
        return idToken;
    }

    /**
     * Exception thrown when the refresh flow fails.
     */
    public static class OAuthFailedException extends Exception {

        OAuthFailedException(TokenErrorResponse err, int httpStatusCode) {
            super(err.toString());
            this.response = err;
            this.httpStatusCode = httpStatusCode;
        }

        final TokenErrorResponse response;
        final int httpStatusCode;

        /**
         * Returns if the refresh token is valid.
         *
         * @return True - if refresh token is valid, False - otherwise.
         */
        public boolean isRefreshTokenInvalid() {
            return httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED
                    || httpStatusCode == HttpURLConnection.HTTP_FORBIDDEN
                    || httpStatusCode == HttpURLConnection.HTTP_BAD_REQUEST;
        }

        /**
         * Returns token error response.
         *
         * @return Token error response.
         */
        public TokenErrorResponse getTokenErrorResponse() {
            return response;
        }

        /**
         * Returns HTTP status code.
         *
         * @return HTTP status code.
         */
        public int getHttpStatusCode() {
            return httpStatusCode;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Helper class to parse an identity service response.
     */
    public static class IdServiceResponse {

        public String username;
        public String email;
        public String firstName;
        public String lastName;
        public String displayName;
        public String pictureUrl;
        public String thumbnailUrl;
        public int pinLength = -1;
        public int screenLockTimeout = -1;
        public boolean biometricUnlockAlowed = true;
        public JSONObject customAttributes;
        public JSONObject customPermissions;

        /**
         * Parameterized constructor built from identity service response.
         *
         * @param response Identity service response.
         */
        public IdServiceResponse(Response response) {
            try {
                final JSONObject parsedResponse = (new RestResponse(response)).asJSONObject();
                username = parsedResponse.getString(USERNAME);
                email = parsedResponse.getString(EMAIL);
                firstName = parsedResponse.getString(FIRST_NAME);
                lastName = parsedResponse.getString(LAST_NAME);
                displayName = parsedResponse.getString(DISPLAY_NAME);
                final JSONObject photos = parsedResponse.getJSONObject(PHOTOS);
                if (photos != null) {
                    pictureUrl = photos.getString(PICTURE);
                    thumbnailUrl = photos.getString(THUMBNAIL);
                }
                customAttributes = parsedResponse.optJSONObject(CUSTOM_ATTRIBUTES);
                customPermissions = parsedResponse.optJSONObject(CUSTOM_PERMISSIONS);
                if (parsedResponse.has(MOBILE_POLICY)) {
                    pinLength = parsedResponse.getJSONObject(MOBILE_POLICY).getInt(PIN_LENGTH);
                    screenLockTimeout = parsedResponse.getJSONObject(MOBILE_POLICY).getInt(SCREEN_LOCK);
                    if (customAttributes != null) {
                        String bioAttribute = customAttributes.optString(BIOMETRIC_UNLOCK).toLowerCase(Locale.US);
                        if (bioAttribute.equals("false")) {
                            biometricUnlockAlowed = false;
                            SalesforceSDKLogger.i(TAG, "Biometric Unlock disabled by connected app.");
                        }
                    }
                }
            } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Could not parse identity response", e);
            }
        }
    }

    /**
     * Helper class to parse a token refresh error response.
     */
    public static class TokenErrorResponse {

        public String error;
        public String errorDescription;

        /**
         * Parameterized constructor built from an error response.
         *
         * @param response Error response.
         */
        public TokenErrorResponse(Response response) {
            try {
                final JSONObject parsedResponse = (new RestResponse(response)).asJSONObject();
                error = parsedResponse.getString(ERROR);
                errorDescription = parsedResponse.getString(ERROR_DESCRIPTION);
            } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Could not parse token error response", e);
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
    public static class TokenEndpointResponse {

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
        public Map<String, String> additionalOauthValues;
        public String idToken;

        /**
         * Parameterized constructor built during login flow.
         *
         * @param callbackUrlParams Callback URL parameters.
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
                final SalesforceSDKManager sdkManager = SalesforceSDKManager.getInstance();
                if (sdkManager != null) {
                    final List<String> additionalOauthKeys = sdkManager.getAdditionalOauthKeys();
                    if (additionalOauthKeys != null && !additionalOauthKeys.isEmpty()) {
                        additionalOauthValues = new HashMap<>();
                        for (final String key : additionalOauthKeys) {
                            if (!TextUtils.isEmpty(key)) {
                                additionalOauthValues.put(key, callbackUrlParams.get(key));
                            }
                        }
                    }
                }
                idToken = callbackUrlParams.get(ID_TOKEN);
            } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Could not parse token endpoint response", e);
            }
        }

        /**
         * Parameterized constructor built from refresh flow response.
         *
         * @param response Token endpoint response.
         */
        public TokenEndpointResponse(Response response) {
            try {
                final JSONObject parsedResponse = (new RestResponse(response)).asJSONObject();
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
                final SalesforceSDKManager sdkManager = SalesforceSDKManager.getInstance();
                if (sdkManager != null) {
                    final List<String> additionalOauthKeys = sdkManager.getAdditionalOauthKeys();
                    if (additionalOauthKeys != null && !additionalOauthKeys.isEmpty()) {
                        additionalOauthValues = new HashMap<>();
                        for (final String key : additionalOauthKeys) {
                            if (!TextUtils.isEmpty(key)) {
                                final String value = parsedResponse.optString(key, null);
                                if (value != null) {
                                    additionalOauthValues.put(key, value);
                                }
                            }
                        }
                    }
                }
                idToken = parsedResponse.optString(ID_TOKEN);
            } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Could not parse token endpoint response", e);
            }
        }

        private void computeOtherFields() throws URISyntaxException {
            idUrlWithInstance = idUrl.replace(new URI(idUrl).getHost(), new URI(instanceUrl).getHost());
            final String[] idUrlFragments = idUrl.split("/");
            userId = idUrlFragments[idUrlFragments.length - 1];
            orgId = idUrlFragments[idUrlFragments.length - 2];
        }
    }
}
