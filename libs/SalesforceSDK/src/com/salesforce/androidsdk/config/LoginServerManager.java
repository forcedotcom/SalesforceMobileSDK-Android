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
import static com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.OnlyShowAuthorizedHosts;
import static com.salesforce.androidsdk.config.RuntimeConfig.getRuntimeConfig;
import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import static java.lang.String.format;
import static java.util.Locale.US;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources.NotFoundException;
import android.content.res.XmlResourceParser;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.MutableLiveData;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

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
	 * Returns true when {@code serverUrl} is the production login pool:
	 * https://login.salesforce.com or internal variants like https://login.test1.pc-rnd.salesforce.com.
	 * My-domain URLs (containing ".my.") are never production-pool servers.
	 */
	public static boolean isProductionLoginServer(@NonNull String serverUrl) {
		try {
			String host = new java.net.URI(serverUrl).getHost();
			if (host == null) return false;
			return host.startsWith("login.") && host.endsWith(".salesforce.com") && !host.contains(".my.");
		} catch (java.net.URISyntaxException e) {
			return false;
		}
	}

	/**
	 * Returns true when {@code serverUrl} is a My Domain server:
	 * host ends with .my.salesforce.com or contains .my. before .salesforce.com
	 * (covers internal envs like https://mobilesdksdb32.test1.my.pc-rnd.salesforce.com).
	 */
	public static boolean isMyDomainServer(@NonNull String serverUrl) {
		try {
			String host = new java.net.URI(serverUrl).getHost();
			if (host == null) return false;
			return host.endsWith(".salesforce.com") && host.contains(".my.");
		} catch (java.net.URISyntaxException e) {
			return false;
		}
	}

	/** Returns true when {@code serverUrl} is one of the three Salesforce pool (non-my-domain) servers. */
	public static boolean isPoolServer(@NonNull String serverUrl) {
		return isProductionLoginServer(serverUrl)
			|| SANDBOX_LOGIN_URL.equals(serverUrl)
			|| WELCOME_LOGIN_URL.equals(serverUrl);
	}

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

	@VisibleForTesting
	public static final String NUMBER_OF_ENTRIES = "number_of_entries";

	@VisibleForTesting
	public static final String SERVER_NAME = "server_name_%d";

	@VisibleForTesting
	public static final String SERVER_URL = "server_url_%d";

	@VisibleForTesting
	public static final String IS_CUSTOM = "is_custom_%d";

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
		final String selectedServerUrl = selectedServerPrefs.getString(SERVER_URL, null);
		final List<LoginServer> loginServers = getLoginServers();
		LoginServer selectedLoginServer;
		if (loginServers.isEmpty()) {
			selectedLoginServer = new LoginServer(ctx.getString(R.string.sf__auth_login_production),
					PRODUCTION_LOGIN_URL, false);
			persistLoginServer(selectedLoginServer.name, selectedLoginServer.url, false, settings);
			loginServers.add(selectedLoginServer);
		} else {
			selectedLoginServer = loginServers.get(0);
		}
		for (LoginServer server : loginServers) {
			if (selectedServerUrl != null && server.url.equals(selectedServerUrl.trim())) {
				selectedLoginServer = server;
				break;
			}
		}
		if (!selectedLoginServer.equals(selectedServer.getValue())
				|| !selectedLoginServer.name.equals(selectedServerPrefs.getString(SERVER_NAME, null))
				|| !selectedLoginServer.url.equals(selectedServerUrl)
				|| !selectedServerPrefs.contains(IS_CUSTOM)
				|| selectedLoginServer.isCustom != selectedServerPrefs.getBoolean(IS_CUSTOM, false)) {
			setSelectedLoginServer(selectedLoginServer);
		}
		return selectedLoginServer;
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
	 * Adds a custom login server.
	 *
	 * @param name The login server name
	 * @param url The login server URL
	 */
	public void addCustomLoginServer(@NonNull String name, @NonNull String url) {
		// Prevent duplicate servers.
		for (LoginServer existingServer : getLoginServers()) {
			if (url.trim().equals(existingServer.url)) {
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
		getSelectedLoginServer();
		final LoginServer addedServer = getLoginServerFromURL(url.trim());
		if (addedServer != null) {
			setSelectedLoginServer(addedServer);
		}
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
	 * Removes only its owning-store entry. An independently saved duplicate in the other store
	 * may become visible after the higher-precedence entry is removed.
	 *
	 * @param server The server to remove.  If the server is not custom, this method does nothing
	 */
	public void removeServer(final LoginServer server) {
		final SharedPreferences owner = getCustomServerPreferences(server);
		if (owner != null) {
			removeServer(server, owner, false);
		}
	}

	private SharedPreferences getCustomServerPreferences(final LoginServer server) {
		if (server == null || !server.isCustom) {
			return null;
		}
		if (getLoginServersFromPreferences(settings).contains(server)) {
			return settings;
		}
		return getLoginServersFromPreferences(runtimePrefs).contains(server) ? runtimePrefs : null;
	}

	/**
	 * Removes a login server from the list.
	 *
	 * @param server                The server to remove
	 * @param sharedPreferences     The shared preferences to remove the server from
	 * @param allowNonCustomRemoval Boolean true allows the removal of non-custom login servers and
	 *                              false does not
	 */
	@VisibleForTesting
	public void removeServer(
			final LoginServer server,
			final SharedPreferences sharedPreferences,
			final boolean allowNonCustomRemoval
	) {
		final List<LoginServer> servers = getLoginServersFromPreferences(sharedPreferences);

		int index = servers.indexOf(server);
		if (index == -1) {
			return;
		}

		boolean removalAlwaysAllowed = server.isCustom && index != -1;
		if (allowNonCustomRemoval || removalAlwaysAllowed) {
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
	 * Returns current MDM servers first. OnlyShowAuthorizedHosts restricts the list only when
	 * AppServiceHosts contains a nonblank host. Otherwise, resource defaults and custom servers
	 * from settings followed by runtime preferences are included, deduplicated by trimmed URL.
	 * Custom entries retain independent ownership: removing an MDM host does not delete a saved
	 * custom entry for the same URL.
	 *
	 * @return The list of login servers
	 */
	public List<LoginServer> getLoginServers() {
		getLoginServersFromRuntimeConfig();
		final Map<String, LoginServer> servers = new LinkedHashMap<>();
		final List<LoginServer> runtimeServers = getLoginServersFromPreferences(runtimePrefs);
		final List<LoginServer> resourceServers = getLoginServersFromPreferences(settings);
		for (LoginServer server : runtimeServers) {
			if (!server.isCustom) {
				servers.putIfAbsent(server.url, server);
			}
		}
		if (!isRuntimeConfigAppServiceHostsSet()
				|| !Boolean.TRUE.equals(runtimeConfig.getBoolean(OnlyShowAuthorizedHosts))) {
			for (LoginServer server : resourceServers) {
				if (!server.isCustom) {
					servers.putIfAbsent(server.url, server);
				}
			}
			for (LoginServer server : resourceServers) {
				if (server.isCustom) {
					servers.putIfAbsent(server.url, server);
				}
			}
			for (LoginServer server : runtimeServers) {
				if (server.isCustom) {
					servers.putIfAbsent(server.url, server);
				}
			}
		}
		return new ArrayList<>(servers.values());
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
		final String[] hosts = runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts);
		if (hosts != null) {
			for (String host : hosts) {
				if (host != null && !host.trim().isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Resets the list of Mobile Device Management (MDM) login servers from the runtime
	 * configuration. This does not remove the user's custom login servers.
	 *
	 * @return The list of login servers from the runtime configuration, not
	 * including the user's custom login servers
	 */
	@SuppressWarnings("UnusedReturnValue")
    public List<LoginServer> getLoginServersFromRuntimeConfig() {
		final String[] mdmLoginServers = runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts);
		final List<LoginServer> allServers = new ArrayList<>();
		resetNonCustomLoginServers(runtimePrefs);
		if (mdmLoginServers != null) {
			String[] mdmLoginServersLabels = runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels);
			if (mdmLoginServersLabels == null || mdmLoginServersLabels.length != mdmLoginServers.length) {
				SalesforceSDKLogger.w(TAG, "No login servers labels provided or wrong number of login servers labels provided - using URLs for the labels");
				mdmLoginServersLabels = mdmLoginServers;
			}

			for (int i = 0; i < mdmLoginServers.length; i++) {
				if (mdmLoginServers[i] == null || mdmLoginServers[i].trim().isEmpty()) {
					continue;
				}
				final String url = mdmLoginServers[i].trim();
				final String trimmedLabel = mdmLoginServersLabels[i] == null ? "" : mdmLoginServersLabels[i].trim();
				final String name = trimmedLabel.isEmpty() ? url : trimmedLabel;
				if (allServers.stream().anyMatch(server -> server.url.equals(url))) {
					continue;
				}

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
	 * Indices refer to displayed rows and are translated to the server's owning backing store.
	 * Cross-store targets are clamped to that store's displayed custom range; durable ordering
	 * across stores is not supported. Hidden rows in the affected backing interval are preserved.
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
		final List<LoginServer> loginServers = getLoginServers();
		if (originalIndex < 0 || originalIndex >= loginServers.size()) {
			return;
		}
		final LoginServer originalLoginServer = loginServers.get(originalIndex);
		final SharedPreferences owner = getCustomServerPreferences(originalLoginServer);
		if (owner == null) {
			return;
		}
		final List<LoginServer> backingServers = getLoginServersFromPreferences(owner);
		final List<Integer> ownerDisplayedIndices = new ArrayList<>();
		for (int i = 0; i < loginServers.size(); i++) {
			if (loginServers.get(i).isCustom && getCustomServerPreferences(loginServers.get(i)) == owner) {
				ownerDisplayedIndices.add(i);
			}
		}
		updatedIndex = Math.max(ownerDisplayedIndices.get(0),
				Math.min(updatedIndex, ownerDisplayedIndices.get(ownerDisplayedIndices.size() - 1)));
		final int backingOriginalIndex = backingServers.indexOf(originalLoginServer);
		final int backingUpdatedIndex = backingServers.indexOf(loginServers.get(updatedIndex));
		if (backingUpdatedIndex == -1) {
			return;
		}
		backingServers.remove(backingOriginalIndex);
		backingServers.add(backingUpdatedIndex, originalLoginServer);
		final Editor editor = owner.edit();
		for (int i = Math.min(backingOriginalIndex, backingUpdatedIndex);
			 i <= Math.max(backingOriginalIndex, backingUpdatedIndex); i++) {
			final LoginServer loginServer = backingServers.get(i);
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

		final SharedPreferences owner = getCustomServerPreferences(originalCustomLoginServer);
		if (owner == null) {
			return;
		}
		final LoginServer existingServer = getLoginServerFromURL(updatedCustomLoginServer.url.trim());
		if (existingServer != null && !existingServer.equals(originalCustomLoginServer)) {
			removeServer(originalCustomLoginServer, owner, false);
			setSelectedLoginServer(existingServer);
			return;
		}
		final int index = getLoginServersFromPreferences(owner).indexOf(originalCustomLoginServer);
		owner.edit()
				.putString(format(US, SERVER_NAME, index), updatedCustomLoginServer.name.trim())
				.putString(format(US, SERVER_URL, index), updatedCustomLoginServer.url.trim())
				.putBoolean(format(US, IS_CUSTOM, index), true)
				.apply();
		final LoginServer replacement = getLoginServerFromURL(updatedCustomLoginServer.url.trim());
		if (replacement != null) {
			setSelectedLoginServer(replacement);
		} else {
			getSelectedLoginServer();
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
		final List<LoginServer> loginServers = new ArrayList<>();

		XmlResourceParser xml;
		try {
			xml = ctx.getResources().getXml(serversXmlResourceId);
		} catch (NotFoundException e) {
			return loginServers;
		}

		int eventType = -1;
		while (eventType != END_DOCUMENT) {
			if (eventType == START_TAG) {
				if (xml.getName().equals("server")) {
					final String name = xml.getAttributeValue(null, "name");
					final String url = xml.getAttributeValue(null, "url");
					if (name != null && url != null) {
						final LoginServer loginServer = new LoginServer(name, url, false);
						loginServers.add(loginServer);
					}
				}
			}
			try {
				eventType = xml.next();
			} catch (Exception e) {
				SalesforceSDKLogger.w(TAG, "Exception thrown while parsing XML", e);
				break;
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
	private @NonNull Integer getNextNonCustomLoginServerIndex(final SharedPreferences sharedPreferences) {
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
		List<LoginServer> servers = getLoginServersFromXML();
		if (servers.isEmpty()) {
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
			if (i == 0 && !isRuntimeConfigAppServiceHostsSet()
					&& ctx.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE).getAll().isEmpty()) {
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
	private void persistLoginServer(final @NonNull String name,
									final @NonNull String url,
									final boolean isCustom,
									final SharedPreferences sharedPreferences
	) {
		// Fetch the current number of servers.
		final int numberOfServers = sharedPreferences.getInt(NUMBER_OF_ENTRIES, 0);

		// Adjust the requested index to the bounds of the non-custom (managed) or custom servers.
		@SuppressWarnings("WrapperTypeMayBePrimitive")
		@NonNull Integer adjustedIndex;
		if (isCustom) {
			adjustedIndex = numberOfServers;
		} else {
			adjustedIndex = getNextNonCustomLoginServerIndex(sharedPreferences);
		}

		final Editor editor = sharedPreferences.edit();

		// Increment existing login servers as needed.
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
	@VisibleForTesting
	public @NonNull List<LoginServer> getLoginServersFromPreferences(final SharedPreferences prefs) {
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
		return allServers;
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
