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
package com.salesforce.androidsdk.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.XmlResourceParser;
import android.text.TextUtils;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey;
import com.salesforce.androidsdk.ui.SalesforceR;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class to manage login hosts (default and user entered).
 *
 * @author bhariharan
 */
public class LoginServerManager {

	private static final String TAG = "LoginServerManager";

	// Default login servers.
    public static final String PRODUCTION_LOGIN_URL = "https://login.salesforce.com";
    public static final String SANDBOX_LOGIN_URL = "https://test.salesforce.com";

	// Legacy keys for login servers properties stored in preferences.
	public static final String LEGACY_SERVER_URL_PREFS_SETTINGS = "server_url_prefs";

	// Keys used in shared preferences.
	private static final String SERVER_URL_FILE = "server_url_file";
	private static final String RUNTIME_PREFS_FILE = "runtime_prefs_file";
	private static final String NUMBER_OF_ENTRIES = "number_of_entries";
	private static final String SERVER_NAME = "server_name_%d";
	private static final String SERVER_URL = "server_url_%d";
	private static final String IS_CUSTOM = "is_custom_%d";
    private static final String SERVER_SELECTION_FILE = "server_selection_file";

	private Context ctx;
	private LoginServer selectedServer;
	private SharedPreferences settings;
	private SharedPreferences runtimePrefs;

    /**
     * Parameterized constructor.
     *
     * @param ctx Context.
     */
    public LoginServerManager(Context ctx) {
    	this.ctx = ctx;
    	settings = ctx.getSharedPreferences(SERVER_URL_FILE,
    			Context.MODE_PRIVATE);
		runtimePrefs = ctx.getSharedPreferences(RUNTIME_PREFS_FILE,
				Context.MODE_PRIVATE);
    	initSharedPrefFile();
    	selectedServer = getSelectedLoginServer();
    }

    /**
     * Returns a LoginServer instance from URL.
     *
     * @param url Server URL.
     * @return Matching LoginServer instance if found, or null.
     */
    public LoginServer getLoginServerFromURL(String url) {
    	if (url == null) {
    		return null;
    	}
    	final List<LoginServer> allServers = getLoginServers();
    	if (allServers != null) {
    		for (final LoginServer server : allServers) {
    			if (server != null && url.equals(server.url)) {
    				return server;
    			}
    		}
    	}
		return null;
    }

    /**
     * Returns the selected login server to display.
     *
     * @return LoginServer instance.
     */
    public LoginServer getSelectedLoginServer() {
        final SharedPreferences selectedServerPrefs = ctx.getSharedPreferences(SERVER_SELECTION_FILE,
                Context.MODE_PRIVATE);
        final String name = selectedServerPrefs.getString(SERVER_NAME, null);
        final String url = selectedServerPrefs.getString(SERVER_URL, null);
        boolean isCustom = selectedServerPrefs.getBoolean(IS_CUSTOM, false);

        // Selection has been saved before.
        if (name != null && url != null) {
            final LoginServer server = new LoginServer(name, url, isCustom);
            selectedServer = server;
        } else {

            // First time selection defaults to the first server on the list.
            final List<LoginServer> allServers = getLoginServers();
            if (allServers != null) {
                final LoginServer server = allServers.get(0);
                if (server != null) {
                    selectedServer = server;
                }
            }

            // For some reason, if it's still not set, sets it to the default.
            if (selectedServer == null) {
                selectedServer = new LoginServer("Production", PRODUCTION_LOGIN_URL, false);
            }

            // Stores the selection for the future.
            setSelectedLoginServer(selectedServer);
        }
    	return selectedServer;
    }

    /**
     * Sets the currently selected login server to display.
     *
     * @param server LoginServer instance.
     */
    public void setSelectedLoginServer(LoginServer server) {
    	if (server == null) {
    		return;
    	}
        final SharedPreferences selectedServerPrefs = ctx.getSharedPreferences(SERVER_SELECTION_FILE,
                Context.MODE_PRIVATE);
        final Editor edit = selectedServerPrefs.edit();
        edit.clear();
        edit.putString(SERVER_NAME, server.name);
        edit.putString(SERVER_URL, server.url);
        edit.putBoolean(IS_CUSTOM, server.isCustom);
        edit.commit();
    	selectedServer = server;
    }

    /**
     * Selects Sandbox as login server (used in tests).
     */
    public void useSandbox() {
    	final LoginServer sandboxServer = getLoginServerFromURL(SANDBOX_LOGIN_URL);
    	setSelectedLoginServer(sandboxServer);
    }

	/**
	 * Adds a custom login server to the shared pref file.
	 *
	 * @param name Server name.
	 * @param url Server URL.
	 */
	public void addCustomLoginServer(String name, String url) {
        if (getLoginServersFromRuntimeConfig() == null) {
            persistLoginServer(name, url, true, settings);
        } else {
            persistLoginServer(name, url, true, runtimePrefs);
        }
        setSelectedLoginServer(new LoginServer(name, url, true));
	}

