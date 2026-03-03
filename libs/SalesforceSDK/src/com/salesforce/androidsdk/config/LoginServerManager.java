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

import static android.content.Context.MODE_PRIVATE;
import static com.salesforce.androidsdk.R.xml.servers;
import static com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.AppServiceHostLabels;
import static com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.AppServiceHosts;
import static com.salesforce.androidsdk.config.RuntimeConfig.getRuntimeConfig;
import static java.lang.String.format;
import static java.util.Locale.US;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.XmlResourceParser;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.MutableLiveData;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Class to manage login hosts (default and user entered).
 *
 * @author bhariharan
 */
public class LoginServerManager {
	// LiveData representation of the users current selected server.
	public MutableLiveData<LoginServer> selectedServer = new MutableLiveData<>();

	private static final String TAG = "LoginServerManager";

	// Default login servers.
	public static final String PRODUCTION_LOGIN_URL = "https://login.salesforce.com";
	public static final String WELCOME_LOGIN_URL = "https://welcome.salesforce.com/discovery";
	public static final String SANDBOX_LOGIN_URL = "https://test.salesforce.com";

	/**
	 * Shared preferences when non-custom login servers are provided by resources servers.xml
	 */
	@VisibleForTesting
	public static final String SERVER_URL_FILE = "server_url_file";

	/**
	 * Shared preferences when non-custom login servers are provided by runtime config Mobile Device Management (MDM)
	 */
	@VisibleForTesting
	public static final String RUNTIME_PREFS_FILE = "runtime_prefs_file";

	private static final String NUMBER_OF_ENTRIES = "number_of_entries";
	private static final String SERVER_NAME = "server_name_%d";
	private static final String SERVER_URL = "server_url_%d";
	private static final String IS_CUSTOM = "is_custom_%d";

	/**
	 * Shared preferences for the selected login server
	 */
	@VisibleForTesting
	public static final String SERVER_SELECTION_FILE = "server_selection_file";

	/**
	 * The Android context used for shared preferences access
	 */
	private final Context ctx;

	/**
	 * The Android runtime configuration used for Mobile Device Management (MDM)
	 */
	private final RuntimeConfig runtimeConfig;

	/** The resource id of the servers.xml file */
	private final int serversXmlResourceId;

	/**
	 * Shared preferences when non-custom resources login servers are provided by servers.xml and associated custom login servers added by the user
	 */
	private final SharedPreferences settings;

	/**
	 * Shared preferences when non-custom resources login servers are provided by the runtime configuration (Mobile Device Management) and associated custom login servers added by the user
	 */
	private final SharedPreferences runtimePrefs;

	/**
	 * Constructs a new login server manager.
	 *
	 * @param ctx The context
	 */
	public LoginServerManager(final Context ctx) {
		this(ctx, getRuntimeConfig(ctx), servers);
	}

