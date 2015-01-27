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
package com.salesforce.androidsdk.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.res.Resources;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey;

/**
 * Class encapsulating the application configuration (consumer key, oauth scopes, refresh behavior).
 */
/**
 * @author wmathurin
 *
 */
public class BootConfig {

	// We expect a assets/www/bootconfig.json file to be provided by hybrid apps.
	private static final String HYBRID_BOOTCONFIG_PATH = "www" +
			System.getProperty("file.separator") + "bootconfig.json";

	// bootconfig.json should contain a map with the following keys.
	private static final String REMOTE_ACCESS_CONSUMER_KEY = "remoteAccessConsumerKey";
	private static final String OAUTH_REDIRECT_URI = "oauthRedirectURI";
	private static final String OAUTH_SCOPES = "oauthScopes";
	private static final String IS_LOCAL = "isLocal";
	private static final String START_PAGE = "startPage";
	private static final String ERROR_PAGE = "errorPage";
	private static final String SHOULD_AUTHENTICATE = "shouldAuthenticate";
	private static final String ATTEMPT_OFFLINE_LOAD = "attemptOfflineLoad";
	private static final String PUSH_NOTIFICATION_CLIENT_ID = "androidPushNotificationClientId";

	// Default for optional configs.
	private static final boolean DEFAULT_SHOULD_AUTHENTICATE = true;
	private static final boolean DEFAULT_ATTEMPT_OFFLINE_LOAD = true;

	private String remoteAccessConsumerKey;
	private String oauthRedirectURI;
	private String[] oauthScopes;
	private boolean isLocal;
	private String startPage;
	private String errorPage;
	private boolean shouldAuthenticate;
	private boolean attemptOfflineLoad;
	private String pushNotificationClientId;

	private static BootConfig INSTANCE = null;

	/**
     * Method to (build and) get the singleton instance.
     *
	 * @param ctx Context.
	 * @return BootConfig instance.
	 */
	public static BootConfig getBootConfig(Context ctx) {
		if (INSTANCE == null) {
			INSTANCE = new BootConfig();
			if (SalesforceSDKManager.getInstance().isHybrid()) {
				INSTANCE.readFromJSON(ctx);
			} else {
				INSTANCE.readFromXML(ctx);
			}
			INSTANCE.readFromRuntimeConfig(ctx);
		}
		return INSTANCE;
	}
	
    /**
     * Use runtime configurations (from MDM provider) if any
     * @param ctx
     */
    private void readFromRuntimeConfig(Context ctx) {
    	RuntimeConfig runtimeConfig = RuntimeConfig.getRuntimeConfig(ctx);
    	String mdmRemoteAccessConsumeKey = runtimeConfig.getString(ConfigKey.ManagedAppOAuthID);
    	String mdmOauthRedirectURI = runtimeConfig.getString(ConfigKey.ManagedAppCallbackURL);
    	
    	if (mdmRemoteAccessConsumeKey != null) remoteAccessConsumerKey = mdmRemoteAccessConsumeKey;
    	if (mdmOauthRedirectURI != null) oauthRedirectURI = mdmOauthRedirectURI;
	}

	/**
     * @return boot config as JSONObject
	 * @throws JSONException
	 */
	public JSONObject asJSON() throws JSONException
	{
		JSONObject config = new JSONObject();

		config.put(REMOTE_ACCESS_CONSUMER_KEY, remoteAccessConsumerKey);
		config.put(OAUTH_REDIRECT_URI, oauthRedirectURI);
		config.put(OAUTH_SCOPES, new JSONArray(Arrays.asList(oauthScopes)));
        config.put(IS_LOCAL, isLocal);
        config.put(START_PAGE, startPage);
        config.put(ERROR_PAGE, errorPage);

        if (pushNotificationClientId != null) 
            config.put(PUSH_NOTIFICATION_CLIENT_ID, pushNotificationClientId);
        config.put(SHOULD_AUTHENTICATE, shouldAuthenticate);
        config.put(ATTEMPT_OFFLINE_LOAD, attemptOfflineLoad);

        return config;
	}

	/**
	 * Initializes this BootConfig object by reading the content of bootconfig.json.
	 *
	 * @param ctx Context.
	 */
	private void readFromJSON(Context ctx) {
		final String jsonStr = readBootConfigFile(ctx);
		parseBootConfigStr(jsonStr);
	}

	/**
	 * Initializes this BootConfig object by reading the config from XML.
	 *
	 * @param ctx Context.
	 */
	private void readFromXML(Context ctx) {
		final Resources res = ctx.getResources();
		remoteAccessConsumerKey = res.getString(R.string.remoteAccessConsumerKey);
		oauthRedirectURI = res.getString(R.string.oauthRedirectURI);
		oauthScopes = res.getStringArray(R.array.oauthScopes);
		pushNotificationClientId = res.getString(R.string.androidPushNotificationClientId);
	}

