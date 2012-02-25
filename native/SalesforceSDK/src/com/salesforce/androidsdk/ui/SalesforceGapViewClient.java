package com.salesforce.androidsdk.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import android.webkit.WebView;

import com.phonegap.DroidGap;
import com.phonegap.DroidGap.GapViewClient;

public class SalesforceGapViewClient extends GapViewClient {

	public static String TAG = "SalesforceGapViewClient";
    public static final String SFDC_WEB_VIEW_CLIENT_SETTINGS = "sfdc_gapviewclient";
    public static final String APP_HOME_URL_PROP_KEY =  "app_home_url";

    
	// tracks whether the next url to be loaded is the result of bootstrap loading
	protected boolean nextUrlIsHomeUrl = false;


	public SalesforceGapViewClient(DroidGap droidGap, DroidGap ctx) {
		droidGap.super(ctx);
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
    	
        // The URL that's loaded after the bootstrap start page will be considered the "app home URL", which can
        // be loaded directly in the event that the app is offline.
        if (this.nextUrlIsHomeUrl) {
        	Log.i(TAG,"Setting '" + url + "' as the home page URL for this app");
        	
    		SharedPreferences sp = myCtx.getSharedPreferences(SFDC_WEB_VIEW_CLIENT_SETTINGS, Context.MODE_PRIVATE);
			Editor e = sp.edit();
			e.putString(APP_HOME_URL_PROP_KEY, url);
			e.commit();
			            
            this.nextUrlIsHomeUrl = false;
        } else {
        	if (url.equalsIgnoreCase(myCtx.startPageUrlString())) {
            	this.nextUrlIsHomeUrl = true;
            }
        }
        
        super.onPageFinished(view, url);

    }
	
}
