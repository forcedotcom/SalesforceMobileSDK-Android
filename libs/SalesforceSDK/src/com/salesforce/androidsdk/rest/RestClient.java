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
package com.salesforce.androidsdk.rest;

import android.util.Log;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;

import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * RestClient allows you to send authenticated HTTP requests to a force.com server.
 */
public class RestClient {

	// Keys in credentials map
	private static final String USER_AGENT = "userAgent";
	private static final String INSTANCE_URL = "instanceUrl";
	private static final String LOGIN_URL = "loginUrl";
	private static final String IDENTITY_URL = "identityUrl";
	private static final String CLIENT_ID = "clientId";
	private static final String ORG_ID = "orgId";
	private static final String USER_ID = "userId";
	private static final String REFRESH_TOKEN = "refreshToken";
	private static final String ACCESS_TOKEN = "accessToken";
	private static final String COMMUNITY_ID = "communityId";
	private static final String COMMUNITY_URL = "communityUrl";


    private static Map<String, OkHttpClient> OK_CLIENTS;
	private ClientInfo clientInfo;
    private HttpAccess httpAccessor;
    private OAuthRefreshInterceptor oAuthRefreshInterceptor;
	private OkHttpClient okHttpClient;

	/** 
	 * AuthTokenProvider interface.
	 * RestClient will call its authTokenProvider to refresh its authToken once it has expired. 
	 */
	public interface AuthTokenProvider {
		String getInstanceUrl();
		String getNewAuthToken();
		String getRefreshToken();
		long getLastRefreshTime();
	}
	
	/**
	 * AsyncRequestCallback interface.
	 * Interface through which the result of an asynchronous request is handled.
	 */
	public interface AsyncRequestCallback {
		/**
		 * NB: onSuccess runs on a network thread
		 *     If you are making your call from an activity and need to make UI changes
		 *     make sure to first consume the response and then call runOnUiThread
		 *
		 *     result.consumeQuietly(); // consume before going back to main thread
		 *     runOnUiThread(new Runnable() {
		 *         @Override
		 *         public void run() { ... }
		 *     });
		 * @param request
		 * @param response
		 */
		void onSuccess(RestRequest request, RestResponse response);

		/**
		 * NB: onError runs on a network thread
		 *     If you are making your call from an activity and need to make UI changes
		 *     make sure to call runOnUiThread
		 *
		 *     runOnUiThread(new Runnable() {
		 *         @Override
		 *         public void run() { ... }
		 *     });
		 * @param exception
		 */
		void onError(Exception exception);
	}
	
    /**
     * Constructs a RestClient with the given clientInfo, authToken, httpAccessor and authTokenProvider.
     * When it gets a 401 (not authorized) response from the server:
     * <ul>
     * <li> If authTokenProvider is not null, it will ask the authTokenProvider for a new access token and retry the request a second time.</li>
     * <li> Otherwise it will return the 401 response.</li>
     * </ul>
	 * @param clientInfo
     * @param authToken
     * @param httpAccessor
     * @param authTokenProvider
	 */
	public RestClient(ClientInfo clientInfo, String authToken, HttpAccess httpAccessor, AuthTokenProvider authTokenProvider) {
		this(clientInfo, httpAccessor, new OAuthRefreshInterceptor(clientInfo, authToken, authTokenProvider));
	}

	public RestClient(ClientInfo clientInfo, HttpAccess httpAccessor, OAuthRefreshInterceptor httpInterceptor) {
		this.clientInfo = clientInfo;
        this.httpAccessor = httpAccessor;
        this.oAuthRefreshInterceptor = httpInterceptor;
		setOkHttpClient();
	}

    /**
     * Clear cache of org-id/user-id to OkHttpClient
     */
    public static void clearOkClientsCache() {
        OK_CLIENTS = null;
    }

