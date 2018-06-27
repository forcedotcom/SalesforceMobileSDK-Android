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

import android.os.AsyncTask;
import android.webkit.URLUtil;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.LoginServerManager;

import okhttp3.HttpUrl;

/**
 * A simple AsyncTask that fetches auth config if it exists and calls the supplied
 * callback method on the UI thread once the call is complete. The auth config is
 * not fetched for production and sandbox.
 *
 * @author bhariharan
 */
public class AuthConfigTask extends AsyncTask<Void, Void, Void> {

    /**
     * A callback interface that is triggered after auth config has been fetched.
     */
    public interface AuthConfigCallbackInterface {

        /**
         * This method is called after auth config has been fetched.
         */
        void onAuthConfigFetched();
    }

    private final AuthConfigCallbackInterface callback;

    /**
     * Parameterized constructor.
     *
     * @param callback Callback.
     */
    public AuthConfigTask(AuthConfigCallbackInterface callback) {
        this.callback = callback;
    }

    @Override
    protected Void doInBackground(Void... nothings) {
        final String loginServer = SalesforceSDKManager.getInstance().getLoginServerManager().getSelectedLoginServer().url.trim();
        if (loginServer.equals(LoginServerManager.PRODUCTION_LOGIN_URL) ||
                loginServer.equals(LoginServerManager.SANDBOX_LOGIN_URL) ||
                !URLUtil.isHttpsUrl(loginServer) || HttpUrl.parse(loginServer) == null) {
            SalesforceSDKManager.getInstance().setBrowserLoginEnabled(false);
            return null;
        }
        final AuthConfigUtil.MyDomainAuthConfig authConfig = AuthConfigUtil.getMyDomainAuthConfig(loginServer);
        boolean browserLoginEnabled = false;
        if (authConfig != null) {
            browserLoginEnabled = authConfig.isBrowserLoginEnabled();
        }
        SalesforceSDKManager.getInstance().setBrowserLoginEnabled(browserLoginEnabled);
        return null;
    }

    @Override
    protected void onPostExecute(Void nothing) {
        if (callback != null) {
            callback.onAuthConfigFetched();
        }
    }
}
