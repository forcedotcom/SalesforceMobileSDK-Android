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
package com.salesforce.androidsdk.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.XmlResourceParser;
import android.util.Log;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.ui.SalesforceR;

/**
 * Class to manage login hosts (default and user entered)
 */
public class LoginServerManager {
	
	// Default login servers
    public static final String PRODUCTION_LOGIN_URL = "https://login.salesforce.com";
    public static final String SANDBOX_LOGIN_URL = "https://test.salesforce.com";
	
	// Key for login servers properties stored in preferences
	public static final String SERVER_URL_PREFS_SETTINGS = "server_url_prefs";
	public static final String SERVER_URL_PREFS_CUSTOM_LABEL = "server_url_custom_label";
	public static final String SERVER_URL_PREFS_CUSTOM_URL = "server_url_custom_url";
	public static final String SERVER_URL_CURRENT_SELECTION = "server_url_current_string";
	
	// Members
	private Context ctx;
	private List<LoginServer> defaultLoginServers;
	private LoginServer customServer;
	private LoginServer selectedServer;
	private SharedPreferences settings;
	
    /**
     * @param ctx
     */
    public LoginServerManager(Context ctx) {
    	this.ctx = ctx;
    	settings = ctx.getSharedPreferences(SERVER_URL_PREFS_SETTINGS, Context.MODE_PRIVATE);
    	
    	// Read default login servers from servers.xml
    	this.defaultLoginServers = getLoginServersFromXML();

    	// If that fails (e.g. if servers.xml is missing), use "legacy" default (i.e. production and sandbox)
    	if (defaultLoginServers == null) {
    		defaultLoginServers = getLegacyLoginServers();
    	}

    	// Look for custom server in pref
		String name = settings.getString(SERVER_URL_PREFS_CUSTOM_LABEL, null);
		String url = settings.getString(SERVER_URL_PREFS_CUSTOM_URL, null);
		if (name != null && url != null) {
			customServer = new LoginServer(name, url, defaultLoginServers.size(), true);
		}
		
		// Look for selected server in pref, select first one if not specified
		String loginUrl = settings.getString(SERVER_URL_CURRENT_SELECTION, null);
		selectedServer = getLoginServerFromURL(loginUrl); 
		if (selectedServer == null) {
			selectedServer = defaultLoginServers.get(0);
		}
    }

    /**
     * @param url
     * @return return matching login server if found or null
     */
    public LoginServer getLoginServerFromURL(String url) {
    	if (url == null) {
    		return null;
    	}
    	
    	if (customServer != null && customServer.url.equals(url)) {
    		return customServer;
    	}
    	
		for (LoginServer server : defaultLoginServers) {
			if (server.url.equals(url)) {
				return server;
			}
		}
		
		return null;
    }
    
    /**
	 * @return list of default login servers
	 */
	public List<LoginServer> getDefaultLoginServers() {
		return Collections.unmodifiableList(defaultLoginServers);
	}
    
    /**
     * @return selected login server
     */
    public LoginServer getSelectedLoginServer() {
    	return selectedServer;
    }
    
    /**
     * @return custom login server
     */
    public LoginServer getCustomLoginServer() {
    	return customServer;
    }

    
    /**
     * Note: server is expected to be customServer or one of defaultLoginServers
     * (LoginServer's constructor is private and therefore only LoginServerManager should construct LoginServer instances)
     * @param server
     */
    public void setSelectedLoginServer(LoginServer server) {
    	selectedServer = server; 
		
    	// Update pref 
        Editor edit = settings.edit();
        edit.putString(SERVER_URL_CURRENT_SELECTION, server.url);
        edit.commit();
    }

    /**
     * Select sandbox as login server (use in tests)
     */
    public void useSandbox() {
    	LoginServer sandboxServer = getLoginServerFromURL(SANDBOX_LOGIN_URL);
    	setSelectedLoginServer(sandboxServer);
    }
    
