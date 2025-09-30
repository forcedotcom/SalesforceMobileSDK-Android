package com.salesforce.androidsdk.app

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKUpgradeManager.UserManager
import com.salesforce.androidsdk.config.AdminSettingsManager
import com.salesforce.androidsdk.config.LegacyAdminSettingsManager
import com.salesforce.androidsdk.push.PushMessaging
import com.salesforce.androidsdk.push.PushService
import com.salesforce.androidsdk.security.KeyStoreWrapper
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

const val LEGACY_ACCOUNT_TYPE = "com.salesforce.androidsdk"
const val VERSION_SHARED_PREF = "version_info"
const val ACC_MGR_KEY = "acc_mgr_version"

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

    private val userMgr = UserManager { users }
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

    @After
    fun tearDown() {
        unmockkAll()
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
        upgradeMgr.writeCurVersion(ACC_MGR_KEY, version)
    }

    fun getVersion(): String {
        return upgradeMgr.getInstalledVersion(ACC_MGR_KEY)
    }

    fun buildUser(orgId: String, userId: String): UserAccount {
        return UserAccount(Bundle().apply {
            putString("orgId", orgId)
            putString("userId", userId)
        })
    }

    @Test
    fun testMigrateAccountType_legacyAccountTypeStillInUse() {
        // Mock SalesforceSDKManager to return the legacy account type
        mockkObject(SalesforceSDKManager)
        val mockSDKManager = mockk<SalesforceSDKManager> {
            every { accountType } returns LEGACY_ACCOUNT_TYPE
            every { appContext.getSharedPreferences(VERSION_SHARED_PREF, Context.MODE_PRIVATE) } returns mockk {
                every { getString(ACC_MGR_KEY, "") } returns "14.0.0"
                every { edit() } returns mockk<SharedPreferences.Editor> {
                    every { putString(any(), any()) } returns this
                    every { commit() } returns true
                }
            }
        }
        every { SalesforceSDKManager.getInstance() } returns mockSDKManager
        mockkStatic(SalesforceSDKLogger::class)
        every { SalesforceSDKLogger.e(any(), any<String>()) } returns Unit

        // Create upgrade manager and upgrade
        val upgradeManager = SalesforceSDKUpgradeManager(userMgr)
        upgradeManager.writeCurVersion(ACC_MGR_KEY, "14.0.0")
        upgradeManager.upgrade()

        // Verify that no account operations were attempted (early return)
        verify(exactly = 0) {
            mockSDKManager.clientManager
            mockSDKManager.userAccountManager
        }
        verify(exactly = 1) {
            SalesforceSDKLogger.e(
                "SalesforceSDKUpgradeManager",
                "No app specific account type found.  To ensure users " +
                        "can login override the \"account_type\" value in your strings.xml.",
            )
        }
    }

    @Test
    fun testMigrateAccountType_successfulMigration() {
        val legacyAccount1 = Account("user1@example.com", LEGACY_ACCOUNT_TYPE)
        val legacyAccount2 = Account("user2@example.com", LEGACY_ACCOUNT_TYPE)
        val userAccount1 = buildUser("org1", "user1")
        val userAccount2 = buildUser("org2", "user2")
        val mockAccountManager = mockk<AccountManager> {
            every { getAccountsByType(LEGACY_ACCOUNT_TYPE) } returns arrayOf(legacyAccount1, legacyAccount2)
            // OS remove account mocked to true
            every { removeAccountExplicitly(any()) } returns true
        }
        val mockUserAccountManager = mockk<UserAccountManager> {
            every { buildUserAccount(legacyAccount1) } returns userAccount1
            every { buildUserAccount(legacyAccount2) } returns userAccount2
            every { createAccount(any()) } returns mockk<Bundle>()
        }

        mockkObject(SalesforceSDKManager)
        every { SalesforceSDKManager.getInstance() } returns mockk {
            every { appContext.getSharedPreferences(VERSION_SHARED_PREF, Context.MODE_PRIVATE) } returns mockk {
                every { getString(ACC_MGR_KEY, "") } returns "14.0.0"
                every { edit() } returns mockk<SharedPreferences.Editor> {
                    every { putString(any(), any()) } returns this
                    every { commit() } returns true
                }
            }
            every { additionalOauthKeys } returns null
            every { accountType } returns "com.new.account_type"
            every { clientManager.accountManager } returns mockAccountManager
            every { userAccountManager } returns mockUserAccountManager
        }

        // Create upgrade manager and trigger migration
        val upgradeManager = SalesforceSDKUpgradeManager(userMgr)
        upgradeManager.writeCurVersion(ACC_MGR_KEY, "14.0.0")
        upgradeManager.upgrade()

        // Verify all accounts were processed
        verify { mockAccountManager.removeAccountExplicitly(legacyAccount1) }
        verify { mockAccountManager.removeAccountExplicitly(legacyAccount2) }
        verify { mockUserAccountManager.createAccount(userAccount1) }
        verify { mockUserAccountManager.createAccount(userAccount2) }
    }

    @Test
    fun testMigrateAccountType_noLegacyAccounts() {
        val mockAccountManager = mockk<AccountManager> {
            every { getAccountsByType(LEGACY_ACCOUNT_TYPE) } returns emptyArray()
        }
        val mockUserAccountManager = mockk<UserAccountManager>()

        mockkObject(SalesforceSDKManager)
        every { SalesforceSDKManager.getInstance() } returns mockk {
            every { appContext.getSharedPreferences(VERSION_SHARED_PREF, Context.MODE_PRIVATE) } returns mockk {
                every { getString(ACC_MGR_KEY, "") } returns "14.0.0"
                every { edit() } returns mockk<SharedPreferences.Editor> {
                    every { putString(any(), any()) } returns this
                    every { commit() } returns true
                }
            }
            every { additionalOauthKeys } returns null
            every { accountType } returns "com.new.account_type"
            every { clientManager.accountManager } returns mockAccountManager
            every { userAccountManager } returns mockUserAccountManager
        }

        // Create upgrade manager and trigger migration
        val upgradeManager = SalesforceSDKUpgradeManager(userMgr)
        upgradeManager.writeCurVersion(ACC_MGR_KEY, "14.0.0")
        upgradeManager.upgrade()

        // Verify no account operations were performed
        verify(exactly = 0) { mockAccountManager.removeAccountExplicitly(any()) }
        verify(exactly = 0) { mockUserAccountManager.createAccount(any()) }
    }

    @Test
    fun testMigrateAccountType_buildUserAccountReturnsNull() {
        val legacyAccount1 = Account("user1@example.com", LEGACY_ACCOUNT_TYPE)
        val legacyAccount2 = Account("user2@example.com", LEGACY_ACCOUNT_TYPE)
        val userAccount2 = buildUser("org2", "user2")
        val mockAccountManager = mockk<AccountManager> {
            every { getAccountsByType(LEGACY_ACCOUNT_TYPE) } returns arrayOf(legacyAccount1, legacyAccount2)
            // OS remove account mocked to true
            every { removeAccountExplicitly(any()) } returns true
        }
        val mockUserAccountManager = mockk<UserAccountManager> {
            // mock corrupted account error
            every { buildUserAccount(legacyAccount1) } returns null
            every { buildUserAccount(legacyAccount2) } returns userAccount2
            every { createAccount(any()) } returns mockk<Bundle>()
        }

        mockkObject(SalesforceSDKManager)
        every { SalesforceSDKManager.getInstance() } returns mockk {
            every { appContext.getSharedPreferences(VERSION_SHARED_PREF, Context.MODE_PRIVATE) } returns mockk {
                every { getString(ACC_MGR_KEY, "") } returns "14.0.0"
                every { edit() } returns mockk<SharedPreferences.Editor> {
                    every { putString(any(), any()) } returns this
                    every { commit() } returns true
                }
            }
            every { additionalOauthKeys } returns null
            every { accountType } returns "com.new.account_type"
            every { clientManager.accountManager } returns mockAccountManager
            every { userAccountManager } returns mockUserAccountManager
        }
        mockkStatic(SalesforceSDKLogger::class)
        every { SalesforceSDKLogger.e(any(), any<String>()) } returns Unit

        // Create upgrade manager and trigger migration
        val upgradeManager = SalesforceSDKUpgradeManager(userMgr)
        upgradeManager.writeCurVersion(ACC_MGR_KEY, "14.0.0")
        upgradeManager.upgrade()

        // Verify account was not removed or recreated when buildUserAccount returns null, but other accounts still succeed.
        verify(exactly = 0) { mockAccountManager.removeAccountExplicitly(legacyAccount1) }
        verify(exactly = 1) {
            SalesforceSDKLogger.e(
                "SalesforceSDKUpgradeManager",
                "Unable to build UserAccount from account: ${legacyAccount1.name}",
            )
            mockAccountManager.removeAccountExplicitly(legacyAccount2)
            mockUserAccountManager.createAccount(userAccount2)
            mockUserAccountManager.createAccount(any())
        }
    }

    @Test
    fun testMigrateAccountType_exceptionDuringMigration() {
        val legacyAccount1 = Account("user1@example.com", LEGACY_ACCOUNT_TYPE)
        val legacyAccount2 = Account("user2@example.com", LEGACY_ACCOUNT_TYPE)
        val userAccount1 = buildUser("org1", "user1")
        val userAccount2 = buildUser("org2", "user2")
        val mockAccountManager = mockk<AccountManager> {
            every { getAccountsByType(LEGACY_ACCOUNT_TYPE) } returns arrayOf(legacyAccount1, legacyAccount2)
            // OS remove account throws for first account
            every { removeAccountExplicitly(legacyAccount1) } throws RuntimeException("Remove failed")
            every { removeAccountExplicitly(legacyAccount2) } returns true
        }
        val mockUserAccountManager = mockk<UserAccountManager> {
            // mock corrupted account error
            every { buildUserAccount(legacyAccount1) } returns userAccount1
            every { buildUserAccount(legacyAccount2) } returns userAccount2
            every { createAccount(any()) } returns mockk<Bundle>()
        }

        mockkObject(SalesforceSDKManager)
        every { SalesforceSDKManager.getInstance() } returns mockk {
            every { appContext.getSharedPreferences(VERSION_SHARED_PREF, Context.MODE_PRIVATE) } returns mockk {
                every { getString(ACC_MGR_KEY, "") } returns "14.0.0"
                every { edit() } returns mockk<SharedPreferences.Editor> {
                    every { putString(any(), any()) } returns this
                    every { commit() } returns true
                }
            }
            every { additionalOauthKeys } returns null
            every { accountType } returns "com.new.account_type"
            every { clientManager.accountManager } returns mockAccountManager
            every { userAccountManager } returns mockUserAccountManager
        }
        mockkStatic(SalesforceSDKLogger::class)
        every { SalesforceSDKLogger.e(any(), any<String>(), any<Exception>()) } returns Unit

        // Create upgrade manager and trigger migration
        val upgradeManager = SalesforceSDKUpgradeManager(userMgr)
        upgradeManager.writeCurVersion(ACC_MGR_KEY, "14.0.0")
        upgradeManager.upgrade()

        // Verify second account was still processed despite first account failing
        verify(exactly = 0) { mockUserAccountManager.createAccount(userAccount1) }
        verify(exactly = 1) {
            SalesforceSDKLogger.e(
                "SalesforceSDKUpgradeManager",
                "Failed to migrate account: ${legacyAccount1.name}",
                any<RuntimeException>()
            )
            mockAccountManager.removeAccountExplicitly(legacyAccount2)
            mockUserAccountManager.createAccount(userAccount2)
        }
    }

    @Test
    fun testMigrateAccountType_noUpgradeNeeded() {
        mockkObject(SalesforceSDKManager)
        val mockSDKManager = mockk<SalesforceSDKManager>(relaxed = true)
        every { SalesforceSDKManager.getInstance() } returns mockSDKManager

        // Create upgrade manager and set version to 15.0.0 (no upgrade needed)
        val upgradeManager = SalesforceSDKUpgradeManager(userMgr)
        upgradeManager.writeCurVersion(ACC_MGR_KEY, "15.0.0")
        upgradeManager.upgrade()

        verify(exactly = 0) { mockSDKManager.accountType }
    }
}