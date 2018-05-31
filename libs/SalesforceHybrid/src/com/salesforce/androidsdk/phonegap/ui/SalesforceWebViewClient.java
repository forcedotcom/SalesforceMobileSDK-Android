/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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
import android.net.Uri;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.salesforce.androidsdk.app.Features;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.CordovaResourceApi.OpenForReadResult;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.engine.SystemWebView;
import org.apache.cordova.engine.SystemWebViewClient;

import java.io.File;
import java.io.IOException;

public class SalesforceWebViewClient extends SystemWebViewClient {

	static final String WWW_DIR = "/android_asset/www";
    private static final String TAG = "SalesforceWebViewClient";

    // The first non-reserved URL that's loaded will be considered the app's "home page", for caching purposes.
    protected boolean foundHomeUrl = false;
    protected Context ctx;
    protected CordovaWebView cordovaWebView;

	/**
	 * Parameterized constructor.
	 *
	 * @param parentEngine SystemWebViewEngine instance.
	 */
    public SalesforceWebViewClient(SalesforceWebViewEngine parentEngine) {
    	super(parentEngine);
    	cordovaWebView = parentEngine.getCordovaWebView();
    	final CordovaInterface cordova = parentEngine.getCordovaInterface();
    	if (cordova != null) {
        	ctx = cordova.getActivity();
    	}
        final SystemWebView webView = (SystemWebView) parentEngine.getView();
    	final String uaStr = SalesforceSDKManager.getInstance().getUserAgent();
        if (webView != null) {
    		final WebSettings webSettings = webView.getSettings();

    		// Setting custom user agent and a bunch of other settings.
    		final String origUserAgent = webSettings.getUserAgentString();
    		final String extendedUserAgentString = uaStr + " Hybrid " + (origUserAgent == null ? "" : origUserAgent);
    		webSettings.setUserAgentString(extendedUserAgentString);

    		// Configure HTML5 cache support.
    		webSettings.setDomStorageEnabled(true);
    		final String cachePath = SalesforceSDKManager.getInstance().getAppContext().getCacheDir().getAbsolutePath();
    		webSettings.setAppCachePath(cachePath);
    		webSettings.setAppCacheEnabled(true);
    		webSettings.setAllowFileAccess(true);
    		webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        }
    }

    @Override
    public boolean shouldOverrideUrlLoading(final WebView view, String url) {
    	if (SalesforceWebViewClientHelper.shouldOverrideUrlLoading(ctx, view, url)) {
    		return true;
        } else {
        	return super.shouldOverrideUrlLoading(view, url);
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (!this.foundHomeUrl && SalesforceWebViewClientHelper.onHomePage(SalesforceSDKManager.getInstance().getAppContext(), view, url)) {
            this.foundHomeUrl = true;
        }
        super.onPageFinished(view, url);
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
    	WebResourceResponse response = super.shouldInterceptRequest(view, url);

    	// Already intercepted (e.g. if url is not whitelisted).
    	if (response != null) {
    		return response;
    	}

    	// Not a localhost request.
		Uri origUri = Uri.parse(url);
		String host = origUri.getHost();
		if (host == null || !host.equals("localhost")) {
			return null;
		}

		// Localhost request.
		SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_LOCALHOST);
		try {
			String localPath = WWW_DIR + origUri.getPath();

			// Trying to access file outside assets/www.
			if (!isFileUnder(localPath, WWW_DIR)) {
				throw new IOException("Trying to access file outside assets/www");
			} else {
				Uri localUri = Uri.parse("file://" + localPath);
				CordovaResourceApi resourceApi = cordovaWebView.getResourceApi();
				OpenForReadResult result = resourceApi.openForRead(localUri, true);
				SalesforceHybridLogger.i(TAG, "Loading local file: " + localUri);
				return new WebResourceResponse(result.mimeType, "UTF-8", result.inputStream);
			}
		} catch (IOException e) {
            SalesforceHybridLogger.e(TAG, "Invalid localhost URL: " + url, e);
			return new WebResourceResponse("text/plain", "UTF-8", null);
		}
    }

    private boolean isFileUnder(String filePath, String dirPath) throws IOException {
    	File file = new File(filePath);
    	File dir = new File(dirPath);
    	return file.getCanonicalPath().indexOf(dir.getCanonicalPath()) == 0;
    }
}
