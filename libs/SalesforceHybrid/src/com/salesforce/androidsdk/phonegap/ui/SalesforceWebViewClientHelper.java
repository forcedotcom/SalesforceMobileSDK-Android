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
import android.text.TextUtils;
import android.webkit.WebView;

import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger;
import com.salesforce.androidsdk.util.AuthConfigUtil;
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
    public static boolean shouldOverrideUrlLoading(Context ctx,
    		WebView view, String url) {
    	final String startURL = SalesforceWebViewClientHelper.isLoginRedirect(ctx, url);
        if (startURL != null && ctx instanceof SalesforceDroidGapActivity) {
            ((SalesforceDroidGapActivity) ctx).refresh(startURL);
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

    private static boolean isReservedUrl(String url) {
        if (url == null || url.trim().equals("")) {
            return false;
        }
        for (String reservedUrlPattern : RESERVED_URL_PATTERNS) {
            if (url.toLowerCase(Locale.US).contains(reservedUrlPattern.toLowerCase(Locale.US))) {
                return true;
            }
        }
        return false;
    }

    private static String isLoginRedirect(Context ctx, String url) {
        final Uri uri = Uri.parse(url);
        final Map<String, String> params = UriFragmentParser.parse(uri);
    	final String ec = params.get("ec");
    	int ecInt = (ec != null ? Integer.parseInt(ec) : -1);
    	final String startURL = params.get("startURL");
        if (ecInt == HttpURLConnection.HTTP_MOVED_PERM
    			|| ecInt == HttpURLConnection.HTTP_MOVED_TEMP) {
            if (!TextUtils.isEmpty(startURL)) {
                return startURL;
            } else {
                return BootConfig.getBootConfig(ctx).getStartPage();
            }
        } else if (isSamlLoginRedirect(ctx, url)) {
            return BootConfig.getBootConfig(ctx).getStartPage();
        } else {
    		return null;
    	}
    }

    private static boolean isSamlLoginRedirect(Context ctx, String url) {
        if (ctx instanceof SalesforceDroidGapActivity) {
            final AuthConfigUtil.MyDomainAuthConfig authConfig = ((SalesforceDroidGapActivity) ctx).getAuthConfig();
            if (authConfig != null) {
                final List<String> ssoUrls = authConfig.getSsoUrls();
                if (ssoUrls != null && ssoUrls.size() > 0) {
                   for (String ssoUrl : ssoUrls) {
                       int paramsIndex = ssoUrl.indexOf("?");
                       if (paramsIndex != -1) {
                           ssoUrl = ssoUrl.substring(0, paramsIndex);
                       }
                       if (url.contains(ssoUrl)) {
                           return true;
                       }
                   }
                }
            }
        }
        return false;
    }
}
