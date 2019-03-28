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
package com.salesforce.androidsdk.util.test;

import android.content.Context;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.util.ResourceReaderHelper;

import org.json.JSONObject;

/**
 * Authentication credentials used to make live server calls in tests
 *
 * To populate test_credentials.json clone SalesforceMobileSDK-Shared and run web app in credsHelper folder
 */
public class TestCredentials {

    public static String API_VERSION;
    public static String ACCOUNT_TYPE;
    public static String ORG_ID;
    public static String USERNAME;
    public static String ACCOUNT_NAME;
    public static String USER_ID;
    public static String LOGIN_URL;
    public static String INSTANCE_URL;
    public static String COMMUNITY_URL;
    public static String IDENTITY_URL;
    public static String CLIENT_ID;
    public static String REFRESH_TOKEN;
    public static String PHOTO_URL;

    public static void init(Context ctx) {
        try {
            JSONObject json = new JSONObject(ResourceReaderHelper.readAssetFile(ctx, "test_credentials.json"));

            API_VERSION = ApiVersionStrings.getVersionNumber(ctx);
            ACCOUNT_TYPE = ctx.getString(R.string.account_type);
            ORG_ID = json.getString("organization_id");
            USERNAME = json.getString("username");
            ACCOUNT_NAME = json.getString("display_name");
            USER_ID = json.getString("user_id");
            LOGIN_URL = json.getString("test_login_domain");
            INSTANCE_URL = json.getString("instance_url");
            COMMUNITY_URL = json.optString("community_url", INSTANCE_URL /* in case the test_credentials.json was obtained for a user / org without community setup */);
            IDENTITY_URL = json.getString("identity_url");
            CLIENT_ID = json.getString("test_client_id");
            REFRESH_TOKEN = json.getString("refresh_token");
            PHOTO_URL = json.getString("photo_url");
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to read test_credentials.json", e);
        }
    }
}
