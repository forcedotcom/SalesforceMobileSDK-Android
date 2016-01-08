/*
 * Copyright (c) 2011-2015, salesforce.com, inc.
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/**
 * Generic HTTP Access layer - used internally by {@link com.salesforce.androidsdk.rest.RestClient}
 * and {@link OAuth2}. This class watches network changes as well.
 */
public class HttpAccess extends BroadcastReceiver {

	public static final String USER_AGENT = "User-Agent";

	/*
	 * FIXME: Remove this when PATCH is available out of the box.
	 *
	 * https://code.google.com/p/android/issues/detail?id=76611
	 */
	private static final String PATCH = "PATCH";

    // Fields to keep track of network.
    private boolean hasNetwork = true;
    private String userAgent;

    // Connection manager.
    private final ConnectivityManager conMgr;

    // Singleton instance.
    public static HttpAccess DEFAULT;

    /**
     * A reasonable default chunk length when sending POST data.
     */
    private static final long DEFAULT_POST_CHUNK_LENGTH_IN_BYTES = 1048576L;

    /**
     * Initializes HttpAccess. Should be called from the application.
     */
    public static void init(Context app, String userAgent) {
        assert DEFAULT == null : "HttpAccess.init should be called once per process";
        DEFAULT = new HttpAccess(app, userAgent);
    }