	/**
	 * Sets the OkHttpclient associated with this user account. The OkHttpclient
	 * are cached in a map and reused as and when a user account
	 * switch occurs, to prevent multiple threads being spawned unnecessarily.
	 */
	private synchronized void setOkHttpClient() {
		if (OK_CLIENTS == null) {
			OK_CLIENTS = new HashMap<>();
		}
		final String uniqueId = clientInfo.buildUniqueId();
		OkHttpClient okHttpClient = null;
		if (uniqueId != null) {
			okHttpClient = OK_CLIENTS.get(uniqueId);
			if (okHttpClient == null) {
				okHttpClient = httpAccessor.getOkHttpClientBuilder()
                        .addInterceptor(oAuthRefreshInterceptor)
                        .build();

                OK_CLIENTS.put(uniqueId, okHttpClient);
			}
		}
		this.okHttpClient = okHttpClient;
	}

	/**
	 * Used by tests
	 * @param okHttpClient
	 */
	public void setOkHttpClient(OkHttpClient okHttpClient) {
		this.okHttpClient = okHttpClient;
	}


	/**
	 * @return credentials as JSONObject
	 */
	public JSONObject getJSONCredentials() {
		RestClient.ClientInfo clientInfo = getClientInfo();
		Map<String, String> data = new HashMap<>();
		data.put(ACCESS_TOKEN, getAuthToken());
		data.put(REFRESH_TOKEN, getRefreshToken());
		data.put(USER_ID, clientInfo.userId);
		data.put(ORG_ID, clientInfo.orgId);
		data.put(CLIENT_ID, clientInfo.clientId);
		data.put(LOGIN_URL, clientInfo.loginUrl.toString());
		data.put(IDENTITY_URL, clientInfo.identityUrl.toString());
		data.put(INSTANCE_URL, clientInfo.instanceUrl.toString());
		data.put(USER_AGENT, SalesforceSDKManager.getInstance().getUserAgent());
		data.put(COMMUNITY_ID, clientInfo.communityId);
		data.put(COMMUNITY_URL, clientInfo.communityUrl);
		return new JSONObject(data);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("RestClient: {\n")
		  .append(clientInfo.toString())
		  // Un-comment if you must: tokens should not be printed to the log
		  // .append("   authToken: ").append(getAuthToken()).append("\n")
		  // .append("   refreshToken: ").append(getRefreshToken()).append("\n")
		  .append("   timeSinceLastRefresh: ").append(oAuthRefreshInterceptor.getElapsedTimeSinceLastRefresh()).append("\n")
		  .append("}\n");
		return sb.toString();
	}

	/**
	 * @return The authToken for this RestClient.
	 */
	public synchronized String getAuthToken() {
		return oAuthRefreshInterceptor.getAuthToken();
	}
	
	/**
	 * @return The refresh token, if available.
	 */
	public String getRefreshToken() {
		return oAuthRefreshInterceptor.getRefreshToken();
	}
	
	/**
	 * @return The client info.
	 */
	public ClientInfo getClientInfo() {
		return clientInfo;
	}

	/**
	 * @return underlying OkHttpClient
	 */
	public OkHttpClient getOkHttpClient() {
		return okHttpClient;
	}

