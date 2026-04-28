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
import com.google.android.play.core.integrity.IntegrityManagerFactory.createStandard
import com.google.android.play.core.integrity.IntegrityServiceException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID
import com.salesforce.androidsdk.rest.AppAttestationChallengeApiClient
import com.salesforce.androidsdk.rest.RestClient
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
 *
 * This method is not intended for public use outside of Salesforce Mobile SDK.
 *
 * TODO: Make this class internal once Java support is removed. ECJ20260421
 *
 * @param apiHostName The Salesforce App Attestation Challenge API host
 * @param deviceId The device id, usually provided by the Salesforce SDK Manager
 * @param googleCloudProjectId The Google Cloud Project ID used with Google Play
 * Integrity API
 * @param integrityManager The Google Play App Integrity API Integrity Manager.
 * This parameter is intended for testing purposes only. Defaults to a new
 * instance
 * @param remoteAccessConsumerKey The Salesforce Connected App (CA) or External
 * Client App (ECA)remote access consumer key, usually provided by the boot
 * config
 * @param restClient The REST client, usually provided by the Salesforce SDK
 * Manager's unauthenticated REST client
 */
class AppAttestationClient(
    context: Context,
    @property:VisibleForTesting
    internal val apiHostName: String,
    @property:VisibleForTesting
    internal val deviceId: String,
    @property:VisibleForTesting
    internal val googleCloudProjectId: Long,
    @property:VisibleForTesting
    internal val integrityManager: StandardIntegrityManager = createStandard(context),
    @property:VisibleForTesting
    internal val remoteAccessConsumerKey: String,
    @property:VisibleForTesting
    internal val restClient: RestClient,
) {


    /** The Google Play Integrity API Token Provider */
    @VisibleForTesting
    internal var integrityTokenProvider: StandardIntegrityTokenProvider? = null

    init {
        prepareIntegrityTokenProvider()
    }

    /**
     * (Re-)prepares the Google Play Integrity Token Provider. Calling this
     * prior to requesting the Integrity Token via
     * [createAppAttestation] reduces the latency of the request.
     */
    @VisibleForTesting
    internal fun prepareIntegrityTokenProvider() = integrityManager.prepareIntegrityToken(
        PrepareIntegrityTokenRequest.builder()
            .setCloudProjectNumber(googleCloudProjectId)
            .build()
    ).addOnSuccessListener(
        ::onPrepareIntegrityTokenProviderSuccess
    ).addOnFailureListener(
        ::onPrepareIntegrityTokenProviderFailure
    )

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
     *
     * This method is not intended for public use outside of Salesforce Mobile
     * SDK.
     *
     * TODO: Make this Kotlin-internal once it is no longer referenced by Java. ECJ20260420
     *
     * @param appAttestationChallenge The Salesforce Mobile App Attestation
     * External Client App (ECA) Plug-In "Challenge" to use
     * @param integrityTokenProvider The Google Play App Integrity API Integrity
     * Token Provider.  This parameter is intended for testing purposes only
     * @param canRetryOnInvalidTokenProvider When true (the default), a single
     * inline retry with a freshly prepared Integrity Token Provider is allowed
     * if the request fails with [INTEGRITY_TOKEN_PROVIDER_INVALID].  The
     * recursive retry call sets this false to guarantee at most one retry
     * and prevent unbounded recursion on the caller thread
     * @return The "attestation" value usable in Salesforce OAuth authorization
     * and token refresh requests or null if the value cannot be created
     */
    suspend fun createAppAttestation(
        appAttestationChallenge: String,
        integrityTokenProvider: StandardIntegrityTokenProvider? = this.integrityTokenProvider,
        canRetryOnInvalidTokenProvider: Boolean = true,
    ): String? {
        // Guard to ensure the Google Play Integrity API Integrity Provider was asynchronously resolved or do so synchronously now.
        val integrityTokenProviderResolved = integrityTokenProvider ?: prepareIntegrityTokenProvider().await()

        // Fetch the Challenge from Salesforce Mobile App Attestation.
        val salesforceAppAttestationChallengeHashByteArray = MessageDigest.getInstance("SHA-256")
            .digest(appAttestationChallenge.toByteArray(UTF_8))
        val salesforceAppAttestationChallengeHashHexString = salesforceAppAttestationChallengeHashByteArray.joinToString("") { "%02x".format(it) }

        // Request the Google Play Integrity Token.
        val integrityTokenResponse = integrityTokenProviderResolved.request(
            StandardIntegrityTokenRequest.builder()
                .setRequestHash(salesforceAppAttestationChallengeHashHexString)
                .build()
        )

        /*
         * Wait for the Google Play Integrity API response and return the
         * Base64-encoded Salesforce OAuth authorization attestation parameter
         * JSON. This may block the calling thread if the Google Play Integrity
         * API introduces latency, though latency is expected to minimal as the
         * API will have been prepared earlier in most scenarios.
         */
        return runCatching {
            integrityTokenResponse.await()

            // When the Google Play Integrity API response is received, return the Base64-encoded Salesforce OAuth authorization attestation parameter JSON.
            OAuthAuthorizationAttestation(
                attestationId = deviceId,
                attestationData = Base64.getEncoder().encodeToString(
                    integrityTokenResponse.getResult().token().encodeToByteArray()
                )
            ).toBase64String()
        }.getOrElse { e ->
            // If the Google Play Integrity API failed due to the Integrity Token Provider being expired, re-prepare it once for an inline retry.
            // The retry call passes canRetryOnInvalidTokenProvider = false to cap retries at one attempt and prevent unbounded recursion on the caller thread if the freshly prepared provider also reports INTEGRITY_TOKEN_PROVIDER_INVALID.
            if (canRetryOnInvalidTokenProvider && (e as? IntegrityServiceException)?.errorCode == INTEGRITY_TOKEN_PROVIDER_INVALID) {
                createAppAttestation(
                    appAttestationChallenge = appAttestationChallenge,
                    integrityTokenProvider = null,
                    canRetryOnInvalidTokenProvider = false,
                )
            } else {
                null
            }
        }
    }

    /**
     * A blocking Java-callable wrapper for [createAppAttestation]
     *
     * This method is not intended for public use outside of Salesforce Mobile
     * SDK.
     *
     * TODO: Remove method when no longer referenced by Java. ECJ20260420
     * @param appAttestationChallenge The Salesforce Mobile App Attestation
     * External Client App (ECA) Plug-In "Challenge" to use
     */
    fun createAppAttestationBlocking(appAttestationChallenge: String) = runBlocking {
        createAppAttestation(appAttestationChallenge)
    }

    /**
     * Fetches a new "Challenge" from the Salesforce App Attestation External
     * Client App (ECA) Plug-In.
     *
     * This method is not intended for public use outside of Salesforce Mobile
     * SDK.
     *
     * TODO: Make this Kotlin-internal once it is no longer referenced by Java. ECJ20260420
     *
     * @return The Salesforce App Attestation ECA Plug-In's "Challenge"
     */
    fun fetchMobileAppAttestationChallenge(): String {
        // Create the Salesforce App Attestation Challenge API client and fetch a new challenge.
        val appAttestationChallengeApiClient = AppAttestationChallengeApiClient(
            apiHostName = apiHostName,
            restClient = restClient
        )
        return appAttestationChallengeApiClient.fetchChallenge(
            attestationId = deviceId,
            remoteConsumerKey = remoteAccessConsumerKey
        )
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
     * Returns a Base64-encoded JSON representation of this object.
     *
     * Note: Standard Base64 alphabet with padding is used by design. The
     * Salesforce App Attestation server-side contract requires the
     * standard (not URL-safe) Base64 encoding with padding, and the value
     * is consumed as-is without URI percent-encoding at the token endpoint
     * (see OAuth2.makeTokenEndpointRequest). This has been verified
     * end-to-end; do not switch to Base64.getUrlEncoder() or strip padding.
     */
    fun toBase64String(): String? = Base64.getEncoder().encodeToString(Json.encodeToString(serializer(), this).encodeToByteArray())
}

