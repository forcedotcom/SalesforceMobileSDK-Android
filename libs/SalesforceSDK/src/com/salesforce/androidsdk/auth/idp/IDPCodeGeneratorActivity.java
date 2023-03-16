/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.auth.idp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.util.LogUtil;

/**
 * Launches a WebView, runs IDP requests within it and finishes itself when done.
 * Passes back either the code (if successful), or the error (if unsuccessful), to the caller.
 *
 * @author bhariharan
 */
public class IDPCodeGeneratorActivity extends Activity implements IDPCodeGeneratorHelper.CodeGeneratorCallback {

    public static final String USER_ACCOUNT_BUNDLE_KEY = "user_account_bundle";
    public static final String ERROR_KEY = "error";
    public static final String CODE_KEY = "code";
    public static final String LOGIN_URL_KEY = "login_url";
    private static final String EC_301 = "ec=301";
    private static final String EC_302 = "ec=302";
    private static final String TAG = "IDPCodeGeneratorActivity";

    private UserAccount userAccount;
    private SPConfig spConfig;
    private String loginUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fetches the required extras.
        final Intent intent = getIntent();

        Log.d(TAG, "onCreate " + LogUtil.intentToString(intent));

        final Bundle extras = intent.getExtras();
        if (extras != null) {
            userAccount = new UserAccount(extras.getBundle(USER_ACCOUNT_BUNDLE_KEY));
            spConfig = new SPConfig(extras.getBundle(SPInitiatedLoginReceiver.SP_CONFIG_BUNDLE_KEY));
        }

        // Protects against screenshots.
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
//                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.sf__idp_code_generator);
        final WebView webView = findViewById(R.id.sf__webview);
        final WebSettings webSettings = webView.getSettings();
        webSettings.setUseWideViewPort(true);
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);

        IDPCodeGeneratorHelper generatorHelper = new IDPCodeGeneratorHelper(webView, userAccount, spConfig, this);
        generatorHelper.generateCode();
    }

    @Override
    public void onResult(int resultCode, @NonNull Intent data) {
        Log.d(TAG, "onResult " + resultCode + " -> " + LogUtil.intentToString(data));
        setResult(resultCode, data);
        finish();
    }
}
