package com.salesforce.samples.authflowtester

import android.Manifest
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.salesforce.samples.authflowtester.pageObjects.AuthFlowTesterPageObject
import com.salesforce.samples.authflowtester.pageObjects.LoginOptionsPageObject
import com.salesforce.samples.authflowtester.pageObjects.LoginPageObject
import com.salesforce.samples.authflowtester.testUtility.coldRestart
import com.salesforce.samples.authflowtester.testUtility.testConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class TokenMigrationTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS
    )

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @get:Rule(order = 2)
    val activityRule = ActivityScenarioRule(AuthFlowTesterActivity::class.java)

    @Test
    fun testMigration() {
        LoginOptionsPageObject(composeTestRule).setOverrideBootConfig(
            consumerKey = "3MVG9H2sjXhorwC_Obi8SL7EU.QskCR_w6cp7mnu3cwTC1lVcyfPdxmHM.VswuiWdQJ6eTRQefowN4AOFId27",
            redirectUri = "caadvancedopaque://success/done",
        )

        val loginPage = LoginPageObject()
        with(testConfig.loginHosts.first().users.first()) {
            loginPage.setUsername(username)
            loginPage.setPassword(password)
        }
        loginPage.tapLogin()

        val app = AuthFlowTesterPageObject(composeTestRule)

        // Wait for the main Compose UI to load after login
        app.waitForNode(CREDS_SECTION_CONTENT_DESC)

        val preMigrationTokenInfo = app.getTokens()

        // Migrate
        val appJson = "{ \"remoteConsumerKey\": \"3MVG9H2sjXhorwC_Obi8SL7EU.R54.P.OzBJKAyvCZ.kNwRjieeZ808wFndS0hUjB0klUq5_cKcZfwU7dboCo\", \"oauthRedirectURI\": \"caadvancedjwt://success/done\" }"
        app.migrateToNewApp(appJson)

        // Cold restart to verify tokens are persisted, not just in memory
        coldRestart()

        val postMigrationTokens = app.getTokens()

        // Assert tokens are new
        assert(preMigrationTokenInfo.accessToken != postMigrationTokens.accessToken)
        assert(preMigrationTokenInfo.refreshToken != postMigrationTokens.refreshToken)

        // Assert new tokens work
        app.revokeAccessToken()
        app.makeApiRequest()
    }
}