	/**
	 * Reads the contents of the boot config file.
	 *
	 * @param ctx Context.
	 * @return String content of bootconfig.json.
	 */
	private String readBootConfigFile(Context ctx) {
		Scanner scanner = null;
		try {
			scanner = new Scanner(ctx.getAssets().open(HYBRID_BOOTCONFIG_PATH));

			// Good trick to get a string from a stream (http://weblogs.java.net/blog/pat/archive/2004/10/stupid_scanner_1.html).
			return scanner.useDelimiter("\\A").next();
		} catch (IOException e) {
			throw new BootConfigException("Failed to open " + HYBRID_BOOTCONFIG_PATH, e);
		} finally {
			if (scanner != null) {
				scanner.close();
			}
		}
	}

	/**
	 * Initializes this BootConfig object by parsing a JSON string.
	 *
	 * @param jsonStr JSON string.
	 */
	private void parseBootConfigStr(String jsonStr) {
		try {
			final JSONObject config = new JSONObject(jsonStr);

			// Required fields.
			remoteAccessConsumerKey = config.getString(REMOTE_ACCESS_CONSUMER_KEY);
			oauthRedirectURI = config.getString(OAUTH_REDIRECT_URI);
			final JSONArray jsonScopes = config.getJSONArray(OAUTH_SCOPES);
			oauthScopes = new String[jsonScopes.length()];
			for (int i=0; i<oauthScopes.length; i++) {
				oauthScopes[i] = jsonScopes.getString(i);
			}
			isLocal = config.getBoolean(IS_LOCAL);
			startPage = config.getString(START_PAGE);
			errorPage = config.getString(ERROR_PAGE);

			// Optional fields.
			pushNotificationClientId = config.optString(PUSH_NOTIFICATION_CLIENT_ID);
			shouldAuthenticate = config.optBoolean(SHOULD_AUTHENTICATE, DEFAULT_SHOULD_AUTHENTICATE);
			attemptOfflineLoad = config.optBoolean(ATTEMPT_OFFLINE_LOAD, DEFAULT_ATTEMPT_OFFLINE_LOAD);
		} catch (JSONException e) {
			throw new BootConfigException("Failed to parse " + HYBRID_BOOTCONFIG_PATH, e);
		}
	}

	/**
	 * Returns the consumer key value specified for your remote access object or connected app.
	 *
	 * @return Consumer key value specified for your remote access object or connected app.
	 */
	public String getRemoteAccessConsumerKey() {
		return remoteAccessConsumerKey;
	}

	/**
	 * Returns the redirect URI value specified for your remote access object or connected app.
	 *
	 * @return Redirect URI value specified for your remote access object or connected app.
	 */
	public String getOauthRedirectURI() {
		return oauthRedirectURI;
	}

	/**
	 * Returns the authorization/access scope(s) that the application needs to ask for at login.
	 * @return Authorization/access scope(s) that the application needs to ask for at login.
	 */
	public String[] getOauthScopes() {
		return oauthScopes;
	}

	/**
	 * Returns if the start page is local or a VF page.
	 *
	 * @return True - if start page is in assets/www, False - if it's a VF page.
	 */
	public boolean isLocal() {
		return isLocal;
	}

	/**
	 * Returns the path to the start page (local or remote).
	 * Example: index.html or /apex/basicpage.
	 *
	 * @return Path to start page (local or remote).
	 */
	public String getStartPage() {
		return startPage;
	}

	/**
	 * Returns the path to the local error page.
	 *
	 * @return Path to local error page.
	 */
	public String getErrorPage() {
		return errorPage;
	}

	/**
	 * Returns whether the app should go through login flow the first time or not.
	 *
	 * @return True - if the app should go through login flow, False - otherwise.
	 */
	public boolean shouldAuthenticate() {
		return shouldAuthenticate;
	}

	/**
	 * Returns whether the app should attempt to load cached content when offline.
	 *
	 * @return True - if the app should attempt to load cached content, False - otherwise.
	 */
	public boolean attemptOfflineLoad() {
		return attemptOfflineLoad;
	}

	/**
	 * Returns the push notification client ID.
	 *
	 * @return Push notification client ID.
	 */
	public String getPushNotificationClientId() {
		return pushNotificationClientId;
	}

	/**
	 * Exception thrown for all bootconfig parsing errors.
	 */
	static public class BootConfigException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public BootConfigException(String msg, Throwable cause) {
			super(msg, cause);
		}
	}
}
