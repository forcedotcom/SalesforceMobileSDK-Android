package com.salesforce.androidsdk.app

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKUpgradeManager.UserManager
import com.salesforce.androidsdk.config.AdminSettingsManager
import com.salesforce.androidsdk.config.LegacyAdminSettingsManager
import com.salesforce.androidsdk.push.PushMessaging
import com.salesforce.androidsdk.push.PushService
import com.salesforce.androidsdk.security.KeyStoreWrapper
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for SalesforceSDKUpgradeManager
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceSDKUpgradeManagerTest {
    private val user11 = buildUser("org-1", "user-1-1")
    private val user12 = buildUser("org-1", "user-1-2")
    private val user21 = buildUser("org-2", "user-2-1")
    private val user31 = buildUser("org-3", "user-3-1")
    private val users = mutableListOf(user11, user12, user21, user31)

    private val userMgr = object : UserManager {
        override fun getAuthenticatedUsers(): MutableList<UserAccount> {
            return users
        }
    }
    private val upgradeMgr = SalesforceSDKUpgradeManager(userMgr)
    private val legacySettingsMgr = LegacyAdminSettingsManager()
    private val adminSettingsMgr = AdminSettingsManager()

    @Before
    fun setup() {
        // start clean
        legacySettingsMgr.resetAll()
        adminSettingsMgr.resetAll()
        PushMessaging.reRegistrationRequested = false
    }

    @Test
    fun testNoUpgrade() {
        // Set up (legacy) org level custom attributes
        legacySettingsMgr.setPrefs(mutableMapOf("custom-org1" to "value1"), user11)
        legacySettingsMgr.setPrefs(mutableMapOf("custom-org2" to "value2"), user21)
        legacySettingsMgr.setPrefs(mutableMapOf("custom-org3" to "value3"), user31)

        // Set up user level custom attributes
        adminSettingsMgr.setPrefs(mutableMapOf("custom-user11" to "value11"), user11)
        adminSettingsMgr.setPrefs(mutableMapOf("custom-user12" to "value12"), user12)
        adminSettingsMgr.setPrefs(mutableMapOf("custom-user21" to "value21"), user21)
        adminSettingsMgr.setPrefs(mutableMapOf("custom-user31" to "value31"), user31)

        // Set version to be current version and run upgrade
        setVersion(SalesforceSDKManager.SDK_VERSION)
        upgradeMgr.upgrade()
        Assert.assertEquals(SalesforceSDKManager.SDK_VERSION, getVersion())

        // Make sure nothing changed
        Assert.assertEquals(
            mutableMapOf("custom-org1" to "value1"),
            legacySettingsMgr.getPrefs(user11)
        )
        Assert.assertEquals(
            mutableMapOf("custom-org1" to "value1"),
            legacySettingsMgr.getPrefs(user12)
        )
        Assert.assertEquals(
            mutableMapOf("custom-org2" to "value2"),
            legacySettingsMgr.getPrefs(user21)
        )
        Assert.assertEquals(
            mutableMapOf("custom-org3" to "value3"),
            legacySettingsMgr.getPrefs(user31)
        )
        Assert.assertEquals(
            mutableMapOf("custom-user11" to "value11"),
            adminSettingsMgr.getPrefs(user11)
        )
        Assert.assertEquals(
            mutableMapOf("custom-user12" to "value12"),
            adminSettingsMgr.getPrefs(user12)
        )
        Assert.assertEquals(
            mutableMapOf("custom-user21" to "value21"),
            adminSettingsMgr.getPrefs(user21)
        )
        Assert.assertEquals(
            mutableMapOf("custom-user31" to "value31"),
            adminSettingsMgr.getPrefs(user31)
        )
    }

    @Test
    fun testUpgrade() {
        // Set up (legacy) org level custom attributes
        legacySettingsMgr.setPrefs(mutableMapOf("custom-org1" to "value1"), user11)
        legacySettingsMgr.setPrefs(mutableMapOf("custom-org2" to "value2"), user21)
        legacySettingsMgr.setPrefs(mutableMapOf("custom-org3" to "value3"), user31)

        // Set up user level custom attributes
        adminSettingsMgr.setPrefs(mutableMapOf("custom-user11" to "value11"), user11)
        adminSettingsMgr.setPrefs(mutableMapOf("custom-user12" to "value12"), user12)
        adminSettingsMgr.setPrefs(mutableMapOf("custom-user21" to "value21"), user21)
        adminSettingsMgr.setPrefs(mutableMapOf("custom-user31" to "value31"), user31)

        // Set version to be old version and run upgrade
        setVersion("10.2.0")
        upgradeMgr.upgrade()
        Assert.assertEquals(SalesforceSDKManager.SDK_VERSION, getVersion())

        // Make sure legacy settings have been cleared
        Assert.assertTrue(legacySettingsMgr.getPrefs(user11).isEmpty())
        Assert.assertTrue(legacySettingsMgr.getPrefs(user12).isEmpty())
        Assert.assertTrue(legacySettingsMgr.getPrefs(user21).isEmpty())
        Assert.assertTrue(legacySettingsMgr.getPrefs(user31).isEmpty())

        // Make sure user level custom attributes include (legacy) org level custom attributes
        Assert.assertEquals(
            mutableMapOf("custom-org1" to "value1", "custom-user11" to "value11"),
            adminSettingsMgr.getPrefs(user11)
        )
        Assert.assertEquals(
            mutableMapOf("custom-org1" to "value1", "custom-user12" to "value12"),
            adminSettingsMgr.getPrefs(user12)
        )
        Assert.assertEquals(
            mutableMapOf("custom-org2" to "value2", "custom-user21" to "value21"),
            adminSettingsMgr.getPrefs(user21)
        )
        Assert.assertEquals(
            mutableMapOf("custom-org3" to "value3", "custom-user31" to "value31"),
            adminSettingsMgr.getPrefs(user31)
        )
    }

    @Test
    fun testUpgradeFromBefore12() {
        // Set version to a version before 12.0.0
        setVersion("11.1.0")

        // Create public key for push notifications
        KeyStoreWrapper.getInstance().getRSAPublicString(PushService.pushNotificationKeyName)

        // Upgrade to latest
        upgradeMgr.upgrade()

        // Make sure re-registration is requested
        Assert.assertTrue(PushMessaging.reRegistrationRequested)
    }

    @Test
    fun testUpgradeFromBefore1302() {
        // Set version to a version before 13.0.2
        setVersion("12.2.0")

        // Create public key for push notifications
        KeyStoreWrapper.getInstance().getRSAPublicString(PushService.pushNotificationKeyName)

        // Upgrade to latest
        upgradeMgr.upgrade()

        // Make sure re-registration is requested
        Assert.assertTrue(PushMessaging.reRegistrationRequested)
    }

    @Test
    fun testUpgradeAfter1302() {
        // Set version to 12.0.0
        setVersion("13.0.2")

        // Create public key for push notifications
        KeyStoreWrapper.getInstance().getRSAPublicString(PushService.pushNotificationKeyName)

        // Upgrade to latest
        upgradeMgr.upgrade()

        // Make sure re-registration is NOT requested
        Assert.assertFalse(PushMessaging.reRegistrationRequested)
    }

    fun setVersion(version: String) {
        upgradeMgr.writeCurVersion("acc_mgr_version", version)
    }

    fun getVersion(): String {
        return upgradeMgr.getInstalledVersion("acc_mgr_version")
    }

    fun buildUser(orgId: String, userId: String): UserAccount {
        return UserAccount(Bundle().apply {
            putString("orgId", orgId)
            putString("userId", userId)
        })
    }
}