    /**
     * Helper to build okHttp Request from RestRequest
     * @param restRequest
     * @return
     */
    public Request buildRequest(RestRequest restRequest) {
        Request.Builder builder =  new Request.Builder()
                .url(HttpUrl.get(clientInfo.resolveUrl(restRequest.getPath())))
                .method(restRequest.getMethod().toString(), restRequest.getRequestBody());

        // Adding addition headers
        final Map<String, String> additionalHttpHeaders = restRequest.getAdditionalHttpHeaders();
        if (additionalHttpHeaders != null) {
            for (Map.Entry<String, String> entry : additionalHttpHeaders.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        return builder.build();
    }

	/**
	 * Send the given restRequest and process the result asynchronously with the given callback.
	 * Note: Intended to be used by code on the UI thread.
	 * @param restRequest
	 * @param callback
	 * @return okHttp Call object (through which you can cancel the request or get the request back)
		 */
		public Call sendAsync(final RestRequest restRequest, final AsyncRequestCallback callback) {
			Request request = buildRequest(restRequest);
			Call call = okHttpClient.newCall(request);
			call.enqueue(new Callback() {
								 @Override
								 public void onFailure(Call call, IOException e) {
									 callback.onError(e);
								 }

								 @Override
								 public void onResponse(Call call, Response response) throws IOException {
									 callback.onSuccess(restRequest, new RestResponse(response));
								 }
							 }
					);
			return call;
	}

	/**
	 * Send the given restRequest synchronously and return a RestResponse
	 * Note: Cannot be used by code on the UI thread (use sendAsync instead).
	 * @param restRequest
	 * @return
	 * @throws IOException 
	 */
	public RestResponse sendSync(RestRequest restRequest) throws IOException {
        Request request = buildRequest(restRequest);
        Response response = okHttpClient.newCall(request).execute();
        return new RestResponse(response);
	}


    /**
     * Send the given restRequest synchronously and return a RestResponse
     * Note: Cannot be used by code on the UI thread (use sendAsync instead).
     * @param restRequest
     * @param interceptors Interceptor(s) to add to the network client before making the request
     * @return
     * @throws IOException
     */
    public RestResponse sendSync(RestRequest restRequest, Interceptor... interceptors) throws IOException {
        Request request = buildRequest(restRequest);
        // builder that shares the same connection pool, dispatcher, and configuration with the original client
        OkHttpClient.Builder clientBuilder = getOkHttpClient().newBuilder();
        for (Interceptor interceptor : interceptors) {
            clientBuilder.addNetworkInterceptor(interceptor);
        }
        Response response = clientBuilder.build().newCall(request).execute();
        return new RestResponse(response);
    }

	/**
	 * All immutable information for an authenticated client (e.g. username, org ID, etc.).
	 */
	public static class ClientInfo {

		public final String clientId;
		public final URI instanceUrl;
		public final URI loginUrl;
		public final URI identityUrl;
		public final String accountName;
		public final String username;
		public final String userId;
		public final String orgId;
		public final String communityId;
		public final String communityUrl;
		public final String firstName;
		public final String lastName;
		public final String displayName;
		public final String email;
		public final String photoUrl;
		public final String thumbnailUrl;

		/**
		 * Parameterized constructor.
		 *
		 * @param clientId Client ID.
		 * @param instanceUrl Instance URL.
		 * @param loginUrl Login URL.
		 * @param identityUrl Identity URL.
		 * @param accountName Account name.
		 * @param username User name.
		 * @param userId User ID.
		 * @param orgId Org ID.
		 * @param communityId Community ID.
		 * @param communityUrl Community URL.
         * @param firstName First Name.
         * @param lastName LastName.
		 * @param displayName DisplayName.
         * @param email Email.
         * @param photoUrl Photo URL.
         * @param thumbnailUrl Thumbnail URL.
		 */
		public ClientInfo(String clientId, URI instanceUrl, URI loginUrl,
				URI identityUrl, String accountName, String username,
				String userId, String orgId, String communityId, String communityUrl,
				String firstName, String lastName, String displayName, String email,
				String photoUrl, String thumbnailUrl ) {
			this.clientId = clientId;
			this.instanceUrl = instanceUrl;
			this.loginUrl = loginUrl;
			this.identityUrl = identityUrl;
			this.accountName = accountName;
			this.username = username;
			this.userId = userId;
			this.orgId = orgId;
			this.communityId = communityId;
			this.communityUrl = communityUrl;
			this.firstName = firstName;
			this.lastName = lastName;
			this.displayName = displayName;
			this.email = email;
			this.photoUrl = photoUrl;
			this.thumbnailUrl = thumbnailUrl;
		}

        /**
         * @return unique id built from user id and org id
         */
        public String buildUniqueId() {
            return this.userId + this.orgId;
        }

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("  ClientInfo: {\n")
			  .append("     loginUrl: ").append(loginUrl.toString()).append("\n")
			  .append("     identityUrl: ").append(identityUrl.toString()).append("\n")
			  .append("     instanceUrl: ").append(instanceUrl.toString()).append("\n")
			  .append("     accountName: ").append(accountName).append("\n")
			  .append("     username: ").append(username).append("\n")
			  .append("     userId: ").append(userId).append("\n")
			  .append("     orgId: ").append(orgId).append("\n")
			  .append("     communityId: ").append(communityId).append("\n")
			  .append("     communityUrl: ").append(communityUrl).append("\n")
              .append("     firstName: ").append(firstName).append("\n")
              .append("     lastName: ").append(lastName).append("\n")
			  .append("     displayName: ").append(displayName).append("\n")
              .append("     email: ").append(email).append("\n")
              .append("     photoUrl: ").append(photoUrl).append("\n")
              .append("     thumbnailUrl: ").append(thumbnailUrl).append("\n")
			  .append("  }\n");
			return sb.toString();
		}

		/**
		 * Returns a string representation of the instance URL. If this is a
		 * community user, the community URL will be returned. If not, the
		 * instance URL will be returned.
		 *
		 * @return Instance URL.
		 */
		public String getInstanceUrlAsString() {
			if (communityUrl != null && !"".equals(communityUrl.trim())) {
				return communityUrl;
			}
			return instanceUrl.toString();
		}

		/**
		 * Returns a URI representation of the instance URL. If this is a
		 * community user, the community URL will be returned. If not, the
		 * instance URL will be returned.
		 *
		 * @return Instance URL.
		 */
		public URI getInstanceUrl() {
			if (communityUrl != null && !"".equals(communityUrl.trim())) {
				URI uri = null;
				try {
					uri = new URI(communityUrl);
				} catch (URISyntaxException e) {
					Log.e("ClientInfo: getCommunityInstanceUrl",
							"URISyntaxException thrown on URL: " + communityUrl);
				}
				return uri;
			}
			return instanceUrl;
		}

		/**
		 * Resolves the given path against the community URL or the instance
		 * URL, depending on whether the user is a community user or not.
		 *
		 * @param path Path.
		 * @return Resolved URL.
		 */
		public URI resolveUrl(String path) {
			String resolvedPathStr = path;

			// Resolve URL only for a relative URL.
			if (!path.matches("[hH][tT][tT][pP][sS]?://.*")) {
				final StringBuilder commInstanceUrl = new StringBuilder();
				if (communityUrl != null && !"".equals(communityUrl.trim())) {
					commInstanceUrl.append(communityUrl);
				} else {
					commInstanceUrl.append(instanceUrl.toString());
				}
				if (!commInstanceUrl.toString().endsWith("/")) {
					commInstanceUrl.append("/");
				}
				if (path.startsWith("/")) {
					path = path.substring(1);
				}
				commInstanceUrl.append(path);
				resolvedPathStr = commInstanceUrl.toString();
			}
			URI uri = null;
			try {
				uri = new URI(resolvedPathStr);
			} catch (URISyntaxException e) {
				Log.e("ClientInfo: resolveUrl",
						"URISyntaxException thrown on URL: " + resolvedPathStr);
			}
			return uri;
		}

	}

    /**
     * Use a unauthenticated client info when do not need authentication support (e.g.
     * if you are talking to non-salesforce servers)
     *
     * NB: Your RestRequest's path will need to be a complete URL
     */
    public static class UnauthenticatedClientInfo extends ClientInfo {
        public static final String NOUSER = "nouser";

        public UnauthenticatedClientInfo() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public String buildUniqueId() {
            return NOUSER;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }

        @Override
        public URI resolveUrl(String path) {
            URI uri = null;
            try {
                uri = new URI(path);
            }
            catch (URISyntaxException e) {
                Log.e("UnauthenticatedC...Info",
                        "resolveUrl URISyntaxException thrown on URL: " + path);
            }

            return uri;
        }
    }

    /**
     * Network interceptor that does oauth refresh and request retry when access token has expired
     */
    public static class OAuthRefreshInterceptor implements Interceptor {

        private final AuthTokenProvider authTokenProvider;
        private String authToken;
        private ClientInfo clientInfo;

        /**
         * Constructs a SalesforceHttpInterceptor with the given clientInfo, authToken, httpAccessor and authTokenProvider.
         * When it gets a 401 (not authorized) response from the server:
         * <ul>
         * <li> If authTokenProvider is not null, it will ask the authTokenProvider for a new access token and retry the request a second time.</li>
         * <li> Otherwise it will return the 401 response.</li>
         * </ul>
         *
         * @param clientInfo
         * @param authToken
         * @param authTokenProvider
         */
        public OAuthRefreshInterceptor(ClientInfo clientInfo, String authToken, AuthTokenProvider authTokenProvider) {
            this.clientInfo = clientInfo;
            this.authToken = authToken;
            this.authTokenProvider = authTokenProvider;
        }


        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            request = buildAuthenticatedRequest(request);
            Response response = chain.proceed(request);


            if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED) { // if unauthorized
                refreshAccessToken();
                if (getAuthToken() != null) {
                    request = buildAuthenticatedRequest(request);

					HttpUrl currentInstanceUrl = HttpUrl.get(clientInfo.getInstanceUrl());
					if (currentInstanceUrl != null && currentInstanceUrl.host() != null) {

						// This happens during instance migration. hosts could change
						// In that case, the new host should replace the old host in the request object
						if (!currentInstanceUrl.host().equals(request.url().host())) {
							request = adjustHostInRequest(request, currentInstanceUrl.host());
						}
					}
                    response = chain.proceed(request);
                }
            }

            return response;
        }

