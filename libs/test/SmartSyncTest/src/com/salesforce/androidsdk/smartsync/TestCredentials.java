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
package com.salesforce.androidsdk.smartsync;

import android.content.Context;

import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.smartsync.tests.R;

/**
 * Authentication credentials used to make live server calls in tests.
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
        API_VERSION = ApiVersionStrings.getVersionNumber(ctx);
        ACCOUNT_TYPE = ctx.getString(R.string.account_type);
        ORG_ID = ctx.getString(R.string.org_id);
        USERNAME = ctx.getString(R.string.username);
        ACCOUNT_NAME = ctx.getString(R.string.account_name);
        USER_ID = ctx.getString(R.string.user_id);
        LOGIN_URL = ctx.getString(R.string.login_url);
        INSTANCE_URL = ctx.getString(R.string.instance_url);
        COMMUNITY_URL = ctx.getString(R.string.community_url);
        IDENTITY_URL = ctx.getString(R.string.identity_url);
        CLIENT_ID = ctx.getString(R.string.remoteAccessConsumerKey);
        REFRESH_TOKEN = ctx.getString(R.string.oauth_refresh_token);
        PHOTO_URL = ctx.getString(R.string.photo_url);
    }
}
