/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey;
import com.salesforce.androidsdk.util.ResourceReaderHelper;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

/**
 * Class encapsulating the application configuration (consumer key, oauth scopes, refresh behavior).
 *
 * @author wmathurin
 */
public class BootConfig {

	private static final String TAG = "BootConfig";

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
	private static final String UNAUTHENTICATED_START_PAGE = "unauthenticatedStartPage";

	// Default for optional configs.
	private static final boolean DEFAULT_SHOULD_AUTHENTICATE = true;
	private static final boolean DEFAULT_ATTEMPT_OFFLINE_LOAD = true;

	private boolean configIsHybrid;

	private String remoteAccessConsumerKey;
	private String oauthRedirectURI;
	private String[] oauthScopes;
	private boolean isLocal;
	private String startPage;
	private String errorPage;
	private boolean shouldAuthenticate;
	private boolean attemptOfflineLoad;
	private String pushNotificationClientId;
	private String unauthenticatedStartPage;

	private static BootConfig INSTANCE = null;

	/**
     * Method to (build and) get the singleton instance.
     *
	 * @param ctx Context.
	 * @return BootConfig instance.
	 */
	public static BootConfig getBootConfig(Context ctx) {
		if (INSTANCE == null) {
			if (SalesforceSDKManager.getInstance().isHybrid()) {
				INSTANCE = getHybridBootConfig(ctx, HYBRID_BOOTCONFIG_PATH);
			} else {
				INSTANCE = new BootConfig();
				INSTANCE.readFromXML(ctx);
			}
			INSTANCE.readFromRuntimeConfig(ctx);
		}
		return INSTANCE;
	}

	/**
	 * Gets a hybrid boot config instance from its JSON configuration file.
	 * @param ctx The context used to stage the JSON configuration file.
	 * @param assetFilePath The relative path to the file, from the assets/ folder of the context.
	 * @return A BootConfig representing the hybrid boot config object.
	 */
	static BootConfig getHybridBootConfig(Context ctx, String assetFilePath) {
		BootConfig hybridBootConfg = new BootConfig();
		hybridBootConfg.configIsHybrid = true;
		JSONObject bootConfigJsonObj = readFromJSON(ctx, assetFilePath);
		hybridBootConfg.parseBootConfig(bootConfigJsonObj);
		return hybridBootConfg;
	}

	/**
	 * Validates a boot config's inputs against basic sanity tests.
	 * @param config The BootConfig instance to validate.
	 * @throws BootConfigException If the boot config is invalid.
	 */
	public static void validateBootConfig(BootConfig config) {
		if (config == null) {
			throw new BootConfigException("No boot config provided.");
		}

		if (config.configIsHybrid) {
			// startPage must be a relative URL.
			if (BootConfig.isAbsoluteUrl(config.getStartPage())) {
				throw new BootConfigException("Start page should not be absolute URL.");
			}

			// unauthenticatedStartPage doesn't make sense in a local setup.  Warn accordingly.
			if (config.isLocal() && config.getUnauthenticatedStartPage() != null) {
				SalesforceSDKLogger.w(TAG, UNAUTHENTICATED_START_PAGE + " set for local app, but it will never be used.");
			}

			// unauthenticatedStartPage doesn't make sense in a remote setup with authentication.  Warn accordingly.
			if (!config.isLocal() && config.shouldAuthenticate() && config.getUnauthenticatedStartPage() != null) {
				SalesforceSDKLogger.w(TAG, UNAUTHENTICATED_START_PAGE + " set for remote app with authentication, but it will never be used.");
			}

			// Lack of unauthenticatedStartPage with remote deferred authentication is an error.
			if (!config.isLocal() && !config.shouldAuthenticate() && TextUtils.isEmpty(config.getUnauthenticatedStartPage())) {
				throw new BootConfigException(UNAUTHENTICATED_START_PAGE + " required for remote app with deferred authentication.");
			}

			// unauthenticatedStartPage, if present, must be an absolute URL.
			if (!TextUtils.isEmpty(config.getUnauthenticatedStartPage())
					&& !BootConfig.isAbsoluteUrl(config.getUnauthenticatedStartPage())) {
				throw new BootConfigException(UNAUTHENTICATED_START_PAGE + " should be absolute URL.");
			}
		}
	}

