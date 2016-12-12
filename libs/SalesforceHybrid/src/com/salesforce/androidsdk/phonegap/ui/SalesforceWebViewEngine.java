/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPreferences;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaWebViewEngine;
import org.apache.cordova.NativeToJsMessageQueue;
import org.apache.cordova.PluginManager;
import org.apache.cordova.engine.SystemWebView;
import org.apache.cordova.engine.SystemWebViewEngine;

/**
 * Salesforce specific implementation of Cordova's SystemWebViewEngine.
 *
 * @author bhariharan
 */
public class SalesforceWebViewEngine extends SystemWebViewEngine {

    /**
     * Used when created via reflection.
     *
     * @param context Context.
     * @param preferences Preferences.
     */
    public SalesforceWebViewEngine(Context context, CordovaPreferences preferences) {
        super(new SalesforceWebView(context));
    }

    public SalesforceWebViewEngine(SystemWebView webView) {
        super(webView, null);
    }

    public SalesforceWebViewEngine(SystemWebView webView, CordovaPreferences preferences) {
        super(webView, preferences);
    }

    @Override
    public void init(CordovaWebView parentWebView, CordovaInterface cordova,
    		CordovaWebViewEngine.Client client, CordovaResourceApi resourceApi,
    		PluginManager pluginManager, NativeToJsMessageQueue nativeToJsMessageQueue) {
    	super.init(parentWebView, cordova, client, resourceApi, pluginManager,
    			nativeToJsMessageQueue);
    	if (webView != null) {
    		((SalesforceWebView) webView).setWebViewClient(this);
    	}
    }

    /**
     * Returns the Cordova interface being used.
     *
     * @return CordovaInterface instance.
     */
    public CordovaInterface getCordovaInterface() {
    	return cordova;
    }
}
