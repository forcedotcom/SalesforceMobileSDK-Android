/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.util;

import android.text.TextUtils;

import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.RestResponse;

import org.json.JSONObject;

import okhttp3.Request;
import okhttp3.Response;

/**
 * This class has utility methods that help with authentication related functionality.
 *
 * @author bhariharan
 */
public class AuthConfigUtil {

    private static final String FORWARD_SLASH = "/";
    private static final String MY_DOMAIN_AUTH_CONFIG_ENDPOINT = "/.well-known/auth-configuration";
    private static final String TAG = "AuthConfigUtil";

    /**
     * Returns the auth config associated with a my domain login endpoint. This call
     * should be made from a background thread since it makes a network request.
     *
     * @param loginUrl Login URL.
     * @return Auth config.
     */
    public static MyDomainAuthConfig getMyDomainAuthConfig(String loginUrl) {
        if (TextUtils.isEmpty(loginUrl)) {
            return null;
        }
        MyDomainAuthConfig authConfig = null;
        if (loginUrl.endsWith(FORWARD_SLASH)) {
            loginUrl = loginUrl.substring(0, loginUrl.length() - 1);
        }
        final String authConfigUrl = loginUrl + MY_DOMAIN_AUTH_CONFIG_ENDPOINT;
        final Request request = new Request.Builder().url(authConfigUrl).get().build();
        try {
            final Response response = HttpAccess.DEFAULT.getOkHttpClient().newCall(request).execute();
            if (response != null && response.isSuccessful()) {
                authConfig = new MyDomainAuthConfig((new RestResponse(response)).asJSONObject());
            }
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Auth config request was not successful", e);
        }
        return authConfig;
    }

    /**
     * This class represents my domain auth config.
     *
     * @author bhariharan
     */
    public static class MyDomainAuthConfig {

        private static final String MOBILE_SDK_KEY = "MobileSDK";
        private static final String USE_NATIVE_BROWSER_KEY = "UseAndroidNativeBrowserForAuthentication";

        private JSONObject authConfig;
        private boolean browserLoginEnabled;

        /**
         * Parameterized constructor.
         *
         * @param authConfig SSO auth config.
         */
        public MyDomainAuthConfig(JSONObject authConfig) {
            this.authConfig = authConfig;
            if (authConfig != null) {
                final JSONObject mobileSDK = authConfig.optJSONObject(MOBILE_SDK_KEY);
                if (mobileSDK != null) {
                    browserLoginEnabled = mobileSDK.optBoolean(USE_NATIVE_BROWSER_KEY);
                }
            }
        }

        /**
         * Returns the my domain auth config.
         *
         * @return Auth config.
         */
        public JSONObject getAuthConfig() {
            return authConfig;
        }

        /**
         * Returns whether browser login has been enabled in this auth config.
         *
         * @return True - if browser login is enabled, False - otherwise.
         */
        public boolean isBrowserLoginEnabled() {
            return browserLoginEnabled;
        }
    }
}
