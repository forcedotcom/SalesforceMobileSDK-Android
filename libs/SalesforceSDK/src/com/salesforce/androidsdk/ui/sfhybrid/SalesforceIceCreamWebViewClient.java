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
package com.salesforce.androidsdk.ui.sfhybrid;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.IceCreamCordovaWebViewClient;
import org.apache.cordova.LOG;
import org.apache.cordova.CordovaResourceApi.OpenForReadResult;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class SalesforceIceCreamWebViewClient extends IceCreamCordovaWebViewClient {

    // The first non-reserved URL that's loaded will be considered the app's "home page", for caching purposes.
    protected boolean foundHomeUrl = false;

    protected SalesforceDroidGapActivity ctx;
    protected CordovaWebView cordovaWebView;

    /**
     * Constructor.
     * 
     * @param cordova
     * @param view
     */
    public SalesforceIceCreamWebViewClient(CordovaInterface cordova, CordovaWebView view) {
        super(cordova, view);
        this.ctx = (SalesforceDroidGapActivity) cordova.getActivity();
        this.cordovaWebView = view;
    }

    @Override
    public boolean shouldOverrideUrlLoading(final WebView view, String url) {
    	if (SalesforceWebViewClientHelper.shouldOverrideUrlLoading(ctx, view, url)) {
    		return true;
        } else {
        	return super.shouldOverrideUrlLoading(view,  url);
        }
    }
    
    @Override
    public void onPageFinished(WebView view, String url) {
        if (!this.foundHomeUrl && SalesforceWebViewClientHelper.onHomePage(ctx, view, url)) {
            this.foundHomeUrl = true;
        }

        super.onPageFinished(view, url);
    }
    
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
    	Uri origUri = Uri.parse(url);
		if (origUri.getHost().equals("localhost")) {
            CordovaResourceApi resourceApi = cordovaWebView.getResourceApi();
            Uri remappedUri = Uri.parse("file:///android_asset/www" + origUri.getPath());
            OpenForReadResult result;
			try {
				result = resourceApi.openForRead(remappedUri, true);
	            return new WebResourceResponse(result.mimeType, "UTF-8", result.inputStream);
			} catch (IOException e) {
	            if (!(e instanceof FileNotFoundException)) {
	                LOG.e("SalesforceIceCreamWebViewClient", "Error occurred while loading a file (returning a 404).", e);
	            }
	            // Results in a 404.
	            return new WebResourceResponse("text/plain", "UTF-8", null);
			}
		}
		else {
			return super.shouldInterceptRequest(view, url);
		}
    }
}
