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
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.auth.LoginServerManager.LoginServer;
import com.salesforce.androidsdk.util.EventsListenerQueue;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

public class LoginServerManagerTest extends InstrumentationTestCase {

	private LoginServerManager loginServerManager;
	private EventsListenerQueue eq;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		Context targetContext = getInstrumentation().getTargetContext();
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
        if (eq != null) {
            eq.tearDown();
            eq = null;
        }
        ForceApp.APP = null;
        super.tearDown();
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
	 * Test for getLegacyLoginServers
	 */
	public void testGetLegacyLoginServers() {
		List<LoginServer> servers = loginServerManager.getLegacyLoginServers();
		assertEquals("Wrong number of servers", 2, servers.size());
		assertProduction(servers.get(0));
		assertSandbox(servers.get(1));
	}

	/**
	 * Test for getLegacyLoginServers
	 */
	public void testGetLoginServersFromXML() {
		List<LoginServer> servers = loginServerManager.getLoginServersFromXML();
		assertEquals("Wrong number of servers", 3, servers.size());
		assertProduction(servers.get(0));
		assertSandbox(servers.get(1));
		assertOther(servers.get(2));
	}
	
	/**
	 * Test for getLoginServerFromURL
	 */
	public void testGetLoginServerFromURL() {
		assertEquals("Expected production", "Production", loginServerManager.getLoginServerFromURL("https://login.salesforce.com").name);
		assertEquals("Expected production", "Sandbox", loginServerManager.getLoginServerFromURL("https://test.salesforce.com").name);
		assertEquals("Expected production", "Other", loginServerManager.getLoginServerFromURL("https://other.salesforce.com").name);
		assertNull("Expected null", loginServerManager.getLoginServerFromURL("https://wrong.salesforce.com"));
	}	
	
	
	private void assertProduction(LoginServer server) {
		assertEquals("Expected production", "Production", server.name);
		assertEquals("Expected production's login", "https://login.salesforce.com", server.url);
		assertEquals("Expected production to be index 0", 0, server.index);
		assertEquals("Expected production to be marked as not custom", false, server.isCustom);
	}

	private void assertSandbox(LoginServer server) {
		assertEquals("Expected sandbox", "Sandbox", server.name);
		assertEquals("Expected sandbox's login", "https://test.salesforce.com", server.url);
		assertEquals("Expected sandbox to be index 1", 1, server.index);
		assertEquals("Expected sandbox to be marked as not custom", false, server.isCustom);
	}

	/**
	 * The other login server is only defined in SalesforceSDKTest/res/xml/servers.xml
	 * @param server
	 */
	private void assertOther(LoginServer server) {
		assertEquals("Expected other", "Other", server.name);
		assertEquals("Expected other's login", "https://other.salesforce.com", server.url);
		assertEquals("Expected other to be index 2", 2, server.index);
		assertEquals("Expected other to be marked as not custom", false, server.isCustom);
	}

}
