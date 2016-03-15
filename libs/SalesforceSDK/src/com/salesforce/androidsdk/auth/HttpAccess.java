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

import java.io.IOException;

/**
 * Generic HTTP Access layer - used internally by {@link com.salesforce.androidsdk.rest.RestClient}
 * and {@link OAuth2}. This class watches network changes as well.
 */
public class HttpAccess extends BroadcastReceiver {

	public static final String USER_AGENT = "User-Agent";

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
     * Exception thrown if the device is offline, during an attempted HTTP call.
     */
    public static class NoNetworkException extends IOException {

        private static final long serialVersionUID = 1L;

        public NoNetworkException(String msg) {
            super(msg);
        }
    }
}