    /**
     * Parameterized constructor.
     *
     * @param app Reference to the application.
     * @param userAgent The user agent to be used with requests.
     */
    public HttpAccess(Context app, String userAgent) {
        this.userAgent = userAgent;

        // Only null in tests.
        if (app == null) {
            conMgr = null;
        } else {

            // Registers a receiver to handle network changes.
            app.registerReceiver(this, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

            // Gets the connectivity manager and current network type.
            conMgr = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
    }

    /**
     * Detects network changes and sets the network connectivity status.
     *
     * @param context The context of the request.
     * @param intent Intent.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (conMgr == null) {
            return;
        }

        // Checks if an active network is available.
        final NetworkInfo activeInfo = conMgr.getActiveNetworkInfo();
        if (activeInfo != null && activeInfo.isConnected()) {
            setHasNetwork(true);
            return;
        }

        // Tries WIFI data connection.
        final NetworkInfo wifiInfo = conMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiInfo != null && wifiInfo.isConnected()) {
            setHasNetwork(true);
            return;
        }

        // Tries mobile connection.
        final NetworkInfo mobileInfo = conMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (mobileInfo == null) {
            setHasNetwork(false);
            return;
        }
    }

    /**
     * Returns the status of network connectivity.
     *
     * @return True - if network connectivity is available, False - otherwise.
     */
    public synchronized boolean hasNetwork() {
        return hasNetwork;
    }
    
    /**
     * Returns the current user agent.
     *
     * @return User agent.
     */
    public String getUserAgent() {
    	return userAgent;
    }

    /**
     * Sets the status of network connectivity.
     *
     * @param b True - if network connectivity is available, False - otherwise.
     */
    private synchronized void setHasNetwork(boolean b) {
        hasNetwork = b;
    }

    /**
     * Wrapper around an HTTP call and its response.
     */
    public static class Execution {

        public final HttpResponse response;

        public Execution(HttpResponse response) throws IllegalStateException, IOException {
            this.response = response;
        }
    }

    /**
     * Executes an HTTP POST.
     *
     * @param headers The headers associated with the post.
     * @param uri The URI to post to.
     * @param requestEntity The entity to post.
     * @return The execution response.
     * @throws IOException
     */
    public Execution doPost(Map<String, String> headers, URI uri, HttpEntity requestEntity) throws IOException {
    	final HttpsURLConnection httpConn = createHttpUrlConnection(uri, HttpPost.METHOD_NAME);
    	addHeaders(httpConn, headers);

        // Allow input and output on this connection
        httpConn.setDoOutput(true);
        httpConn.setDoInput(true);
        httpConn.setUseCaches(false);

        final long contentLength = requestEntity == null ? 0 : requestEntity.getContentLength();

        if (contentLength >= 0) {

            // Let the connection know the size of the data being sent so that it can chunk it appropriately.
            httpConn.setFixedLengthStreamingMode(contentLength);
        } else {

            // The size isn't known - i.e. data is probably being streamed. Choose a concrete chunk size to prevent OOM errors.
            httpConn.setFixedLengthStreamingMode(DEFAULT_POST_CHUNK_LENGTH_IN_BYTES);
        }
    	return execute(httpConn, requestEntity);
    }

    /**
     * Executes an HTTP PATCH.
     *
     * @param headers The headers associated with the post.
     * @param uri The URI to post to.
     * @param requestEntity The entity to post.
     * @return The execution response.
     * @throws IOException
     */
    public Execution doPatch(Map<String, String> headers, URI uri, HttpEntity requestEntity) throws IOException {
    	final HttpsURLConnection httpConn = createHttpUrlConnection(uri, PATCH);
    	addHeaders(httpConn, headers);
    	return execute(httpConn, requestEntity);
    }

    /**
     * Executes an HTTP PUT.
     *
     * @param headers The headers associated with the post.
     * @param uri The URI to post to.
     * @param requestEntity The entity to post.
     * @return The execution response.
     * @throws IOException
     */
    public Execution doPut(Map<String, String> headers, URI uri, HttpEntity requestEntity) throws IOException {
    	final HttpsURLConnection httpConn = createHttpUrlConnection(uri, HttpPut.METHOD_NAME);
    	addHeaders(httpConn, headers);
    	return execute(httpConn, requestEntity);
    }

    /**
     * Executes an HTTP GET.
     *
     * @param headers The headers associated with the get.
     * @param uri The URI to get.
     * @return The execution response.
     * @throws IOException
     */
    public Execution doGet(Map<String, String> headers, URI uri) throws IOException {
    	final HttpsURLConnection httpConn = createHttpUrlConnection(uri, HttpGet.METHOD_NAME);
    	addHeaders(httpConn, headers);
    	return execute(httpConn, null);
    }

    /**
     * Executes an HTTP HEAD.
     *
     * @param headers The headers associated with the get.
     * @param uri The URI to get.
     * @return The execution response.
     * @throws IOException
     */
    public Execution doHead(Map<String, String> headers, URI uri) throws IOException {
    	final HttpsURLConnection httpConn = createHttpUrlConnection(uri, HttpHead.METHOD_NAME);
    	addHeaders(httpConn, headers);
    	return execute(httpConn, null);
    }

    /**
     * Executes an HTTP DELETE.
     *
     * @param headers The headers associated with the delete.
     * @param uri The URI to delete from.
     * @return The execution response.
     * @throws IOException
     */
    public Execution doDelete(Map<String, String> headers, URI uri) throws IOException {
    	final HttpsURLConnection httpConn = createHttpUrlConnection(uri, HttpDelete.METHOD_NAME);
    	addHeaders(httpConn, headers);
    	return execute(httpConn, null);
    }

    /**
     * Executes a fully formed request, and returns the results.
     *
     * @param httpConn HTTPS connection object.
     * @param reqEntity Request entity.
     * @return The execution response.
     * @throws IOException
     */
    protected Execution execute(HttpsURLConnection httpConn, HttpEntity reqEntity) throws IOException {
    	if (httpConn == null) {
    		return null;
    	}
    	Execution exec = null;
    	if (reqEntity != null) {
    		final Header contentType = reqEntity.getContentType();
    		if (contentType != null) {
        		httpConn.setRequestProperty(contentType.getName(), contentType.getValue());
    		}
    		final Header contentEncoding = reqEntity.getContentEncoding();
    		if (contentEncoding != null) {
        		httpConn.setRequestProperty(contentEncoding.getName(), contentEncoding.getValue());
    		}
    		final long contentLen = reqEntity.getContentLength();
    		if (contentLen > 0) {
        		httpConn.setRequestProperty("Content-Length", Long.toString(contentLen));
    		}
            final OutputStream outputStream = httpConn.getOutputStream();

            if (outputStream != null) {
                reqEntity.writeTo(outputStream);
            }
        }
        int statusCode;
        try {
            statusCode = httpConn.getResponseCode();
        } catch (IOException ioe) {
            statusCode = httpConn.getResponseCode();
        }
        final String reasonPhrase = httpConn.getResponseMessage();
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
        	responseInputStream = httpConn.getInputStream();
        } catch (FileNotFoundException e) {
        	responseInputStream = httpConn.getErrorStream();
        }
        if (responseInputStream != null) {
            final BasicHttpEntity entity = new BasicHttpEntity();
            entity.setContent(responseInputStream);
            response.setEntity(entity);
        }
        exec = new Execution(response);
    	return exec;
    }

    /**
     * Adds headers to the HTTP request.
     *
     * @param httpConn HTTPS connection object.
     * @param headers Headers.
     */
    private void addHeaders(HttpsURLConnection httpConn, Map<String, String> headers) {
        if (headers == null || httpConn == null) {
        	return;
        }
        for (final Map.Entry<String, String> h : headers.entrySet()) {
        	httpConn.setRequestProperty(h.getKey(), h.getValue());
        }
    }

    /**
     * Creates a HTTP connection to the URI specified.
     *
     * @param uri URI.
     * @param requestMethod HTTP method.
     * @return HttpsUrlConnection instance.
     * @throws IOException
     */
    private HttpsURLConnection createHttpUrlConnection(URI uri, String requestMethod) throws IOException {
    	HttpsURLConnection httpConn = null;
    	if (uri != null) {
    		URL url = uri.toURL();
    		if (url != null) {

    			/*
    			 * FIXME: PATCH has been added to the latest OkHttp library,
    			 * which has been consumed in the AOSP branch. When this change
    			 * makes it to mainstream Android, replace the custom PATCH
    			 * config here with stock PATCH.
    			 *
    			 * https://code.google.com/p/android/issues/detail?id=76611
    			 */
    			if (PATCH.equals(requestMethod)) {
                    final String urlString;
                    if (uri.getQuery() == null) {
                        urlString = url.toString() + "?_HttpMethod=PATCH";
                    } else {
                        urlString = url.toString() + "&_HttpMethod=PATCH";
                    }
    				url = new URL(urlString);
    				requestMethod = HttpPost.METHOD_NAME;
    			}
    			httpConn = (HttpsURLConnection) url.openConnection();
    			httpConn.setRequestMethod(requestMethod);
    			httpConn.setRequestProperty(USER_AGENT, userAgent);
    			httpConn.setConnectTimeout(60000);
    			httpConn.setReadTimeout(20000);

                /*
                 * FIXME: Remove this piece of code once minApi >= Lollipop.
                 */
                if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
                    try {
                        httpConn.setSSLSocketFactory(SalesforceTLSSocketFactory.getInstance());
                    } catch (KeyManagementException e) {
                        Log.e("HttpAccess: createHttpUrlConnection", "Exception thrown while setting SSL socket factory", e);
                    } catch (NoSuchAlgorithmException e) {
                        Log.e("HttpAccess: createHttpUrlConnection", "Exception thrown while setting SSL socket factory", e);
                    }
                }
            }
    	}
    	return httpConn;
    }

    /**
     * Exception thrown if the device is offline, during an attempted HTTP call.
     */
    public static class NoNetworkException extends IOException {

        private static final long serialVersionUID = 1L;

        public NoNetworkException(String msg) {
            super(msg);
        }
    }
}
