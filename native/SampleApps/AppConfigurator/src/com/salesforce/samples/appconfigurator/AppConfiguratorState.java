/*
 * Copyright (c) 2014, salesforce.com, inc.
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

package com.salesforce.samples.appconfigurator;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

public class AppConfiguratorState {

    // Prefs
    private static final String PREFS_KEY = "AppConfiguratorPrefs";

    // Copied from com.salesforce.androidsdk.config.RuntimeConfig
    public enum ConfigKey {
        LOGIN_SERVERS,
        LOGIN_SERVERS_LABELS,
        REMOTE_ACCESS_CONSUMER_KEY,
        OAUTH_REDIRECT_URI;
    }

    // Default values
    private static final String DEFAULT_TARGET_APP = "com.salesforce.samples.configuredapp";
    private static final String DEFAULT_LOGIN_SERVERS = "https://test.salesforce.com,https://login.salesforce.com";
    private static final String DEFAULT_LOGIN_SERVERS_LABELS = "sandbox,production";
    private static final String DEFAULT_REMOTE_ACCESS_CONSUMER_KEY = "3MVG9Iu66FKeHhINkB1l7xt7kR8czFcCTUhgoA8Ol2Ltf1eYHOU4SqQRSEitYFDUpqRWcoQ2.dBv_a1Dyu5xa";
    private static final String DEFAULT_OAUTH_REDIRECT_URI = "testsfdc:///mobilesdk/detect/oauth/done";

    // State
    private String loginServers;
    private String loginServersLabels;
    private String remoteAccessConsumerKey;
    private String oauthRedirectURI;

    // Singleton instance
    private static AppConfiguratorState INSTANCE;

    public synchronized static AppConfiguratorState getInstance(Context ctx) {
        if (INSTANCE == null) {
            INSTANCE = new AppConfiguratorState(ctx);
        }
        return INSTANCE;
    }

    private AppConfiguratorState(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE);
        loginServers = prefs.getString(ConfigKey.LOGIN_SERVERS.name(), DEFAULT_LOGIN_SERVERS);
        loginServersLabels = prefs.getString(ConfigKey.LOGIN_SERVERS_LABELS.name(), DEFAULT_LOGIN_SERVERS_LABELS);
        remoteAccessConsumerKey = prefs.getString(ConfigKey.REMOTE_ACCESS_CONSUMER_KEY.name(), DEFAULT_REMOTE_ACCESS_CONSUMER_KEY);
        oauthRedirectURI = prefs.getString(ConfigKey.OAUTH_REDIRECT_URI.name(), DEFAULT_OAUTH_REDIRECT_URI);
    }

    public String getTargetApp() {
        return DEFAULT_TARGET_APP;
    }

    public String getLoginServers() {
        return loginServers;
    }

    public String getLoginServersLabels() {
        return loginServersLabels;
    }

    public String getRemoteAccessConsumerKey() {
        return remoteAccessConsumerKey;
    }

    public String getOauthRedirectURI() {
        return oauthRedirectURI;
    }

    /**
     * Save configurations to preferences and as app restrictions on target app
     * @param loginServers
     * @param loginServersLabels
     * @param remoteAccessConsumerKey
     * @param oauthRedirectURI
     */
    public void saveConfigurations(Context ctx, String loginServers, String loginServersLabels, String remoteAccessConsumerKey, String oauthRedirectURI) {
        // Save to fields
        this.loginServers = loginServers;
        this.loginServersLabels = loginServersLabels;
        this.remoteAccessConsumerKey = remoteAccessConsumerKey;
        this.oauthRedirectURI = oauthRedirectURI;

        // Save to preferences
        ctx.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
                .edit()
                .putString(ConfigKey.LOGIN_SERVERS.name(), loginServers)
                .putString(ConfigKey.LOGIN_SERVERS_LABELS.name(), loginServersLabels)
                .putString(ConfigKey.REMOTE_ACCESS_CONSUMER_KEY.name(), remoteAccessConsumerKey)
                .putString(ConfigKey.OAUTH_REDIRECT_URI.name(), oauthRedirectURI)
                .apply();

        // Save to app restrictions on target app
        DevicePolicyManager devicePolicyManager
                = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        Bundle restrictions = new Bundle();
        if (!loginServers.isEmpty()) restrictions.putStringArray(ConfigKey.LOGIN_SERVERS.name(), loginServers.split(","));
        if (!loginServersLabels.isEmpty()) restrictions.putStringArray(ConfigKey.LOGIN_SERVERS_LABELS.name(), loginServersLabels.split(","));
        if (!remoteAccessConsumerKey.isEmpty()) restrictions.putString(ConfigKey.REMOTE_ACCESS_CONSUMER_KEY.name(), remoteAccessConsumerKey);
        if (!oauthRedirectURI.isEmpty()) restrictions.putString(ConfigKey.OAUTH_REDIRECT_URI.name(), oauthRedirectURI);
        devicePolicyManager.setApplicationRestrictions(
                AppConfiguratorAdminReceiver.getComponentName(ctx),
                getTargetApp(), restrictions);
    }
}