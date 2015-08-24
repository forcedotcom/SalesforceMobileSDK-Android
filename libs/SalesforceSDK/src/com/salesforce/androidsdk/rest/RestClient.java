/*
 * Copyright (c) 2014-2015, salesforce.com, inc.
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

import android.text.TextUtils;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.HttpStack;
import com.android.volley.toolbox.NoCache;
import com.google.common.collect.Maps;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.HttpAccess.Execution;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.rest.files.ConnectUriBuilder;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * RestClient allows you to send authenticated HTTP requests to a force.com server.
 */
public class RestClient {

	// Constants used for multipart requests.
	private static final String MPE_BOUNDARY = "************************";
	private static final String MPE_SEPARATOR = "--";
	private static final String NEWLINE = "\r\n";

    private static Map<String, RequestQueue> REQUEST_QUEUES;

	private ClientInfo clientInfo;
	private RequestQueue requestQueue;
	private SalesforceHttpStack httpStack;

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
		void onSuccess(RestRequest request, RestResponse response);
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
		this(clientInfo, new SalesforceHttpStack(clientInfo, authToken, httpAccessor, authTokenProvider));
	}

	public RestClient(ClientInfo clientInfo, SalesforceHttpStack httpStack) {
		this.clientInfo = clientInfo;
		this.httpStack = httpStack;
		setRequestQueue();
	}

	/**
	 * Sets the request queue associated with this user account. The request
	 * queues are cached in a map and reused as and when a user account
	 * switch occurs, to prevent multiple threads being spawned unnecessarily.
	 */
	private synchronized void setRequestQueue() {
		if (REQUEST_QUEUES == null) {
			REQUEST_QUEUES = new HashMap<String, RequestQueue>();
		}
		final String uniqueId = clientInfo.buildUniqueId();
		RequestQueue queue = null;
		if (uniqueId != null) {
			queue = REQUEST_QUEUES.get(uniqueId);
			if (queue == null) {
				queue = new RequestQueue(new NoCache(), new BasicNetwork(httpStack));
				queue.start();
				REQUEST_QUEUES.put(uniqueId, queue);
			}
		}
		this.requestQueue = queue;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("RestClient: {\n")
		  .append(clientInfo.toString())
		  // Un-comment if you must: tokens should not be printed to the log
		  // .append("   authToken: ").append(getAuthToken()).append("\n")
		  // .append("   refreshToken: ").append(getRefreshToken()).append("\n")
		  .append("   timeSinceLastRefresh: ").append(httpStack.getElapsedTimeSinceLastRefresh()).append("\n")
		  .append("}\n");
		return sb.toString();
	}

	/**
	 * @return The authToken for this RestClient.
	 */
	public synchronized String getAuthToken() {
		return httpStack.getAuthToken();
	}
	
	/**
	 * @return The refresh token, if available.
	 */
	public String getRefreshToken() {
		return httpStack.getRefreshToken();
	}
	
	/**
	 * @return The client info.
	 */
	public ClientInfo getClientInfo() {
		return clientInfo;
	}

	/**
	 * @return underlying RequestQueue (using when calling sendAsync)
	 */
	public RequestQueue getRequestQueue() {
		return requestQueue;
	}
	
	/**
	 * Send the given restRequest and process the result asynchronously with the given callback.
	 * Note: Intended to be used by code on the UI thread.
	 * @param restRequest
	 * @param callback
	 * @return volley.Request object wrapped around the restRequest (to allow cancellation etc)
	 */
	public Request<?> sendAsync(RestRequest restRequest, AsyncRequestCallback callback) {
		WrappedRestRequest wrappedRestRequest = new WrappedRestRequest(clientInfo, restRequest, callback);
		return requestQueue.add(wrappedRestRequest);
	}

	/**
	 * Send the given restRequest synchronously and return a RestResponse
	 * Note: Cannot be used by code on the UI thread (use sendAsync instead).
	 * @param restRequest
	 * @return
	 * @throws IOException 
	 */
	public RestResponse sendSync(RestRequest restRequest) throws IOException {
		return sendSync(restRequest.getMethod(), restRequest.getPath(), restRequest.getRequestEntity(), restRequest.getAdditionalHttpHeaders());
	}

	/**
	 * Send an arbitrary HTTP request synchronously, using the given method, path and httpEntity.
	 * Note: Cannot be used by code on the UI thread (use sendAsync instead).
	 * 
	 * @param method				the HTTP method for the request (GET/POST/DELETE etc)
	 * @param path					the URI path, this will automatically be resolved against the users current instance host.
	 * @param httpEntity			the request body if there is one, can be null.
	 * @return 						a RestResponse instance that has information about the HTTP response returned by the server.
	 */
	public RestResponse sendSync(RestMethod method, String path, HttpEntity httpEntity) throws IOException {
		return sendSync(method, path, httpEntity, null);
	}

	/**
	 * Send an arbitrary HTTP request synchronously, using the given method, path, httpEntity and additionalHttpHeaders.
	 * Note: Cannot be used by code on the UI thread (use sendAsync instead).
	 * 
	 * @param method				the HTTP method for the request (GET/POST/DELETE etc)
	 * @param path					the URI path, this will automatically be resolved against the users current instance host.
	 * @param httpEntity			the request body if there is one, can be null.
	 * @param additionalHttpHeaders additional HTTP headers to add the generated HTTP request, can be null.
	 * @return 						a RestResponse instance that has information about the HTTP response returned by the server. 
	 * 
	 * @throws IOException
	 */
	public RestResponse sendSync(RestMethod method, String path, HttpEntity httpEntity, Map<String, String> additionalHttpHeaders) throws IOException {
		return new RestResponse(httpStack.performRequest(method.asVolleyMethod(), path, httpEntity, additionalHttpHeaders, true));
	}

	/**
	 * Uploads a new file to the server. This will create a new file at version 1.
	 *
	 * @param theFile The path of the local file to upload to the server.
	 * @param name The name/title of this file.
	 * @param title Title.
	 * @param description Description.
	 * @return The response received from the server.
	 */
	public RestResponse uploadFile(File theFile, String name, String title, String description) {
		return httpStack.uploadFile(theFile, name, this, title, description);
	}

	/**
	 * Only used in tests
	 * @param httpAccessor
	 */
	public void setHttpAccessor(HttpAccess httpAccessor) {
		this.httpStack.setHttpAccessor(httpAccessor);
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
		 */
		public ClientInfo(String clientId, URI instanceUrl, URI loginUrl,
				URI identityUrl, String accountName, String username,
				String userId, String orgId, String communityId, String communityUrl) {
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
            super(null, null, null, null, null, null, null, null, null, null);
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
                Log.e("UnauthenticatedClientInfo: resolveUrl",
                        "URISyntaxException thrown on URL: " + path);
            }

            return uri;
        }
    }

	/**
	 * HttpStack for talking to Salesforce (sets oauth header and does oauth refresh when needed)
	 */
	public static class SalesforceHttpStack implements HttpStack {

		private final AuthTokenProvider authTokenProvider;
		private HttpAccess httpAccessor;
		private String authToken;
		private ClientInfo clientInfo;
		
	    /**
	     * Constructs a SalesforceHttpStack with the given clientInfo, authToken, httpAccessor and authTokenProvider.
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
		public SalesforceHttpStack(ClientInfo clientInfo, String authToken, HttpAccess httpAccessor, AuthTokenProvider authTokenProvider) {
			this.clientInfo = clientInfo;
			this.authToken = authToken;
			this.httpAccessor = httpAccessor;
			this.authTokenProvider = authTokenProvider;
		}
		
		@Override
		public HttpResponse performRequest(Request<?> request, Map<String, String> additionalHeaders)
				throws IOException, AuthFailureError {
			int method = request.getMethod();
			URI url = URI.create(request.getUrl());
			HttpEntity requestEntity = null;
			if (request instanceof WrappedRestRequest) {
				RestRequest restRequest = ((WrappedRestRequest) request).getRestRequest();
				
				// To avoid httpEntity -> bytes -> httpEntity conversion
				requestEntity = restRequest.getRequestEntity();
				
				// Combine headers
				if (restRequest.getAdditionalHttpHeaders() != null) {
					if (additionalHeaders == null) {
						additionalHeaders = restRequest.getAdditionalHttpHeaders(); 
					} else {
						additionalHeaders = Maps.newHashMap(additionalHeaders);
						additionalHeaders.putAll(restRequest.getAdditionalHttpHeaders());
					}
				}
			} else {
				if (request.getBody() != null) {
					requestEntity = new ByteArrayEntity(request.getBody());
				}
			}

			return performRequest(method, url, requestEntity, additionalHeaders, true);
		}

		/**
		 * Uploads a new file to the server. This will create a new file at version 1.
		 *
		 * @param theFile The path of the local file to upload to the server.
		 * @param name The name/title of this file.
		 * @param client RestClient instance.
		 * @param title Title.
		 * @param description Description.
		 * @return The response received from the server.
		 */
		public RestResponse uploadFile(File theFile, String name, RestClient client,
				String title, String description) {
			return uploadFile(theFile, name, client, title, description, true);
		}

		/**
		 * @return The authToken for this RestClient.
		 */
		public synchronized String getAuthToken() {
			return authToken;
		}
		
		/**
		 * Change authToken for this RestClient
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
			}
			else {
				return System.currentTimeMillis() - lastRefreshTime;
			}
		}

		/**
		 * Only used in tests
		 * @param httpAccessor
		 */
		public void setHttpAccessor(HttpAccess httpAccessor) {
			this.httpAccessor = httpAccessor; 
		}

		/**
		 * @param method
		 * @param url
		 * @param httpEntity
		 * @param additionalHttpHeaders
		 * @return
		 * @throws IOException
		 */
		public HttpResponse doRequest(int method, URI url, HttpEntity httpEntity,
				Map<String, String> additionalHttpHeaders) throws IOException {
			Execution exec = null;

			// Prepare headers.
			Map<String, String> headers = new HashMap<String, String>();
			if (additionalHttpHeaders != null) {
				headers.putAll(additionalHttpHeaders);
			}
			if (getAuthToken() != null) {
				headers.put("Authorization", "Bearer " + authToken);
			}

			// Do the actual call.
			switch(method) {
			case Request.Method.DELETE:
				exec = httpAccessor.doDelete(headers, url); break;
			case Request.Method.GET:
				exec = httpAccessor.doGet(headers, url); break;
			case RestMethod.MethodHEAD:
				exec = httpAccessor.doHead(headers, url); break;
			case RestMethod.MethodPATCH:
				exec = httpAccessor.doPatch(headers, url, httpEntity); break;
			case Request.Method.POST:
				exec = httpAccessor.doPost(headers, url, httpEntity); break;
			case Request.Method.PUT:
				exec = httpAccessor.doPut(headers, url, httpEntity); break;
			}

			return exec.response;
		}

		/**
		 * Swaps the existing access token for a new one.
		 */
		private void refreshAccessToken(HttpResponse response) throws IOException {
            // If we haven't retried already and we have an accessTokenProvider
            // Then let's try to get a new authToken
            if (authTokenProvider != null) {
                // remember to consume this response so the connection can get re-used
                HttpEntity entity = response.getEntity();
                if (entity != null && entity.isStreaming()) {
                    InputStream instream = entity.getContent();
                    if (instream != null) {
                        instream.close();
                    }
                }
				final String newAuthToken = authTokenProvider.getNewAuthToken();
				if (newAuthToken != null) {
					setAuthToken(newAuthToken);
				}
                // Check if the instanceUrl changed
                String instanceUrl = authTokenProvider.getInstanceUrl();
                if (!clientInfo.instanceUrl.toString().equalsIgnoreCase(instanceUrl)) {
                    try {
                            // Create a new ClientInfo
                            clientInfo = new ClientInfo(clientInfo.clientId, new URI(instanceUrl),
                                clientInfo.loginUrl, clientInfo.identityUrl,
                                clientInfo.accountName, clientInfo.username,
                                clientInfo.userId, clientInfo.orgId, clientInfo.communityId,
                                clientInfo.communityUrl);
                    } catch (URISyntaxException ex) {
                        Log.w("RestClient", "Invalid server URL", ex);
                    }
                }
			}
		}

        /**
         * @param method
         * @param url
         * @param httpEntity
         * @param additionalHttpHeaders
         * @param retryInvalidToken
         * @return
         * @throws IOException
         */
        public HttpResponse performRequest(int method, URI url, HttpEntity httpEntity, Map<String, String> additionalHttpHeaders, boolean retryInvalidToken) throws IOException {
            HttpResponse response = doRequest(method, url, httpEntity, additionalHttpHeaders);
            // 401 bad access token
            if (retryInvalidToken && response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
                refreshAccessToken(response);
                return performRequest(method, url, httpEntity, additionalHttpHeaders, false);
            }
            // Done
            return response;
        }

        /**
         * @param method
         * @param path
         * @param httpEntity
         * @param additionalHttpHeaders
         * @param retryInvalidToken
         * @return
         * @throws IOException
         */
        public HttpResponse performRequest(int method, String path, HttpEntity httpEntity, Map<String, String> additionalHttpHeaders, boolean retryInvalidToken) throws IOException {
            HttpResponse response = doRequest(method, clientInfo.resolveUrl(path), httpEntity, additionalHttpHeaders);
            // 401 bad access token
            if (retryInvalidToken && response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
                refreshAccessToken(response);
                return performRequest(method, path, httpEntity, additionalHttpHeaders, false);
            }

            // Done
            return response;
        }

		private RestResponse uploadFile(File theFile, String name, RestClient client,
				String title, String description, boolean retryInvalidToken) {
			HttpURLConnection conn = null;
			RestResponse restResponse = null;
			byte[] buf = new byte[1024];
			try {
				final String bodyContentDisposition = "Content-Disposition: form-data; name=\""
						+ "fileData" + "\"; filename=\"" + name + "\"";

				// Reads the contents of the file.
				final FileInputStream fileInputStream = new FileInputStream(theFile);

				// Establishes a connection with the server.
				final String path = (new ConnectUriBuilder()).appendPath("users").appendPath("me/files").toString();
				final URI uri = client.getClientInfo().resolveUrl(path);
				conn = (HttpURLConnection) uri.toURL().openConnection();
				conn.setDoOutput(true);
				conn.setDoInput(true);
				conn.setUseCaches(false);
				conn.setRequestMethod(HttpPost.METHOD_NAME);
				conn.setRequestProperty(HttpAccess.USER_AGENT, SalesforceSDKManager.getInstance().getUserAgent());
				conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + MPE_BOUNDARY);
				conn.setRequestProperty("Connection", "Keep-Alive");

				// Adds the auth token to the header.
				if (getAuthToken() != null) {
					conn.setRequestProperty("Authorization", "Bearer " + authToken);
				}

				// Builds the request in parts.
				final DataOutputStream dataOutputStream = new DataOutputStream(conn.getOutputStream());
				dataOutputStream.writeBytes(MPE_SEPARATOR + MPE_BOUNDARY + NEWLINE);
				dataOutputStream.writeBytes("Content-Type: application/json; charset=UTF-8" + NEWLINE);
				dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"json\"" + NEWLINE);
				final JSONObject header = new JSONObject();
				if (!TextUtils.isEmpty(title)) {
					header.put("title", title);
				}
				if (!TextUtils.isEmpty(description)) {
					header.put("desc", description);
				}
				dataOutputStream.writeBytes(header.toString());
				dataOutputStream.writeBytes(MPE_SEPARATOR + MPE_BOUNDARY + NEWLINE);
				dataOutputStream.writeBytes(bodyContentDisposition + NEWLINE);
				dataOutputStream.writeBytes(NEWLINE);
				try {
					for (int readNum; (readNum = fileInputStream.read(buf)) != -1;) {
						dataOutputStream.write(buf, 0, readNum);
					}
				} catch (IOException ex) {
					throw ex;
				}
				dataOutputStream.writeBytes(NEWLINE);
				dataOutputStream.writeBytes(MPE_SEPARATOR + MPE_BOUNDARY + MPE_SEPARATOR + NEWLINE);
				fileInputStream.close();
				dataOutputStream.flush();
				dataOutputStream.close();

				// Processes the response received from the server.
				final int statusCode = conn.getResponseCode();
				final String reasonPhrase = conn.getResponseMessage();
				final ProtocolVersion protocolVersion = new HttpVersion(1, 1);
				final StatusLine statusLine = new BasicStatusLine(protocolVersion,
						statusCode, reasonPhrase);
				final HttpResponse response = new BasicHttpResponse(statusLine);
				InputStream responseInputStream = null;

    	    	/*
    	     	 * Tries to read the response stream here. If it fails with a
    	     	 * FileNotFoundException, tries to read the error stream instead.
    	     	 */
				try {
					responseInputStream = conn.getInputStream();
				} catch (FileNotFoundException e) {
					responseInputStream = conn.getErrorStream();
				}
				if (responseInputStream != null) {
					final BasicHttpEntity entity = new BasicHttpEntity();
					entity.setContent(responseInputStream);
					response.setEntity(entity);
				}

				// 401 bad access token.
				if (retryInvalidToken && statusCode == HttpStatus.SC_UNAUTHORIZED) {
					final HttpEntity entity = response.getEntity();
					if (entity != null && entity.isStreaming()) {
						if (responseInputStream != null) {
							responseInputStream.close();
						}
					}
					refreshAccessToken(response);
					return uploadFile(theFile, name, client, title, description, false);
				}
				restResponse = new RestResponse(response);
			} catch (Exception e) {
				Log.e("SalesforceHttpStack:uploadFile", "Exception thrown while uploading file", e);
			} finally {
				if (conn != null) {
					conn.disconnect();
				}
			}
			return restResponse;
		}
	}

	/**
	 * A RestRequest wrapped in a Request<?>
	 */
	public static class WrappedRestRequest extends Request<RestResponse> {

		private RestRequest restRequest;
		private AsyncRequestCallback callback;

		/**
		 * Constructor
		 * @param restRequest
		 * @param callback
		 */
		public WrappedRestRequest(ClientInfo clientInfo, RestRequest restRequest, final AsyncRequestCallback callback) {
			super(restRequest.getMethod().asVolleyMethod(),
				  clientInfo.resolveUrl(restRequest.getPath()).toString(),
				  new Response.ErrorListener() {
					@Override
					public void onErrorResponse(VolleyError error) {
						callback.onError(error);
					}
				});
			this.restRequest = restRequest;
			this.callback = callback;
		}

		public RestRequest getRestRequest() {
			return restRequest;
		}

		public HttpEntity getRequestEntity() {
			return restRequest.getRequestEntity();
		}
		
		@Override
		public byte[] getBody() throws AuthFailureError {
			try {
				HttpEntity requestEntity = restRequest.getRequestEntity();
				return requestEntity == null ? null : EntityUtils.toByteArray(requestEntity);
			}
			catch (IOException e) {
				Log.e("WrappedRestRequest.getBody", "Could not read request entity", e);
				return null;
			}
		}

		@Override
	    public String getBodyContentType() {
			HttpEntity requestEntity = restRequest.getRequestEntity();
			Header contentType = requestEntity == null ? null : requestEntity.getContentType();
			return (contentType == null ? "application/x-www-form-urlencoded" : contentType.getValue()) + "; charset=" + HTTP.UTF_8;
	    }		
		
		@Override
		protected void deliverResponse(RestResponse restResponse) {
			callback.onSuccess(restRequest, restResponse);
		}

		@Override
		protected Response<RestResponse> parseNetworkResponse(
				NetworkResponse networkResponse) {
            return Response.success(new RestResponse(networkResponse),
                    HttpHeaderParser.parseCacheHeaders(networkResponse));
		}
		
	}
}
