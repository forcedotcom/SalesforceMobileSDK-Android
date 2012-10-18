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
package com.salesforce.androidsdk.phonegap;

import java.io.IOException;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;

/**
 * Class encapsulating the application configuration (consumer key, oauth scopes, refresh behavior)
 */
public class BootConfig {

	// We expect a assets/www/bootconfig.json
	private static final String BOOTCONFIG_PATH = "www/bootconfig.json";
		
	// bootconfig.json should contain a map with the following keys
	private static final String REMOTE_ACCESS_CONSUMER_KEY = "remoteAccessConsumerKey";
	private static final String OAUTH_REDIRECT_URI = "oauthRedirectURI";
	private static final String OAUTH_SCOPES = "oauthScopes";
	private static final String AUTO_REFRESH_ON_FOREGROUND = "autoRefreshOnForeground";
	private static final String AUTO_REFRESH_PERIODICALLY = "autoRefreshPeriodically";
	private static final String ATTEMPT_OFFLINE_LOAD = "attemptOfflineLoad";

	// Default for optional configs
	private static final boolean DEFAULT_AUTO_REFRESH_ON_FOREGROUND = true;
	private static final boolean DEFAULT_AUTO_REFRESH_PERIODICALLY = true;
	private static final boolean DEFAULT_ATTEMPT_OFFLINE_LOAD = true;

	
	private String remoteAccessConsumerKey;
	private String oauthRedirectURI;
	private String[] oauthScopes;
	private boolean isLocal;
	private String startPage;
	private boolean autoRefreshOnForeground;
	private boolean autoRefreshPeriodically;
	private boolean attemptOfflineLoad;
	
	/**
	 * Read boot configuration from www/assets/bootconfig.json
	 * @throws BootConfigException 
	 */
	public void readFromJSON(Context ctx) throws BootConfigException
	{
		String jsonStr = readBootConfigFile(ctx);
		parseBootConfigStr(jsonStr);
	}

	/**
	 * @param ctx
	 * @return
	 * @throws BootConfigException
	 */
	private String readBootConfigFile(Context ctx)
			throws BootConfigException {
		Scanner scanner = null;
		try {
			scanner = new Scanner(ctx.getAssets().open(BOOTCONFIG_PATH));
			return scanner.useDelimiter("\\A").next(); // good trick to get a string from a stream see http://weblogs.java.net/blog/pat/archive/2004/10/stupid_scanner_1.html
		}
		catch (IOException e) {
			throw new BootConfigException("Failed to open " + BOOTCONFIG_PATH, e);
		}
		finally {
			if (scanner != null) {
				scanner.close();
			}
		}
	}

	/**
	 * @param jsonStr
	 * @throws BootConfigException
	 */
	private void parseBootConfigStr(String jsonStr) throws BootConfigException {
		try {
			JSONObject config = new JSONObject(jsonStr);
			
			Log.i(getClass().getSimpleName(), "config: " + config.toString(2));

			// Required
			remoteAccessConsumerKey = config.getString(REMOTE_ACCESS_CONSUMER_KEY);
			oauthRedirectURI = config.getString(OAUTH_REDIRECT_URI);
			JSONArray jsonScopes = config.getJSONArray(OAUTH_SCOPES);
			oauthScopes = new String[jsonScopes.length()];
			for (int i=0; i<oauthScopes.length; i++) {
				oauthScopes[i] = jsonScopes.getString(i);
			}
			isLocal = config.getBoolean("isLocal");
			startPage = config.getString("startPage");
			
			// Optional
			autoRefreshOnForeground = config.optBoolean(AUTO_REFRESH_ON_FOREGROUND, DEFAULT_AUTO_REFRESH_ON_FOREGROUND);
			autoRefreshPeriodically = config.optBoolean(AUTO_REFRESH_PERIODICALLY, DEFAULT_AUTO_REFRESH_PERIODICALLY);
			attemptOfflineLoad = config.optBoolean(ATTEMPT_OFFLINE_LOAD, DEFAULT_ATTEMPT_OFFLINE_LOAD);
		}
		catch (JSONException e) {
			throw new BootConfigException("Failed to parse " + BOOTCONFIG_PATH, e);
		}
	}

	/**
	 * @return consumer key value specified for your remote access object or connected app
	 */
	public String getRemoteAccessConsumerKey() {
		return remoteAccessConsumerKey;
	}
	
	/**
	 * @return redirect URI value specified for your remote access object or connected app
	 */
	public String getOauthRedirectURI() {
		return oauthRedirectURI;
	}
	
	/**
	 * @return  authorization/access scope(s) that the application needs to ask for at login
	 */
	public String[] getOauthScopes() {
		return oauthScopes;
	}

	/**
	 * @return true if start page is www/assets and false if it's a VF page
	 */
	public boolean isLocal() {
		return isLocal;
	}
	
	/**
	 * @return path to start page (e.g. index.html or /apex/basicpage)
	 */
	public String getStartPage() {
		return startPage;
	}

	/**
	 * @return true if app should refresh oauth session when foregrounded
	 */
	public boolean autoRefreshOnForeground() {
		return autoRefreshOnForeground;
	}

	/**
	 * @return true if app should refresh oauth session periodically
	 */
	public boolean autoRefreshPeriodically() {
		return autoRefreshPeriodically;
	}

	/**
	 * @return true, if app should attempt to load previously cached content when offline
	 */
	public boolean attemptOfflineLoad() {
		return attemptOfflineLoad;
	}

	/**
	 * Exception thrown for all bootconfig parsing errors
	 *
	 */
	static public class BootConfigException extends Exception {
		private static final long serialVersionUID = 1L;

		public BootConfigException(String msg, Throwable cause) {
			super(msg, cause);
		}
	}
}
