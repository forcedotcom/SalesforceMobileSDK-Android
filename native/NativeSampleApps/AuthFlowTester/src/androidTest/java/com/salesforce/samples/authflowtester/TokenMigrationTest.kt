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
package com.salesforce.samples.authflowtester

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.samples.authflowtester.pageObjects.AuthFlowTesterPageObject
import com.salesforce.samples.authflowtester.pageObjects.LoginOptionsPageObject
import com.salesforce.samples.authflowtester.pageObjects.LoginPageObject
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig
import com.salesforce.samples.authflowtester.testUtility.KnownUserConfig
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class TokenMigrationTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @After
    fun cleanup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            SalesforceSDKManager.getInstance().logout(frontActivity = null, showLoginPage = false)
        }
    }

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @get:Rule(order = 2)
    val activityRule = ActivityScenarioRule(AuthFlowTesterActivity::class.java)

    val loginPage = LoginPageObject(composeTestRule)
    val loginOptions = LoginOptionsPageObject(composeTestRule)
    val app = AuthFlowTesterPageObject(composeTestRule)

    // region Migration within same app (scope upgrade)

    // Migrate within same CA (scope upgrade).
    @Test
    fun testMigrateCA_AddMoreScopes() {
        loginAndValidate(
            KnownAppConfig.CA_JWT,
            scopeSelection = ScopeSelection.SUBSET,
        )

        migrateAndValidate(
            KnownAppConfig.CA_JWT,
            scopeSelection = ScopeSelection.ALL,
        )
    }

    // Migrate within same ECA (scope upgrade).
    @Test
    fun testMigrateECA_AddMoreScopes() {
        loginAndValidate(
            KnownAppConfig.ECA_JWT,
            scopeSelection = ScopeSelection.SUBSET,
        )

        migrateAndValidate(
            KnownAppConfig.ECA_JWT,
            scopeSelection = ScopeSelection.ALL,
        )
    }

    // Migrate within same Beacon (scope upgrade).
    @Test
    fun testMigrateBeacon_AddMoreScopes() {
        loginAndValidate(
            KnownAppConfig.BEACON_JWT,
            scopeSelection = ScopeSelection.SUBSET,
        )

        migrateAndValidate(
            KnownAppConfig.BEACON_JWT,
            scopeSelection = ScopeSelection.ALL,
        )
    }

    // endregion
    // region Migration to or from beacon

    // Migrate from CA to Beacon
    @Test
    fun testMigrateCAToBeacon() {
        loginAndValidate(
            KnownAppConfig.CA_OPAQUE,
        )
        migrateAndValidate(
            KnownAppConfig.BEACON_OPAQUE,
        )
    }

    // Migrate from Beacon to CA
    @Test
    fun testMigrateBeaconToCA() {
        loginAndValidate(
            KnownAppConfig.BEACON_OPAQUE,
        )
        migrateAndValidate(
            KnownAppConfig.CA_OPAQUE
        )
    }

    // endregion
    // region Cross-App Migrations with rollbacks

    // Migrate from CA to ECA and back to CA
    @Test
    fun testMigrateCAToECA() {
        loginAndValidate(
            KnownAppConfig.CA_OPAQUE,
        )
        migrateAndValidate(
            KnownAppConfig.ECA_OPAQUE,
        )
        migrateAndValidate(
            KnownAppConfig.CA_OPAQUE
        )
    }

    // Migrate from CA to Beacon and back to CA
    @Test
    fun testMigrateCAToBeaconAndBack() {
        loginAndValidate(
            KnownAppConfig.CA_OPAQUE
        )
        migrateAndValidate(
            KnownAppConfig.BEACON_OPAQUE
        )
        migrateAndValidate(
            KnownAppConfig.CA_OPAQUE
        )
    }

    // Migrate from Beacon opaque to Beacon JWT and back to Beacon opaque
    @Test
    fun testMigrateBeaconOpaqueToJWTAndBack() {
        loginAndValidate(
            KnownAppConfig.BEACON_OPAQUE
        )
        migrateAndValidate(
            KnownAppConfig.BEACON_JWT
        )
        migrateAndValidate(
            KnownAppConfig.BEACON_OPAQUE
        )
    }

    // endregion

    private fun loginAndValidate(
        knownAppConfig: KnownAppConfig,
        knownLoginHostConfig: KnownLoginHostConfig = KnownLoginHostConfig.REGULAR_AUTH,
        knownUserConfig: KnownUserConfig = KnownUserConfig.FIRST,
        scopeSelection: ScopeSelection = ScopeSelection.EMPTY,
    ) {
        loginPage.openLoginOptions()
        loginOptions.setOverrideBootConfig(knownAppConfig, scopeSelection)
        loginPage.login(knownLoginHostConfig, knownUserConfig)
        app.waitForAppLoad()

        app.validateUser(knownLoginHostConfig, knownUserConfig)
        app.validateOAuthValues(knownAppConfig, scopeSelection)
    }

    private fun migrateAndValidate(
        knownAppConfig: KnownAppConfig,
        knownLoginHostConfig: KnownLoginHostConfig = KnownLoginHostConfig.REGULAR_AUTH,
        knownUserConfig: KnownUserConfig = KnownUserConfig.FIRST,
        scopeSelection: ScopeSelection = ScopeSelection.EMPTY,
    ) {
        val (preAccessToken, preRefreshToken) = app.getTokens()
        app.migrateToNewApp(knownAppConfig, scopeSelection)
        val (postAccessToken, postRefreshToken) = app.getTokens()

        // Assert tokens are new
        assert(preAccessToken != postAccessToken)
        assert(preRefreshToken != postRefreshToken)

        app.validateUser(knownLoginHostConfig, knownUserConfig)
        app.validateOAuthValues(knownAppConfig, scopeSelection)

        // Assert new tokens work
        app.revokeAccessToken()
        app.validateApiRequest()
    }
}