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
import java.io.InputStream;
import java.net.URI;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.HttpEntityWrapper;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.AndroidHttpClient;
import android.util.Log;

/**
 * Generic HTTP Access layer - used internally by {@link com.salesforce.androidsdk.rest.RestClient}
 * and {@link OAuth2}.
 *
 * Basically a wrapper around an
 * {@link <a href="http://developer.android.com/reference/android/net/http/AndroidHttpClient.html">AndroidHttpClient</a>}.
 * It watches network changes (e.g. wifi to 3g) and resets the AndroidHttpClient when needed.
 */
public class HttpAccess extends BroadcastReceiver {

    // Reference to app
    private Application app;

    // Http client
    private AndroidHttpClient http;

    // Fields to keep track of network
    private boolean    hasNetwork      = true;
    private int        currentNetworkSubType = -1;
    private String     networkFailReason;
    private String	   userAgent;

    // Connection manager
    private final ConnectivityManager conMgr;

    // Singleton instance
    public static HttpAccess DEFAULT;


    /**
     * Initialize HttpAccess.
     * Should be called from application
     */
    public static void init(Application app, String userAgent) {
        assert DEFAULT == null : "HttpAccess.init should be called once per process";
        DEFAULT = new HttpAccess(app, userAgent);
    }

    /**
     * Creates a new HttpAccess object.
     * @param app Reference to the Application.
     * @param userAgent The user agent to be used with requests.
     */
    public HttpAccess(Application app, String userAgent) {
        // Set user agent
        this.userAgent = userAgent;
        Log.d("HttpAccess:constructor", "User-Agent string: " + userAgent);

        // Using android http client
        http = getHttpClient();
        ((AndroidHttpClient) http).enableCurlLogging("HttpAccess", Log.DEBUG);

        // Only null in tests
        if (app == null) {
            conMgr = null;
        }
        else {

            // Keep reference to app
            this.app = app;

            // Registering receiver to handle network changes
            app.registerReceiver(this, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

            // Getting connectivity manager and current network type
            conMgr = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (conMgr != null) {
                final NetworkInfo netInfo = conMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                if (netInfo != null) {
                    currentNetworkSubType = netInfo.getSubtype();
                }
            }
        }
    }

    /**
     * Build the AndroidHttpClient.
     * @return A configured instance of AndroidHttpClient.
     */
    private AndroidHttpClient getHttpClient() {
        return AndroidHttpClient.newInstance(userAgent, app);
    }

    /**
     * Detects network changes and resets the network when needed.
     *
     * Note: The intent info is only for this particular change, meaning it will get sent when the phone detects changes in the wireless
     * state even though the user is actually using wifi.  In other words, don't use it; look for the current real state using the
     * manager service.
     * @param context The context of the request.
     * @param intent Not used.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (conMgr == null) {
            return;
        }

        // Check if an active network is available.
        final NetworkInfo activeInfo = conMgr.getActiveNetworkInfo();
        if (activeInfo != null && activeInfo.isConnected()) {
            setHasNetwork(true, null);
            return;
        }

        // Try WIFI data connection.
        final NetworkInfo wifiInfo = conMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiInfo != null && wifiInfo.isConnected()) {
            setHasNetwork(true, null);
            return;
        }

        // Try mobile connection.
        final NetworkInfo mobileInfo = conMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
	if (mobileInfo == null) {
            setHasNetwork(false, "No active data connection");
            return;
        }

        // Reset network if type changed.
        if (mobileInfo != null) {
            if (currentNetworkSubType != mobileInfo.getSubtype()) {
                currentNetworkSubType = mobileInfo.getSubtype();
                resetNetwork();
            }

            // On 2.2 this receiver fires when we register for it, so we get the current state, not a transition, so
            // typically in this case isFailover() is going to be true from the last failover, so we don't reset the
            // network in that case. We also have the default HttpAccess instance created early on
            // so that this initial receive can be processed before we try and make any calls.
            if (mobileInfo.isFailover()) {
                if (!mobileInfo.isConnected()) {
                    setHasNetwork(false, "No active data connection");
                    return;
                }
            }
            if (mobileInfo.isConnected()) {
                setHasNetwork(true, null);
                return;
            }
            setHasNetwork(false, mobileInfo.getReason() == null ? wifiInfo.getReason() : mobileInfo.getReason());
        }
    }

    /**
     * Shut down existing connections and create a new http client.
     */
    public synchronized void resetNetwork() {
        (new Thread(new Runnable() {
            @Override
            public void run() {
                http.close();
                http = getHttpClient();
            }
        })).start();
    }

    /**
     * @return true if the network is available.
     */
    public synchronized boolean hasNetwork() {
        return hasNetwork;
    }

    /**
     * @param b
     * @param reason
     */
    private synchronized void setHasNetwork(boolean b, String reason){
        hasNetwork = b;
        networkFailReason = reason;
    }

    /**
     * @throws IOException
     */
    private void checkNetwork() throws IOException {
        if (!hasNetwork()) {
            throw new NoNetworkException(networkFailReason == null ? "Network not available" : networkFailReason);
        }
    }


    /**************************************************************************************************
     *
     * HTTP calls methods
     *
     **************************************************************************************************/

    /**
     * Wrapper around an HTTP call and its response.
     */
    public static class Execution {

        public Execution(HttpRequestBase request, HttpResponse response) throws IllegalStateException, IOException {
            this.request = request;
            this.response = response;
        }

        public final HttpRequestBase request;
        public final HttpResponse response;
    }

    /**
     * Executes an HTTP POST.
     * @param headers The headers associated with the post.
     * @param uri The URI to post to.
     * @param requestEntity The entity to post.
     * @return The execution response.
     * @throws ClientProtocolException
     * @throws IOException
     */
    public Execution doPost(Map<String,String> headers, URI uri, HttpEntity requestEntity) throws ClientProtocolException, IOException {
        HttpPost post = new HttpPost(uri);
        post.setEntity(requestEntity);
        return execute(headers, post);
    }

    /**
     * Executes an HTTP PATCH
     * @param headers The headers associated with the post.
     * @param uri The URI to post to.
     * @param requestEntity The entity to post.
     * @return The execution response.
     * @throws ClientProtocolException
     * @throws IOException
     */
    public Execution doPatch(Map<String,String> headers, URI uri, HttpEntity requestEntity) throws ClientProtocolException, IOException {
        HttpPatch patch = new HttpPatch(uri);
        patch.setEntity(requestEntity);
        return execute(headers, patch);
    }

    /**
     * Executes an HTTP PUT
     * @param headers The headers associated with the post.
     * @param uri The URI to post to.
     * @param requestEntity The entity to post.
     * @return The execution response.
     * @throws ClientProtocolException
     * @throws IOException
     */
    public Execution doPut(Map<String,String> headers, URI uri, HttpEntity requestEntity) throws ClientProtocolException, IOException {
        HttpPut put = new HttpPut(uri);
        put.setEntity(requestEntity);
        return execute(headers, put);
    }

    /**
     * Executes an HTTP GET
     * @param headers The headers associated with the get.
     * @param uri The URI to get.
     * @return The execution response.
     * @throws IOException
     */
    public Execution doGet(Map<String,String> headers, URI uri) throws IOException {
        HttpGet get = new HttpGet(uri);
        return execute(headers, get);
    }

    /**
     * Executes an HTTP HEAD
     * @param headers The headers associated with the get.
     * @param uri The URI to get.
     * @return The execution response.
     * @throws IOException
     */
    public Execution doHead(Map<String,String> headers, URI uri) throws IOException {
        HttpHead head = new HttpHead(uri);
        return execute(headers, head);
    }

    /**
     * Executes an HTTP DELETE
     * @param headers The headers associated with the delete.
     * @param uri The URI to delete from.
     * @return The execution response.
     * @throws IOException
     */
    public Execution doDelete(Map<String, String> headers, URI uri) throws IOException {
        HttpDelete del = new HttpDelete(uri);
        return execute(headers, del);
    }

    /**
     * Updates request with the headers, and then executes it and returns the results.
     * @param headers The headers associated with the request.
     * @param req The request.
     * @return The execution response.
     * @throws ClientProtocolException
     * @throws IOException
     */
    protected Execution execute(Map<String,String> headers, HttpRequestBase req) throws ClientProtocolException, IOException {
        checkNetwork();
        try {
            addHeaders(req, headers);
            AndroidHttpClient.modifyRequestToAcceptGzipResponse(req);
            return execute(req);
        } catch (IOException t) {
            if (req.getMethod().equalsIgnoreCase("GET")) {
                return execute(req);
            }
            throw t;
        }
    }

    /**
     * Executes a fully formed request, and returns the results.
     * @param req The request.
     * @return The execution resonse.
     * @throws ClientProtocolException
     * @throws IOException
     */
    protected Execution execute(HttpRequestBase req) throws ClientProtocolException, IOException {
        HttpResponse res = http.execute(req);
        HttpEntity entity = res.getEntity();
        if (entity != null) {
            // Wrapping response entity to handle gzip decompression transparently
            // Note: AndroidHttpClient doesn't let you add response interceptors to the underlying DefaultHttpClient
            res.setEntity(new GzipDecompressingEntity(res.getEntity()));
        }
        return new Execution(req, res);
    }

    /**
     * @param req
     * @param headers
     */
    private void addHeaders(HttpRequestBase req, Map<String, String> headers) {
        if (headers == null) return;
        for (Map.Entry<String, String> h : headers.entrySet()) {
            req.addHeader(h.getKey(), h.getValue());
        }
    }

    /**
     * Subclass of HttpRequestBase, used to do an HTTP PATCH.
     */
    static class HttpPatch extends HttpEntityEnclosingRequestBase {

        public final static String METHOD_NAME = "PATCH";

        public HttpPatch() {
            super();
        }

        public HttpPatch(final URI uri) {
            super();
            setURI(uri);
        }

        /**
         * @throws IllegalArgumentException if the uri is invalid.
         */
        public HttpPatch(final String uri) {
            super();
            setURI(URI.create(uri));
        }

        @Override
        public String getMethod() {
            return METHOD_NAME;
        }

    }

    /**
     * Entity wrapper for compressed (gzip-ped) or non-compressed entities
     */
    public static class GzipDecompressingEntity extends HttpEntityWrapper {
        public GzipDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }

        @Override
        public InputStream getContent() throws IOException,
                IllegalStateException {
            return AndroidHttpClient.getUngzippedContent(wrappedEntity);
        }

        @Override
        public long getContentLength() {
            // length of ungzipped content is not known
            return -1;
        }
    }

    /**
     * Thrown when offline during an attempted http call
     */
    public static class NoNetworkException extends IOException {
        private static final long serialVersionUID = 1L;

        public NoNetworkException(String msg) {
            super(msg);
        }

    }
}
