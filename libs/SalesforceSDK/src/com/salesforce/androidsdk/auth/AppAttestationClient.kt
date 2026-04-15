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
import androidx.annotation.VisibleForTesting
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory.createStandard
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.salesforce.androidsdk.rest.AppAttestationChallengeApiClient
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import com.salesforce.androidsdk.util.SalesforceSDKLogger.w
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.Base64

/**
 * App attestation features supporting the Salesforce App Attestation External
 * Client App (ECA) Plugin, the Salesforce Challenge API, Google Play Integrity
 * API and integration of app attestation with Salesforce Authentication.
 * @param googleCloudProjectId The Google Cloud Project ID used with Google Play
 * Integrity API
 * @param deviceId The device id, usually provided by the Salesforce SDK Manager
 * @param remoteAccessConsumerKey The Salesforce Connected App (CA) or External
 * Client App (ECA)remote access consumer key, usually provided by the boot
 * config
 * @param restClient The REST client, usually provided by the Salesforce SDK
 * Manager's unauthenticated REST client
 */
class AppAttestationClient(
    context: Context,
    val deviceId: String,
    val googleCloudProjectId: Long,
    val remoteAccessConsumerKey: String,
    val restClient: RestClient
) {

    /** The Google Play Integrity Manager and Token Provider */
    private val integrityManager = createStandard(context)


    /** The Google Play Integrity API Token Provider */
    @VisibleForTesting
    internal var integrityTokenProvider: StandardIntegrityTokenProvider? = null

    /**
     * Prepares for authorization and authorization token refresh with app
     * attestation using the Salesforce Mobile App Attestation Challenge API
     * and Google Play Integrity API.
     * @param googleCloudProjectId The Google Cloud Project ID
     */
    init {
        prepareIntegrityTokenProvider()
    }

    /**
     * (Re-)prepares the Google Play Integrity token provider.
     * @param integrityManager The Google Play Integrity API integrity manager.
     * This parameter is intended for testing purposes only
     */
    @VisibleForTesting
    internal fun prepareIntegrityTokenProvider(
        integrityManager: StandardIntegrityManager = this.integrityManager
    ): Task<StandardIntegrityTokenProvider> {

        // Prepare the Google Play Integrity token.  Calling this prior to requesting the Integrity Token reduces the latency of the request.
        return integrityManager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(googleCloudProjectId)
                .build()
        ).addOnSuccessListener(
            ::onPrepareIntegrityTokenProviderSuccess
        ).addOnFailureListener(
            ::onPrepareIntegrityTokenProviderFailure
        )
    }

    /**
     * A success callback used by [prepareIntegrityTokenProvider].
     * @param tokenProvider The Google Play API Integrity Token Provider
     */
    @VisibleForTesting
    internal fun onPrepareIntegrityTokenProviderSuccess(tokenProvider: StandardIntegrityTokenProvider) {
        integrityTokenProvider = tokenProvider
    }

    /**
     * A failure callback for [prepareIntegrityTokenProvider].
     * @param exception The exception provided by Google Play Integrity API
     */
    @VisibleForTesting
    internal fun onPrepareIntegrityTokenProviderFailure(exception: Exception) {
        w(javaClass.name, "Failed to prepare Google Play Integrity Token Provider: '${exception.message}'. App Attestation will be disabled.")
    }

    /**
     * Creates a Salesforce App Attestation External Client App (ECA) Plugin
     * "attestation".  First a Salesforce Mobile App Attestation "Challenge" is
     * requested for the device id.  Then, a Google Play Integrity API Token is
     * fetched using the "Challenge" as the Request Hash. The resulting token is
     * encoded into a value usable as the "attestation" parameter in the
     * Salesforce OAuth authorization request.
     * @param integrityManager The Google Play Integrity API integrity manager.
     * This parameter is intended for testing purposes only
     * @param integrityTokenProvider The Google Play App Integrity API Integrity
     * Token Provider.  This parameter is intended for testing purposes only
     * @return The "attestation" value usable in Salesforce OAuth authorization
     * and token refresh requests
     */
    suspend fun createSalesforceOAuthAuthorizationAppAttestation(
        integrityManager: StandardIntegrityManager = this.integrityManager,
        integrityTokenProvider: StandardIntegrityTokenProvider? = this.integrityTokenProvider,
    ): String? {
        // Guard to ensure the Google Play Integrity API Integrity Provider was asynchronously resolved or do so synchronously now
        val integrityTokenProviderResolved = integrityTokenProvider ?: prepareIntegrityTokenProvider(integrityManager).result

        // Fetch the Salesforce Mobile App Attestation Challenge.
        val salesforceAppAttestationChallenge = fetchSalesforceMobileAppAttestationChallenge()
        val salesforceAppAttestationChallengeHashByteArray = MessageDigest.getInstance("SHA-256")
            .digest(salesforceAppAttestationChallenge.toByteArray(UTF_8))
        val salesforceAppAttestationChallengeHashHexString = salesforceAppAttestationChallengeHashByteArray.joinToString("") { "%02x".format(it) }

        // Request the Google Play Integrity Token.
        val integrityTokenResponse = integrityTokenProviderResolved.request(
            StandardIntegrityTokenRequest.builder()
                .setRequestHash(salesforceAppAttestationChallengeHashHexString)
                .build()
        )
        val googlePlayIntegrityTask = integrityTokenResponse.addOnFailureListener { exception ->
            e(javaClass.name, "Failed To Receive Google Play Integrity Token: Message: '${exception.message}'.")
            // Synchronously re-prepare the Google Play Integrity Token Provider to provide a result to the caller.
            runBlocking { prepareIntegrityTokenProvider().await() /* TODO: Make this retry the request. ECJ20260414 */ }
        }

        /*
         * Wait for the Google Play Integrity API response and return the
         * Base64-encoded Salesforce OAuth authorization attestation parameter
         * JSON. This may block the calling thread if the Google Play Integrity
         * API introduces latency, though latency is expected to minimal as the
         * API will have been prepared earlier in most scenarios.
         */
        googlePlayIntegrityTask.await()
        return OAuthAuthorizationAttestation(
            attestationId = deviceId,
            attestationData = Base64.getEncoder().encodeToString(
                googlePlayIntegrityTask.getResult().token().encodeToByteArray()
            )
        ).toBase64String()
    }

    /**
     * A blocking Java-callable wrapper for
     * [createSalesforceOAuthAuthorizationAppAttestation]
     */
    @JvmName("createSalesforceOAuthAuthorizationAppAttestationBlocking")
    fun createSalesforceOAuthAuthorizationAppAttestationBlocking() = runBlocking { createSalesforceOAuthAuthorizationAppAttestation() }

    /**
     * Fetches a new "Challenge" from the Salesforce App Attestation External
     * Client App (ECA) Plug-In.
     * @return The Salesforce App Attestation ECA Plug-In's "Challenge"
     */
    internal fun fetchSalesforceMobileAppAttestationChallenge(): String {
        // Create the Salesforce App Attestation Challenge API client and fetch a new challenge.
        val appAttestationChallengeApiClient = AppAttestationChallengeApiClient(
            apiHostName = "msdkappattestationtestorg.test1.my.pc-rnd.salesforce.com", // TODO: Replace with template placeholder. ECJ20260311
            restClient = restClient
        )
        val salesforceAppAttestationChallenge = appAttestationChallengeApiClient.fetchChallenge(
            attestationId = deviceId,
            remoteConsumerKey = remoteAccessConsumerKey
        )

        return salesforceAppAttestationChallenge
    }
}

/**
 * A Salesforce OAuth 2.0 authorization "attestation" parameter.
 * @param attestationId The attestation id used when creating the Salesforce
 * Mobile App Attestation API Challenge.  This is intended to be the
 * Salesforce Mobile SDK device id
 * @param attestationData The token provided by the Google Play Integrity API
 */
@Serializable
internal data class OAuthAuthorizationAttestation(
    val attestationId: String,
    val attestationData: String,
) {

    /**
     * Returns a Base64-encoded JSON representation of this object
     */
    fun toBase64String(): String? = Base64.getEncoder().encodeToString(Json.encodeToString(serializer(), this).encodeToByteArray())
}