	/**
	 * Clears all saved custom servers.
	 */
	public void reset() {
		Editor edit = settings.edit();
		edit.clear();
		edit.commit();
		edit = runtimePrefs.edit();
		edit.clear();
		edit.commit();
        final SharedPreferences selectedServerPrefs = ctx.getSharedPreferences(SERVER_SELECTION_FILE,
                Context.MODE_PRIVATE);
        edit = selectedServerPrefs.edit();
        edit.clear();
        edit.commit();
		initSharedPrefFile();
	}

	/**
	 * Returns the list of login servers.
	 * Checks run time configuration first.
	 * Reads from preferences if no runtime configuration found.
	 *
	 * @return List of login servers.
	 */
	public List<LoginServer> getLoginServers() {
		List<LoginServer> allServers = getLoginServersFromRuntimeConfig();
		if (allServers == null) {
			allServers = getLoginServersFromPreferences();
		} else {
            allServers = getLoginServersFromPreferences(runtimePrefs);
        }
		return allServers;
	}

	/**
	 * Returns the list of login servers from runtime configuration
	 * (from MDM provider), if any.
	 *
	 * @return List of login servers or null.
	 */
	public List<LoginServer> getLoginServersFromRuntimeConfig() {
		RuntimeConfig runtimeConfig = RuntimeConfig.getRuntimeConfig(ctx);
		String[] mdmLoginServers = null;
		try {
			mdmLoginServers = runtimeConfig.getStringArray(ConfigKey.AppServiceHosts);
		} catch (Exception e) {
			SalesforceSDKLogger.w(TAG, "Exception thrown while attempting to read array, attempting to read string value instead", e);
		}
		if (mdmLoginServers == null) {
			final String loginServer = runtimeConfig.getString(ConfigKey.AppServiceHosts);
			if (!TextUtils.isEmpty(loginServer)) {
				mdmLoginServers = new String[] {loginServer};
			}
		}
		final List<LoginServer> allServers = new ArrayList<LoginServer>();
		if (mdmLoginServers != null) {
			String[] mdmLoginServersLabels = null;
			try {
				mdmLoginServersLabels = runtimeConfig.getStringArray(ConfigKey.AppServiceHostLabels);
			} catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Exception thrown while attempting to read array, attempting to read string value instead", e);
			}
			if (mdmLoginServersLabels == null) {
				final String loginServerLabel = runtimeConfig.getString(ConfigKey.AppServiceHostLabels);
				if (!TextUtils.isEmpty(loginServerLabel)) {
					mdmLoginServersLabels = new String[] {loginServerLabel};
				}
			}
			if (mdmLoginServersLabels == null || mdmLoginServersLabels.length != mdmLoginServers.length) {
                SalesforceSDKLogger.w(TAG, "No login servers labels provided or wrong number of login servers labels provided - Using URLs for the labels");
				mdmLoginServersLabels = mdmLoginServers;
			}
            final List<LoginServer> storedServers = getLoginServersFromPreferences(runtimePrefs);
			for (int i = 0; i < mdmLoginServers.length; i++) {
				String name = mdmLoginServersLabels[i];
				String url = mdmLoginServers[i];
				final LoginServer server = new LoginServer(name, url, false);
                if (storedServers == null || !storedServers.contains(server)) {
                    persistLoginServer(name, url, false, runtimePrefs);
                }
				allServers.add(server);
			}
		}
		return (allServers.size() > 0 ? allServers : null);
	}

	/**
	 * Returns the list of all saved servers, including custom servers.
	 *
	 * @return List of all saved servers.
	 */
	public List<LoginServer> getLoginServersFromPreferences() {
		return getLoginServersFromPreferences(settings);
	}

	/**
	 * Returns production and sandbox as the login servers
	 * (only called when servers.xml is missing).
	 */
	private List<LoginServer> getLegacyLoginServers() {
		final SalesforceR salesforceR = SalesforceSDKManager.getInstance().getSalesforceR();
		final List<LoginServer> loginServers = new ArrayList<LoginServer>();
		final LoginServer productionServer = new LoginServer(ctx.getString(salesforceR.stringAuthLoginProduction()),
				PRODUCTION_LOGIN_URL, false);
		loginServers.add(productionServer);
		final LoginServer sandboxServer = new LoginServer(ctx.getString(salesforceR.stringAuthLoginSandbox()),
				SANDBOX_LOGIN_URL, false);
		loginServers.add(sandboxServer);
		return loginServers;
	}

	/**
	 * Returns the list of login servers from XML.
	 *
	 * @return Login servers defined in 'res/xml/servers.xml', or null.
	 */
	private List<LoginServer> getLoginServersFromXML() {
		List<LoginServer> loginServers = null;
		int id = ctx.getResources().getIdentifier("servers", "xml", ctx.getPackageName());
		if (id != 0) {
			loginServers = new ArrayList<LoginServer>();
			final XmlResourceParser xml = ctx.getResources().getXml(id);
			int eventType = -1;		
			while (eventType != XmlResourceParser.END_DOCUMENT) {
				if (eventType == XmlResourceParser.START_TAG) {
					if (xml.getName().equals("server")) {
						String name = xml.getAttributeValue(null, "name");
						String url = xml.getAttributeValue(null, "url");
						final LoginServer loginServer = new LoginServer(name,
								url, false);
						loginServers.add(loginServer);
					}
				}
				try {
					eventType = xml.next();
				} catch (XmlPullParserException e) {
                    SalesforceSDKLogger.w(TAG, "Exception thrown while parsing XML", e);
				} catch (IOException e) {
                    SalesforceSDKLogger.w(TAG, "Exception thrown while parsing XML", e);
				}
			}
		}
		return loginServers;
	}

	/**
	 * Initializes the shared pref file with all available servers for
	 * the first time, if necessary. This is required primarily for the
	 * first time a user is upgrading to a newer version of the Mobile SDK.
	 */
	private void initSharedPrefFile() {
		final Map<String, ?> values = settings.getAll();
		if (values != null && !values.isEmpty()) {
			return;
		}
		List<LoginServer> servers = getLoginServersFromXML();
		if (servers == null || servers.isEmpty()) {
			servers = getLegacyLoginServers();
		}
		int numServers = servers.size();
	    final Editor edit = settings.edit();
		for (int i = 0; i < numServers; i++) {
			final LoginServer curServer = servers.get(i);
			edit.putString(String.format(SERVER_NAME, i), curServer.name);
		    edit.putString(String.format(SERVER_URL, i), curServer.url);
		    edit.putBoolean(String.format(IS_CUSTOM, i), curServer.isCustom);
		    if (i == 0) {
		    	setSelectedLoginServer(curServer);
		    }
		}
	    edit.putInt(NUMBER_OF_ENTRIES, numServers);
	    edit.commit();
	}

	/**
	 * Adds a custom login server to the specified shared pref file.
	 *
	 * @param name Server name.
	 * @param url Server URL.
	 * @param isCustom True - if it is a custom server, False - otherwise.
	 * @param sharedPrefs SharedPreferences file.
	 */
	private void persistLoginServer(String name, String url, boolean isCustom, SharedPreferences sharedPrefs) {
		if (name == null || url == null) {
			return;
		}
		int numServers = sharedPrefs.getInt(NUMBER_OF_ENTRIES, 0);
		final Editor edit = sharedPrefs.edit();
		edit.putString(String.format(SERVER_NAME, numServers), name);
		edit.putString(String.format(SERVER_URL, numServers), url);
		edit.putBoolean(String.format(IS_CUSTOM, numServers), isCustom);
		edit.putInt(NUMBER_OF_ENTRIES, ++numServers);
		edit.commit();
	}

	/**
	 * Returns the list of all saved servers, including custom servers.
	 *
	 * @param prefs SharedPreferences file.
	 * @return List of all saved servers.
	 */
	private List<LoginServer> getLoginServersFromPreferences(SharedPreferences prefs) {
		int numServers = prefs.getInt(NUMBER_OF_ENTRIES, 0);
		if (numServers == 0) {
			return null;
		}
		final List<LoginServer> allServers = new ArrayList<LoginServer>();
		for (int i = 0; i < numServers; i++) {
			final String name = prefs.getString(String.format(SERVER_NAME, i), null);
			final String url = prefs.getString(String.format(SERVER_URL, i), null);
			boolean isCustom = prefs.getBoolean(String.format(IS_CUSTOM, i), false);
			if (name != null && url != null) {
				final LoginServer server = new LoginServer(name, url, isCustom);
				allServers.add(server);
			}
		}
		return (allServers.size() > 0 ? allServers : null);
	}

	/**
	 * Class to encapsulate a login server name, URL, index and type (custom or not).
	 */
	public static class LoginServer {

		public final String name;
		public final String url;
		public final boolean isCustom;

		/**
		 * Parameterized constructor.
		 *
		 * @param name Server name.
		 * @param url Server URL.
		 * @param isCustom True - if custom URL, False - otherwise.
		 */
		public LoginServer(String name, String url, boolean isCustom) {
			this.name = name;
			this.url = url;
			this.isCustom = isCustom;
		}

		@Override 
		public String toString() {
			return "Name: " + name + ", URL: " + url + ", Custom URL: " + isCustom;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null || obj.getClass() != getClass()) {
				return false;
			}
			final LoginServer server = (LoginServer) obj;
			return (name.trim().equals(server.name.trim()) &&
					url.trim().equals(server.url.trim()) && (isCustom == server.isCustom));
		}
	}
}
