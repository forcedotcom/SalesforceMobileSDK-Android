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
package com.salesforce.samples.authflowtester.testUtility

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.salesforce.samples.authflowtester.AuthFlowTesterActivity
import com.salesforce.samples.authflowtester.pageObjects.AuthFlowTesterPageObject
import com.salesforce.samples.authflowtester.pageObjects.AuthorizationPageObject
import com.salesforce.samples.authflowtester.pageObjects.LoginOptionsPageObject
import com.salesforce.samples.authflowtester.pageObjects.ChromeCustomTabPageObject
import com.salesforce.samples.authflowtester.pageObjects.LoginPageObject
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection.EMPTY
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.REGULAR_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig.ADVANCED_AUTH
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig.CA_OPAQUE
import org.junit.Rule

abstract class AuthFlowTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @get:Rule(order = 2)
    val activityRule = ActivityScenarioRule(AuthFlowTesterActivity::class.java)

    val loginOptions = LoginOptionsPageObject(composeTestRule)
    val app = AuthFlowTesterPageObject(composeTestRule)

    val user: KnownUserConfig by lazy {
        val minSdk = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationInfo.minSdkVersion
        val userNumber = (Build.VERSION.SDK_INT - minSdk) % KnownUserConfig.values().count()
        KnownUserConfig.values()[userNumber]
    }

    // For MultiUser tests
    val otherUser: KnownUserConfig by lazy {
        val userNumber = (user.ordinal + 1) % KnownUserConfig.values().count()
        KnownUserConfig.values()[userNumber]
    }

    open fun loginAndValidate(
        knownAppConfig: KnownAppConfig,
        scopeSelection: ScopeSelection = EMPTY,
        useWebServerFlow: Boolean = true,
        useHybridAuthToken: Boolean = true,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
        knownUserConfig: KnownUserConfig = user,
    ) {
        val loginPage = when(knownLoginHostConfig) {
            REGULAR_AUTH -> LoginPageObject(composeTestRule)
            ADVANCED_AUTH -> ChromeCustomTabPageObject(composeTestRule)
        }

        if (!useWebServerFlow || !useHybridAuthToken ||
            knownAppConfig != CA_OPAQUE || scopeSelection != EMPTY) {

            loginPage.openLoginOptions()

            if (!useWebServerFlow) {
                loginOptions.disableWebServerFlow()
            }

            if (!useHybridAuthToken) {
                loginOptions.disableHybridAuthToken()
            }

            if (knownAppConfig == CA_OPAQUE && scopeSelection == EMPTY) {
                Espresso.pressBack()
            } else {
                loginOptions.setOverrideBootConfig(knownAppConfig, scopeSelection)
            }
        }

        if (knownLoginHostConfig != REGULAR_AUTH) {
            loginPage.changeServer(knownLoginHostConfig)
        }

        loginPage.login(knownLoginHostConfig, knownUserConfig)
        app.waitForAppLoad()

        app.validateUser(knownLoginHostConfig, knownUserConfig)
        app.validateOAuthValues(knownAppConfig, scopeSelection)
        app.validateApiRequest()
    }

    fun migrateAndValidate(
        knownAppConfig: KnownAppConfig,
        knownLoginHostConfig: KnownLoginHostConfig = REGULAR_AUTH,
        scopeSelection: ScopeSelection = EMPTY,
        knownUserConfig: KnownUserConfig = user,
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