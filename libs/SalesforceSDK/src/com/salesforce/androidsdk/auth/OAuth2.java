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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
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
    protected static final String CLIENT_ID = "client_id"; // OAuth 2.0 token endpoint request body parameter names
    protected static final String GRANT_TYPE = "grant_type"; // OAuth 2.0 token endpoint request body parameter names
    private static final String ERROR = "error";
    private static final String ERROR_DESCRIPTION = "error_description";
    protected static final String FORMAT = "format";
    private static final String ID = "id";
    private static final String INSTANCE_URL = "instance_url";
    private static final String API_INSTANCE_URL = "api_instance_url";
    protected static final String JSON = "json";
    private static final String MOBILE_POLICY = "mobile_policy";
    private static final String SCREEN_LOCK_TIMEOUT = "screen_lock";
    private static final String BIOMETRIC_AUTHENTICATION = "ENABLE_BIOMETRIC_AUTHENTICATION";
    private static final String BIOMETRIC_AUTHENTICATION_TIMEOUT = "BIOMETRIC_AUTHENTICATION_TIMEOUT";
    private static final int BIOMETRIC_AUTHENTICATION_DEFAULT_TIMEOUT = 15;
    private static final String HYBRID_REFRESH = "hybrid_refresh";  // Grant Type Values
    public static final String LOGIN_HINT = "login_hint";
    private static final String REFRESH_TOKEN = "refresh_token";  // Grant Type Values
    protected static final String RESPONSE_TYPE = "response_type";
    private static final String SCOPE = "scope";
    protected static final String REDIRECT_URI = "redirect_uri";
    private static final String DEVICE_ID = "device_id";
    private static final String TOKEN = "token";
    private static final String HYBRID_TOKEN = "hybrid_token";
    private static final String USERNAME = "username";
    private static final String EMAIL = "email";
    private static final String FIRST_NAME = "first_name";
    private static final String LAST_NAME = "last_name";
    private static final String DISPLAY_NAME = "display_name";
    private static final String PHOTOS = "photos";
    private static final String PICTURE = "picture";
    private static final String THUMBNAIL = "thumbnail";
    protected static final String AUTHORIZATION_CODE = "authorization_code";
    protected static final String HYBRID_AUTH_CODE = "hybrid_auth_code";
    protected static final String CODE = "code";
    protected static final String CODE_CHALLENGE = "code_challenge";
    protected static final String CODE_VERIFIER = "code_verifier";
    private static final String CUSTOM_ATTRIBUTES = "custom_attributes";
    private static final String CUSTOM_PERMISSIONS = "custom_permissions";
    private static final String SFDC_COMMUNITY_ID = "sfdc_community_id";
    protected static final String SFDC_COMMUNITY_URL = "sfdc_community_url";
    private static final String ID_TOKEN = "id_token";
    private static final String AND = "&";
    private static final String EQUAL = "=";
    private static final String QUESTION = "?";
    private static final String TOUCH = "touch";
    private static final String FRONTDOOR = "/secur/frontdoor.jsp?";
    private static final String SID = "sid";
    private static final String RETURL = "retURL";
    protected static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String ASSERTION = "assertion";
    private static final String JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    protected static final String OAUTH_AUTH_PATH = "/services/oauth2/authorize";

    /** Endpoint path for Salesforce Identity API initialize headless, password-less login flow */
    protected static String OAUTH_ENDPOINT_HEADLESS_INIT_PASSWORDLESS_LOGIN = "/services/auth/headless/init/passwordless/login";

    /** Endpoint path for Salesforce Identity API initialize headless registration flow */
    protected static String OAUTH_ENDPOINT_HEADLESS_INIT_REGISTRATION = "/services/auth/headless/init/registration";

    /** Endpoint path for Salesforce Identity API headless forgot password flow */
    protected static String OAUTH_ENDPOINT_HEADLESS_FORGOT_PASSWORD = "/services/auth/headless/forgot_password";

    private static final String OAUTH_DISPLAY_PARAM = "?display=";
    protected static final String OAUTH_TOKEN_PATH = "/services/oauth2/token";
    private static final String OAUTH_REVOKE_PATH = "/services/oauth2/revoke?token=%s&revoke_reason=%s";
    private static final String LIGHTNING_DOMAIN = "lightning_domain";
    private static final String LIGHTNING_SID = "lightning_sid";
    private static final String VF_DOMAIN = "visualforce_domain";
    private static final String VF_SID = "visualforce_sid";
    private static final String CONTENT_DOMAIN = "content_domain";
    private static final String CONTENT_SID = "content_sid";
    private static final String CSRF_TOKEN = "csrf_token";
    private static final String EMPTY_STRING = "";
    private static final String FORWARD_SLASH = "/";
    private static final String SINGLE_SPACE = " ";
    private static final String TAG = "OAuth2";
    private static final String ID_URL = "id";
    private static final String ASSERTED_USER = "asserted_user";
    private static final String USER_ID = "user_id";
    private static final String ORG_ID = "organization_id";
    private static final String NICKNAME = "nick_name";
    private static final String URLS = "urls";
    private static final String ENTERPRISE_SOAP_URL = "enterprise";
    private static final String METADATA_SOAP_URL = "metadata";
    private static final String PARTNER_SOAP_URL = "partner";
    private static final String REST_URL = "rest";
    private static final String REST_SOBJECTS_URL = "sobjects";
    private static final String REST_SEARCH_URL = "search";
    private static final String REST_QUERY_URL = "query";
    private static final String REST_RECENT_URL = "recent";
    private static final String PROFILE_URL = "profile";
    private static final String CHATTER_FEEDS_URL = "feeds";
    private static final String CHATTER_GROUPS_URL = "groups";
    private static final String CHATTER_USERS_URL = "users";
    private static final String CHATTER_FEED_ITEMS_URL = "feed_items";
    private static final String IS_ACTIVE = "active";
    private static final String USER_TYPE = "user_type";
    private static final String LANGUAGE = "language";
    private static final String LOCALE = "locale";
    private static final String UTC_OFFSET = "utcOffset";
    private static final String LAST_MODIFIED_DATE = "last_modified_date";
    private static final String NATIVE_LOGIN = "nativeLogin";
    private static final String COOKIE_CLIENT_SRC = "cookie-clientSrc";
    private static final String COOKIE_SID_CLIENT = "cookie-sid_Client";
    private static final String SID_COOKIE_NAME = "sidCookieName";
    private static final String PARENT_SID = "parent_sid";
    private static final String TOKEN_FORMAT = "token_format";
    private static final String BEACON_CHILD_CONSUMER_SECRET = "beacon_child_consumer_secret";
    private static final String BEACON_CHILD_CONSUMER_KEY = "beacon_child_consumer_key";

    public static final DateFormat TIMESTAMP_FORMAT;
    static {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        TIMESTAMP_FORMAT.setTimeZone(tz);
    }

    public enum LogoutReason {
        // Corrupted client state
        CORRUPT_STATE,

        // Corrupted client state detected by application
        CORRUPT_STATE_APP_CONFIGURATION_SETTINGS,      // bad configuration settings
        CORRUPT_STATE_APP_PROVIDER_ERROR_INVALID_USER, // invalid user
        CORRUPT_STATE_APP_INVALID_RESTCLIENT,          // invalid rest client
        CORRUPT_STATE_APP_OTHER,                       // other

        // Corrupted client state detected by Mobile SDK
        CORRUPT_STATE_MSDK,

        REFRESH_TOKEN_EXPIRED,   // Refresh token expired
        SSDK_LOGOUT_POLICY,      // SSDK initiated logout for policy violation
        TIMEOUT,                 // Timeout while waiting for server response
        UNEXPECTED,              // Unexpected error or crash
        UNEXPECTED_RESPONSE,     // Unexpected response from server
        UNKNOWN,                 // Unknown
        USER_LOGOUT,             // User initiated logout
        REFRESH_TOKEN_ROTATED;   // Refresh token rotated

        @NonNull
        @Override
        public String toString() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Builds the URL to the authorization web page for this login server.
     * You need not provide the 'refresh_token' scope, as it is provided automatically.
     *
     * This overload defaults `loginHint` to null and does not enable Salesforce Welcome Login hint.
     *
     * @param useWebServerAuthentication True to use web server flow, False to use user agent flow
     * @param useHybridAuthentication    True to use "hybrid" flow
     * @param loginServer                Base protocol and server to use (e.g. https://login.salesforce.com).
     * @param clientId                   OAuth client ID.
     * @param callbackUrl                OAuth callback URL or redirect URL.
     * @param scopes                     A list of OAuth scopes to request (e.g. {"visualforce", "api"}). If null,
     *                                   the default OAuth scope is provided.
     * @param displayType                OAuth display type. If null, the default of 'touch' is used.
     * @param codeChallenge              Code challenge to use when using web server flow
     * @param addlParams                 Any additional parameters that may be added to the request.
     * @return A URL to start the OAuth flow in a web browser/view.
     * @see <a href="https://help.salesforce.com/apex/HTViewHelpDoc?language=en&id=remoteaccess_oauth_scopes.htm">RemoteAccess OAuth Scopes</a>
     */
    public static URI getAuthorizationUrl(
            boolean useWebServerAuthentication,
            boolean useHybridAuthentication,
            URI loginServer,
            String clientId,
            String callbackUrl,
            String[] scopes,
            String displayType,
            String codeChallenge,
            Map<String, String> addlParams) {
        return getAuthorizationUrl(
                useWebServerAuthentication,
                useHybridAuthentication,
                loginServer,
                clientId,
                callbackUrl,
                scopes,
                null,
                displayType,
                codeChallenge,
                addlParams
        );
    }

    /**
     * Builds the URL to the authorization web page for this login server.
     * You need not provide the 'refresh_token' scope, as it is provided automatically.
     *
     * @param useWebServerAuthentication True to use web server flow, False to use user agent flow
     * @param useHybridAuthentication    True to use "hybrid" flow
     * @param loginServer                Base protocol and server to use (e.g. https://login.salesforce.com).
     * @param clientId                   OAuth client ID.
     * @param callbackUrl                OAuth callback URL or redirect URL.
     * @param scopes                     A list of OAuth scopes to request (e.g. {"visualforce", "api"}). If null,
     *                                   the default OAuth scope is provided.
     * @param loginHint                  When applicable, the Salesforce Welcome Login hint
     * @param displayType                OAuth display type. If null, the default of 'touch' is used.
     * @param codeChallenge              Code challenge to use when using web server flow
     * @param addlParams                 Any additional parameters that may be added to the request.
     * @return A URL to start the OAuth flow in a web browser/view.
     * @see <a href="https://help.salesforce.com/apex/HTViewHelpDoc?language=en&id=remoteaccess_oauth_scopes.htm">RemoteAccess OAuth Scopes</a>
     */
    public static URI getAuthorizationUrl(
            boolean useWebServerAuthentication,
            boolean useHybridAuthentication,
            URI loginServer,
            String clientId,
            String callbackUrl,
            String[] scopes,
            String loginHint,
            String displayType,
            String codeChallenge,
            Map<String,String> addlParams) {
        final StringBuilder sb = new StringBuilder(loginServer.toString());
        final String responseType = useWebServerAuthentication
                ? CODE
                : useHybridAuthentication ? HYBRID_TOKEN : TOKEN;
        sb.append(OAUTH_AUTH_PATH).append(getBrandedLoginPath());
        sb.append(OAUTH_DISPLAY_PARAM).append(displayType == null ? TOUCH : displayType);
        sb.append(AND).append(RESPONSE_TYPE).append(EQUAL).append(responseType);
        sb.append(AND).append(CLIENT_ID).append(EQUAL).append(Uri.encode(clientId));
        if (scopes != null && scopes.length > 0) {
            sb.append(AND).append(SCOPE).append(EQUAL).append(Uri.encode(computeScopeParameter(scopes)));
        }
        if (!TextUtils.isEmpty(loginHint)) {
            sb.append(AND).append(LOGIN_HINT).append(EQUAL).append(Uri.encode(loginHint));
        }
        sb.append(AND).append(REDIRECT_URI).append(EQUAL).append(callbackUrl);
        sb.append(AND).append(DEVICE_ID).append(EQUAL).append(SalesforceSDKManager.getInstance().getDeviceId());
        if (useWebServerAuthentication) {
            sb.append(AND).append(CODE_CHALLENGE).append(EQUAL).append(Uri.encode(codeChallenge));
        }
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
     * Returns a 'frontdoor'ed URL
     * Front door will authenticate client navigating to that URL using given access token
     *
     * @param url the URL to "frontdoor"
     * @param accessToken access token to use as sid
     * @param instanceURL instance url for the sid
     * @param addlParams additional paramaters
     *
     * @return 'frontdoor'ed URL (or the original url if access token or instance url are null)
     * @deprecated Use {@link com.salesforce.androidsdk.rest.RestRequest#getRequestForSingleAccess(String)} instead
     */
    @Deprecated
    public static URI getFrontdoorUrl(URI url,
                                      String accessToken,
                                      String instanceURL,
                                      Map<String, String> addlParams) {
        if (accessToken == null || instanceURL == null) {
            return url;
        }
        final StringBuilder sb = new StringBuilder(instanceURL);
        sb.append(FRONTDOOR);
        sb.append(SID).append(EQUAL).append(accessToken);
        sb.append(AND).append(RETURL).append(EQUAL).append(Uri.encode(url.toString()));
        if (addlParams != null && addlParams.size() > 0) {
            for (final Map.Entry<String,String> entry : addlParams.entrySet()) {
                final String value = entry.getValue() == null ? EMPTY_STRING : entry.getValue();
                sb.append(AND).append(entry.getKey()).append(EQUAL).append(Uri.encode(value));
            }
        }
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
     * Exchange code for credentials.
     *
     * @param httpAccessor HTTPAccess instance.
     * @param loginServer Login server.
     * @param clientId Client ID.
     * @param code Code returned from the IDP.
     * @param codeVerifier Code verifier used to generate 'code_challenge'.
     * @param callbackUrl Callback URL.
     * @return Full set of credentials.
     *
     * @throws OAuthFailedException See {@link OAuthFailedException}.
     * @throws IOException See {@link IOException}.
     */
    public static TokenEndpointResponse exchangeCode(HttpAccess httpAccessor, URI loginServer,
                                                     String clientId, String code, String codeVerifier,
                                                     String callbackUrl)
            throws OAuthFailedException, IOException {
        final FormBody.Builder builder = new FormBody.Builder();
        final boolean useHybridAuthentication = SalesforceSDKManager.getInstance().shouldUseHybridAuthentication();
        final String grantType = useHybridAuthentication ? HYBRID_AUTH_CODE : AUTHORIZATION_CODE;
        builder.add(GRANT_TYPE, grantType);
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
        final boolean useHybridAuthentication = SalesforceSDKManager.getInstance().shouldUseHybridAuthentication();
        final String grantType = useHybridAuthentication ? HYBRID_REFRESH : REFRESH_TOKEN;
        builder.add(GRANT_TYPE, grantType);
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
     * @param reason The reason the refresh token is being revoked.
     */
    public static void revokeRefreshToken(HttpAccess httpAccessor, URI loginServer, String refreshToken, LogoutReason reason) {
        final String requestPath = String.format(OAUTH_REVOKE_PATH, refreshToken, reason.toString());
        final Request request = new Request.Builder().url(loginServer.toString() + requestPath).get().build();
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
        public boolean screenLock;
        public int screenLockTimeout = -1;
        public boolean biometricAuth;
        public int biometricAuthTimeout = -1;
        public JSONObject customAttributes;
        public JSONObject customPermissions;

        public String idUrl;
        public boolean assertedUser;
        public String userId;
        public String orgId;
        public String nickname;
        public String photos;
        public String urls;
        public String enterpriseSoapUrl;
        public String metadataSoapUrl;
        public String partnerSoapUrl;
        public String restUrl;
        public String restSObjectsUrl;
        public String restSearchUrl;
        public String restQueryUrl;
        public String restRecentUrl;
        public String profileUrl;
        public String chatterFeedsUrl;
        public String chatterGroupsUrl;
        public String chatterUsersUrl;
        public String chatterFeedItemsUrl;
        public boolean isActive;
        public String userType;
        public String language;
        public String locale;
        public int utcOffset;
        public boolean mobilePolicyConfigured;
        public Date lastModifiedDate;
        public boolean nativeLogin;

        /**
         * Parameterized constructor built from identity service response.
         *
         * @param response Identity service response.
         */
        public IdServiceResponse(Response response) {
            try {
                final JSONObject parsedResponse = (new RestResponse(response)).asJSONObject();
                populateFromJSON(parsedResponse);
            } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Could not parse identity response", e);
            }
        }

        /**
         * Parameterized constructor built from identity service response json.
         *
         * @param jsonObject Identity service response json.
         */
        public IdServiceResponse(JSONObject jsonObject) {
            populateFromJSON(jsonObject);
        }

        private void populateFromJSON(JSONObject parsedResponse) {
            try {
                username = parsedResponse.getString(USERNAME);
                email = parsedResponse.getString(EMAIL);
                firstName = parsedResponse.getString(FIRST_NAME);
                lastName = parsedResponse.getString(LAST_NAME);
                displayName = parsedResponse.getString(DISPLAY_NAME);
                final JSONObject photos = parsedResponse.optJSONObject(PHOTOS);
                if (photos != null) {
                    pictureUrl = photos.getString(PICTURE);
                    thumbnailUrl = photos.getString(THUMBNAIL);
                }

                idUrl = parsedResponse.getString(ID_URL);
                assertedUser = parsedResponse.optBoolean(ASSERTED_USER);
                userId = parsedResponse.getString(USER_ID);
                orgId = parsedResponse.getString(ORG_ID);
                nickname = parsedResponse.getString(NICKNAME);
                final JSONObject urls = parsedResponse.optJSONObject(URLS);
                if (urls != null) {
                    enterpriseSoapUrl = urls.getString(ENTERPRISE_SOAP_URL);
                    metadataSoapUrl = urls.getString(METADATA_SOAP_URL);
                    partnerSoapUrl = urls.getString(PARTNER_SOAP_URL);
                    restUrl = urls.getString(REST_URL);
                    restSObjectsUrl = urls.getString(REST_SOBJECTS_URL);
                    restSearchUrl = urls.getString(REST_SEARCH_URL);
                    restQueryUrl = urls.getString(REST_QUERY_URL);
                    restRecentUrl = urls.getString(REST_RECENT_URL);
                    profileUrl = urls.getString(PROFILE_URL);
                    chatterFeedsUrl = urls.getString(CHATTER_FEEDS_URL);
                    chatterGroupsUrl = urls.getString(CHATTER_GROUPS_URL);
                    chatterUsersUrl = urls.getString(CHATTER_USERS_URL);
                    chatterFeedItemsUrl = urls.getString(CHATTER_FEED_ITEMS_URL);
                }
                isActive = parsedResponse.optBoolean(IS_ACTIVE);
                userType = parsedResponse.getString(USER_TYPE);
                language = parsedResponse.getString(LANGUAGE);
                locale = parsedResponse.getString(LOCALE);
                utcOffset = parsedResponse.optInt(UTC_OFFSET, -1);
                mobilePolicyConfigured = parsedResponse.has(MOBILE_POLICY);
                lastModifiedDate = parseDateString(parsedResponse.getString(LAST_MODIFIED_DATE));
                nativeLogin = parsedResponse.optBoolean(NATIVE_LOGIN);

                customAttributes = parsedResponse.optJSONObject(CUSTOM_ATTRIBUTES);
                customPermissions = parsedResponse.optJSONObject(CUSTOM_PERMISSIONS);

                if (customAttributes != null && customAttributes.has(BIOMETRIC_AUTHENTICATION)) {
                    biometricAuth = true;
                    if (customAttributes.has(BIOMETRIC_AUTHENTICATION_TIMEOUT)) {
                        biometricAuthTimeout = customAttributes.getInt(BIOMETRIC_AUTHENTICATION_TIMEOUT);
                    }

                    if (biometricAuthTimeout < 1) {
                        // Set to the lowest session timeout value (15 minutes) if not specified.
                        biometricAuthTimeout = BIOMETRIC_AUTHENTICATION_DEFAULT_TIMEOUT;
                    }
                }


                if (mobilePolicyConfigured) {
                    JSONObject mobilePolicyObject = parsedResponse.getJSONObject(MOBILE_POLICY);
                    screenLock = mobilePolicyObject.has(SCREEN_LOCK_TIMEOUT);
                    screenLockTimeout = mobilePolicyObject.getInt(SCREEN_LOCK_TIMEOUT);

                    if (screenLock && biometricAuth) {
                        SalesforceSDKLogger.w(TAG, "Ignoring ScreenLock because BiometricAuthentication is enabled.");
                        screenLock = false;
                    }
                }
            } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Could not parse identity response", e);
            }
        }

        public static Date parseDateString(String dateString) {
            try {
                return TIMESTAMP_FORMAT.parse(dateString);
            } catch (ParseException e) {
                SalesforceSDKLogger.w(TAG, "Could not parse date string " + dateString, e);
                return null;
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
        public String apiInstanceUrl;
        public String idUrl;
        public String idUrlWithInstance;
        public String orgId;
        public String userId;
        public String code;
        public String communityId;
        public String communityUrl;
        public Map<String, String> additionalOauthValues;
        public String idToken;
        public String lightningDomain;
        public String lightningSid;
        public String vfDomain;
        public String vfSid;
        public String contentDomain;
        public String contentSid;
        public String csrfToken;
        public String cookieClientSrc;
        public String cookieSidClient;
        public String sidCookieName;
        public String parentSid;
        public String tokenFormat;
        public String beaconChildConsumerKey;
        public String beaconChildConsumerSecret;

        /**
         * Parameterized constructor built from params during user agent login flow.
         *
         * @param callbackUrlParams Callback URL parameters.
         * @param additionalOauthKeys Additional oauth keys.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        public TokenEndpointResponse(Map<String, String> callbackUrlParams, List<String> additionalOauthKeys) {
            try {
                authToken = callbackUrlParams.get(ACCESS_TOKEN);
                refreshToken = callbackUrlParams.get(REFRESH_TOKEN);
                instanceUrl = callbackUrlParams.get(INSTANCE_URL);
                apiInstanceUrl = callbackUrlParams.get(API_INSTANCE_URL);
                idUrl = callbackUrlParams.get(ID);
                code = callbackUrlParams.get(CODE);
                computeOtherFields();
                communityId = callbackUrlParams.get(SFDC_COMMUNITY_ID);
                communityUrl = callbackUrlParams.get(SFDC_COMMUNITY_URL);
                if (additionalOauthKeys != null && !additionalOauthKeys.isEmpty()) {
                    additionalOauthValues = new HashMap<>();
                    for (final String key : additionalOauthKeys) {
                        if (!TextUtils.isEmpty(key)) {
                            additionalOauthValues.put(key, callbackUrlParams.get(key));
                        }
                    }
                }
                idToken = callbackUrlParams.get(ID_TOKEN);
                lightningDomain = callbackUrlParams.get(LIGHTNING_DOMAIN);
                lightningSid = callbackUrlParams.get(LIGHTNING_SID);
                vfDomain = callbackUrlParams.get(VF_DOMAIN);
                vfSid = callbackUrlParams.get(VF_SID);
                contentDomain = callbackUrlParams.get(CONTENT_DOMAIN);
                contentSid = callbackUrlParams.get(CONTENT_SID);
                csrfToken = callbackUrlParams.get(CSRF_TOKEN);
                cookieClientSrc = callbackUrlParams.get(COOKIE_CLIENT_SRC);
                cookieSidClient = callbackUrlParams.get(COOKIE_SID_CLIENT);
                sidCookieName = callbackUrlParams.get(SID_COOKIE_NAME);
                parentSid = callbackUrlParams.get(PARENT_SID);
                tokenFormat = callbackUrlParams.get(TOKEN_FORMAT);

                // NB: beacon apps not supported with user agent flow so no beacon child fields expected

            } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Could not parse token endpoint response", e);
            }
        }

        /**
         * Parameterized constructor built from params during user agent login flow.
         *
         * @param callbackUrlParams Callback URL parameters.
         */
        public TokenEndpointResponse(Map<String, String> callbackUrlParams) {
            this(callbackUrlParams, SalesforceSDKManager.getInstance() != null
                    ? SalesforceSDKManager.getInstance() .getAdditionalOauthKeys()
                    : null);
        }

        /**
         * Parameterized constructor built from refresh flow response
         * or code exchange response (web server login flow).
         *
         * @param response Token endpoint response.
         * @param additionalOauthKeys Additional oauth keys.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        public TokenEndpointResponse(Response response, List<String> additionalOauthKeys) {
            try {
                final JSONObject parsedResponse = (new RestResponse(response)).asJSONObject();
                Log.d(TAG, "parsedResponse-->" + parsedResponse);
                authToken = parsedResponse.getString(ACCESS_TOKEN);
                instanceUrl = parsedResponse.getString(INSTANCE_URL);
                if (parsedResponse.has(API_INSTANCE_URL)) {
                    apiInstanceUrl = parsedResponse.getString(API_INSTANCE_URL);
                }
                idUrl = parsedResponse.getString(ID);
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
                if (additionalOauthKeys != null && !additionalOauthKeys.isEmpty()) {
                    additionalOauthValues = new HashMap<>();
                    for (final String key : additionalOauthKeys) {
                        if (!TextUtils.isEmpty(key)) {
                            final String value = parsedResponse.optString(key);
                            additionalOauthValues.put(key, value);
                        }
                    }
                }
                idToken = parsedResponse.optString(ID_TOKEN);
                lightningDomain = parsedResponse.optString(LIGHTNING_DOMAIN);
                lightningSid = parsedResponse.optString(LIGHTNING_SID);
                vfDomain = parsedResponse.optString(VF_DOMAIN);
                vfSid = parsedResponse.optString(VF_SID);
                contentDomain = parsedResponse.optString(CONTENT_DOMAIN);
                contentSid = parsedResponse.optString(CONTENT_SID);
                csrfToken = parsedResponse.optString(CSRF_TOKEN);
                cookieClientSrc = parsedResponse.optString(COOKIE_CLIENT_SRC);
                cookieSidClient = parsedResponse.optString(COOKIE_SID_CLIENT);
                sidCookieName = parsedResponse.optString(SID_COOKIE_NAME);
                parentSid = parsedResponse.optString(PARENT_SID);
                tokenFormat = parsedResponse.optString(TOKEN_FORMAT);

                // Beacon child fields expected when using a beacon app and web server flow
                if (parsedResponse.has(BEACON_CHILD_CONSUMER_KEY)) {
                    beaconChildConsumerKey = parsedResponse.getString(BEACON_CHILD_CONSUMER_KEY);
                }
                if (parsedResponse.has(BEACON_CHILD_CONSUMER_SECRET)) {
                    beaconChildConsumerSecret = parsedResponse.getString(BEACON_CHILD_CONSUMER_SECRET);
                }

            } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Could not parse token endpoint response", e);
            }
        }

        /**
         * Parameterized constructor built from refresh flow response
         * or code exchange response (web server login flow).
         *
         * @param response Token endpoint response.
         */
        public TokenEndpointResponse(Response response) {
            this(response, SalesforceSDKManager.getInstance() != null
                    ? SalesforceSDKManager.getInstance() .getAdditionalOauthKeys()
                    : null);
        }

        private void computeOtherFields() throws URISyntaxException {
            idUrlWithInstance = idUrl.replace(new URI(idUrl).getHost(), new URI(instanceUrl).getHost());
            final String[] idUrlFragments = idUrl.split("/");
            userId = idUrlFragments[idUrlFragments.length - 1];
            orgId = idUrlFragments[idUrlFragments.length - 2];
        }
    }
}
