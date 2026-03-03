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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.salesforce.androidsdk.config.LoginServerManager;
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Tests for LoginServerManager.
 * <p>
 * TODO: Each test should include a cold start. ECJ20260302
 * <p>
 * TODO: Default servers from resources server.xml. ECJ20260302
 * <p>
 * TODO: Add server from resources server.xml WITH NO custom servers. ECJ20260302
 * TODO: Update server from resources server.xml WITH NO custom servers. ECJ20260302
 * TODO: Remove server from resources server.xml WITH NO custom servers. ECJ20260302
 * <p>
 * TODO: Add server from resources server.xml WITH custom servers. ECJ20260302
 * TODO: Update server from resources server.xml WITH custom servers. ECJ20260302
 * TODO: Remove server from resources server.xml WITH custom servers. ECJ20260302
 * <p>
 * TODO: Default servers from runtime config. ECJ20260302
 * <p>
 * TODO: Add server from runtime config WITH NO custom servers. ECJ20260302
 * TODO: Update server from runtime config WITH NO custom servers. ECJ20260302
 * TODO: Remove server from runtime config WITH NO custom servers. ECJ20260302
 * <p>
 * TODO: Add server from runtime config WITH custom servers. ECJ20260302
 * TODO: Update server from runtime config WITH custom servers. ECJ20260302
 * TODO: Remove server from runtime config WITH custom servers. ECJ20260302
 * TODO: ECJ20260302
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

    @Rule
    public final InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Before
    public void setUp() throws Exception {
        loginServerManager = new LoginServerManager(getInstrumentation().getTargetContext());
        loginServerManager.reset();
    }

    @After
    public void tearDown() throws Exception {
        loginServerManager.reset();
        loginServerManager = null;
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

    /**
     * Test selectedServer LiveData.
     */
    @Test
    public void testLiveData() {
        // Assert the method returns the same result as the backing LiveData.
        assertLiveData();

        loginServerManager.addCustomLoginServer("live data", PRODUCTION_URL);
        assertLiveData();

        loginServerManager.selectedServer.postValue(new LoginServer("Live Data 2", PRODUCTION_URL, false));
        assertLiveData();
    }

    /**
     * Test removing the last server.
     */
    @Test
    public void testRemoveServer() {
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL);
        int originalServerSize = 4; // 3 default servers + 1 custom
        List<LoginServer> servers = loginServerManager.getLoginServers();
        Assert.assertEquals("Expected one custom login server", originalServerSize, servers.size());
        LoginServer lastServer = servers.get(3);

        // Remove
        loginServerManager.removeServer(lastServer);
        servers = loginServerManager.getLoginServers();
        Assert.assertEquals("", (originalServerSize - 1), servers.size());
        Assert.assertFalse("List should not contain removed server.", servers.contains(lastServer));
    }

    /**
     * Test removing a server in the middle reorders the rest.
     */
    @Test
    public void testRemoveReordersServers() {
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL);
        loginServerManager.addCustomLoginServer(CUSTOM_NAME_2, CUSTOM_URL_2);
        int originalServerSize = 5; // 3 default servers + 2 custom
        List<LoginServer> servers = loginServerManager.getLoginServers();
        Assert.assertEquals("Expected one custom login server", originalServerSize, servers.size());
        LoginServer serverToDelete = servers.get(3);

        // Remove
        loginServerManager.removeServer(serverToDelete);
        servers = loginServerManager.getLoginServers();
        Assert.assertEquals("No servers removed.", (originalServerSize - 1), servers.size());
        Assert.assertFalse("List should not contain removed server.", servers.contains(serverToDelete));

        // Assert Reorder
        assertProduction(servers.get(0));
        assertSandbox(servers.get(1));
        assertOther(servers.get(2));
        assertCustom2(servers.get(3));
    }

    /**
     * Test attempting to remove a non-custom server.
     */
    @Test
    public void testRemoveNonCustomServer() {
        int originalServerSize = 3; // 3 default servers
        List<LoginServer> servers = loginServerManager.getLoginServers();
        Assert.assertEquals("Expected one custom login server", originalServerSize, servers.size());
        LoginServer serverToDelete = servers.get(0);

        // Remove
        loginServerManager.removeServer(serverToDelete);
        servers = loginServerManager.getLoginServers();
        Assert.assertEquals("Servers should not be removed.", originalServerSize, servers.size());
    }

    /**
     * Test attempting to add a duplicate server default or custom server.
     */
    @Test
    public void testAddingDuplicateServers() {
        int originalServerSize = 3; // 3 default servers
        List<LoginServer> servers = loginServerManager.getLoginServers();
        Assert.assertEquals("Expected one custom login server", originalServerSize, servers.size());
        LoginServer prodServer = loginServerManager.getLoginServerFromURL(PRODUCTION_URL);

        // Attempt to add a default server as a custom server.
        loginServerManager.addCustomLoginServer(prodServer.name, prodServer.url);
        Assert.assertEquals("Duplicate server should not be added.", originalServerSize,
                loginServerManager.getLoginServers().size());

        // Attempt to add a duplicate custom server.
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL);
        Assert.assertEquals("Custom server should be added.", (originalServerSize + 1),
                loginServerManager.getLoginServers().size());
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL);
        Assert.assertEquals("Duplicate custom server should not be added.", (originalServerSize + 1),
                loginServerManager.getLoginServers().size());

        // Ensure servers with duplicate names but unique URLs are allowed.
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL_2);
        Assert.assertEquals("Custom server should be added.", (originalServerSize + 2),
                loginServerManager.getLoginServers().size());
        loginServerManager.addCustomLoginServer(prodServer.name, "https://custom3.com");
        Assert.assertEquals("Custom server should be added..", (originalServerSize + 3),
                loginServerManager.getLoginServers().size());
    }

    /**
     * Test both replace and re-order custom login server.
     */
    @Test
    public void testReplaceAndReOrderCustomLoginServer() {

        // Test data.
        final String originalName = "ORIGINAL_CUSTOM_LOGIN_SERVER_FOR_REPLACEMENT_TEST";
        final String originalUrl = "https://original.example.com";
        final LoginServer originalCustomLoginServer = new LoginServer(
                originalName,
                originalUrl,
                true
        );
        final String otherName = "OTHER_CUSTOM_LOGIN_SERVER_FOR_REPLACEMENT_TEST";
        final String otherUrl = "https://other.example.com";
        final LoginServer otherCustomLoginServer = new LoginServer(
                otherName,
                otherUrl,
                true
        );
        final String updatedName = "UPDATED_CUSTOM_LOGIN_SERVER_FOR_REPLACEMENT_TEST";
        final String updatedUrl = "https://updated.example.com";
        final LoginServer updatedCustomLoginServer = new LoginServer(
                updatedName,
                updatedUrl,
                true
        );
        final String nonCustomName = "NON_CUSTOM_LOGIN_SERVER_FOR_REPLACEMENT_TEST";
        final String nonCustomUrl = "https://non.custom.example.com";
        final LoginServer nonCustomLoginServer = new LoginServer(
                nonCustomName,
                nonCustomUrl,
                false
        );

        // Verify the original and other custom login servers are not present.
        Assert.assertFalse(loginServerManager.getLoginServers().contains(originalCustomLoginServer));
        Assert.assertFalse(loginServerManager.getLoginServers().contains(otherCustomLoginServer));


        // Add the original and other custom login server.
        loginServerManager.addCustomLoginServer(originalName, originalUrl);
        loginServerManager.addCustomLoginServer(otherName, otherUrl);

        // Verify the original and other custom login servers were added.
        Assert.assertEquals(originalCustomLoginServer, loginServerManager.getLoginServers().get(loginServerManager.getLoginServers().size() - 2));
        Assert.assertEquals(otherCustomLoginServer, loginServerManager.getLoginServers().get(loginServerManager.getLoginServers().size() - 1));


        // Prepare for negative tests.
        final LoginServer production = new LoginServer("Production", "https://login.salesforce.com", false);
        final LoginServer productionMismatch = new LoginServer("Production?", "https://login.salesforce.com", true);
        final LoginServer productionReplacement = new LoginServer("Production Replaced", "https://login.salesforce.com", false);
        final LoginServer productionReplacementMismatch = new LoginServer("Production Replaced?", "https://login.salesforce.com", true);

        // Attempt the prohibited replacement of a non-custom login server where the original matches.
        loginServerManager.replaceCustomLoginServer(production, productionReplacement);
        Assert.assertTrue(loginServerManager.getLoginServers().contains(production));
        Assert.assertFalse(loginServerManager.getLoginServers().contains(productionReplacement));


        // Attempt the prohibited replacement of a non-custom login server where the original doesn't exit.
        loginServerManager.replaceCustomLoginServer(productionMismatch, productionReplacementMismatch);
        Assert.assertTrue(loginServerManager.getLoginServers().contains(production));
        Assert.assertFalse(loginServerManager.getLoginServers().contains(productionReplacement));


        // Attempt the prohibited reordering of a non-custom login server.
        loginServerManager.reorderCustomLoginServer(0, 1);
        Assert.assertEquals(loginServerManager.getLoginServers().get(0), production);


        // Replace the original custom login server with a non-custom server.
        loginServerManager.replaceCustomLoginServer(originalCustomLoginServer, nonCustomLoginServer);

        // Verify the original and other custom login servers weren't changed.
        Assert.assertFalse(loginServerManager.getLoginServers().contains(nonCustomLoginServer));
        Assert.assertEquals(originalCustomLoginServer, loginServerManager.getLoginServers().get(loginServerManager.getLoginServers().size() - 2));
        Assert.assertEquals(otherCustomLoginServer, loginServerManager.getLoginServers().get(loginServerManager.getLoginServers().size() - 1));


        // Replace the original custom login server.
        loginServerManager.replaceCustomLoginServer(originalCustomLoginServer, updatedCustomLoginServer);

        // Verify the original custom login server is not present.
        Assert.assertFalse(loginServerManager.getLoginServers().contains(originalCustomLoginServer));

        // Verify the updated and other custom login servers are present.
        Assert.assertEquals(updatedCustomLoginServer, loginServerManager.getLoginServers().get(loginServerManager.getLoginServers().size() - 2));
        Assert.assertEquals(otherCustomLoginServer, loginServerManager.getLoginServers().get(loginServerManager.getLoginServers().size() - 1));

        // Attempt to move the updated custom login server above the non-custom login servers.
        loginServerManager.reorderCustomLoginServer(loginServerManager.getLoginServers().indexOf(updatedCustomLoginServer), 0);

        // Verify the updated custom login server is actually immediately following the last non-custom login server.
        final List<LoginServer> loginServers = loginServerManager.getLoginServers();
        int lastNonCustomIndex = -1;
        for (int i = 0; i < loginServers.size(); i++) {
            final LoginServer loginServer = loginServers.get(i);
            if (!loginServer.isCustom) {
                lastNonCustomIndex = i;
            }
        }
        Assert.assertEquals(loginServers.get(lastNonCustomIndex + 1), updatedCustomLoginServer);


        // Attempt to move the updated custom login server one greater than the upper bounds of the login servers list.
        loginServerManager.reorderCustomLoginServer(loginServerManager.getLoginServers().indexOf(updatedCustomLoginServer), loginServerManager.getLoginServers().size());

        // Attempt to move the updated custom login server more than one greater than the upper bounds of the login servers list.
        loginServerManager.reorderCustomLoginServer(loginServerManager.getLoginServers().indexOf(updatedCustomLoginServer), loginServerManager.getLoginServers().size() + 1);

        // Attempt to move the updated custom login server more than one less than the upper bounds of the login servers list.
        loginServerManager.reorderCustomLoginServer(loginServerManager.getLoginServers().indexOf(updatedCustomLoginServer), loginServerManager.getLoginServers().size() - 1);

        // Verify the updated custom login server is now the last login server in the list.
        Assert.assertEquals(loginServerManager.getLoginServers().getLast(), updatedCustomLoginServer);
    }

    private void assertProduction(LoginServer server) {
        Assert.assertEquals("Expected production's name", "Production", server.name);
        Assert.assertEquals("Expected production's url", PRODUCTION_URL, server.url);
        Assert.assertFalse("Expected production to be marked as not custom", server.isCustom);
    }

    private void assertSandbox(LoginServer server) {
        Assert.assertEquals("Expected sandbox's name", "Sandbox", server.name);
        Assert.assertEquals("Expected sandbox's url", SANDBOX_URL, server.url);
        Assert.assertFalse("Expected sandbox to be marked as not custom", server.isCustom);
    }

    private void assertOther(LoginServer server) {
        Assert.assertEquals("Expected other's name", "Other", server.name);
        Assert.assertEquals("Expected other's url", OTHER_URL, server.url);
        Assert.assertFalse("Expected other to be marked as not custom", server.isCustom);
    }

    private void assertCustom(LoginServer server) {
        Assert.assertEquals("Expected custom's name", CUSTOM_NAME, server.name);
        Assert.assertEquals("Expected custom's url", CUSTOM_URL, server.url);
        Assert.assertTrue("Expected custom to be marked as not custom", server.isCustom);
    }

    private void assertCustom2(LoginServer server) {
        Assert.assertEquals("Expected custom2's name", CUSTOM_NAME_2, server.name);
        Assert.assertEquals("Expected custom2's url", CUSTOM_URL_2, server.url);
        Assert.assertTrue("Expected custom2 to be marked as not custom", server.isCustom);
    }

    private void assertLiveData() {
        Assert.assertEquals(loginServerManager.getSelectedLoginServer(), loginServerManager.selectedServer.getValue());
    }
}
