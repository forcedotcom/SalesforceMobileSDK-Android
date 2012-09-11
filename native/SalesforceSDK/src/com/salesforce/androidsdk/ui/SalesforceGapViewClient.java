package com.salesforce.androidsdk.ui;

import java.util.Arrays;
import java.util.List;

import org.apache.cordova.CordovaWebViewClient;
import org.apache.cordova.DroidGap;

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
            Arrays.asList(SalesforceDroidGapActivity.BOOTSTRAP_START_PAGE, "/secur/frontdoor.jsp", "/secur/contentDoor");


    // The first non-reserved URL that's loaded will be considered the app's "home page", for caching purposes.
    protected boolean foundHomeUrl = false;


    public SalesforceGapViewClient(DroidGap ctx) {
        super(ctx);
    }
    
    


    /**
     * Notify the host application that a page has finished loading.
     *
     * @param view          The webview initiating the callback.
     * @param url           The url of the page.
     */
    @Override
    public void onPageFinished(WebView view, String url) {
        SalesforceDroidGapActivity myCtx = (SalesforceDroidGapActivity)this.ctx;

        // The first URL that's loaded that's not one of the URLs used in the bootstrap process will
        // be considered the "app home URL", which can be loaded directly in the event that the app is offline.
        if (!this.foundHomeUrl && !isReservedUrl(url)) {
            Log.i(TAG,"Setting '" + url + "' as the home page URL for this app");

            SharedPreferences sp = myCtx.getSharedPreferences(SFDC_WEB_VIEW_CLIENT_SETTINGS, Context.MODE_PRIVATE);
            Editor e = sp.edit();
            e.putString(APP_HOME_URL_PROP_KEY, url);
            e.commit();

            this.foundHomeUrl = true;
            EventsObservable.get().notifyEvent(EventType.GapWebViewPageFinished, url);
        }

        super.onPageFinished(view, url);
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
