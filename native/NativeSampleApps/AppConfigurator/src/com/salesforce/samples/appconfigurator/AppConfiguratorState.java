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

package com.salesforce.samples.appconfigurator;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

public class AppConfiguratorState {

    // Prefs
    private static final String PREFS_KEY = "AppConfiguratorPrefs";

    // Copied from com.salesforce.androidsdk.config.RuntimeConfig
    public enum ConfigKey {
		AppServiceHosts,
		AppServiceHostLabels,
		ManagedAppOAuthID,
		ManagedAppCallbackURL,
		RequireCertAuth,
		ManagedAppCertAlias,
        OnlyShowAuthorizedHosts,
        IDPAppURLScheme
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
    private boolean requireCertAuth;
    private String certAlias;
    private boolean onlyShowAuthorizedHosts;
    private String idpAppURLScheme;

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
        loginServers = prefs.getString(ConfigKey.AppServiceHosts.name(), DEFAULT_LOGIN_SERVERS);
        loginServersLabels = prefs.getString(ConfigKey.AppServiceHostLabels.name(), DEFAULT_LOGIN_SERVERS_LABELS);
        remoteAccessConsumerKey = prefs.getString(ConfigKey.ManagedAppOAuthID.name(), DEFAULT_REMOTE_ACCESS_CONSUMER_KEY);
        oauthRedirectURI = prefs.getString(ConfigKey.ManagedAppCallbackURL.name(), DEFAULT_OAUTH_REDIRECT_URI);
        requireCertAuth = prefs.getBoolean(ConfigKey.RequireCertAuth.name(), false);
        certAlias = prefs.getString(ConfigKey.ManagedAppCertAlias.name(), null);
        onlyShowAuthorizedHosts = prefs.getBoolean(ConfigKey.OnlyShowAuthorizedHosts.name(), false);
        idpAppURLScheme = prefs.getString(ConfigKey.IDPAppURLScheme.name(), null);
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

    public boolean requiresCertAuth() {
        return requireCertAuth;
    }

    public String getCertAlias() {
        return certAlias;
    }

    public boolean shouldOnlyShowAuthorizedHosts() {
        return onlyShowAuthorizedHosts;
    }

    public String getIDPAppURLScheme() {
        return idpAppURLScheme;
    }

    /**
     * Save configurations to preferences and as app restrictions on target app
     * @param loginServers
     * @param loginServersLabels
     * @param remoteAccessConsumerKey
     * @param oauthRedirectURI
     * @param requireCertAuth
     * @param certAlias
     * @param onlyShowAuthorizedHosts
     * @param idpAppURLScheme
     */
    public void saveConfigurations(Context ctx, String loginServers,
    		String loginServersLabels, String remoteAccessConsumerKey,
    		String oauthRedirectURI, boolean requireCertAuth, String certAlias,
            boolean onlyShowAuthorizedHosts, String idpAppURLScheme) {

        // Save to fields
        this.loginServers = loginServers;
        this.loginServersLabels = loginServersLabels;
        this.remoteAccessConsumerKey = remoteAccessConsumerKey;
        this.oauthRedirectURI = oauthRedirectURI;
        this.requireCertAuth = requireCertAuth;
        this.certAlias = certAlias;
        this.onlyShowAuthorizedHosts = onlyShowAuthorizedHosts;
        this.idpAppURLScheme = idpAppURLScheme;

        // Save to preferences
        ctx.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
                .edit()
                .putString(ConfigKey.AppServiceHosts.name(), loginServers)
                .putString(ConfigKey.AppServiceHostLabels.name(), loginServersLabels)
                .putString(ConfigKey.ManagedAppOAuthID.name(), remoteAccessConsumerKey)
                .putString(ConfigKey.ManagedAppCallbackURL.name(), oauthRedirectURI)
                .putBoolean(ConfigKey.RequireCertAuth.name(), requireCertAuth)
                .putString(ConfigKey.ManagedAppCertAlias.name(), certAlias)
                .putBoolean(ConfigKey.OnlyShowAuthorizedHosts.name(), onlyShowAuthorizedHosts)
                .putString(ConfigKey.IDPAppURLScheme.name(), idpAppURLScheme)
                .apply();

        // Save to app restrictions on target app
        DevicePolicyManager devicePolicyManager
                = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        Bundle restrictions = new Bundle();
        if (!loginServers.isEmpty()) restrictions.putStringArray(ConfigKey.AppServiceHosts.name(), loginServers.split(","));
        if (!loginServersLabels.isEmpty()) restrictions.putStringArray(ConfigKey.AppServiceHostLabels.name(), loginServersLabels.split(","));
        if (!remoteAccessConsumerKey.isEmpty()) restrictions.putString(ConfigKey.ManagedAppOAuthID.name(), remoteAccessConsumerKey);
        if (!oauthRedirectURI.isEmpty()) restrictions.putString(ConfigKey.ManagedAppCallbackURL.name(), oauthRedirectURI);
        restrictions.putBoolean(ConfigKey.RequireCertAuth.name(), requireCertAuth);
        if (!certAlias.isEmpty()) restrictions.putString(ConfigKey.ManagedAppCertAlias.name(), certAlias);
        restrictions.putBoolean(ConfigKey.OnlyShowAuthorizedHosts.name(), onlyShowAuthorizedHosts);
        if (!idpAppURLScheme.isEmpty()) restrictions.putString(ConfigKey.IDPAppURLScheme.name(), idpAppURLScheme);
        devicePolicyManager.setApplicationRestrictions(
                AppConfiguratorAdminReceiver.getComponentName(ctx),
                getTargetApp(), restrictions);
    }
}
