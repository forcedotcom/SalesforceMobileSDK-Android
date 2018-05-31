/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.salesforce.androidsdk.app.SalesforceSDKManager;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionSpec;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.TlsVersion;

/**
 * Generic HTTP Access layer - used internally by {@link com.salesforce.androidsdk.rest.RestClient}
 * and {@link OAuth2}. This class watches network changes as well.
 */
public class HttpAccess {

    // Timeouts.
    public static final int CONNECT_TIMEOUT = 60;
    public static final int READ_TIMEOUT = 20;

    // User agent header name.
	private static final String USER_AGENT = "User-Agent";

    private String userAgent;
    private OkHttpClient okHttpClient;

    // Connection manager.
    private final ConnectivityManager conMgr;

    // Singleton instance.
    public static HttpAccess DEFAULT;

    /**
     * Initializes HttpAccess. Should be called from the application.
     */
    public static void init(Context app) {
        assert DEFAULT == null : "HttpAccess.init should be called once per process";
        DEFAULT = new HttpAccess(app, null /* user agent will be calculated at request time */);
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

            // Gets the connectivity manager and current network type.
            conMgr = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        }

    }

    /**
     *
     * @return okHttpClient.Builder with appropriate connection spec and user agent interceptor
     */
    public OkHttpClient.Builder getOkHttpClientBuilder() {
        ConnectionSpec connectionSpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_1, TlsVersion.TLS_1_2)
                .build();
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectionSpecs(Collections.singletonList(connectionSpec))
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .addNetworkInterceptor(new UserAgentInterceptor());
        return builder;
    }

    /**
     *
     * @return okHttpClient tied to this HttpAccess - builds one if needed
     */
    public synchronized OkHttpClient getOkHttpClient() {
        if (okHttpClient == null) {
            okHttpClient = getOkHttpClientBuilder().build();
        }
        return okHttpClient;
    }

    /**
     * Returns the status of network connectivity.
     *
     * @return True - if network connectivity is available, False - otherwise.
     */
    public synchronized boolean hasNetwork() {
        boolean isConnected = true;
        if (conMgr != null) {
            final NetworkInfo activeInfo = conMgr.getActiveNetworkInfo();
            if (activeInfo == null || !activeInfo.isConnected()) {
                isConnected = false;
            }
        }
        return isConnected;
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
     * Exception thrown if the device is offline, during an attempted HTTP call.
     */
    public static class NoNetworkException extends IOException {

        private static final long serialVersionUID = 1L;

        public NoNetworkException(String msg) {
            super(msg);
        }
    }

    /**
     * Interceptor that adds user agent header
     */
    public static class UserAgentInterceptor implements Interceptor {

        private String userAgent;

        public UserAgentInterceptor() {
            // User this constructor to have the user agent computed for each call
        }

        public UserAgentInterceptor(String userAgent) {
            this.userAgent = userAgent;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            Request requestWithUserAgent = originalRequest.newBuilder()
                    .header(HttpAccess.USER_AGENT, userAgent == null ? SalesforceSDKManager.getInstance().getUserAgent() : userAgent)
                    .build();
            return chain.proceed(requestWithUserAgent);
        }
    }
}
