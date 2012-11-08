/*
 * Copyright (c) 2012, salesforce.com, inc.
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

package com.salesforce.androidsdk.ui;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaWebViewClient;
import org.apache.cordova.api.CordovaInterface;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import android.webkit.WebView;

import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

public class SalesforceGapViewClient extends CordovaWebViewClient {

    public static String TAG = "SalesforceGapViewClient";
    public static final String SFDC_WEB_VIEW_CLIENT_SETTINGS = "sfdc_gapviewclient";
    public static final String APP_HOME_URL_PROP_KEY =  "app_home_url";

    // Full and partial URLs to exclude from consideration when determining the home page URL.
    private static final List<String> RESERVED_URL_PATTERNS =
            Arrays.asList("/secur/frontdoor.jsp", "/secur/contentDoor");


    // The first non-reserved URL that's loaded will be considered the app's "home page", for caching purposes.
    protected boolean foundHomeUrl = false;

    protected Activity ctx;

    /**
     * Constructor.
     * 
     * @param cordova
     * @param view
     */
    public SalesforceGapViewClient(CordovaInterface cordova, CordovaWebView view) {
        super(cordova, view);
        this.ctx = cordova.getActivity();
    }


    /**
     * Notify the host application that a page has finished loading.
     *
     * @param view          The webview initiating the callback.
     * @param url           The url of the page.
     */
    @Override
    public void onPageFinished(WebView view, String url) {
        // The first URL that's loaded that's not one of the URLs used in the bootstrap process will
        // be considered the "app home URL", which can be loaded directly in the event that the app is offline.
        if (!this.foundHomeUrl && !isReservedUrl(url)) {
            Log.i(TAG,"Setting '" + url + "' as the home page URL for this app");

            SharedPreferences sp = ctx.getSharedPreferences(SFDC_WEB_VIEW_CLIENT_SETTINGS, Context.MODE_PRIVATE);
            Editor e = sp.edit();
            e.putString(APP_HOME_URL_PROP_KEY, url);
            e.commit();

            this.foundHomeUrl = true;
            EventsObservable.get().notifyEvent(EventType.GapWebViewPageFinished, url);
        }

        super.onPageFinished(view, url);
    }

    
    /**
     * @return app's home page
     */
    public static String getAppHomeUrl(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(SalesforceGapViewClient.SFDC_WEB_VIEW_CLIENT_SETTINGS, Context.MODE_PRIVATE);
        String url = sp.getString(SalesforceGapViewClient.APP_HOME_URL_PROP_KEY, null);
        return url;
    }

    /**
     * @param ctx
     * @return true if there is a cached version of the app's home page
     */
    public static boolean hasCachedAppHome(Context ctx) {
    	String cachedAppHomeUrl = getAppHomeUrl(ctx);
    	return cachedAppHomeUrl != null && (new File(cachedAppHomeUrl)).exists();
    }
    
    /**
     * Whether the given URL is one of the expected URLs used in the bootstrapping process
     * of the app.  Used for determining the app's "home page" URL.
     * @param url The URL to compare against the reserved list.
     * @return True if this URL is used in the bootstrapping process, false otherwise.
     */
    private static boolean isReservedUrl(String url) {
        if (url == null || url.trim().equals(""))
            return false;
        for (String reservedUrlPattern : RESERVED_URL_PATTERNS) {
            if (url.toLowerCase().contains(reservedUrlPattern.toLowerCase()))
                return true;
        }

        return false;
    }
}
