package com.salesforce.androidsdk.auth

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.content.res.Resources
import android.content.res.XmlResourceParser
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
import com.salesforce.androidsdk.tests.R.xml.servers
import com.salesforce.androidsdk.tests.R.xml.servers_nulls
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParserException

@RunWith(AndroidJUnit4::class)
@SmallTest
class LoginServerManagerTestKt {

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
        val runtimeConfig = mockk<RuntimeConfig>()
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
        val runtimeConfig = mockk<RuntimeConfig>()
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 2, servers?.size)
        assertEquals("MDM 1", servers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", servers?.get(0)?.url)
        assertEquals(false, servers?.get(0)?.isCustom)
        assertEquals("MDM 2", servers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", servers?.get(1)?.url)
        assertEquals(false, servers?.get(1)?.isCustom)

        assertEquals("MDM 1", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.url)
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
        val runtimeConfig = mockk<RuntimeConfig>()
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns null

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 2, servers?.size)
        assertEquals(servers?.get(0)?.url, servers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", servers?.get(0)?.url)
        assertEquals(false, servers?.get(0)?.isCustom)
        assertEquals(servers?.get(1)?.url, servers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", servers?.get(1)?.url)
        assertEquals(false, servers?.get(1)?.isCustom)

        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.url)
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
        val runtimeConfig = mockk<RuntimeConfig>()
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 2, servers?.size)
        assertEquals(servers?.get(0)?.url, servers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", servers?.get(0)?.url)
        assertEquals(false, servers?.get(0)?.isCustom)
        assertEquals(servers?.get(1)?.url, servers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", servers?.get(1)?.url)
        assertEquals(false, servers?.get(1)?.isCustom)

        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.url)
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
        var runtimeConfig = mockk<RuntimeConfig>()
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        var loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 2, loginServers?.size)
        assertEquals("MDM 1", loginServers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
        assertEquals("MDM 2", loginServers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", loginServers?.get(1)?.url)
        assertEquals(false, loginServers?.get(1)?.isCustom)

        assertEquals("MDM 1", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)

        runtimeConfig = mockk<RuntimeConfig>()
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm1.example.com/1/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 1.1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 3, loginServers?.size)
        assertEquals("MDM 1", loginServers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
        assertEquals("MDM 1.1", loginServers?.get(1)?.name)
        assertEquals("https://mdm1.example.com/1/1", loginServers?.get(1)?.url)
        assertEquals(false, loginServers?.get(1)?.isCustom)
        assertEquals("MDM 2", loginServers?.get(2)?.name)
        assertEquals("https://mdm2.example.com/2", loginServers?.get(2)?.url)
        assertEquals(false, loginServers?.get(2)?.isCustom)

        assertEquals("MDM 1", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.url)
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
        var runtimeConfig = mockk<RuntimeConfig>()
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm1.example.com/1/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 1.1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        var loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 3, loginServers?.size)
        assertEquals("MDM 1", loginServers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
        assertEquals("MDM 1.1", loginServers?.get(1)?.name)
        assertEquals("https://mdm1.example.com/1/1", loginServers?.get(1)?.url)
        assertEquals(false, loginServers?.get(1)?.isCustom)
        assertEquals("MDM 2", loginServers?.get(2)?.name)
        assertEquals("https://mdm2.example.com/2", loginServers?.get(2)?.url)
        assertEquals(false, loginServers?.get(2)?.isCustom)

        assertEquals("MDM 1", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)

        runtimeConfig = mockk<RuntimeConfig>()
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm1.example.com/1/2", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 1.2", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 3, loginServers?.size)
        assertEquals("MDM 1", loginServers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
        assertEquals("MDM 1.2", loginServers?.get(1)?.name)
        assertEquals("https://mdm1.example.com/1/2", loginServers?.get(1)?.url)
        assertEquals(false, loginServers?.get(1)?.isCustom)
        assertEquals("MDM 2", loginServers?.get(2)?.name)
        assertEquals("https://mdm2.example.com/2", loginServers?.get(2)?.url)
        assertEquals(false, loginServers?.get(2)?.isCustom)

        assertEquals("MDM 1", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.url)
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
        var runtimeConfig = mockk<RuntimeConfig>()
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm1.example.com/1/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 1.1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        var loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 3, loginServers?.size)
        assertEquals("MDM 1", loginServers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
        assertEquals("MDM 1.1", loginServers?.get(1)?.name)
        assertEquals("https://mdm1.example.com/1/1", loginServers?.get(1)?.url)
        assertEquals(false, loginServers?.get(1)?.isCustom)
        assertEquals("MDM 2", loginServers?.get(2)?.name)
        assertEquals("https://mdm2.example.com/2", loginServers?.get(2)?.url)
        assertEquals(false, loginServers?.get(2)?.isCustom)

        assertEquals("MDM 1", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)

        runtimeConfig = mockk<RuntimeConfig>()
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        loginServers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 2, loginServers?.size)
        assertEquals("MDM 1", loginServers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", loginServers?.get(0)?.url)
        assertEquals(false, loginServers?.get(0)?.isCustom)
        assertEquals("MDM 2", loginServers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", loginServers?.get(1)?.url)
        assertEquals(false, loginServers?.get(1)?.isCustom)

        assertEquals("MDM 1", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.url)
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
        val runtimeConfig = mockk<RuntimeConfig>()
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
        val runtimeConfig = mockk<RuntimeConfig>()
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
        val runtimeConfig = mockk<RuntimeConfig>()
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
        val runtimeConfig = mockk<RuntimeConfig>()
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
        val runtimeConfig = mockk<RuntimeConfig>()
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
        val runtimeConfig = mockk<RuntimeConfig>()
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
        val runtimeConfig = mockk<RuntimeConfig>()
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
        every { sharedPreferences.getString(String.format(SERVER_NAME, 0), null) } returns null
        every { sharedPreferences.getString(String.format(SERVER_URL, 0), null) } returns null
        every { sharedPreferences.getBoolean(String.format(IS_CUSTOM, 0), false) } returns false

        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns sharedPreferencesSelectedServer
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns sharedPreferences
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>()
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
        val runtimeConfig = mockk<RuntimeConfig>()
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
        val runtimeConfig = mockk<RuntimeConfig>()
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
        val runtimeConfig = mockk<RuntimeConfig>()
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
        val runtimeConfig = mockk<RuntimeConfig>()
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val loginServer = LoginServer("MDM 3", "https://mdm3.example.com/3", false)
        loginServerManager?.removeServer(loginServer)

        val servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 2, servers?.size)
        assertEquals("MDM 1", servers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", servers?.get(0)?.url)
        assertEquals(false, servers?.get(0)?.isCustom)
        assertEquals("MDM 2", servers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", servers?.get(1)?.url)
        assertEquals(false, servers?.get(1)?.isCustom)

        assertEquals("MDM 1", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)
    }

    @Test
    fun testRemoveServerCustomNotFound() {
        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>()
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)

        val loginServer = LoginServer("MDM 3", "https://mdm3.example.com/3", true)
        loginServerManager?.removeServer(loginServer)

        val servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 2, servers?.size)
        assertEquals("MDM 1", servers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", servers?.get(0)?.url)
        assertEquals(false, servers?.get(0)?.isCustom)
        assertEquals("MDM 2", servers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", servers?.get(1)?.url)
        assertEquals(false, servers?.get(1)?.isCustom)

        assertEquals("MDM 1", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)
    }

    @Test
    fun testRemoveServerCustomFoundWithAllowsAllowNonCustomRemoval() {
        val context = mockk<Context>()
        every { context.resources } returns getInstrumentation().targetContext.resources
        every { context.getSharedPreferences(SERVER_SELECTION_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_SELECTION_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(SERVER_URL_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(SERVER_URL_FILE, MODE_PRIVATE)
        every { context.getSharedPreferences(RUNTIME_PREFS_FILE, any()) } returns getInstrumentation().targetContext.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE)
        val runtimeConfig = mockk<RuntimeConfig>()
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) } returns arrayOf("https://mdm1.example.com/1", "https://mdm2.example.com/2")
        every { runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels) } returns arrayOf("MDM 1", "MDM 2")

        loginServerManager = LoginServerManager(context, runtimeConfig, servers)
        loginServerManager?.addCustomLoginServer("MDM 3", "https://mdm3.example.com/3")

        var servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 3, servers?.size)
        assertEquals("MDM 3", servers?.get(2)?.name)
        assertEquals("https://mdm3.example.com/3", servers?.get(2)?.url)
        assertEquals(true, servers?.get(2)?.isCustom)

        val loginServer = LoginServer("MDM 3", "https://mdm3.example.com/3", true)
        loginServerManager?.removeServer(loginServer, context.getSharedPreferences(RUNTIME_PREFS_FILE, MODE_PRIVATE), true)

        servers = loginServerManager?.loginServers

        assertEquals("Wrong number of servers", 2, servers?.size)
        assertEquals("MDM 1", servers?.get(0)?.name)
        assertEquals("https://mdm1.example.com/1", servers?.get(0)?.url)
        assertEquals(false, servers?.get(0)?.isCustom)
        assertEquals("MDM 2", servers?.get(1)?.name)
        assertEquals("https://mdm2.example.com/2", servers?.get(1)?.url)
        assertEquals(false, servers?.get(1)?.isCustom)

        assertEquals("MDM 1", loginServerManager?.getSelectedLoginServer()?.name)
        assertEquals("https://mdm1.example.com/1", loginServerManager?.getSelectedLoginServer()?.url)
        assertEquals(false, loginServerManager?.getSelectedLoginServer()?.isCustom)
    }
}
