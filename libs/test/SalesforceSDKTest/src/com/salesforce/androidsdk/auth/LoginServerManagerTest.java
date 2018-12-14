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
package com.salesforce.androidsdk.auth;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.LoginServerManager;
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Tests for LoginServerManager.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class LoginServerManagerTest {

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

	@Before
	public void setUp() throws Exception {
		targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        eq = new EventsListenerQueue();

        // Wait for app initialization to complete.
        final Application app = Instrumentation.newApplication(TestForceApp.class, targetContext);
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app);
        if (!SalesforceSDKManager.hasInstance()) {
            eq.waitForEvent(EventType.AppCreateComplete, 5000);
        }
        loginServerManager = SalesforceSDKManager.getInstance().getLoginServerManager();
    }

    @After
    public void tearDown() throws Exception {
    	if (loginServerManager != null) {
    		loginServerManager.reset();
    	}
    	if (eq != null) {
            eq.tearDown();
            eq = null;
        }
    }

    /**
	 * Test for getLoginServerFromURL.
	 */
    @Test
	public void testGetLoginServerFromURL() {
        assertProduction(loginServerManager.getLoginServerFromURL(PRODUCTION_URL));
        assertSandbox(loginServerManager.getLoginServerFromURL(SANDBOX_URL));
        assertOther(loginServerManager.getLoginServerFromURL(OTHER_URL));
        Assert.assertNull("Expected null", loginServerManager.getLoginServerFromURL("https://wrong.salesforce.com"));
	}

	/**
	 * Test for getDefaultLoginServer.
	 */
    @Test
	public void testGetDefaultLoginServers() {
		final List<LoginServer> servers = loginServerManager.getLoginServers();
        Assert.assertEquals("Wrong number of servers", 3, servers.size());
		assertProduction(servers.get(0));
		assertSandbox(servers.get(1));
		assertOther(servers.get(2));
	}

	/**
	 * Test for getSelectedLoginServer/setSelectedLoginServer when there is no custom login server.
	 */
    @Test
	public void testGetSetLoginServerWithoutCustomServer() {

		// Starting point, production selected by default.
		assertProduction(loginServerManager.getSelectedLoginServer());

		// Selecting production.
		loginServerManager.setSelectedLoginServer(new LoginServer("Production",
				PRODUCTION_URL, false));
		assertProduction(loginServerManager.getSelectedLoginServer());

		// Selecting sandbox.
		loginServerManager.setSelectedLoginServer(new LoginServer("Sandbox",
				SANDBOX_URL, false));
		assertSandbox(loginServerManager.getSelectedLoginServer());

		// Selecting other.
		loginServerManager.setSelectedLoginServer(new LoginServer("Other",
				OTHER_URL, false));
		assertOther(loginServerManager.getSelectedLoginServer());
	}

	/**
	 * Test for getSelectedLoginServer/setSelectedLoginServer when there is a custom login server.
	 */
    @Test
	public void testGetSetLoginServerWithCustomServer() {

		// Starting point, production selected by default.
		assertProduction(loginServerManager.getSelectedLoginServer());

		// Adding custom server, custom should be selected.
		loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL);
		assertCustom(loginServerManager.getSelectedLoginServer());
	}

	/**
	 * Test for adding more than one custom server.
	 */
    @Test
	public void testAddMultipleCustomServers() {

		// Starting point, only 3 servers.
		List<LoginServer> servers = loginServerManager.getLoginServers();
        Assert.assertEquals("Expected no custom login servers", 3, servers.size());

		// Adding first custom server.
		loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL);
		servers = loginServerManager.getLoginServers();
        Assert.assertEquals("Expected one custom login server", 4, servers.size());

		// Adding second custom server.
		loginServerManager.addCustomLoginServer(CUSTOM_NAME_2, CUSTOM_URL_2);
		servers = loginServerManager.getLoginServers();
        Assert.assertEquals("Expected one custom login server", 5, servers.size());
	}

	/**
	 * Test for getCustomLoginServer/setCustomLoginServer.
	 */
    @Test
	public void testGetSetCustomLoginServer() {

		// Starting point, custom is null.
        Assert.assertNull("Expected no custom login server", loginServerManager.getLoginServerFromURL(CUSTOM_URL));

		// Adding custom server.
		loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL);
		assertCustom(loginServerManager.getSelectedLoginServer());

		// Adding a second custom server.
		loginServerManager.addCustomLoginServer(CUSTOM_NAME_2, CUSTOM_URL_2);
		assertCustom2(loginServerManager.getSelectedLoginServer());
	}

	/**
	 * Test for useSandbox.
	 */
    @Test
	public void testUseSandbox() {

		// Starting point, production selected by default.
		assertProduction(loginServerManager.getSelectedLoginServer());

		// Calling useSandbox.
		loginServerManager.useSandbox();
		assertSandbox(loginServerManager.getSelectedLoginServer());
	}

	/**
	 * Test for reset.
	 */
    @Test
	public void testReset() {

		// Starting point, only 3 servers.
		List<LoginServer> servers = loginServerManager.getLoginServers();
        Assert.assertEquals("Expected no custom login servers", 3, servers.size());

		// Adding custom server.
		loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL);
		servers = loginServerManager.getLoginServers();
        Assert.assertEquals("Expected one custom login server", 4, servers.size());

		// Selecting sandbox.
		loginServerManager.useSandbox();
		assertSandbox(loginServerManager.getSelectedLoginServer());

		/*
		 * Calling reset - selection should go back to production
		 * and custom server should be removed from shared prefs.
		 */
		loginServerManager.reset();
		servers = loginServerManager.getLoginServers();
        Assert.assertEquals("Expected no custom login servers", 3, servers.size());
		assertProduction(loginServerManager.getSelectedLoginServer());
	}

	private void assertProduction(LoginServer server) {
        Assert.assertEquals("Expected production's name", "Production", server.name);
        Assert.assertEquals("Expected production's url", PRODUCTION_URL, server.url);
        Assert.assertEquals("Expected production to be marked as not custom", false, server.isCustom);
	}

	private void assertSandbox(LoginServer server) {
        Assert.assertEquals("Expected sandbox's name", "Sandbox", server.name);
        Assert.assertEquals("Expected sandbox's url", SANDBOX_URL, server.url);
        Assert.assertEquals("Expected sandbox to be marked as not custom", false, server.isCustom);
	}

	private void assertOther(LoginServer server) {
        Assert.assertEquals("Expected other's name", "Other", server.name);
        Assert.assertEquals("Expected other's url", OTHER_URL, server.url);
        Assert.assertEquals("Expected other to be marked as not custom", false, server.isCustom);
	}

	private void assertCustom(LoginServer server) {
        Assert.assertEquals("Expected custom's name", CUSTOM_NAME, server.name);
        Assert.assertEquals("Expected custom's url", CUSTOM_URL, server.url);
        Assert.assertEquals("Expected custom to be marked as not custom", true, server.isCustom);
	}

	private void assertCustom2(LoginServer server) {
        Assert.assertEquals("Expected custom2's name", CUSTOM_NAME_2, server.name);
        Assert.assertEquals("Expected custom2's url", CUSTOM_URL_2, server.url);
        Assert.assertEquals("Expected custom2 to be marked as not custom", true, server.isCustom);
	}
}
