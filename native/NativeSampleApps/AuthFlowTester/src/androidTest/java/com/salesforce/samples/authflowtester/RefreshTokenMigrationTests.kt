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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.salesforce.samples.authflowtester.testUtility.AuthFlowTest
import com.salesforce.samples.authflowtester.testUtility.KnownAppConfig
import com.salesforce.samples.authflowtester.testUtility.KnownLoginHostConfig
import com.salesforce.samples.authflowtester.testUtility.KnownUserConfig
import com.salesforce.samples.authflowtester.testUtility.ScopeSelection
import org.junit.Test
import org.junit.runner.RunWith

// TODO: remove loginAndValidate override when W-20524841 is fixed.

@RunWith(AndroidJUnit4::class)
@LargeTest
class RefreshTokenMigrationTests: AuthFlowTest() {

    // region Migration within same app (scope upgrade)

    // Migrate within same CA (scope upgrade).
    @Test
    fun testMigrate_CA_AddMoreScopes() {
        loginAndValidate(
            knownAppConfig = KnownAppConfig.CA_JWT,
            scopeSelection = ScopeSelection.SUBSET,
        )

        migrateAndValidate(
            KnownAppConfig.CA_JWT,
            scopeSelection = ScopeSelection.ALL,
        )
    }

    // Migrate within same ECA (scope upgrade).
    @Test
    fun testMigrate_ECA_AddMoreScopes() {
        loginAndValidate(
            knownAppConfig = KnownAppConfig.ECA_JWT,
            scopeSelection = ScopeSelection.SUBSET,
        )

        migrateAndValidate(
            KnownAppConfig.ECA_JWT,
            scopeSelection = ScopeSelection.ALL,
        )
    }

    // Migrate within same Beacon (scope upgrade).
    @Test
    fun testMigrate_Beacon_AddMoreScopes() {
        loginAndValidate(
            knownAppConfig = KnownAppConfig.BEACON_JWT,
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
    fun testMigrateCA_To_Beacon() {
        loginAndValidate(
            knownAppConfig = KnownAppConfig.CA_OPAQUE,
        )
        migrateAndValidate(
            KnownAppConfig.BEACON_OPAQUE,
        )
    }

    // Migrate from Beacon to CA
    @Test
    fun testMigrateBeacon_To_CA() {
        loginAndValidate(
            knownAppConfig = KnownAppConfig.BEACON_OPAQUE,
        )
        migrateAndValidate(
            KnownAppConfig.CA_OPAQUE
        )
    }

    // endregion
    // region Cross-App Migrations with rollbacks

    // Migrate from CA to ECA and back to CA
    @Test
    fun testMigrateCA_To_ECA() {
        loginAndValidate(
            knownAppConfig = KnownAppConfig.CA_OPAQUE,
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
    fun testMigrateCA_To_BeaconAndBack() {
        loginAndValidate(
            knownAppConfig = KnownAppConfig.CA_OPAQUE
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
    fun testMigrateBeaconOpaque_To_JWTAndBack() {
        loginAndValidate(
            knownAppConfig = KnownAppConfig.BEACON_OPAQUE
        )
        migrateAndValidate(
            KnownAppConfig.BEACON_JWT
        )
        migrateAndValidate(
            KnownAppConfig.BEACON_OPAQUE
        )
    }

    // endregion

    override fun loginAndValidate(
        knownAppConfig: KnownAppConfig,
        scopeSelection: ScopeSelection,
        useWebServerFlow: Boolean,
        useHybridAuthToken: Boolean,
        knownLoginHostConfig: KnownLoginHostConfig,
        knownUserConfig: KnownUserConfig,
    ) {
        super.loginAndValidate(
            knownAppConfig,
            scopeSelection,
            useWebServerFlow,
            useHybridAuthToken = false,
            knownLoginHostConfig,
            knownUserConfig = user,
        )
    }
}