	/**
	 * Use runtime configurations (from MDM provider) if any
	 *
	 * @param ctx
	 */
	private void readFromRuntimeConfig(Context ctx) {
		RuntimeConfig runtimeConfig = RuntimeConfig.getRuntimeConfig(ctx);
		String mdmRemoteAccessConsumeKey = runtimeConfig.getString(ConfigKey.ManagedAppOAuthID);
		String mdmOauthRedirectURI = runtimeConfig.getString(ConfigKey.ManagedAppCallbackURL);
		if (!TextUtils.isEmpty(mdmRemoteAccessConsumeKey)) {
			remoteAccessConsumerKey = mdmRemoteAccessConsumeKey;
		}
		if (!TextUtils.isEmpty(mdmOauthRedirectURI)) {
			oauthRedirectURI = mdmOauthRedirectURI;
		}
	}

	/**
	 * @return boot config as JSONObject
	 */
	public JSONObject asJSON() {
		try {
			JSONObject config = new JSONObject();
			config.put(REMOTE_ACCESS_CONSUMER_KEY, remoteAccessConsumerKey);
			config.put(OAUTH_REDIRECT_URI, oauthRedirectURI);
			config.put(OAUTH_SCOPES, new JSONArray(Arrays.asList(oauthScopes)));
			config.put(IS_LOCAL, isLocal);
			config.put(START_PAGE, startPage);
			config.put(ERROR_PAGE, errorPage);
			if (!TextUtils.isEmpty(pushNotificationClientId)) {
				config.put(PUSH_NOTIFICATION_CLIENT_ID, pushNotificationClientId);
			}
			config.put(SHOULD_AUTHENTICATE, shouldAuthenticate);
			config.put(ATTEMPT_OFFLINE_LOAD, attemptOfflineLoad);
			config.put(UNAUTHENTICATED_START_PAGE, unauthenticatedStartPage);

			return config;
		}
		catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Initializes this BootConfig object by reading the content of the JSON configuration file
	 * at the specified path.
	 *
	 * @param ctx            Context.
	 * @param assetsFilePath The relative file path to the assets/ folder of the context.
	 * @return A BootConfig representing the hybrid boot config object.
	 */
	private static JSONObject readFromJSON(Context ctx, String assetsFilePath) {
		String jsonStr = ResourceReaderHelper.readAssetFile(ctx, assetsFilePath);
		if (jsonStr == null) {
			throw new BootConfigException("Failed to open " + assetsFilePath);
		}
		try {
			JSONObject jsonObj = new JSONObject(jsonStr);
			return jsonObj;
		} catch (JSONException e) {
			throw new BootConfigException("Failed to parse " + assetsFilePath, e);
		}
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
	 * Initializes this BootConfig object by parsing a JSON object.
	 *
	 * @param config JSON object representing boot config.
	 */
	private void parseBootConfig(JSONObject config) {
		try {
			// Required fields.
			remoteAccessConsumerKey = config.getString(REMOTE_ACCESS_CONSUMER_KEY);
			oauthRedirectURI = config.getString(OAUTH_REDIRECT_URI);
			final JSONArray jsonScopes = config.getJSONArray(OAUTH_SCOPES);
			oauthScopes = new String[jsonScopes.length()];
			for (int i = 0; i < oauthScopes.length; i++) {
				oauthScopes[i] = jsonScopes.getString(i);
			}
			isLocal = config.getBoolean(IS_LOCAL);
			startPage = config.getString(START_PAGE);
			errorPage = config.getString(ERROR_PAGE);

			// Optional fields.
			pushNotificationClientId = config.optString(PUSH_NOTIFICATION_CLIENT_ID);
			shouldAuthenticate = config.optBoolean(SHOULD_AUTHENTICATE, DEFAULT_SHOULD_AUTHENTICATE);
			attemptOfflineLoad = config.optBoolean(ATTEMPT_OFFLINE_LOAD, DEFAULT_ATTEMPT_OFFLINE_LOAD);
			unauthenticatedStartPage = config.optString(UNAUTHENTICATED_START_PAGE);
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
	 * Returns the path to the optional unauthenticated start page (for remote deferred
	 * authentication).
	 * @return URL of the unauthenticated start page.
	 */
	public String getUnauthenticatedStartPage() { return unauthenticatedStartPage; }

	/**
	 * Convenience method to determine whether a configured startPage value is an absolute URL.
	 * @return true if startPage is an absolute URL, false otherwise.
	 */
	public static boolean isAbsoluteUrl(String urlString) {
		return (urlString != null && (urlString.startsWith("http://") || urlString.startsWith("https://")));
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

		public BootConfigException(String msg) {
			super(msg);
		}

		public BootConfigException(String msg, Throwable cause) {
			super(msg, cause);
		}
	}
}
