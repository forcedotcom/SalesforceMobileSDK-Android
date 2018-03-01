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
package com.salesforce.androidsdk.phonegap.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.webkit.WebView;

import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.UriFragmentParser;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Helper class for SalesforceWebViewClient.
 */
public class SalesforceWebViewClientHelper {

	private static String TAG = "SalesforceWebViewClientHelper";
    private static final String SFDC_WEB_VIEW_CLIENT_SETTINGS = "sfdc_gapviewclient";
    private static final String APP_HOME_URL_PROP_KEY =  "app_home_url";
    private static final String VF_SESSION_PREFIX = "%2Fvisualforce%2Fsession%3Furl%3D";
    private static final String EMPTY_STRING = "";
    private static final String EC_PARAM = "ec";
    private static final String START_URL_PARAM = "startURL";
    private static final List<String> RESERVED_URL_PATTERNS =
            Arrays.asList("/secur/frontdoor.jsp", "/secur/contentDoor");

    /**
     * To be called from shouldOverrideUrlLoading.
     *
     * @param ctx Context.
     * @param view The WebView initiating the callback.
     * @param url The URL of the page.
     * @return True - if loading should be overridden, False - otherwise.
     */
    public static boolean shouldOverrideUrlLoading(Context ctx,
    		WebView view, String url) {
    	final String startURL = SalesforceWebViewClientHelper.isLoginRedirect(url);
        if (startURL != null && ctx instanceof SalesforceDroidGapActivity) {
            ((SalesforceDroidGapActivity) ctx).refresh(startURL);
        	return true;
        } else {
        	return false;
        }
    }

    /**
     * To be called from onPageFinished.
     *
     * @param ctx Context.
     * @param view The WebView initiating the callback.
     * @param url The URL of the page.
     * @return True - if we have arrived on the actual home page, False - otherwise.
     */
    public static boolean onHomePage(Context ctx, WebView view, String url) {

        /*
         * The first URL that's loaded that's not one of the URLs used in the bootstrap
         * process will be considered the "app home URL", which can be loaded directly
         * in the event that the app is offline.
         */
        if (!isReservedUrl(url)) {
            SalesforceHybridLogger.i(TAG, "Setting '" + url + "' as the home page URL for this app");
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
     * Returns the app's home page.
     *
     * @param ctx Context.
     * @return App's home page.
     */
    public static String getAppHomeUrl(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(SalesforceWebViewClientHelper.SFDC_WEB_VIEW_CLIENT_SETTINGS, Context.MODE_PRIVATE);
        String url = sp.getString(SalesforceWebViewClientHelper.APP_HOME_URL_PROP_KEY, null);
        return url;
    }

    /**
     * Returns if the app has a cached app home.
     *
     * @param ctx Context.
     * @return True - if there is a cached version of the app's home page, False - otherwise.
     */
    public static boolean hasCachedAppHome(Context ctx) {
    	String cachedAppHomeUrl = getAppHomeUrl(ctx);
    	return cachedAppHomeUrl != null && (new File(cachedAppHomeUrl)).exists();
    }

    private static boolean isReservedUrl(String url) {
        if (url == null || url.trim().equals(EMPTY_STRING)) {
            return false;
        }
        for (String reservedUrlPattern : RESERVED_URL_PATTERNS) {
            if (url.toLowerCase(Locale.US).contains(reservedUrlPattern.toLowerCase(Locale.US))) {
                return true;
            }
        }
        return false;
    }

    private static String isLoginRedirect(String url) {
    	final Uri uri = Uri.parse(url);
        final Map<String, String> params = UriFragmentParser.parse(uri);
    	final String ec = params.get(EC_PARAM);
    	int ecInt = (ec != null ? Integer.parseInt(ec) : -1);
    	String startURL = params.get(START_URL_PARAM);
        if ((ecInt == HttpURLConnection.HTTP_MOVED_PERM
    			|| ecInt == HttpURLConnection.HTTP_MOVED_TEMP)
    			&& startURL != null) {
            if (startURL.contains(VF_SESSION_PREFIX)) {
                startURL = startURL.replace(VF_SESSION_PREFIX, EMPTY_STRING);
            }
    		return startURL;
    	} else {
    		return null;
    	}
    }
}
