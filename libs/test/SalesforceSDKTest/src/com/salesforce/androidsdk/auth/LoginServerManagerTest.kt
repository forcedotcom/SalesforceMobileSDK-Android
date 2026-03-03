package com.salesforce.androidsdk.auth

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.salesforce.androidsdk.config.LoginServerManager
import com.salesforce.androidsdk.config.LoginServerManager.RUNTIME_PREFS_FILE
import com.salesforce.androidsdk.config.LoginServerManager.SERVER_SELECTION_FILE
import com.salesforce.androidsdk.config.LoginServerManager.SERVER_URL_FILE
import com.salesforce.androidsdk.config.RuntimeConfig
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.AppServiceHostLabels
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.AppServiceHosts
import com.salesforce.androidsdk.tests.R.xml.servers
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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

        Assert.assertEquals("Wrong number of servers", 2, servers?.size)
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

        Assert.assertEquals("Wrong number of servers", 2, loginServers?.size)
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

        Assert.assertEquals("Wrong number of servers", 3, loginServers?.size)
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

        Assert.assertEquals("Wrong number of servers", 3, loginServers?.size)
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

        Assert.assertEquals("Wrong number of servers", 3, loginServers?.size)
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

        Assert.assertEquals("Wrong number of servers", 3, loginServers?.size)
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

        Assert.assertEquals("Wrong number of servers", 2, loginServers?.size)
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
}
