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
package com.salesforce.androidsdk.ui.sfhybrid;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.http.HttpStatus;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;

import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.UriFragmentParser;


/**
 * Helper class for SalesforceWebViewClient, SalesforceIceCreamWebViewClient
 * 
 */
public class SalesforceWebViewClientHelper {

	public static String TAG = "SalesforceWebViewClientHelper";
    public static final String SFDC_WEB_VIEW_CLIENT_SETTINGS = "sfdc_gapviewclient";
    public static final String APP_HOME_URL_PROP_KEY =  "app_home_url";

    // Full and partial URLs to exclude from consideration when determining the home page URL.
    private static final List<String> RESERVED_URL_PATTERNS =
            Arrays.asList("/secur/frontdoor.jsp", "/secur/contentDoor");

    /**
     * To be called from shouldOverrideUrlLoading.
     * 
     * @param ctx
     * @param view
     * @param url
     * @return
     */
    public static boolean shouldOverrideUrlLoading(SalesforceDroidGapActivity ctx,
    		WebView view, String url) {
    	final String startURL = SalesforceWebViewClientHelper.isLoginRedirect(url);
        if (startURL != null) {
        	ctx.refresh(startURL);
        	return true;
        } else {
        	return false;
        }
    }

    /**
     * To be called from onPageFinished.
     * Return true if we have arrived on the actual home page and false otherwise.
     * 
     * @param ctx			Context.
     * @param view          The webview initiating the callback.
     * @param url           The url of the page.
     */
    public static boolean onHomePage(Context ctx, WebView view, String url) {
        // The first URL that's loaded that's not one of the URLs used in the bootstrap process will
        // be considered the "app home URL", which can be loaded directly in the event that the app is offline.
        if (!isReservedUrl(url)) {
            Log.i(TAG,"Setting '" + url + "' as the home page URL for this app");

            SharedPreferences sp = ctx.getSharedPreferences(SFDC_WEB_VIEW_CLIENT_SETTINGS, Context.MODE_PRIVATE);
            Editor e = sp.edit();
            e.putString(APP_HOME_URL_PROP_KEY, url);
            e.commit();

            EventsObservable.get().notifyEvent(EventType.GapWebViewPageFinished, url);
            
            return true;
        } else {
        	return false;
        }
    }
    
    /**
     * @return app's home page
     */
    public static String getAppHomeUrl(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(SalesforceWebViewClientHelper.SFDC_WEB_VIEW_CLIENT_SETTINGS, Context.MODE_PRIVATE);
        String url = sp.getString(SalesforceWebViewClientHelper.APP_HOME_URL_PROP_KEY, null);
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
            if (url.toLowerCase(Locale.US).contains(reservedUrlPattern.toLowerCase(Locale.US)))
                return true;
        }
        return false;
    }

    /**
     * Login redirect are of the form https://host/?ec=30x&startURL=xyz
     * @param url
     * @return null if this is not a login redirect and return the the value for startURL if this is a login redirect
     */
    private static String isLoginRedirect(String url) {
    	final Uri uri = Uri.parse(url);
        final Map<String, String> params = UriFragmentParser.parse(uri);
    	final String ec = params.get("ec");
    	int ecInt = (ec != null ? Integer.parseInt(ec) : -1);
    	final String startURL = params.get("startURL");
        if ((ecInt == HttpStatus.SC_MOVED_PERMANENTLY
    			|| ecInt == HttpStatus.SC_MOVED_TEMPORARILY)
    			&& startURL != null) {
    		return startURL;
    	} else {
    		return null;
    	}
    }
}