	/**
	 * Constructs a new login server manager.
	 *
	 * @param ctx The context
	 */
	@VisibleForTesting
	public LoginServerManager(
			final Context ctx,
			final RuntimeConfig runtimeConfig,
			final int serversXmlResourceId
	) {
		this.ctx = ctx;
		this.runtimeConfig = runtimeConfig;
		this.serversXmlResourceId = serversXmlResourceId;
		settings = ctx.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE);
		runtimePrefs = ctx.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE);

		// (Re-)initialize non-custom login servers provided by the resources servers.xml.
		resetNonCustomLoginServers(settings);
		initSharedPrefFile();

		// Select a default login server.
		getSelectedLoginServer();
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
	 * Returns the selected login server. This will set a default login server if needed and ensure
	 * the selected login server is available in the current list of login servers.
	 *
	 * @return The selected login server
	 */
	public LoginServer getSelectedLoginServer() {
		final SharedPreferences selectedServerPrefs = ctx.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE);
		final String name = selectedServerPrefs.getString(SERVER_NAME, null);
		final String url = selectedServerPrefs.getString(SERVER_URL, null);
		boolean isCustom = selectedServerPrefs.getBoolean(IS_CUSTOM, false);

		// Refresh the list of mobile device management (MDM) servers from the runtime config.
		if (isRuntimeConfigAppServiceHostsSet()) {
			getLoginServersFromRuntimeConfig();
		}

		// Get the active list of login servers.
		final List<LoginServer> loginServers = getLoginServers();

		// Selection has been saved before and is available in the active list of login servers.
		if (name != null && url != null && loginServers.stream().anyMatch(server -> server.name.equals(name) && server.url.equals(url))) {
			final LoginServer server = new LoginServer(name, url, isCustom);

			// Only notify live data consumers if the value has changed.
			if (!server.equals(selectedServer.getValue())) {
				selectedServer.postValue(server);
			}
		} else {

			// First time selection defaults to the first server on the list.
			if (loginServers != null) {
				final LoginServer server = loginServers.get(0);
				if (server != null) {
					selectedServer.postValue(server);
				}
			}

			// Stores the selection for the future.
			setSelectedLoginServer(selectedServer.getValue());
		}
		return selectedServer.getValue();
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
		final SharedPreferences selectedServerPrefs = ctx.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE);
		final Editor edit = selectedServerPrefs.edit();
		edit.clear();
		edit.putString(SERVER_NAME, server.name);
		edit.putString(SERVER_URL, server.url);
		edit.putBoolean(IS_CUSTOM, server.isCustom);
		edit.apply();
		if (Looper.myLooper() == Looper.getMainLooper()) {
			selectedServer.setValue(server);
		} else {
			selectedServer.postValue(server);
		}
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
		// Prevent duplicate servers.
		for (LoginServer existingServer : getLoginServers()) {
			if (url.equals(existingServer.url)) {
				setSelectedLoginServer(existingServer);
				return;
			}
		}

		persistLoginServer(
				name,
				url,
				true /* Custom */,
				getSharedPreferences() /* Active Shared Preferences */
		);
		setSelectedLoginServer(new LoginServer(name, url, true));
	}

	/**
	 * Clears all saved custom servers.
	 */
	public void reset() {
		Editor edit = settings.edit();
		edit.clear();
		edit.apply();
		edit = runtimePrefs.edit();
		edit.clear();
		edit.apply();
		final SharedPreferences selectedServerPrefs = ctx.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE);
		edit = selectedServerPrefs.edit();
		edit.clear();
		edit.apply();
		initSharedPrefFile();
	}

	/**
	 * Removes a custom login server from the list.
	 *
	 * @param server The server to remove.  If the server is not custom, this method does nothing
	 */
	public void removeServer(final LoginServer server) {
		removeServer(server, settings, false);
	}

	/**
	 * Removes a login server from the list.
	 *
	 * @param server                The server to remove
	 * @param sharedPreferences     The shared preferences to remove the server from
	 * @param allowNonCustomRemoval Boolean true allows the removal of non-custom login servers and
	 *                              false does not
	 */
	private void removeServer(
			final LoginServer server,
			final SharedPreferences sharedPreferences,
			final boolean allowNonCustomRemoval
	) {
		final List<LoginServer> servers = getLoginServersFromPreferences(sharedPreferences);

		int index = servers.indexOf(server);

		if (allowNonCustomRemoval || server.isCustom && index != -1) {
			int numServers = servers.size();
			Deque<LoginServer> stack = new ArrayDeque<>(servers.subList(index + 1, numServers));

			final Editor edit = sharedPreferences.edit();
			edit.remove(format(US, SERVER_NAME, index))
					.remove(format(US, SERVER_URL, index))
					.remove(format(US, IS_CUSTOM, index));

			// Re-index servers after the one removed from the list.
			for (int i = (index + 1); i < numServers; i++) {
				LoginServer reIndexServer = stack.pop();
				edit.remove(format(US, SERVER_NAME, i))
						.remove(format(US, SERVER_URL, i))
						.remove(format(US, IS_CUSTOM, i))
						.putString(format(US, SERVER_NAME, i - 1), reIndexServer.name)
						.putString(format(US, SERVER_URL, i - 1), reIndexServer.url)
						.putBoolean(format(US, IS_CUSTOM, i - 1), reIndexServer.isCustom);
			}

			edit.putInt(NUMBER_OF_ENTRIES, --numServers).apply();
		}
	}

	/**
	 * Returns the list of login servers.  Defaults to mobile device management (MDM) login servers
	 * from the runtime configuration, if available, or resources login servers from the
	 * servers.xml.  MDM and resources login servers are not custom and cannot be removed by the
	 * user. A separate list of custom login servers is stored in a shared preferences for each of
	 * MDM and resources.
	 *
	 * @return The list of login servers
	 */
	public List<LoginServer> getLoginServers() {
		return getLoginServersFromPreferences(getSharedPreferences());
	}

	/**
	 * Returns the active shared preferences when using login servers from mobile device management
	 * (MDM) or the resources server.xml.
	 *
	 * @return SharedPreferences The active shared preferences
	 */
	private SharedPreferences getSharedPreferences() {
		return isRuntimeConfigAppServiceHostsSet() ? runtimePrefs : settings;
	}

	/**
	 * Determines if managed (non-custom) login servers are provided by mobile device management
	 * (MDM).
	 *
	 * @return boolean True indicates managed login servers are provided by MDM or the resources
	 * server.xml when false
	 */
	private boolean isRuntimeConfigAppServiceHostsSet() {
		try {
			return runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) != null;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Resets the list of Mobile Device Management (MDM) login servers from the runtime
	 * configuration. This does not remove the user's custom login servers.
	 */
	@SuppressWarnings("UnusedReturnValue")
    public List<LoginServer> getLoginServersFromRuntimeConfig() {
		String[] mdmLoginServers = null;
		try {
			mdmLoginServers = runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts);
		} catch (Exception e) {
			SalesforceSDKLogger.w(TAG, "Exception thrown while attempting to read array, attempting to read string value instead", e);
		}
		final List<LoginServer> allServers = new ArrayList<>();
		if (mdmLoginServers != null) {
			String[] mdmLoginServersLabels = null;
			try {
				mdmLoginServersLabels = runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels);
			} catch (Exception e) {
				SalesforceSDKLogger.w(TAG, "Exception thrown while attempting to read array, attempting to read string value instead", e);
			}
			if (mdmLoginServersLabels == null || mdmLoginServersLabels.length != mdmLoginServers.length) {
				SalesforceSDKLogger.w(TAG, "No login servers labels provided or wrong number of login servers labels provided - using URLs for the labels");
				mdmLoginServersLabels = mdmLoginServers;
			}

			// Reset non-custom servers from Mobile Device Management (MDM).
			resetNonCustomLoginServers(runtimePrefs);

			for (int i = 0; i < mdmLoginServers.length; i++) {
				final String name = mdmLoginServersLabels[i];
				final String url = mdmLoginServers[i];
				final LoginServer server = new LoginServer(name, url, false);
				persistLoginServer(
						name,
						url,
						false, /* Non-Custom */
						runtimePrefs
				);
				allServers.add(server);
			}
		}
		return (!allServers.isEmpty() ? allServers : null);
	}

	/**
	 * Returns the list of login servers from resources (servers.xml) and the user's custom servers.
	 * Note this does not consider login servers the runtime config provides from Mobile Device
	 * Management (MDM).  Consider using getLoginServers() instead if you need to consider MDM
	 * login servers.
	 *
	 * @return The list of login servers from resources (servers.xml) and the user's custom servers
	 */
	@SuppressWarnings("unused")
	public List<LoginServer> getLoginServersFromPreferences() {
		return getLoginServersFromPreferences(settings);
	}

	/**
	 * Reorders a custom login server in the list of login servers.
	 *
	 * @param originalIndex The original index of the custom login server.  If this is not the index
	 *                      of a custom login server, this method will do nothing
	 * @param updatedIndex  The new index of the custom login server.  This must be after the last
	 *                      non-custom login server and within the updatable bounds of the list.  If
	 *                      it is not it will be automatically corrected
	 */
	@SuppressWarnings("unused")
	public void reorderCustomLoginServer(
			final int originalIndex,
			int updatedIndex
	) {
		// Get the login server at the original index.
		final List<LoginServer> loginServers = getLoginServers();
		final LoginServer originalLoginServer = loginServers.get(originalIndex);

		// Guard against reordering a non-custom login server.
		if (!originalLoginServer.isCustom) {
			return;
		}

		// Adjust the re-ordered custom login server index to be within bounds.
		final List<LoginServer> servers = getLoginServers();
		updatedIndex = getIndexAdjustedToCustomLoginServerBounds(updatedIndex, getSharedPreferences());

		// Update the login server list.
		loginServers.remove(originalIndex);
		loginServers.add(updatedIndex, originalLoginServer);

		// Edit each login server indexed after the updated index.
		final Editor editor = getSharedPreferences().edit();
		for (int i = updatedIndex; i < loginServers.size(); i++) {
			final LoginServer loginServer = loginServers.get(i);
			editor.remove(format(US, SERVER_NAME, i))
					.remove(format(US, SERVER_URL, i))
					.remove(format(US, IS_CUSTOM, i))
					.putString(format(US, SERVER_NAME, i), loginServer.name)
					.putString(format(US, SERVER_URL, i), loginServer.url)
					.putBoolean(format(US, IS_CUSTOM, i), loginServer.isCustom);
		}
		editor.apply();
	}

	/**
	 * Replaces one custom login server with another.
	 *
	 * @param originalCustomLoginServer The original custom login server.  If this is not a custom
	 *                                  login server or doesn't match an existing login server this
	 *                                  method will do nothing.
	 * @param updatedCustomLoginServer  The updated custom login server.  If this is not a custom
	 *                                  login server this method will do nothing.
	 */
	@SuppressWarnings("unused")
	public void replaceCustomLoginServer(
			final LoginServer originalCustomLoginServer,
			final LoginServer updatedCustomLoginServer
	) {
		// Guard against replacing a non-custom login server.
		if (!originalCustomLoginServer.isCustom || !updatedCustomLoginServer.isCustom) {
			return;
		}

		final int originalIndex = getLoginServers().indexOf(originalCustomLoginServer);

		// Guard against an original login server that doesn't exist.
		if (originalIndex == -1) {
			return;
		}

		removeServer(originalCustomLoginServer);
		addCustomLoginServer(updatedCustomLoginServer.name, updatedCustomLoginServer.url);
		reorderCustomLoginServer(getLoginServers().size() - 1, originalIndex);
	}

	/**
	 * Adjusts a login server index to be within the bounds of the custom login servers.
	 *
	 * @param index The login server index
	 * @param sharedPreferences The login server shared preferences
	 * @return The adjusted login server index
	 */
	private int getIndexAdjustedToCustomLoginServerBounds(
			final Integer index,
			final SharedPreferences sharedPreferences
	) {
		// Determine the last non-custom login server index.
		int firstCustomLoginServerIndex = getNextNonCustomLoginServerIndex(sharedPreferences);

		final List<LoginServer> servers = getLoginServers();
		if (index == null) {
			return servers.size();
		} else if (index <= firstCustomLoginServerIndex) {
			return firstCustomLoginServerIndex;
		} else if (index >= servers.size()) {
			return servers.size() - 1;
		} else {
			return index;
		}
	}

	/**
	 * Returns production and sandbox as the login servers
	 * (only called when servers.xml is missing).
	 */
	private List<LoginServer> getLegacyLoginServers() {
		final List<LoginServer> loginServers = new ArrayList<>();
		final LoginServer productionServer = new LoginServer(ctx.getString(R.string.sf__auth_login_production),
				PRODUCTION_LOGIN_URL, false);
		loginServers.add(productionServer);
		final LoginServer sandboxServer = new LoginServer(ctx.getString(R.string.sf__auth_login_sandbox),
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
		if (servers != 0) {
			loginServers = new ArrayList<>();
			final XmlResourceParser xml = ctx.getResources().getXml(serversXmlResourceId);
			int eventType = -1;
			while (eventType != XmlResourceParser.END_DOCUMENT) {
				if (eventType == XmlResourceParser.START_TAG) {
					if (xml.getName().equals("server")) {
						final String name = xml.getAttributeValue(null, "name");
						final String url = xml.getAttributeValue(null, "url");
						final LoginServer loginServer = new LoginServer(name,
								url, false);
						loginServers.add(loginServer);
					}
				}
				try {
					eventType = xml.next();
				} catch (XmlPullParserException | IOException e) {
					SalesforceSDKLogger.w(TAG, "Exception thrown while parsing XML", e);
				}
			}
		}
		return loginServers;
	}

	/**
	 * Returns the next available non-custom login server index which suffixes the non-custom login
	 * servers and prefixes the custom login servers.
	 *
	 * @param sharedPreferences The login server shared preferences
	 * @return The next available non-custom login server index
	 */
	private Integer getNextNonCustomLoginServerIndex(final SharedPreferences sharedPreferences) {
		final List<LoginServer> servers = getLoginServersFromPreferences(sharedPreferences);
		int result = servers.size();
		for (int i = result - 1; i >= 0; i--) {
			if (servers.get(i).isCustom) {
				result = i;
			}
		}
		return result;
	}

	/**
	 * Resets the list of resources login servers from the server.xml. This does not remove the
	 * user's custom login servers.
	 */
	private void initSharedPrefFile() {
		if (isRuntimeConfigAppServiceHostsSet()) {
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
			persistLoginServer(
					curServer.name,
					curServer.url,
					curServer.isCustom,
					settings
			);

			// Set the default login server to the first entry once.
			if (i == 0 && ctx.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE).getAll().isEmpty()) {
				setSelectedLoginServer(curServer);
			}
		}
		edit.apply();
	}

	/**
	 * Persists a login server to the specified shared preferences.
	 *
	 * @param name              The login server name
	 * @param url               The login server URL
	 * @param isCustom          boolean true for non-custom (managed) login servers, false for
	 *                          custom (user-entered) login servers
	 * @param sharedPreferences The shared preferences
	 */
	private void persistLoginServer(final String name,
									final String url,
									final boolean isCustom,
									final SharedPreferences sharedPreferences
	) {
		// Guards.
		if (name == null || url == null) {
			return;
		}

		// Fetch the current number of servers.
		final int numberOfServers = sharedPreferences.getInt(NUMBER_OF_ENTRIES, 0);

		// Adjust the requested index to the bounds of the non-custom (managed) or custom servers.
		Integer adjustedIndex;
		if (isCustom) {
			adjustedIndex = getIndexAdjustedToCustomLoginServerBounds(null, sharedPreferences);
		} else {
			adjustedIndex = getNextNonCustomLoginServerIndex(sharedPreferences);
		}

		final Editor editor = sharedPreferences.edit();

		// Increment existing login servers as needed.
		if (adjustedIndex != null) {
			for (int i = numberOfServers - 1; i >= adjustedIndex; i--) {
				final int incrementedIndex = i + 1;
				final String loginServerNameKey = format(US, SERVER_NAME, i);
				final String loginServerUrlKey = format(US, SERVER_URL, i);
				final String loginServerIsCustomKey = format(US, IS_CUSTOM, i);

				final String loginServerName = sharedPreferences.getString(loginServerNameKey, null);
				final String loginServerUrl = sharedPreferences.getString(loginServerUrlKey, null);
				final boolean loginServerIsCustom = sharedPreferences.getBoolean(loginServerIsCustomKey, false);

				editor
						.remove(loginServerNameKey)
						.remove(loginServerUrlKey)
						.remove(loginServerIsCustomKey)
						.putString(format(US, SERVER_NAME, incrementedIndex), loginServerName)
						.putString(format(US, SERVER_URL, incrementedIndex), loginServerUrl)
						.putBoolean(format(US, IS_CUSTOM, incrementedIndex), loginServerIsCustom);
			}
		}

		// Insert the new login server.
		editor.putString(format(US, SERVER_NAME, adjustedIndex), name.trim());
		editor.putString(format(US, SERVER_URL, adjustedIndex), url.trim());
		editor.putBoolean(format(US, IS_CUSTOM, adjustedIndex), isCustom);

		editor.putInt(NUMBER_OF_ENTRIES, numberOfServers + 1);

		editor.apply();
	}

	/**
	 * Returns the list of all saved servers, including custom servers.
	 *
	 * @param prefs SharedPreferences file.
	 * @return List of all saved servers.
	 */
	private @NonNull List<LoginServer> getLoginServersFromPreferences(final SharedPreferences prefs) {
		int numServers = prefs.getInt(NUMBER_OF_ENTRIES, 0);
		if (numServers == 0) {
			return new ArrayList<>();
		}
		final List<LoginServer> allServers = new ArrayList<>();
		for (int i = 0; i < numServers; i++) {
			final String name = prefs.getString(format(US, SERVER_NAME, i), null);
			final String url = prefs.getString(format(US, SERVER_URL, i), null);
			boolean isCustom = prefs.getBoolean(format(US, IS_CUSTOM, i), false);
			if (name != null && url != null) {
				final LoginServer server = new LoginServer(name, url.trim(), isCustom);
				allServers.add(server);
			}
		}
		return !allServers.isEmpty() ? allServers : new ArrayList<>();
	}

	/**
	 * Resets the list of non-custom login servers in the provided shared preferences.
	 *
	 * @param sharedPreferences The shared preferences
	 */
	private void resetNonCustomLoginServers(
			final SharedPreferences sharedPreferences
	) {
		final List<LoginServer> loginServersFromPreferences = getLoginServersFromPreferences(sharedPreferences);
		for (int i = 0; i < loginServersFromPreferences.size(); i++) {
			final LoginServer loginServer = loginServersFromPreferences.get(i);
			if (!loginServer.isCustom) {
				removeServer(loginServer, sharedPreferences, true);
			}
		}
	}

	/**
	 * Class to encapsulate a login server name, URL, index and type (custom or not).
	 */
	public static class LoginServer {

        @NonNull
		public final String name;
        @NonNull
		public final String url;
		public final boolean isCustom;

		/**
		 * Parameterized constructor.
		 *
		 * @param name Server name.
		 * @param url Server URL.
		 * @param isCustom True - if custom URL, False - otherwise.
		 */
		public LoginServer(@NonNull String name, @NonNull String url, boolean isCustom) {
			this.name = name;
			this.url = url;
			this.isCustom = isCustom;
		}

		@NonNull
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