    /**
     * Select login server by index in the list defaultLoginServers + customServer
     * @param index
     */
    public void setSelectedLoginServerByIndex(int index) {
    	if (index == defaultLoginServers.size() && customServer != null) {
    		setSelectedLoginServer(customServer);
    	}
    	else if (index >= 0 && index < defaultLoginServers.size()) {
    		setSelectedLoginServer(defaultLoginServers.get(index));
    	}
    	else {
    		// Bad index - selecting first
    		setSelectedLoginServer(defaultLoginServers.get(0));
    	}
    }
    
    
	/**
	 * Record custom login server in memory and pref file
	 * @param name
	 * @param url
	 */
	public void setCustomLoginServer(String name, String url) {
		customServer = new LoginServer(name, url, defaultLoginServers.size(), true);
		
		// Update pref
		SharedPreferences.Editor editor = settings.edit();
        editor.putString(SERVER_URL_PREFS_CUSTOM_LABEL, name);
        editor.putString(SERVER_URL_PREFS_CUSTOM_URL, url);
        editor.commit();
	}
	
	/**
	 * Clear custom login server in memory and pref file
	 * Reset selected server to be the first default login server (production)
	 */
	public void reset() {
		selectedServer = defaultLoginServers.get(0);
		customServer = null;

		// Update pref
		SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        editor.commit();
	}

	/**
	 * Return production and sandbox as the login servers (only called when servers.xml is missing)
	 */
	List<LoginServer> getLegacyLoginServers() {
		SalesforceR salesforceR = SalesforceSDKManager.getInstance().getSalesforceR();
		List<LoginServer> loginServers = new ArrayList<LoginServer>();

		int index = 0;
		LoginServer productionServer = new LoginServer(ctx.getString(salesforceR.stringAuthLoginProduction()), PRODUCTION_LOGIN_URL, index++, false);
		loginServers.add(productionServer);
		Log.i("LoginServerManager.getLegacyLoginServers", "Read " + productionServer + " from servers.xml"); 

		LoginServer sandboxServer = new LoginServer(ctx.getString(salesforceR.stringAuthLoginSandbox()), SANDBOX_LOGIN_URL, index++, false);
		loginServers.add(sandboxServer);
		Log.i("LoginServerManager.getLegacyLoginServers", "Read " + sandboxServer + " from servers.xml"); 
	
		return loginServers; 
	}    
    
	/**
	 * @return login servers defined in res/xml/servers.xml or null
	 */
	List<LoginServer> getLoginServersFromXML() {
		List<LoginServer> loginServers = null;
		
		int id = ctx.getResources().getIdentifier("servers", "xml", ctx.getPackageName());
		if (id != 0) {
			loginServers = new ArrayList<LoginServer>();
			XmlResourceParser xml = ctx.getResources().getXml(id);
			int index = 0;
			int eventType = -1;			
			while (eventType != XmlResourceParser.END_DOCUMENT) {
				if (eventType == XmlResourceParser.START_TAG) {
					if (xml.getName().equals("server")) {
						String name = xml.getAttributeValue(null, "name");
						String url = xml.getAttributeValue(null, "url");
						LoginServer loginServer = new LoginServer(name, url, index++, false);
						loginServers.add(loginServer);
						Log.i("LoginServerManager.getLoginServersFromXml", "Read " + loginServer + " from servers.xml"); 
					}
				}
				try {
					eventType = xml.next();
				} catch (XmlPullParserException e) {
					Log.w("LoginServerManager.getLoginServersFromXml", e);
				} catch (IOException e) {
					Log.w("LoginServerManager.getLoginServersFromXml", e);
				}
			}
		}

		return loginServers;
	}
	
	
	/**
	 * Class to encapsulate a login server name, url. index and type (custom or not)
	 *
	 */
	public static class LoginServer {
		public final String name;
		public final String url;
		public final int index;
		public final boolean isCustom;
		
		private LoginServer(String name, String url, int index, boolean isCustom) {
			this.name = name;
			this.url = url;
			this.index = index;
			this.isCustom = isCustom;
		}
		
		@Override 
		public String toString() {
			return index + ": " + name + "[" + url + "] custom:" + isCustom;
		}
	}


}
