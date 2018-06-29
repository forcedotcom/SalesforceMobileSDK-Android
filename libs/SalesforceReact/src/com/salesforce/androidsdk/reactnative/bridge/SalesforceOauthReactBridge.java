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
package com.salesforce.androidsdk.reactnative.bridge;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.salesforce.androidsdk.reactnative.ui.SalesforceReactActivity;

public class SalesforceOauthReactBridge extends ReactContextBaseJavaModule {

    private static final String TAG = "SalesforceOauthReactBridge";

    public SalesforceOauthReactBridge(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return TAG;
    }

    @ReactMethod
    public void authenticate(ReadableMap args,
                             Callback successCallback, Callback errorCallback) {
        final SalesforceReactActivity currentActivity = (SalesforceReactActivity) getCurrentActivity();
        if (currentActivity != null) {
            currentActivity.authenticate(successCallback, errorCallback);
        }
        else {
            if (errorCallback != null) {
                errorCallback.invoke("SalesforceReactActivity not found");
            }
        }
    }


    @ReactMethod
    public void getAuthCredentials(ReadableMap args,
                                   Callback successCallback, Callback errorCallback) {
        final SalesforceReactActivity currentActivity = (SalesforceReactActivity) getCurrentActivity();
        if (currentActivity != null) {
            currentActivity.getAuthCredentials(successCallback, errorCallback);
        }
        else {
            if (errorCallback != null) {
                errorCallback.invoke("SalesforceReactActivity not found");
            }
        }
    }

    @ReactMethod
    public void logoutCurrentUser(ReadableMap args,
                                  Callback successCallback, Callback errorCallback) {
        final SalesforceReactActivity currentActivity = (SalesforceReactActivity) getCurrentActivity();
        if (currentActivity != null) {
            currentActivity.logout(successCallback);
        }
        else {
            if (errorCallback != null) {
                errorCallback.invoke("SalesforceReactActivity not found");
            }
        }
    }
}