		/**
		 * Build new request which has the new host. This is essential in case of instance migration
		 *
		 * @param request
		 * @param host the host segment of the url to be placed
		 * @return
		 */
		private Request adjustHostInRequest(Request request, final String host) {
			HttpUrl.Builder urlBuilder = request.url().newBuilder();

			// Only replace the host
			urlBuilder.host(host);

			Request.Builder builder = request.newBuilder();
			builder.url(urlBuilder.build());
			return builder.build();
		}

		/**
         * Build new request which has authentication header
         * @param request
         * @return
         */
        private Request buildAuthenticatedRequest(Request request) {
            Request.Builder builder = request.newBuilder();
            setAuthHeader(builder);
            return builder.build();
        }

        /**
         * @return The authToken for this RestClient.
         */
        public synchronized String getAuthToken() {
            return authToken;
        }

        /**
         * Set auth header
         *
         * @param builder
         */
        private void setAuthHeader(Request.Builder builder) {
            if (authToken != null) { //Add Auth token to each request if authorized
                OAuth2.addAuthorizationHeader(builder, authToken);
            }
        }

        /**
         * Change authToken for this RestClient
         *
         * @param newAuthToken
         */
        private synchronized void setAuthToken(String newAuthToken) {
            authToken = newAuthToken;
        }

