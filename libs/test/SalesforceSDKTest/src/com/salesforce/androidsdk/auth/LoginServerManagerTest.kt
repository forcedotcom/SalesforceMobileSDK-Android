/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.auth

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.content.res.Resources
import android.content.res.XmlResourceParser
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.salesforce.androidsdk.R.string.sf__auth_login_production
import com.salesforce.androidsdk.R.string.sf__auth_login_sandbox
import com.salesforce.androidsdk.config.LoginServerManager
import com.salesforce.androidsdk.config.LoginServerManager.IS_CUSTOM
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.config.LoginServerManager.NUMBER_OF_ENTRIES
import com.salesforce.androidsdk.config.LoginServerManager.RUNTIME_PREFS_FILE
import com.salesforce.androidsdk.config.LoginServerManager.SERVER_NAME
import com.salesforce.androidsdk.config.LoginServerManager.SERVER_SELECTION_FILE
import com.salesforce.androidsdk.config.LoginServerManager.SERVER_URL
import com.salesforce.androidsdk.config.LoginServerManager.SERVER_URL_FILE
import com.salesforce.androidsdk.config.RuntimeConfig
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.AppServiceHostLabels
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.AppServiceHosts
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.OnlyShowAuthorizedHosts
import com.salesforce.androidsdk.tests.R.xml.servers
import com.salesforce.androidsdk.tests.R.xml.servers_nulls
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParserException

@RunWith(AndroidJUnit4::class)
@SmallTest
class LoginServerManagerMockTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private fun manager(runtimeConfig: RuntimeConfig): LoginServerManager {
        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().context.resources
        every { context.getString(sf__auth_login_production) } returns getInstrumentation().targetContext.getString(sf__auth_login_production)
        every { context.getSharedPreferences(any(), any()) } answers {
            getInstrumentation().targetContext.getSharedPreferences(firstArg(), MODE_PRIVATE)
        }
        return LoginServerManager(context, runtimeConfig, servers).also { loginServerManager = it }
    }

    private fun prefs(file: String) = getInstrumentation().targetContext.getSharedPreferences(file, MODE_PRIVATE)

    @Test
    fun test_givenEmptyLoginStoresAndStaleSelection_whenSelected_thenProductionPersistedWithoutDuplicates() {
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns emptyArray()
        every { runtimeConfig.getBoolean(OnlyShowAuthorizedHosts) } returns true
        val manager = manager(runtimeConfig)
        manager.setSelectedLoginServer(LoginServer("Stale", "https://stale.example.com", true))
        prefs(SERVER_URL_FILE).edit().clear().commit()
        prefs(RUNTIME_PREFS_FILE).edit().clear().commit()
        assertEquals(emptyList<LoginServer>(), manager.loginServers)
        val production = LoginServer(getInstrumentation().targetContext.getString(sf__auth_login_production), LoginServerManager.PRODUCTION_LOGIN_URL, false)

        val selected = runCatching { manager.selectedLoginServer }
        assertEquals("Empty stores must select production: ${selected.exceptionOrNull()}", production, selected.getOrNull())
        assertEquals(production, manager.selectedServer.value)
        assertEquals(mapOf(SERVER_NAME to production.name, SERVER_URL to production.url, IS_CUSTOM to false), prefs(SERVER_SELECTION_FILE).all)
        repeat(3) {
            assertEquals(production, manager.selectedLoginServer)
            assertEquals(listOf(production), manager.loginServers)
            assertEquals(listOf(production), manager.loginServersFromPreferences)
            assertEquals(1, prefs(SERVER_URL_FILE).getInt(NUMBER_OF_ENTRIES, 0))
            assertEquals(emptyList<LoginServer>(), manager.getLoginServersFromPreferences(prefs(RUNTIME_PREFS_FILE)))
        }
    }

    @Test
    fun test_givenUnrestrictedMdm_whenComposed_thenMdmDefaultsAndBothCustomStores() {
        loginServerManager?.addCustomLoginServer("Settings", "https://settings.example.com")
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm.example.com")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM")
        val manager = manager(runtimeConfig)
        manager.addCustomLoginServer("Runtime", "https://runtime.example.com")
        assertEquals(listOf("MDM", "Production", "Sandbox", "Other", "Settings", "Runtime"), manager.loginServers.map { it.name })
        assertEquals(2, prefs(RUNTIME_PREFS_FILE).getInt(NUMBER_OF_ENTRIES, 0))
        assertEquals(4, prefs(SERVER_URL_FILE).getInt(NUMBER_OF_ENTRIES, 0))
        assertEquals("https://runtime.example.com", prefs(RUNTIME_PREFS_FILE).getString(String.format(SERVER_URL, 1), null))
    }

    @Test
    fun test_givenFreshMdm_whenInitialized_thenDefaultsExistButFirstMdmSelected() {
        prefs(SERVER_SELECTION_FILE).edit().clear().commit()
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm.example.com")
        val manager = manager(runtimeConfig)
        assertEquals("https://mdm.example.com", manager.selectedLoginServer.url)
        assertEquals(3, manager.loginServersFromPreferences.size)
    }

    @Test
    fun test_givenUsableMdm_whenResetAndPayloadReordered_thenGetterPersistsSelectionAcrossRecreation() {
        prefs(SERVER_SELECTION_FILE).edit().clear().commit()
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        var hosts = arrayOf("https://mdm1.example.com", "https://mdm2.example.com")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } answers { hosts }
        val manager = manager(runtimeConfig)
        val selected = manager.selectedLoginServer
        manager.reset()
        assertEquals(emptyMap<String, Any>(), prefs(SERVER_SELECTION_FILE).all)
        assertEquals(selected, manager.selectedServer.value)
        assertEquals(selected, manager.selectedLoginServer)
        assertEquals("Getter must persist selection even when LiveData is unchanged", selected.url,
            prefs(SERVER_SELECTION_FILE).getString(SERVER_URL, null))
        assertEquals(selected.name, prefs(SERVER_SELECTION_FILE).getString(SERVER_NAME, null))
        assertEquals(false, prefs(SERVER_SELECTION_FILE).getBoolean(IS_CUSTOM, true))
        hosts = hosts.reversedArray()
        val reloaded = manager(runtimeConfig)
        assertEquals("https://mdm2.example.com", reloaded.loginServers.first().url)
        assertEquals(selected, reloaded.selectedLoginServer)
        assertEquals(selected, reloaded.selectedServer.value)
    }

    @Test
    fun test_givenUnchangedLiveData_whenStoredSelectionIncompleteOrDifferent_thenCanonicalPrefsRepairedOnlyAsNeeded() {
        prefs(SERVER_SELECTION_FILE).edit().clear().commit()
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm.example.com")
        val manager = manager(runtimeConfig)
        val selected = manager.selectedLoginServer
        val selectionPrefs = prefs(SERVER_SELECTION_FILE)
        val corruptions: List<(SharedPreferences.Editor) -> Unit> = listOf(
            { it.remove(SERVER_NAME) },
            { it.remove(SERVER_URL) },
            { it.remove(IS_CUSTOM) },
            { it.putString(SERVER_NAME, "Stale") },
            { it.putString(SERVER_URL, " ${selected.url} ") },
            { it.putBoolean(IS_CUSTOM, true) }
        )
        for (corrupt in corruptions) {
            selectionPrefs.edit().also(corrupt).commit()
            assertEquals(selected, manager.selectedServer.value)
            assertEquals(selected, manager.selectedLoginServer)
            assertEquals(mapOf(SERVER_NAME to selected.name, SERVER_URL to selected.url, IS_CUSTOM to false), selectionPrefs.all)
        }
        var notifications = 0
        val observer = Observer<LoginServer> { notifications++ }
        manager.selectedServer.observeForever(observer)
        try {
            val initialNotifications = notifications
            repeat(3) { assertEquals(selected, manager.selectedLoginServer) }
            assertEquals("Canonical selection must not be rewritten/published on every getter", initialNotifications, notifications)
        } finally {
            manager.selectedServer.removeObserver(observer)
        }
    }

    @Test
    fun test_givenDuplicateSelectedCustom_whenMdmChanges_thenCanonicalSelectionAndIndependentOwnership() {
        loginServerManager?.addCustomLoginServer("Saved", "https://saved.example.com")
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        var hosts: Array<String>? = arrayOf(" https://saved.example.com ", "https://saved.example.com")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } answers { hosts }
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("Managed", "Duplicate")
        val manager = manager(runtimeConfig)
        assertEquals(LoginServer("Managed", "https://saved.example.com", false), manager.selectedLoginServer)
        assertEquals(4, manager.loginServers.size)
        hosts = arrayOf("https://new.example.com")
        assertEquals(listOf("https://new.example.com", LoginServerManager.PRODUCTION_LOGIN_URL, LoginServerManager.SANDBOX_LOGIN_URL, "https://other.salesforce.com", "https://saved.example.com"), manager.loginServers.map { it.url })
        assertEquals(LoginServer("Saved", "https://saved.example.com", true), manager.selectedLoginServer)
        hosts = null
        assertEquals(4, manager.loginServers.size)
        assertEquals(0, manager.getLoginServersFromPreferences(prefs(RUNTIME_PREFS_FILE)).size)
    }

    @Test
    fun test_givenRestrictionChanges_whenProjected_thenHiddenCustomEntriesSurvive() {
        loginServerManager?.addCustomLoginServer("Saved", "https://saved.example.com")
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm.example.com")
        var restricted = false
        every { runtimeConfig.getBoolean(OnlyShowAuthorizedHosts) } answers { restricted }
        val manager = manager(runtimeConfig)
        manager.addCustomLoginServer("Runtime", "https://runtime.example.com")
        restricted = true
        assertEquals(listOf("https://mdm.example.com"), manager.loginServers.map { it.url })
        restricted = false
        assertEquals(6, manager.loginServers.size)
    }

    @Test
    fun test_givenEmptyRestrictedPayload_whenSelected_thenDefaultsAndRememberedSelectionSurvive() {
        loginServerManager?.addCustomLoginServer("Saved", "https://saved.example.com")
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        var hosts: Array<String>? = emptyArray()
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } answers { hosts }
        every { runtimeConfig.getBoolean(OnlyShowAuthorizedHosts) } returns true
        val manager = manager(runtimeConfig)
        assertEquals(listOf("Production", "Sandbox", "Other", "Saved"), manager.loginServers.map { it.name })
        assertEquals(LoginServer("Saved", "https://saved.example.com", true), manager.selectedLoginServer)
        assertEquals(manager.selectedLoginServer, manager.selectedServer.value)
        assertEquals("https://saved.example.com", prefs(SERVER_SELECTION_FILE).getString(SERVER_URL, null))
        hosts = arrayOf("https://saved.example.com")
        assertEquals("https://saved.example.com", manager.selectedLoginServer.url)
        hosts = emptyArray()
        assertEquals(LoginServer("Saved", "https://saved.example.com", true), manager.selectedLoginServer)
        assertEquals(manager.selectedLoginServer, manager.selectedServer.value)
        assertEquals(emptyList<LoginServer>(), manager.getLoginServersFromPreferences(prefs(RUNTIME_PREFS_FILE)))
        hosts = null
        assertEquals(listOf("Production", "Sandbox", "Other", "Saved"), manager.loginServers.map { it.name })
        assertEquals("https://saved.example.com", manager.selectedLoginServer.url)
    }

    @Test
    fun test_givenBlankRestrictedPayload_whenInitialized_thenDefaultsSelectedAndNewCustomUsesSettings() {
        prefs(SERVER_SELECTION_FILE).edit().clear().commit()
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf(null, "", " \t ")
        every { runtimeConfig.getBoolean(OnlyShowAuthorizedHosts) } returns true
        val manager = manager(runtimeConfig)
        assertEquals(listOf("Production", "Sandbox", "Other"), manager.loginServers.map { it.name })
        assertEquals(LoginServer("Production", LoginServerManager.PRODUCTION_LOGIN_URL, false), manager.selectedLoginServer)
        assertEquals(manager.selectedLoginServer, manager.selectedServer.value)
        manager.addCustomLoginServer("Saved", "https://saved.example.com")
        assertEquals("Saved", manager.selectedLoginServer.name)
        assertEquals(listOf("Saved"), manager.loginServersFromPreferences.filter { it.isCustom }.map { it.name })
        assertEquals(emptyList<LoginServer>(), manager.getLoginServersFromPreferences(prefs(RUNTIME_PREFS_FILE)))
    }

    @Test
    fun test_givenAbsentHostsAndRestrictionFlag_whenInitialized_thenDefaultsAndBothCustomStoresRemainAvailable() {
        loginServerManager?.addCustomLoginServer("Settings", "https://settings.example.com")
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        var hosts: Array<String>? = arrayOf("https://mdm.example.com")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } answers { hosts }
        val manager = manager(runtimeConfig)
        manager.addCustomLoginServer("Runtime", "https://runtime.example.com")
        hosts = null
        every { runtimeConfig.getBoolean(OnlyShowAuthorizedHosts) } returns true
        assertEquals(listOf("Production", "Sandbox", "Other", "Settings", "Runtime"), manager.loginServers.map { it.name })
        assertEquals("Runtime", manager.selectedLoginServer.name)
        prefs(SERVER_SELECTION_FILE).edit().clear().commit()
        val reloaded = manager(runtimeConfig)
        assertEquals("Production", reloaded.selectedLoginServer.name)
        assertEquals("Production", reloaded.selectedServer.value?.name)
    }

    @Test
    fun test_givenMergedStores_whenReplacingOntoExistingCustomOrDefault_thenOriginalRemovedAndUnrelatedRowsPersist() {
        loginServerManager?.addCustomLoginServer("Saved", "https://saved.example.com")
        loginServerManager?.addCustomLoginServer("Unrelated", "https://unrelated.example.com")
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm.example.com")
        val manager = manager(runtimeConfig)
        manager.addCustomLoginServer("A", "https://a.example.com")
        manager.addCustomLoginServer("B", "https://b.example.com")
        manager.addCustomLoginServer("C", "https://c.example.com")
        val settingsBefore = prefs(SERVER_URL_FILE).all
        manager.replaceCustomLoginServer(manager.loginServers.first { it.name == "A" }, LoginServer("Ignored", " https://saved.example.com ", true))
        assertEquals(LoginServer("Saved", "https://saved.example.com", true), manager.selectedLoginServer)
        assertEquals(listOf("B", "C"), manager.getLoginServersFromPreferences(prefs(RUNTIME_PREFS_FILE)).filter { it.isCustom }.map { it.name })
        manager.replaceCustomLoginServer(manager.loginServers.first { it.name == "B" }, LoginServer("Ignored", LoginServerManager.PRODUCTION_LOGIN_URL, true))
        assertEquals(LoginServer("Production", LoginServerManager.PRODUCTION_LOGIN_URL, false), manager.selectedLoginServer)
        assertEquals(settingsBefore, prefs(SERVER_URL_FILE).all)
        val reloaded = manager(runtimeConfig)
        assertEquals(listOf(LoginServer("C", "https://c.example.com", true)), reloaded.getLoginServersFromPreferences(prefs(RUNTIME_PREFS_FILE)).filter { it.isCustom })
        assertEquals(listOf("Saved", "Unrelated"), reloaded.loginServersFromPreferences.filter { it.isCustom }.map { it.name })
        assertEquals("https://mdm.example.com", reloaded.loginServers.first().url)
        assertEquals("Production", reloaded.selectedLoginServer.name)
    }

    @Test
    fun test_givenDuplicateCustomStores_whenDisplayedOwnerRemoved_thenLowerPrecedenceOwnerIsRevealed() {
        val url = "https://duplicate.example.com"
        loginServerManager?.addCustomLoginServer("Settings owner", url)
        loginServerManager?.addCustomLoginServer("Settings unrelated", "https://settings.example.com")
        // Seed a legacy duplicate that addCustomLoginServer correctly refuses to create today.
        prefs(RUNTIME_PREFS_FILE).edit()
            .putInt(NUMBER_OF_ENTRIES, 2)
            .putString(String.format(SERVER_NAME, 0), "Runtime owner")
            .putString(String.format(SERVER_URL, 0), url)
            .putBoolean(String.format(IS_CUSTOM, 0), true)
            .putString(String.format(SERVER_NAME, 1), "Runtime unrelated")
            .putString(String.format(SERVER_URL, 1), "https://runtime.example.com")
            .putBoolean(String.format(IS_CUSTOM, 1), true).commit()
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm.example.com")
        val manager = manager(runtimeConfig)
        val runtimeBefore = prefs(RUNTIME_PREFS_FILE).all
        assertEquals("Settings owner", manager.getLoginServerFromURL(url).name)
        manager.removeServer(manager.getLoginServerFromURL(url))
        assertEquals(runtimeBefore, prefs(RUNTIME_PREFS_FILE).all)
        val reloaded = manager(runtimeConfig)
        assertEquals(LoginServer("Runtime owner", url, true), reloaded.getLoginServerFromURL(url))
        assertEquals(listOf("Settings unrelated"), reloaded.loginServersFromPreferences.filter { it.isCustom }.map { it.name })
        reloaded.removeServer(reloaded.getLoginServerFromURL(url))
        assertNull(manager(runtimeConfig).getLoginServerFromURL(url))
        assertEquals(listOf("Runtime unrelated"), manager.getLoginServersFromPreferences(prefs(RUNTIME_PREFS_FILE)).filter { it.isCustom }.map { it.name })
    }

    @Test
    fun test_givenNullBlankAndNonblankMdmLabels_whenParsed_thenFallbackAndTrimStayPairedWithHosts() {
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf(
            null, " https://null.example.com ", " https://empty.example.com ",
            " https://whitespace.example.com ", " ", " https://padded.example.com ",
            " https://nonblank.example.com "
        )
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf(
            "Skipped null host", null, "", " \t\n ", "Skipped blank host", " \tManaged Label \n ", "Real Label"
        )
        val manager = manager(runtimeConfig)
        val expected = listOf(
            LoginServer("https://null.example.com", "https://null.example.com", false),
            LoginServer("https://empty.example.com", "https://empty.example.com", false),
            LoginServer("https://whitespace.example.com", "https://whitespace.example.com", false),
            LoginServer("Managed Label", "https://padded.example.com", false),
            LoginServer("Real Label", "https://nonblank.example.com", false)
        )
        assertEquals(expected, manager.loginServersFromRuntimeConfig)
        assertEquals(expected, manager.getLoginServersFromPreferences(prefs(RUNTIME_PREFS_FILE)))
        assertEquals(expected, manager.loginServers.take(expected.size))
    }

    @Test
    fun test_givenNullMdmElements_whenParsed_thenLabelsStayPaired() {
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf(null, " https://mdm.example.com ", " ")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("Skipped", null, "Blank")
        val manager = manager(runtimeConfig)
        assertEquals(LoginServer("https://mdm.example.com", "https://mdm.example.com", false), manager.loginServers.first())
        assertEquals(4, manager.loginServers.size)
    }

    @Test
    fun test_givenMergedStores_whenReorderedReplacedRemoved_thenOnlyOwnerStoreChanges() {
        loginServerManager?.addCustomLoginServer("A", "https://a.example.com")
        loginServerManager?.addCustomLoginServer("Hidden", "https://hidden.example.com")
        loginServerManager?.addCustomLoginServer("B", "https://b.example.com")
        loginServerManager?.addCustomLoginServer("C", "https://c.example.com")
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://hidden.example.com")
        val manager = manager(runtimeConfig)
        manager.addCustomLoginServer("D", "https://d.example.com")
        manager.addCustomLoginServer("E", "https://e.example.com")
        manager.reorderCustomLoginServer(manager.loginServers.indexOfFirst { it.name == "A" }, manager.loginServers.indexOfFirst { it.name == "B" })
        assertEquals(listOf("Hidden", "B", "A", "C"), manager.loginServersFromPreferences.filter { it.isCustom }.map { it.name })
        manager.reorderCustomLoginServer(manager.loginServers.indexOfFirst { it.name == "A" }, manager.loginServers.lastIndex)
        assertEquals(listOf("Hidden", "B", "C", "A"), manager.loginServersFromPreferences.filter { it.isCustom }.map { it.name })
        manager.reorderCustomLoginServer(manager.loginServers.indexOfFirst { it.name == "E" }, 0)
        assertEquals(listOf("E", "D"), manager.getLoginServersFromPreferences(prefs(RUNTIME_PREFS_FILE)).filter { it.isCustom }.map { it.name })
        val e = manager.loginServers.first { it.name == "E" }
        manager.replaceCustomLoginServer(e, LoginServer("F", "https://f.example.com", true))
        assertEquals(listOf("F", "D"), manager.getLoginServersFromPreferences(prefs(RUNTIME_PREFS_FILE)).filter { it.isCustom }.map { it.name })
        manager.removeServer(manager.loginServers.first { it.name == "F" })
        assertEquals(listOf("D"), manager.getLoginServersFromPreferences(prefs(RUNTIME_PREFS_FILE)).filter { it.isCustom }.map { it.name })
        assertEquals(7, prefs(SERVER_URL_FILE).getInt(NUMBER_OF_ENTRIES, 0))
        val reloaded = manager(runtimeConfig)
        assertEquals(listOf("Hidden", "B", "C", "A"), reloaded.loginServersFromPreferences.filter { it.isCustom }.map { it.name })
        assertEquals(listOf("D"), reloaded.getLoginServersFromPreferences(prefs(RUNTIME_PREFS_FILE)).filter { it.isCustom }.map { it.name })
    }

    private var loginServerManager: LoginServerManager? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        loginServerManager = LoginServerManager(getInstrumentation().targetContext)
        loginServerManager?.reset()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        loginServerManager?.reset()
        loginServerManager = null
    }

    /**
     * Test for testGetLoginServersFromRuntimeConfigWhenRuntimeConfigHasNull.
     */
    @Test
    fun testGetLoginServersFromRuntimeConfigWhenRuntimeConfigHasNull() {
        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns null
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val servers = loginServerManager?.loginServersFromRuntimeConfig

        assertNull(servers)
    }

    /**
     * Test for testGetRuntimeConfigLoginServers.
     */
    @Test
    fun testGetRuntimeConfigLoginServers() {
        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 5, servers?.size)
        assertEquals("MDM 1", servers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", servers?.get(0)?.url)
        assertEquals(false, servers?.get(0)?.isCustom)
        assertEquals("MDM 2", servers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", servers?.get(1)?.url)
        assertEquals(false, servers?.get(1)?.isCustom)

        assertEquals("Production", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals(LoginServerManager.PRODUCTION_LOGIN_URL, loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)
    }

    /**
     * Test for testGetRuntimeConfigLoginServersWithoutLabels.
     */
    @Test
    fun testGetRuntimeConfigLoginServersWithoutLabels() {
        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 5, servers?.size)
        assertEquals(servers?.get(0)?.url, servers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", servers?.get(0)?.url)
        assertEquals(false, servers?.get(0)?.isCustom)
        assertEquals(servers?.get(1)?.url, servers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", servers?.get(1)?.url)
        assertEquals(false, servers?.get(1)?.isCustom)

        assertEquals("Production", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals(LoginServerManager.PRODUCTION_LOGIN_URL, loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)
    }

    /**
     * Test for testGetRuntimeConfigLoginServersWithoutIncorrectLabelCount.
     */
    @Test
    fun testGetRuntimeConfigLoginServersWithoutIncorrectLabelCount() {
        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 5, servers?.size)
        assertEquals(servers?.get(0)?.url, servers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", servers?.get(0)?.url)
        assertEquals(false, servers?.get(0)?.isCustom)
        assertEquals(servers?.get(1)?.url, servers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", servers?.get(1)?.url)
        assertEquals(false, servers?.get(1)?.isCustom)

        assertEquals("Production", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals(LoginServerManager.PRODUCTION_LOGIN_URL, loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)
    }

    /**
     * Test for testAddRuntimeConfigLoginServers.
     */
    @Test
    fun testAddRuntimeConfigLoginServers() {
        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        var runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        var loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 5, loginServers?.size)
        assertEquals("MDM 1", loginServers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
        assertEquals("MDM 2", loginServers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", loginServers?.get(1)?.url)
        assertEquals(false, loginServers?.get(1)?.isCustom)

        assertEquals("Production", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals(LoginServerManager.PRODUCTION_LOGIN_URL, loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)

        runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm1.example.com/1/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 1.1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 6, loginServers?.size)
        assertEquals("MDM 1", loginServers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
        assertEquals("MDM 1.1", loginServers?.get(1)?.name)
        assertEquals("https://mdm1.example.com/1/1", loginServers?.get(1)?.url)
        assertEquals(false, loginServers?.get(1)?.isCustom)
        assertEquals("MDM 2", loginServers?.get(2)?.name)
        assertEquals("https://mdm2.example.com/2", loginServers?.get(2)?.url)
        assertEquals(false, loginServers?.get(2)?.isCustom)

        assertEquals("Production", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals(LoginServerManager.PRODUCTION_LOGIN_URL, loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)
    }

    /**
     * Test for testUpdateRuntimeConfigLoginServers.
     */
    @Test
    fun testUpdateRuntimeConfigLoginServers() {
        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        var runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm1.example.com/1/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 1.1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        var loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 6, loginServers?.size)
        assertEquals("MDM 1", loginServers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
        assertEquals("MDM 1.1", loginServers?.get(1)?.name)
        assertEquals("https://mdm1.example.com/1/1", loginServers?.get(1)?.url)
        assertEquals(false, loginServers?.get(1)?.isCustom)
        assertEquals("MDM 2", loginServers?.get(2)?.name)
        assertEquals("https://mdm2.example.com/2", loginServers?.get(2)?.url)
        assertEquals(false, loginServers?.get(2)?.isCustom)

        assertEquals("Production", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals(LoginServerManager.PRODUCTION_LOGIN_URL, loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)

        runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm1.example.com/1/2", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 1.2", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 6, loginServers?.size)
        assertEquals("MDM 1", loginServers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
        assertEquals("MDM 1.2", loginServers?.get(1)?.name)
        assertEquals("https://mdm1.example.com/1/2", loginServers?.get(1)?.url)
        assertEquals(false, loginServers?.get(1)?.isCustom)
        assertEquals("MDM 2", loginServers?.get(2)?.name)
        assertEquals("https://mdm2.example.com/2", loginServers?.get(2)?.url)
        assertEquals(false, loginServers?.get(2)?.isCustom)

        assertEquals("Production", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals(LoginServerManager.PRODUCTION_LOGIN_URL, loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)
    }

    /**
     * Test for testRemoveRuntimeConfigLoginServers.
     */
    @Test
    fun testRemoveRuntimeConfigLoginServers() {
        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        var runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm1.example.com/1/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 1.1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        var loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 6, loginServers?.size)
        assertEquals("MDM 1", loginServers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
        assertEquals("MDM 1.1", loginServers?.get(1)?.name)
        assertEquals("https://mdm1.example.com/1/1", loginServers?.get(1)?.url)
        assertEquals(false, loginServers?.get(1)?.isCustom)
        assertEquals("MDM 2", loginServers?.get(2)?.name)
        assertEquals("https://mdm2.example.com/2", loginServers?.get(2)?.url)
        assertEquals(false, loginServers?.get(2)?.isCustom)

        assertEquals("Production", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals(LoginServerManager.PRODUCTION_LOGIN_URL, loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)

        runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 5, loginServers?.size)
        assertEquals("MDM 1", loginServers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
        assertEquals("MDM 2", loginServers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", loginServers?.get(1)?.url)
        assertEquals(false, loginServers?.get(1)?.isCustom)

        assertEquals("Production", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals(LoginServerManager.PRODUCTION_LOGIN_URL, loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)
    }

    /**
     * Test for testNullsInResourcesXmlLoginServers.
     */
    @Test
    fun testNullsInResourcesXmlLoginServers() {

        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns null
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers_nulls)

        val loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 1, loginServers?.size)
        assertEquals("Example Login Server", loginServers?.get(0)?.name)
        assertEquals("https://www.example.com", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
    }

    /**
     * Test for testNullsInSelectedLoginServer.
     */
    @Test
    fun testNullsInSelectedLoginServer() {

        val sharedPreferencesSelectedServer = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferencesSelectedServer.getString(SERVER_NAME, null) } returns null
        every { sharedPreferencesSelectedServer.getString(SERVER_URL, null) } returns null
        every { sharedPreferencesSelectedServer.getBoolean(IS_CUSTOM, false) } returns false

        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferences.getInt(NUMBER_OF_ENTRIES, 0) } returns 1
        every { sharedPreferences.getString(String.format(SERVER_NAME, 0), null) } returns "Default Login Server"
        every { sharedPreferences.getString(String.format(SERVER_URL, 0), null) } returns "https://default.example.com"
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 0), false) } returns false

        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns sharedPreferencesSelectedServer
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns sharedPreferences
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns null
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val selectedLoginServer = loginServerManager?.selectedLoginServer

        assertEquals("Default Login Server", selectedLoginServer?.name)
        assertEquals("https://default.example.com", selectedLoginServer?.url)
    }

    /**
     * Test for testNullNameInSelectedLoginServer.
     */
    @Test
    fun testNullNameInSelectedLoginServer() {

        val sharedPreferencesSelectedServer = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferencesSelectedServer.getString(SERVER_NAME, null) } returns null
        every { sharedPreferencesSelectedServer.getString(SERVER_URL, null) } returns "https://selected.example.com"
        every { sharedPreferencesSelectedServer.getBoolean(IS_CUSTOM, false) } returns false

        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferences.getInt(NUMBER_OF_ENTRIES, 0) } returns 1
        every { sharedPreferences.getString(String.format(SERVER_NAME, 0), null) } returns "Default Login Server"
        every { sharedPreferences.getString(String.format(SERVER_URL, 0), null) } returns "https://default.example.com"
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 0), false) } returns false

        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns sharedPreferencesSelectedServer
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns sharedPreferences
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns null
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val selectedLoginServer = loginServerManager?.selectedLoginServer

        assertEquals("Default Login Server", selectedLoginServer?.name)
        assertEquals("https://default.example.com", selectedLoginServer?.url)
    }

    /**
     * Test for testNullUrlInSelectedLoginServer.
     */
    @Test
    fun testNullUrlInSelectedLoginServer() {

        val sharedPreferencesSelectedServer = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferencesSelectedServer.getString(SERVER_NAME, null) } returns "Selected Login Server"
        every { sharedPreferencesSelectedServer.getString(SERVER_URL, null) } returns null
        every { sharedPreferencesSelectedServer.getBoolean(IS_CUSTOM, false) } returns false

        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferences.getInt(NUMBER_OF_ENTRIES, 0) } returns 1
        every { sharedPreferences.getString(String.format(SERVER_NAME, 0), null) } returns "Default Login Server"
        every { sharedPreferences.getString(String.format(SERVER_URL, 0), null) } returns "https://default.example.com"
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 0), false) } returns false

        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns sharedPreferencesSelectedServer
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns sharedPreferences
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns null
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val selectedLoginServer = loginServerManager?.selectedLoginServer

        assertEquals("Default Login Server", selectedLoginServer?.name)
        assertEquals("https://default.example.com", selectedLoginServer?.url)
    }

    /**
     * Test for testRemovedSelectedLoginServer.
     */
    @Test
    fun testRemovedSelectedLoginServer() {

        val sharedPreferencesSelectedServer = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferencesSelectedServer.getString(SERVER_NAME, null) } returns "Selected Login Server"
        every { sharedPreferencesSelectedServer.getString(SERVER_URL, null) } returns "https://selected.example.com"
        every { sharedPreferencesSelectedServer.getBoolean(IS_CUSTOM, false) } returns false

        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferences.getInt(NUMBER_OF_ENTRIES, 0) } returns 1
        every { sharedPreferences.getString(String.format(SERVER_NAME, 0), null) } returns "Default Login Server"
        every { sharedPreferences.getString(String.format(SERVER_URL, 0), null) } returns "https://default.example.com"
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 0), false) } returns false

        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns sharedPreferencesSelectedServer
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns sharedPreferences
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns null
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val selectedLoginServer = loginServerManager?.selectedLoginServer

        assertEquals("Default Login Server", selectedLoginServer?.name)
        assertEquals("https://default.example.com", selectedLoginServer?.url)
    }

    /**
     * Test for testRemovedNameSelectedLoginServer.
     */
    @Test
    fun testRemovedNameSelectedLoginServer() {

        val sharedPreferencesSelectedServer = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferencesSelectedServer.getString(SERVER_NAME, null) } returns "Selected Login Server"
        every { sharedPreferencesSelectedServer.getString(SERVER_URL, null) } returns "https://selected.example.com"
        every { sharedPreferencesSelectedServer.getBoolean(IS_CUSTOM, false) } returns false

        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferences.getInt(NUMBER_OF_ENTRIES, 0) } returns 1
        every { sharedPreferences.getString(String.format(SERVER_NAME, 0), null) } returns "Default Login Server"
        every { sharedPreferences.getString(String.format(SERVER_URL, 0), null) } returns "https://selected.example.com"
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 0), false) } returns false

        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns sharedPreferencesSelectedServer
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns sharedPreferences
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns null
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val selectedLoginServer = loginServerManager?.selectedLoginServer

        assertEquals("Default Login Server", selectedLoginServer?.name)
        assertEquals("https://selected.example.com", selectedLoginServer?.url)
    }

    /**
     * Test for testRemovedUrlSelectedLoginServer.
     */
    @Test
    fun testRemovedUrlSelectedLoginServer() {

        val sharedPreferencesSelectedServer = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferencesSelectedServer.getString(SERVER_NAME, null) } returns "Selected Login Server"
        every { sharedPreferencesSelectedServer.getString(SERVER_URL, null) } returns "https://selected.example.com"
        every { sharedPreferencesSelectedServer.getBoolean(IS_CUSTOM, false) } returns false

        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferences.getInt(NUMBER_OF_ENTRIES, 0) } returns 1
        every { sharedPreferences.getString(String.format(SERVER_NAME, 0), null) } returns "Selected Login Server"
        every { sharedPreferences.getString(String.format(SERVER_URL, 0), null) } returns "https://default.example.com"
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 0), false) } returns false

        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns sharedPreferencesSelectedServer
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns sharedPreferences
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns null
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val selectedLoginServer = loginServerManager?.selectedLoginServer

        assertEquals("Selected Login Server", selectedLoginServer?.name)
        assertEquals("https://default.example.com", selectedLoginServer?.url)
    }

    /**
     * Test for testNullSelectedLoginServer.
     */
    @Test
    fun testNullSelectedLoginServer() {

        val sharedPreferencesSelectedServer = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferencesSelectedServer.getString(SERVER_NAME, null) } returns null
        every { sharedPreferencesSelectedServer.getString(SERVER_URL, null) } returns null
        every { sharedPreferencesSelectedServer.getBoolean(IS_CUSTOM, false) } returns false

        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferences.getInt(NUMBER_OF_ENTRIES, 0) } returns 1
        every { sharedPreferences.getString(String.format(SERVER_NAME, 0), null) } returns "Production"
        every { sharedPreferences.getString(String.format(SERVER_URL, 0), null) } returns "https://login.salesforce.com"
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 0), false) } returns false

        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns sharedPreferencesSelectedServer
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns sharedPreferences
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns null
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val selectedLoginServer = loginServerManager?.selectedLoginServer

        assertEquals("Production", selectedLoginServer?.name)
        assertEquals("https://login.salesforce.com", selectedLoginServer?.url)
    }

    /**
     * Test for testNullsInSharedPreferencesLoginServers.
     */
    @Test
    fun testNullsInSharedPreferencesLoginServers() {

        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferences.getInt(NUMBER_OF_ENTRIES, 0) } returns 4
        every { sharedPreferences.getString(String.format(SERVER_NAME, 0), null) } returns null
        every { sharedPreferences.getString(String.format(SERVER_URL, 0), null) } returns null
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 0), false) } returns false

        every { sharedPreferences.getString(String.format(SERVER_NAME, 1), null) } returns "Any String"
        every { sharedPreferences.getString(String.format(SERVER_URL, 1), null) } returns null
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 1), false) } returns false

        every { sharedPreferences.getString(String.format(SERVER_NAME, 2), null) } returns null
        every { sharedPreferences.getString(String.format(SERVER_URL, 2), null) } returns "Any String"
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 2), false) } returns false

        every { sharedPreferences.getString(String.format(SERVER_NAME, 3), null) } returns "Example Login Server"
        every { sharedPreferences.getString(String.format(SERVER_URL, 3), null) } returns "https://login.example.com"
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 3), false) } returns false

        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns sharedPreferences
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns null
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        var loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 1, loginServers?.size)

        loginServers = loginServerManager?.getLoginServersFromPreferences(sharedPreferences)

        assertEquals("Wrong number of servers", 1, loginServers?.size)
        assertEquals("Example Login Server", loginServers?.get(0)?.name)
        assertEquals("https://login.example.com", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
    }

    /**
     * Test for testEmptyInSharedPreferencesLoginServers.
     */
    @Test
    fun testEmptyInSharedPreferencesLoginServers() {

        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferences.getInt(NUMBER_OF_ENTRIES, 0) } returns 4
        every { sharedPreferences.getString(String.format(SERVER_NAME, 0), null) } returns null
        every { sharedPreferences.getString(String.format(SERVER_URL, 0), null) } returns null
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 0), false) } returns false

        every { sharedPreferences.getString(String.format(SERVER_NAME, 1), null) } returns "Any String"
        every { sharedPreferences.getString(String.format(SERVER_URL, 1), null) } returns null
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 1), false) } returns false

        every { sharedPreferences.getString(String.format(SERVER_NAME, 2), null) } returns null
        every { sharedPreferences.getString(String.format(SERVER_URL, 2), null) } returns "Any String"
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 2), false) } returns false

        every { sharedPreferences.getString(String.format(SERVER_NAME, 3), null) } returns "Example Login Server"
        every { sharedPreferences.getString(String.format(SERVER_URL, 3), null) } returns "https://login.example.com"
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 3), false) } returns false

        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns sharedPreferences
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns null
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        var loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 1, loginServers?.size)

        loginServers = loginServerManager?.getLoginServersFromPreferences(sharedPreferences)

        assertEquals("Wrong number of servers", 1, loginServers?.size)
        assertEquals("Example Login Server", loginServers?.get(0)?.name)
        assertEquals("https://login.example.com", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
    }

    /**
     * Test for testErrorInSharedPreferencesLoginServers.
     */
    @Test
    fun testErrorInSharedPreferencesLoginServers() {
        val xmlResourceParser = mockk<XmlResourceParser>(relaxed = true)
        every { xmlResourceParser.next() } throws XmlPullParserException("Error in XML")

        val resources = mockk<Resources>(relaxed = true)
        every { resources.getXml(any()) } returns xmlResourceParser

        val context = mockk<Context>()
        every { context.resources } returns resources
        every { context.getString(sf__auth_login_production) } returns "Production"
        every { context.getString(sf__auth_login_sandbox) } returns "Sandbox"
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns null
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val loginServers = loginServerManager?.loginServers
        assertEquals("Wrong number of servers", 2, loginServers?.size)
    }

    @Test
    fun testRemoveServerNonCustomNotFound() {
        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val loginServer = LoginServer("MDM 3", "https://mdm3.example.com/3", false)
        loginServerManager?.removeServer(loginServer)

        val servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 5, servers?.size)
        assertEquals("MDM 1", servers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", servers?.get(0)?.url)
        assertEquals(false, servers?.get(0)?.isCustom)
        assertEquals("MDM 2", servers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", servers?.get(1)?.url)
        assertEquals(false, servers?.get(1)?.isCustom)

        assertEquals("Production", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals(LoginServerManager.PRODUCTION_LOGIN_URL, loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)
    }

    @Test
    fun testRemoveServerCustomNotFound() {
        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val loginServer = LoginServer("MDM 3", "https://mdm3.example.com/3", true)
        loginServerManager?.removeServer(loginServer)

        val servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 5, servers?.size)
        assertEquals("MDM 1", servers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", servers?.get(0)?.url)
        assertEquals(false, servers?.get(0)?.isCustom)
        assertEquals("MDM 2", servers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", servers?.get(1)?.url)
        assertEquals(false, servers?.get(1)?.isCustom)

        assertEquals("Production", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals(LoginServerManager.PRODUCTION_LOGIN_URL, loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)
    }

    @Test
    fun testRemoveServerCustomFoundWithAllowNonCustomRemoval() {
        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>(relaxed = true)
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)
        loginServerManager?.addCustomLoginServer("MDM 3", "https://mdm3.example.com/3")

        var servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 6, servers?.size)
        assertEquals("MDM 3", servers?.get(5)?.name)
        assertEquals("https://mdm3.example.com/3", servers?.get(5)?.url)
        assertEquals(true, servers?.get(5)?.isCustom)

        val loginServer = LoginServer("MDM 3", "https://mdm3.example.com/3", true)
        loginServerManager?.removeServer(loginServer, context.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE), true)

        servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 5, servers?.size)
        assertEquals("MDM 1", servers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", servers?.get(0)?.url)
        assertEquals(false, servers?.get(0)?.isCustom)
        assertEquals("MDM 2", servers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", servers?.get(1)?.url)
        assertEquals(false, servers?.get(1)?.isCustom)
    }

    // region isProductionLoginServer / isMyDomainServer / isPoolServer

    @Test
    fun test_givenProductionUrl_whenIsProductionLoginServer_thenTrue() {
        assertEquals(true, LoginServerManager.isProductionLoginServer("https://login.salesforce.com"))
    }

    @Test
    fun test_givenInternalProductionPoolUrl_whenIsProductionLoginServer_thenTrue() {
        assertEquals(true, LoginServerManager.isProductionLoginServer("https://login.test1.pc-rnd.salesforce.com"))
    }

    @Test
    fun test_givenMyDomainUrl_whenIsProductionLoginServer_thenFalse() {
        assertEquals(false, LoginServerManager.isProductionLoginServer("https://myorg.my.salesforce.com"))
    }

    @Test
    fun test_givenInternalMyDomainUrl_whenIsProductionLoginServer_thenFalse() {
        assertEquals(false, LoginServerManager.isProductionLoginServer("https://mobilesdksdb32.test1.my.pc-rnd.salesforce.com"))
    }

    @Test
    fun test_givenSandboxUrl_whenIsProductionLoginServer_thenFalse() {
        assertEquals(false, LoginServerManager.isProductionLoginServer("https://test.salesforce.com"))
    }

    @Test
    fun test_givenMyDomainUrl_whenIsMyDomainServer_thenTrue() {
        assertEquals(true, LoginServerManager.isMyDomainServer("https://myorg.my.salesforce.com"))
    }

    @Test
    fun test_givenInternalMyDomainUrl_whenIsMyDomainServer_thenTrue() {
        assertEquals(true, LoginServerManager.isMyDomainServer("https://mobilesdksdb32.test1.my.pc-rnd.salesforce.com"))
    }

    @Test
    fun test_givenProductionUrl_whenIsMyDomainServer_thenFalse() {
        assertEquals(false, LoginServerManager.isMyDomainServer("https://login.salesforce.com"))
    }

    @Test
    fun test_givenInternalProductionUrl_whenIsMyDomainServer_thenFalse() {
        assertEquals(false, LoginServerManager.isMyDomainServer("https://login.test1.pc-rnd.salesforce.com"))
    }

    @Test
    fun test_givenProductionUrl_whenIsPoolServer_thenTrue() {
        assertEquals(true, LoginServerManager.isPoolServer(LoginServerManager.PRODUCTION_LOGIN_URL))
    }

    @Test
    fun test_givenInternalProductionUrl_whenIsPoolServer_thenTrue() {
        assertEquals(true, LoginServerManager.isPoolServer("https://login.test1.pc-rnd.salesforce.com"))
    }

    @Test
    fun test_givenSandboxUrl_whenIsPoolServer_thenTrue() {
        assertEquals(true, LoginServerManager.isPoolServer(LoginServerManager.SANDBOX_LOGIN_URL))
    }

    @Test
    fun test_givenWelcomeUrl_whenIsPoolServer_thenTrue() {
        assertEquals(true, LoginServerManager.isPoolServer(LoginServerManager.WELCOME_LOGIN_URL))
    }

    @Test
    fun test_givenMyDomainUrl_whenIsPoolServer_thenFalse() {
        assertEquals(false, LoginServerManager.isPoolServer("https://myorg.my.salesforce.com"))
    }

    // endregion
}
