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

import java.util.List;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.auth.LoginServerManager.LoginServer;
import com.salesforce.androidsdk.util.EventsListenerQueue;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Tests for LoginServerManager
 *
 */
public class LoginServerManagerTest extends InstrumentationTestCase {

	private static final String PRODUCTION_URL = "https://login.salesforce.com";
	private static final String SANDBOX_URL = "https://test.salesforce.com";
	private static final String OTHER_URL = "https://other.salesforce.com";
	private static final String CUSTOM_NAME = "New";
	private static final String CUSTOM_URL = "https://new.com";
	private static final String CUSTOM_NAME_2 = "New2";
	private static final String CUSTOM_URL_2 = "https://new2.com";
	
	private LoginServerManager loginServerManager;
	private EventsListenerQueue eq;
	private Context targetContext;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		targetContext = getInstrumentation().getTargetContext();
        eq = new EventsListenerQueue();

        // Wait for app initialization to complete.
        Instrumentation.newApplication(TestForceApp.class, targetContext);
        if (ForceApp.APP == null) {
            eq.waitForEvent(EventType.AppCreateComplete, 5000);
        }
        loginServerManager = ForceApp.APP.getLoginServerManager();
    }

    @Override
    public void tearDown() throws Exception {
    	if (loginServerManager != null) {
    		loginServerManager.reset();
    	}
    	if (eq != null) {
            eq.tearDown();
            eq = null;
        }
        ForceApp.APP = null;
        super.tearDown();
    }

    /**
	 * Test for getLoginServerFromURL
	 */
	public void testGetLoginServerFromURL() {
        assertProduction(loginServerManager.getLoginServerFromURL(PRODUCTION_URL));
        assertSandbox(loginServerManager.getLoginServerFromURL(SANDBOX_URL));
        assertOther(loginServerManager.getLoginServerFromURL(OTHER_URL));
		assertNull("Expected null", loginServerManager.getLoginServerFromURL("https://wrong.salesforce.com"));
	}	
	
    
	/**
	 * Test for getDefaultLoginServer
	 */
	public void testGetDefaultLoginServers() {
		List<LoginServer> servers = loginServerManager.getDefaultLoginServers();
		assertEquals("Wrong number of servers", 3, servers.size());
		assertProduction(servers.get(0));
		assertSandbox(servers.get(1));
		assertOther(servers.get(2));
	}

	/**
	 * Test for getSelectedLoginServer/setSelectedLoginServer when there is no custom login server
	 */
	public void testGetSetLoginServerWithoutCustomServer() {
		// Starting point, nothing in prefs, production selected by default
		checkPrefs(null, null, null);
		assertProduction(loginServerManager.getSelectedLoginServer());
		
		// Selecting production
		loginServerManager.setSelectedLoginServer(loginServerManager.getDefaultLoginServers().get(0));
		checkPrefs(null, null, PRODUCTION_URL);
		assertProduction(loginServerManager.getSelectedLoginServer());

		// Selecting sandbox
		loginServerManager.setSelectedLoginServer(loginServerManager.getDefaultLoginServers().get(1));
		checkPrefs(null, null, SANDBOX_URL);
		assertSandbox(loginServerManager.getSelectedLoginServer());
		
		// Selecting other
		loginServerManager.setSelectedLoginServer(loginServerManager.getDefaultLoginServers().get(2));
		assertOther(loginServerManager.getSelectedLoginServer());
	}

	/**
	 * Test for getSelectedLoginServer/setSelectedLoginServer when there is a custom login server
	 */
	public void testGetSetLoginServerWithCustomServer() {
		// Starting point, nothing in prefs, production selected by default
		checkPrefs(null, null, null);
		assertProduction(loginServerManager.getSelectedLoginServer());

		// Setting custom. production should still be selected
		loginServerManager.setCustomLoginServer(CUSTOM_NAME, CUSTOM_URL);
		checkPrefs(CUSTOM_NAME, CUSTOM_URL, null);
		assertProduction(loginServerManager.getSelectedLoginServer());
		
		// Selecting custom
		loginServerManager.setSelectedLoginServer(loginServerManager.getCustomLoginServer());
		checkPrefs(CUSTOM_NAME, CUSTOM_URL, CUSTOM_URL);
		assertCustom(loginServerManager.getSelectedLoginServer());
	}
	
	
	/**
	 * Test for getCustomLoginServer/setCustomLoginServer
	 */
	public void testGetSetCustomLoginServer() {
		// Starting point, nothing in prefs, custom is null
		checkPrefs(null, null, null);
		assertNull("Expected no custom login server", loginServerManager.getCustomLoginServer());

		// Adding custom
		loginServerManager.setCustomLoginServer(CUSTOM_NAME, CUSTOM_URL);
		checkPrefs(CUSTOM_NAME, CUSTOM_URL, null);
		assertCustom(loginServerManager.getCustomLoginServer());
		
		// Changing custom
		loginServerManager.setCustomLoginServer(CUSTOM_NAME_2, CUSTOM_URL_2);
		checkPrefs(CUSTOM_NAME_2, CUSTOM_URL_2, null);
		assertCustom2(loginServerManager.getCustomLoginServer());
	}

	/**
	 * Test for useSandbox
	 */
	public void testUseSandbox() {
		// Starting point, nothing in prefs, production selected by default
		checkPrefs(null, null, null);
		assertEquals("Expected production", PRODUCTION_URL, loginServerManager.getSelectedLoginServer().url);

		// Calling useSandbox
		loginServerManager.useSandbox();
		assertSandbox(loginServerManager.getSelectedLoginServer());
	}
	
	
	/**
	 * Test for setSelectedLoginServerByIndex when there is no custom login server
	 */
	public void testSetSelectedLoginServerByIndexWithoutCustomServer() {
		// Starting point, nothing in prefs, production selected by default
		checkPrefs(null, null, null);
		assertProduction(loginServerManager.getSelectedLoginServer());
		
		// Selecting production
		loginServerManager.setSelectedLoginServerByIndex(0);
		checkPrefs(null, null, PRODUCTION_URL);
		assertProduction(loginServerManager.getSelectedLoginServer());

		// Selecting sandbox
		loginServerManager.setSelectedLoginServerByIndex(1);
		checkPrefs(null, null, SANDBOX_URL);
		assertSandbox(loginServerManager.getSelectedLoginServer());
		
		// Selecting other
		loginServerManager.setSelectedLoginServerByIndex(2);
		assertOther(loginServerManager.getSelectedLoginServer());
	}

	/**
	 * Test for setSelectedLoginServerByIndex when there is a custom login server
	 */
	public void testSetSelectedLoginServerByIndexWithCustomServer() {
		// Starting point, nothing in prefs, production selected by default
		checkPrefs(null, null, null);
		assertProduction(loginServerManager.getSelectedLoginServer());

		// Setting custom. production should still be selected
		loginServerManager.setCustomLoginServer(CUSTOM_NAME, CUSTOM_URL);
		checkPrefs(CUSTOM_NAME, CUSTOM_URL, null);
		assertProduction(loginServerManager.getSelectedLoginServer());
		
		// Selecting custom
		loginServerManager.setSelectedLoginServerByIndex(3);
		checkPrefs(CUSTOM_NAME, CUSTOM_URL, CUSTOM_URL);
		assertCustom(loginServerManager.getSelectedLoginServer());
	}

	
	/**
	 * Test for reset
	 */
	public void testReset() {
		// Starting point, nothing in prefs, custom is null
		checkPrefs(null, null, null);
		assertNull("Expected no custom login server", loginServerManager.getCustomLoginServer());

		// Adding custom
		loginServerManager.setCustomLoginServer(CUSTOM_NAME, CUSTOM_URL);
		checkPrefs(CUSTOM_NAME, CUSTOM_URL, null);
		assertCustom(loginServerManager.getCustomLoginServer());
		
		// Selecting sandbox
		loginServerManager.setSelectedLoginServerByIndex(1);
		checkPrefs(CUSTOM_NAME, CUSTOM_URL, SANDBOX_URL);
		assertSandbox(loginServerManager.getSelectedLoginServer());
		
		// Calling reset - selection should go back to production and prefs should be cleared
		loginServerManager.reset();
		checkPrefs(null, null, null);
		assertNull("Expected no custom login server", loginServerManager.getCustomLoginServer());
		assertProduction(loginServerManager.getSelectedLoginServer());
	}
	
	
	/**
	 * Test for getLoginServersFromXML
	 */
	public void testGetLoginServersFromXML() {
		List<LoginServer> servers = loginServerManager.getLoginServersFromXML();
		assertEquals("Wrong number of servers", 3, servers.size());
		assertProduction(servers.get(0));
		assertSandbox(servers.get(1));
		assertOther(servers.get(2));
	}
	
	
	//
	// Helper methods
	//

	private void assertProduction(LoginServer server) {
		assertEquals("Expected production's name", "Production", server.name);
		assertEquals("Expected production's url", PRODUCTION_URL, server.url);
		assertEquals("Expected production to be index 0", 0, server.index);
		assertEquals("Expected production to be marked as not custom", false, server.isCustom);
	}

	private void assertSandbox(LoginServer server) {
		assertEquals("Expected sandbox's name", "Sandbox", server.name);
		assertEquals("Expected sandbox's url", SANDBOX_URL, server.url);
		assertEquals("Expected sandbox to be index 1", 1, server.index);
		assertEquals("Expected sandbox to be marked as not custom", false, server.isCustom);
	}

	/**
	 * The other login server is only defined in SalesforceSDKTest/res/xml/servers.xml
	 * @param server
	 */
	private void assertOther(LoginServer server) {
		assertEquals("Expected other's name", "Other", server.name);
		assertEquals("Expected other's url", OTHER_URL, server.url);
		assertEquals("Expected other to be index 2", 2, server.index);
		assertEquals("Expected other to be marked as not custom", false, server.isCustom);
	}

	private void assertCustom(LoginServer server) {
		assertEquals("Expected custom's name", CUSTOM_NAME, server.name);
		assertEquals("Expected custom's url", CUSTOM_URL, server.url);
		assertEquals("Expected custom to be index 3", 3, server.index);
		assertEquals("Expected custom to be marked as not custom", true, server.isCustom);
	}	
	
	private void assertCustom2(LoginServer server) {
		assertEquals("Expected custom2's name", CUSTOM_NAME_2, server.name);
		assertEquals("Expected custom2's url", CUSTOM_URL_2, server.url);
		assertEquals("Expected custom2 to be index 3", 3, server.index);
		assertEquals("Expected custom2 to be marked as not custom", true, server.isCustom);
	}		
	
	/**
	 * Check custom label, custom url and current selection preferences
	 * @param customLabel
	 * @param customUrl
	 * @param currentSelection
	 */
	private void checkPrefs(String customLabel, String customUrl, String currentSelection) {
		SharedPreferences settings = targetContext.getSharedPreferences(LoginServerManager.SERVER_URL_PREFS_SETTINGS, Context.MODE_PRIVATE);
		checkPref(settings, LoginServerManager.SERVER_URL_PREFS_CUSTOM_LABEL, customLabel);
		checkPref(settings, LoginServerManager.SERVER_URL_PREFS_CUSTOM_URL, customUrl);
		checkPref(settings, LoginServerManager.SERVER_URL_CURRENT_SELECTION, currentSelection);
	}
	
	/**
	 * Check one preference
	 * @param settings
	 * @param key
	 * @param expectedValue
	 */
	private void checkPref(SharedPreferences settings, String key, String expectedValue) {
		if (expectedValue == null) {
			assertFalse("Expected null value for key " + key, settings.contains(key));
		}
		else {
			assertEquals("Expected value " + expectedValue + " for " + key, expectedValue, settings.getString(key, null));
		}
	}

}