        /**
         * @return The refresh token, if available.
         */
        public String getRefreshToken() {
            return (authTokenProvider != null ? authTokenProvider.getRefreshToken() : null);
        }

        /**
         * @return Elapsed time (ms) since the last refresh.
         */
        public long getElapsedTimeSinceLastRefresh() {
            long lastRefreshTime = (authTokenProvider != null ? authTokenProvider.getLastRefreshTime() : -1);
            if (lastRefreshTime < 0) {
                return -1;
            } else {
                return System.currentTimeMillis() - lastRefreshTime;
            }
        }

        /**
         * Swaps the existing access token for a new one.
         */
        private void refreshAccessToken() throws IOException {
            // If we haven't retried already and we have an accessTokenProvider
            // Then let's try to get a new authToken
            if (authTokenProvider != null) {
                final String newAuthToken = authTokenProvider.getNewAuthToken();
                if (newAuthToken != null) {
                    setAuthToken(newAuthToken);
                }

                // Check if the instanceUrl changed
                String instanceUrl = authTokenProvider.getInstanceUrl();
                if (instanceUrl == null) {
                    throw new IOException("Instance URL is null");
                }
                if (!clientInfo.instanceUrl.toString().equalsIgnoreCase(instanceUrl)) {
                    try {
                        // Create a new ClientInfo
                        clientInfo = new ClientInfo(clientInfo.clientId, new URI(instanceUrl),
                                clientInfo.loginUrl, clientInfo.identityUrl,
                                clientInfo.accountName, clientInfo.username,
                                clientInfo.userId, clientInfo.orgId, clientInfo.communityId,
                                clientInfo.communityUrl, clientInfo.firstName, clientInfo.lastName,
                                clientInfo.displayName, clientInfo.email, clientInfo.photoUrl, clientInfo.thumbnailUrl);
                    } catch (URISyntaxException ex) {
                        Log.w("RestClient", "Invalid server URL", ex);
                    }
                }
            }
        }
    }
}
