package com.salesforce.androidsdk.auth

import android.app.Instrumentation
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.OAUTH_AUTH_PATH
import com.salesforce.androidsdk.auth.interfaces.OtpVerificationMethod
import com.salesforce.androidsdk.auth.interfaces.NativeLoginResult
import com.salesforce.androidsdk.rest.RestRequest
import android.util.Base64
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import com.salesforce.androidsdk.auth.dpop.DPoPKeyManager
import com.salesforce.androidsdk.auth.dpop.DPoPNonceCache
import com.salesforce.androidsdk.auth.dpop.DPoPProofBuilder
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestClient.OAuthRefreshInterceptor
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.SHOW_BIOMETRIC
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.withContext
import okhttp3.Call
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class NativeLoginManagerTest {
    private lateinit var mgr: NativeLoginManager
    private lateinit var bioAuthManager: BiometricAuthenticationManager
    private lateinit var activityMonitors: List<Instrumentation.ActivityMonitor>
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Retained before any mocking so that tearDown can clean up regardless of mock state. */
    private lateinit var realUserAccountManager: UserAccountManager
    private var originalUseDPoP = true

    @Before
    fun setUp() {
        val sdkManager = SalesforceSDKManager.getInstance()
        originalUseDPoP = sdkManager.useDPoP
        realUserAccountManager = sdkManager.userAccountManager
        activityMonitors = listOf(
            sdkManager.webViewLoginActivityClass,
            sdkManager.loginActivityClass,
            sdkManager.mainActivityClass,
        )
            .distinctBy { it.name }
            .map { instrumentation.addMonitor(it.name, null, true) }
        mgr = NativeLoginManager("clientId", "redirect", "loginUrl")
    }

    @After
    fun tearDown() {
        unmockkAll()
        SalesforceSDKManager.getInstance().useDPoP = originalUseDPoP
        realUserAccountManager.signoutCurrentUser(null, false, OAuth2.LogoutReason.USER_LOGOUT)
        instrumentation.waitForIdleSync()
        activityMonitors.forEach(instrumentation::removeMonitor)
        unmockkAll()
    }

    @Test
    fun testIsValidUsername() {
        Assert.assertFalse("Should not allow empty username.", mgr.isValidUsername(""))
        Assert.assertFalse("Should not allow invalid username.", mgr.isValidUsername("test@c"))
        // Success
        Assert.assertTrue(mgr.isValidUsername("test@c.co"))
    }

    @Test
    fun testIsValidPassword() {
        Assert.assertFalse("Should not allow empty password.", mgr.isValidPassword(""))
        Assert.assertFalse("Should not allow password shorter than 8 chars.", mgr.isValidPassword("test123"))
        Assert.assertFalse("Should not allow password without any letter chars.", mgr.isValidPassword("123456789"))
        Assert.assertFalse("Should not allow password without any numbers.", mgr.isValidPassword("abcdefghi"))
        // Success
        Assert.assertTrue(mgr.isValidPassword("test1234"))
    }

    @Test
    fun testShouldShowBackButton() {
        Assert.assertFalse("Should not show back button with no users logged in.", mgr.shouldShowBackButton)

        addUserAccount()
        Assert.assertTrue("Should show back button when there is a logged in user.", mgr.shouldShowBackButton)

        SalesforceSDKManager.getInstance().userAccountManager
            .signoutCurrentUser(null, false, OAuth2.LogoutReason.USER_LOGOUT)
        Assert.assertFalse("Should not show back button with no users logged in.", mgr.shouldShowBackButton)
    }

    @Test
    fun testShouldShowBackButtonBioAuth() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager as BiometricAuthenticationManager
        addUserAccount()
        Assert.assertTrue("Should show back button when there is a logged in user.", mgr.shouldShowBackButton)

        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        Assert.assertTrue("Should show back if not locked.", mgr.shouldShowBackButton)

        bioAuthManager.lock()
        Assert.assertFalse("Should not show back when bio auth locked.", mgr.shouldShowBackButton)
    }

    @Test
    fun testBiometricAuthenticationUsername() {
        Assert.assertNull("Should not return username with no user logged in.", mgr.biometricAuthenticationUsername)

        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager as BiometricAuthenticationManager
        addUserAccount()
        Assert.assertNull("Should not return username when bio auth is not enabled.", mgr.biometricAuthenticationUsername)

        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        Assert.assertNull("Should not return username when not locked.", mgr.biometricAuthenticationUsername)

        bioAuthManager.lock()
        assertEquals("Should return username.", "test_username", mgr.biometricAuthenticationUsername)
    }

    @Test
    fun nativeLoginManager_createRequestBody_filtersNullValues() {

        val result = mgr.createRequestBody("key1" to "value1", "key2" to null)

        val buffer = okio.Buffer()
        result.writeTo(buffer)
        assertEquals("key1=value1", buffer.readUtf8())
    }

    @Test
    fun testPresentBiometricAuthReturnsFalseWhenNotLocked() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager as BiometricAuthenticationManager
        addUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.biometricOptIn(true)
        // Not locked — should return false.
        val activity = mockk<FragmentActivity>(relaxed = true)
        val mockBiometricManager = mockk<BiometricManager>()
        val mockBiometricPrompt = mockk<BiometricPrompt>(relaxed = true)
        Assert.assertFalse("Should return false when not locked.", mgr.buildAndShowBiometricAuth(activity, mockBiometricManager, mockBiometricPrompt))
    }

    @Test
    fun testPresentBiometricAuthReturnsFalseWhenNotOptedIn() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager as BiometricAuthenticationManager
        addUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        // Opted out, but locked.
        bioAuthManager.lock()
        val activity = mockk<FragmentActivity>(relaxed = true)
        val mockBiometricManager = mockk<BiometricManager>()
        val mockBiometricPrompt = mockk<BiometricPrompt>(relaxed = true)
        Assert.assertFalse("Should return false when not opted in.", mgr.buildAndShowBiometricAuth(activity, mockBiometricManager, mockBiometricPrompt))
    }

    @Test
    fun testPresentBiometricAuthReturnsFalseWhenNoUser() {
        // No user account — bio auth manager won't be locked.
        val activity = mockk<FragmentActivity>(relaxed = true)
        val mockBiometricManager = mockk<BiometricManager>()
        val mockBiometricPrompt = mockk<BiometricPrompt>(relaxed = true)
        Assert.assertFalse("Should return false with no user.", mgr.buildAndShowBiometricAuth(activity, mockBiometricManager, mockBiometricPrompt))
    }

    @Test
    fun test_givenNativeFallback_whenIntentCreated_thenPreservesWebContractWithoutNativeDPoPState() {
        val intent = mgr.getFallbackWebAuthenticationIntent()
        assertEquals(SalesforceSDKManager.getInstance().webViewLoginActivityClass.name,
            intent.component?.className)
        assertEquals(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP, intent.flags)
        // The private launch marker revokes native authority, but carries no native DPoP state.
        val fallbackMarker = "com.salesforce.androidsdk.auth.NATIVE_FALLBACK"
        assertEquals(setOf(SHOW_BIOMETRIC, fallbackMarker), intent.extras?.keySet()?.toSet())
        Assert.assertTrue(intent.getBooleanExtra(fallbackMarker, false))
        Assert.assertFalse(
            "Fallback web auth intent should always have SHOW_BIOMETRIC=false.",
            intent.extras?.getBoolean(SHOW_BIOMETRIC) ?: true
        )
    }

    @Test
    fun testPresentBiometricAuthReturnsFalseWhenBioAuthManagerIsNull() {
        val sdkManager = SalesforceSDKManager.getInstance()
        val originalBioAuthManager = sdkManager.biometricAuthenticationManager
        try {
            sdkManager.biometricAuthenticationManager = null
            val activity = mockk<FragmentActivity>(relaxed = true)
            val mockBiometricManager = mockk<BiometricManager>()
            val mockBiometricPrompt = mockk<BiometricPrompt>(relaxed = true)
            Assert.assertFalse(
                "Should return false when bio auth manager is null.",
                mgr.buildAndShowBiometricAuth(activity, mockBiometricManager, mockBiometricPrompt)
            )
        } finally {
            sdkManager.biometricAuthenticationManager = originalBioAuthManager
        }
    }

    @Test
    fun testPresentBiometricAuthReturnsFalseWhenCannotAuthenticate() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
                as BiometricAuthenticationManager
        addUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.biometricOptIn(true)
        bioAuthManager.lock()

        val mockBiometricManager = mockk<BiometricManager>()
        every { mockBiometricManager.canAuthenticate(any()) } returns BIOMETRIC_ERROR_NO_HARDWARE
        val mockBiometricPrompt = mockk<BiometricPrompt>(relaxed = true)

        val activity = mockk<FragmentActivity>(relaxed = true)
        Assert.assertFalse(
            "Should return false when biometric hardware unavailable.",
            mgr.buildAndShowBiometricAuth(activity, mockBiometricManager, mockBiometricPrompt)
        )
    }

    @Test
    fun testPresentBiometricAuthReturnsTrueWhenAllConditionsMet() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
                as BiometricAuthenticationManager
        addUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.biometricOptIn(true)
        bioAuthManager.lock()

        val mockBiometricManager = mockk<BiometricManager>()
        every { mockBiometricManager.canAuthenticate(any()) } returns BIOMETRIC_SUCCESS
        val mockBiometricPrompt = mockk<BiometricPrompt>(relaxed = true)

        val activity = mockk<FragmentActivity>(relaxed = true)
        every { activity.resources.getString(any()) } returns "Biometric Auth"

        Assert.assertTrue(
            "Should return true when locked, opted in, and biometric available.",
            mgr.buildAndShowBiometricAuth(activity, mockBiometricManager, mockBiometricPrompt)
        )
    }

    @Test
    fun testOnBiometricAuthenticationSucceededRefreshesAndUnlocks() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
                as BiometricAuthenticationManager
        addUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.lock()
        Assert.assertTrue("Should be locked.", bioAuthManager.locked)

        val mockInterceptor = mockk<OAuthRefreshInterceptor>(relaxed = true)
        val mockClient = mockk<RestClient>(relaxed = true)
        every { mockClient.oAuthRefreshInterceptor } returns mockInterceptor

        val mockClientManager = mockk<ClientManager>()
        every { mockClientManager.peekRestClient() } returns mockClient

        val activity = mockk<FragmentActivity>(relaxed = true)
        mgr.onBiometricAuthenticationSucceeded(activity, mockClientManager)

        verify { mockInterceptor.refreshAccessToken() }
        Assert.assertFalse("Should be unlocked after success.", bioAuthManager.locked)
        verify { activity.finish() }
    }

    @Test
    fun testOnBiometricAuthenticationSucceededHandlesRefreshFailure() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
                as BiometricAuthenticationManager
        addUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.lock()

        val mockInterceptor = mockk<OAuthRefreshInterceptor>(relaxed = true)
        every { mockInterceptor.refreshAccessToken() } throws RuntimeException("Token refresh failed")
        val mockClient = mockk<RestClient>(relaxed = true)
        every { mockClient.oAuthRefreshInterceptor } returns mockInterceptor

        val mockClientManager = mockk<ClientManager>()
        every { mockClientManager.peekRestClient() } returns mockClient

        val activity = mockk<FragmentActivity>(relaxed = true)
        mgr.onBiometricAuthenticationSucceeded(activity, mockClientManager)

        // Should still unlock and finish even if refresh fails.
        Assert.assertFalse("Should be unlocked even after refresh failure.", bioAuthManager.locked)
        verify { activity.finish() }
    }

    @Test
    fun testOnBiometricAuthenticationSucceededKeepsLockAndFinishesWhenClientCannotBeBuilt() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
                as BiometricAuthenticationManager
        addUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.lock()

        val mockClientManager = mockk<ClientManager>()
        every { mockClientManager.peekRestClient() } returns null

        val activity = mockk<FragmentActivity>(relaxed = true)
        mgr.onBiometricAuthenticationSucceeded(activity, mockClientManager)

        Assert.assertTrue("The biometric lock must remain when no client can be built.", bioAuthManager.locked)
        verify(exactly = 1) { activity.finish() }
    }

    @Test
    fun testBiometricAuthenticationUsernameWithNativeLoginUser() {
        bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager as BiometricAuthenticationManager
        addNativeLoginUserAccount()
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        bioAuthManager.storeMobilePolicy(account, enabled = true, timeout = 15)
        bioAuthManager.lock()
        assertEquals(
            "Should return username for native login user when locked.",
            "test_username",
            mgr.biometricAuthenticationUsername
        )
    }

    /**
     * Tests that native login uses the app attestation during login.  This test
     * can be removed when a comprehensive test of native login is created so
     * long as that test covers the inclusion of the attestation parameter.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun nativeLoginManager_login_collectsAppAttestation() = runTest {

        installAppAttestationClient(attestation = TEST_APP_ATTESTATION)
        val restClient = createRestClientStubbingFailedLoginResponse()
        mgr = createNativeLoginManagerForTest(restClient = restClient)

        mgr.login(TEST_USERNAME, TEST_PASSWORD)
        advanceUntilIdle()

        verifyLoginRequestAttestation(restClient, expectedAttestationValue = TEST_APP_ATTESTATION)
    }

    /**
     * Tests that native login does not include app attestation during login
     * when the app attestation client is set but
     * [AppAttestationClient.createAppAttestation] returns null (for example,
     * because the Google Play Integrity API could not produce a token).  This
     * test can be removed when a comprehensive test of native login is created
     * so long as that test covers the exclusion of the attestation parameter.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun nativeLoginManager_login_doesNotCollectAppAttestationWhenCreateAppAttestationReturnsNull() = runTest {

        installAppAttestationClient(attestation = null)
        val restClient = createRestClientStubbingFailedLoginResponse()
        mgr = createNativeLoginManagerForTest(restClient = restClient)

        mgr.login(TEST_USERNAME, TEST_PASSWORD)
        advanceUntilIdle()

        verifyLoginRequestAttestation(restClient, expectedAttestationValue = null)
    }

    /**
     * Tests that native login does not include app attestation during login
     * when it is not applicable.  This test can be removed when a comprehensive
     * test of native login is created so long as that test covers the exclusion
     * of the attestation parameter.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun nativeLoginManager_login_doesNotCollectAppAttestationWhenAppAttestationClientIsNotSet() = runTest {

        val restClient = createRestClientStubbingFailedLoginResponse()
        mgr = createNativeLoginManagerForTest(restClient = restClient)

        mgr.login(TEST_USERNAME, TEST_PASSWORD)
        advanceUntilIdle()

        verifyLoginRequestAttestation(restClient, expectedAttestationValue = null)
    }

    /**
     * Tests that native login does not include app attestation during login
     * when the app attestation client is set but
     * [AppAttestationClient.fetchMobileAppAttestationChallenge] returns null
     * (for example, because [AppAttestationClient.apiHostName] is null, meaning
     * Salesforce App Attestation is disabled for the current login server).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun nativeLoginManager_login_doesNotCollectAppAttestationWhenFetchChallengeReturnsNull() = runTest {

        val mockAppAttestationClient = mockk<AppAttestationClient>(relaxed = true).apply {
            coEvery { fetchMobileAppAttestationChallenge() } returns null
        }
        val realSdkManager = SalesforceSDKManager.getInstance()
        val spySdkManager = spyk(realSdkManager)
        every { spySdkManager.appAttestationClient } returns mockAppAttestationClient
        mockkObject(SalesforceSDKManager)
        every { SalesforceSDKManager.getInstance() } returns spySdkManager

        val restClient = createRestClientStubbingFailedLoginResponse()
        mgr = createNativeLoginManagerForTest(restClient = restClient)

        mgr.login(TEST_USERNAME, TEST_PASSWORD)
        advanceUntilIdle()

        verifyLoginRequestAttestation(restClient, expectedAttestationValue = null)
    }

    /**
     * Tests that [NativeLoginManager.login] builds the Basic-Auth
     * `Authorization` header using the standard Base64 alphabet with padding
     * (RFC 4648 §4, as required by RFC 7617), rather than the URL-safe
     * alphabet.  The password below is chosen so that the colon-concatenated,
     * UTF-8-encoded username:password bytes Base64-encode differently under
     * the standard and URL-safe alphabets (contains `/` and requires `=`
     * padding under the standard alphabet), so a regression back to
     * URL-safe encoding would be caught.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun nativeLoginManager_login_usesStandardBase64ForAuthorizationHeader() = runTest {
        val restClient = createRestClientStubbingFailedLoginResponse()
        mgr = createNativeLoginManagerForTest(restClient = restClient)

        mgr.login(TEST_DIVERGING_USERNAME, TEST_DIVERGING_PASSWORD)
        advanceUntilIdle()

        val expectedCreds = java.util.Base64.getEncoder().encodeToString(
            "$TEST_DIVERGING_USERNAME:$TEST_DIVERGING_PASSWORD".toByteArray()
        )
        verifyLoginRequestAuthorizationHeader(restClient, "Basic $expectedCreds")
    }

    /**
     * Tests that [NativeLoginManager.submitPasswordlessAuthorizationRequest],
     * which shares the same colon-concatenated Base64 encoding helper as
     * [NativeLoginManager.login], also produces a standard-alphabet,
     * padded Basic-Auth header rather than URL-safe encoding.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun nativeLoginManager_submitPasswordlessAuthorizationRequest_usesStandardBase64ForAuthorizationHeader() = runTest {
        val restClient = createRestClientStubbingFailedLoginResponse()
        mgr = createNativeLoginManagerForTest(restClient = restClient)

        mgr.submitPasswordlessAuthorizationRequest(
            otp = TEST_DIVERGING_PASSWORD,
            otpIdentifier = TEST_DIVERGING_USERNAME,
            otpVerificationMethod = OtpVerificationMethod.Email
        )
        advanceUntilIdle()

        val expectedCreds = java.util.Base64.getEncoder().encodeToString(
            "$TEST_DIVERGING_USERNAME:$TEST_DIVERGING_PASSWORD".toByteArray()
        )
        verifyLoginRequestAuthorizationHeader(restClient, "Basic $expectedCreds")
    }

    // region Helpers used by attestation and Authorization-header tests

    @Test
    fun test_givenEnabledDPoP_whenNamedUserAuthorize_thenBindsCodeToBothProofs() = runTest {
        verifyNativeCodeBinding(0, true)
    }

    @Test
    fun test_givenEnabledDPoP_whenPasswordlessAuthorize_thenBindsCodeToBothProofs() = runTest {
        verifyNativeCodeBinding(1, true)
    }

    @Test
    fun test_givenEnabledDPoP_whenRegistrationAuthorize_thenBindsCodeToBothProofs() = runTest {
        verifyNativeCodeBinding(2, true)
    }

    @Test
    fun test_givenDisabledDPoP_whenAllNativeAuthorizePaths_thenOmitsCodeBinding() = runTest {
        for (flow in 0..2) verifyNativeCodeBinding(flow, false)
    }

    private suspend fun verifyNativeCodeBinding(flow: Int, enabled: Boolean) {
        installAppAttestationClient(attestation = null)
        SalesforceSDKManager.getInstance().useDPoP = enabled
        val requests = mutableListOf<RestRequest>()
        val client = scriptedNativeClient(requests, listOf(
            200 to "{\"code\":\"test-code\",\"sfdc_community_url\":\"https://community.example.test/site\"}",
            400 to "{\"error\":\"invalid_grant\"}",
        )) { SalesforceSDKManager.getInstance().useDPoP = !enabled }
        val manager = createNativeLoginManagerForTest(client)
        val result = when (flow) {
            0 -> manager.login(TEST_DIVERGING_USERNAME, TEST_DIVERGING_PASSWORD)
            1 -> manager.submitPasswordlessAuthorizationRequest(TEST_DIVERGING_PASSWORD, TEST_DIVERGING_USERNAME, OtpVerificationMethod.Email)
            else -> manager.completeRegistration(TEST_DIVERGING_PASSWORD, TEST_DIVERGING_USERNAME, OtpVerificationMethod.Sms)
        }
        assertEquals(NativeLoginResult.UnknownError, result)
        assertEquals(2, requests.size)
        val authorize = requests[0]
        val token = requests[1]
        val authorizeUri = android.net.Uri.parse(authorize.path)
        val binding = authorizeUri.getQueryParameter("dpop_jkt")
        if (enabled) {
            Assert.assertNotNull("Native authorize must bind the code with dpop_jkt", binding)
            assertEquals(setOf("dpop_jkt"), authorizeUri.queryParameterNames)
            requests.forEach { request ->
                val proof = checkNotNull(request.additionalHttpHeaders["DPoP"])
                assertEquals("Code binding must match both authorize and token proof JWKs", binding, proofThumbprint(proof))
                assertEquals(com.salesforce.androidsdk.auth.dpop.DPoPURLHelper.canonicalize(request.path), proofClaims(proof).getString("htu"))
                assertEquals("POST", proofClaims(proof).getString("htm"))
                Assert.assertFalse(proofClaims(proof).has("ath"))
            }
        } else {
            Assert.assertNull(binding)
            assertEquals("$TEST_LOGIN_URL$OAUTH_AUTH_PATH", authorize.path)
            requests.forEach { Assert.assertNull(it.additionalHttpHeaders?.get("DPoP")) }
        }
        assertEquals("https://community.example.test/site${OAuth2.OAUTH_TOKEN_PATH}", token.path)
        Assert.assertNull(android.net.Uri.parse(token.path).getQueryParameter("dpop_jkt"))
        val expectedBasic = java.util.Base64.getEncoder().encodeToString("$TEST_DIVERGING_USERNAME:$TEST_DIVERGING_PASSWORD".toByteArray())
        assertEquals("Basic $expectedBasic", authorize.additionalHttpHeaders["Authorization"])
        assertEquals(listOf("Named-User", "passwordless-login", "user-registration")[flow], authorize.additionalHttpHeaders["Auth-Request-Type"])
        if (flow != 0) assertEquals(if (flow == 1) "email" else "sms", authorize.additionalHttpHeaders["Auth-Verification-Type"])
        Assert.assertNull(token.additionalHttpHeaders?.get("Authorization"))
        val authBody = android.net.Uri.parse("https://body.example.test/?${requestBody(authorize)}")
        val tokenBody = android.net.Uri.parse("https://body.example.test/?${requestBody(token)}")
        assertEquals(setOf("response_type", "client_id", "redirect_uri", "code_challenge"), authBody.queryParameterNames)
        assertEquals(setOf("code", "grant_type", "client_id", "redirect_uri", "code_verifier"), tokenBody.queryParameterNames)
        assertEquals("code_credentials", authBody.getQueryParameter("response_type"))
        assertEquals("test-code", tokenBody.getQueryParameter("code"))
        assertEquals(if (SalesforceSDKManager.getInstance().useHybridAuthentication) OAuth2.HYBRID_AUTH_CODE else OAuth2.AUTHORIZATION_CODE, tokenBody.getQueryParameter("grant_type"))
        for (body in listOf(authBody, tokenBody)) {
            assertEquals(TEST_CLIENT_ID, body.getQueryParameter("client_id"))
            assertEquals(TEST_REDIRECT_URI, body.getQueryParameter("redirect_uri"))
        }
        assertEquals(com.salesforce.androidsdk.security.SalesforceKeyGenerator.getSHA256Hash(checkNotNull(tokenBody.getQueryParameter("code_verifier"))), authBody.getQueryParameter("code_challenge"))
        requests.forEach { assertEquals("application/x-www-form-urlencoded; charset=utf-8", it.requestBody.contentType().toString()) }
    }

    private fun proofThumbprint(proof: String): String {
        val jwk = JSONObject(String(Base64.decode(proof.split('.')[0], Base64.URL_SAFE))).getJSONObject("jwk")
        val canonical = "{\"crv\":\"${jwk.getString("crv")}\",\"kty\":\"${jwk.getString("kty")}\",\"x\":\"${jwk.getString("x")}\",\"y\":\"${jwk.getString("y")}\"}"
        return Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    @Test
    fun test_givenEnabledDPoP_whenNativeAuthorize_thenProofPreservesBasic() = runTest {
        SalesforceSDKManager.getInstance().useDPoP = true
        val requests = mutableListOf<RestRequest>()
        val client = scriptedNativeClient(requests, listOf(400 to "{\"error\":\"invalid_grant\"}"))
        val manager = NativeLoginManager(TEST_CLIENT_ID, TEST_REDIRECT_URI, "https://native.example.test/site", restClient = client)

        assertEquals(NativeLoginResult.InvalidCredentials, manager.login(TEST_USERNAME, TEST_PASSWORD))

        val request = requests.single()
        Assert.assertTrue(request.additionalHttpHeaders["Authorization"]?.startsWith("Basic ") == true)
        val proof = request.additionalHttpHeaders["DPoP"]
        Assert.assertNotNull("Enabled native authorization must carry a DPoP proof", proof)
        val claims = JSONObject(String(Base64.decode(proof!!.split('.')[1], Base64.URL_SAFE)))
        assertEquals("POST", claims.getString("htm"))
        assertEquals("https://native.example.test/site$OAUTH_AUTH_PATH", claims.getString("htu"))
        Assert.assertFalse(claims.has("ath"))
    }

    private fun scriptedNativeClient(
        requests: MutableList<RestRequest>,
        responses: List<Pair<Int, String>>,
        nonces: List<String?> = emptyList(),
        onRequest: (RestRequest) -> Unit = {},
    ): RestClient = mockk<RestClient>(relaxed = true).apply {
        every { sendAsync(any(), any()) } answers {
            val request = firstArg<RestRequest>()
            val (code, body) = responses[requests.size]
            val nonce = nonces.getOrNull(requests.size)
            requests.add(request)
            onRequest(request)
            val response = Response.Builder().request(Request.Builder().url(request.path).build())
                .protocol(Protocol.HTTP_1_1).code(code).message("scripted")
                .apply { nonce?.let { header("DPoP-Nonce", it) } }
                .body(body.toResponseBody()).build()
            secondArg<RestClient.AsyncRequestCallback>().onSuccess(request, RestResponse(response))
            mockk<Call>(relaxed = true)
        }
    }

    @Test
    fun test_givenNativeNonceChallenges_whenAuthorizeAndToken_thenRetryOnceWithFreshProofs() = runTest {
        SalesforceSDKManager.getInstance().useDPoP = true
        for (flow in 0..2) {
            val requests = mutableListOf<RestRequest>()
            val client = scriptedNativeClient(requests, listOf(
                400 to "{\"error\":\"use_dpop_nonce\"}",
                200 to "{\"code\":\"test-code\",\"sfdc_community_url\":\"https://community.example.test/site\"}",
                401 to "{\"error\":\"use_dpop_nonce\"}",
                400 to "{\"error\":\"invalid_grant\"}",
            ), listOf("authorize-challenge", "authorize-success", "token-challenge", "token-final")) {
                SalesforceSDKManager.getInstance().useDPoP = false
            }
            SalesforceSDKManager.getInstance().useDPoP = true
            val manager = NativeLoginManager(TEST_CLIENT_ID, TEST_REDIRECT_URI, "https://native.example.test/site", restClient = client)
            val result = when (flow) {
                0 -> manager.login(TEST_USERNAME, TEST_PASSWORD)
                1 -> manager.submitPasswordlessAuthorizationRequest("test-otp", "test-id", OtpVerificationMethod.Email)
                else -> manager.completeRegistration("test-otp", "test-id", OtpVerificationMethod.Sms)
            }
            assertEquals("Both endpoint challenges must recover within their retry bound", NativeLoginResult.UnknownError, result)
            assertEquals(4, requests.size)
            val proofs = requests.map { checkNotNull(it.additionalHttpHeaders["DPoP"]) }
            val claims = proofs.map(::proofClaims)
            assertEquals("authorize-challenge", claims[1].getString("nonce"))
            assertEquals("authorize-success", claims[2].getString("nonce"))
            assertEquals("token-challenge", claims[3].getString("nonce"))
            assertEquals(4, claims.map { it.getString("jti") }.toSet().size)
            assertEquals(1, proofs.map { JSONObject(String(Base64.decode(it.split('.')[0], Base64.URL_SAFE))).getJSONObject("jwk").toString() }.toSet().size)
            requests.forEachIndexed { index, request ->
                assertEquals(com.salesforce.androidsdk.auth.dpop.DPoPURLHelper.canonicalize(request.path), claims[index].getString("htu"))
                assertEquals("POST", claims[index].getString("htm"))
                Assert.assertFalse(claims[index].has("ath"))
            }
            assertEquals(requestBody(requests[0]), requestBody(requests[1]))
            assertEquals(requests[0].path, requests[1].path)
            val binding = android.net.Uri.parse(requests[0].path).getQueryParameter("dpop_jkt")
            Assert.assertNotNull(binding)
            proofs.forEach { assertEquals(binding, proofThumbprint(it)) }
            assertEquals(requestBody(requests[2]), requestBody(requests[3]))
            assertEquals(requests[0].additionalHttpHeaders["Authorization"], requests[1].additionalHttpHeaders["Authorization"])
        }
    }

    @Test
    fun test_givenRepeatedNativeChallenge_whenAuthorize_thenStopsAfterOneRetry() = runTest {
        SalesforceSDKManager.getInstance().useDPoP = true
        val requests = mutableListOf<RestRequest>()
        val client = scriptedNativeClient(requests, List(2) { 400 to "{\"error\":\"use_dpop_nonce\"}" }, listOf("first", "second"))
        val manager = NativeLoginManager(TEST_CLIENT_ID, TEST_REDIRECT_URI, "https://native.example.test", restClient = client)
        assertEquals(NativeLoginResult.InvalidCredentials, manager.login(TEST_USERNAME, TEST_PASSWORD))
        assertEquals("A repeated challenge terminates after exactly one retry", 2, requests.size)
        assertEquals("first", proofClaims(checkNotNull(requests[1].additionalHttpHeaders["DPoP"])).getString("nonce"))
    }

    @Test
    fun test_givenNativeChallengeWithoutNonce_whenAuthorize_thenDoesNotRetry() = runTest {
        SalesforceSDKManager.getInstance().useDPoP = true
        val requests = mutableListOf<RestRequest>()
        val client = scriptedNativeClient(requests, listOf(400 to "{\"error\":\"use_dpop_nonce\"}"))
        val manager = NativeLoginManager(TEST_CLIENT_ID, TEST_REDIRECT_URI, "https://native.example.test", restClient = client)
        assertEquals(NativeLoginResult.InvalidCredentials, manager.login(TEST_USERNAME, TEST_PASSWORD))
        assertEquals(1, requests.size)
    }

    @Test
    fun test_givenUnrelatedNonJsonErrorWithNonce_whenAuthorize_thenPreservesInvalidCredentials() = runTest {
        SalesforceSDKManager.getInstance().useDPoP = true
        val requests = mutableListOf<RestRequest>()
        val client = scriptedNativeClient(requests, listOf(400 to "not-json"), listOf("nonce"))
        val manager = NativeLoginManager(TEST_CLIENT_ID, TEST_REDIRECT_URI, TEST_LOGIN_URL, restClient = client)
        assertEquals(NativeLoginResult.InvalidCredentials, manager.login(TEST_USERNAME, TEST_PASSWORD))
        assertEquals(1, requests.size)
    }

    @Test
    fun test_givenDisabledNativeDPoP_whenFlagChanges_thenNoProofOrKeyIsCreated() = runTest {
        SalesforceSDKManager.getInstance().useDPoP = false
        val requests = mutableListOf<RestRequest>()
        val client = scriptedNativeClient(requests, listOf(
            200 to "{\"code\":\"test-code\",\"sfdc_community_url\":\"https://community.example.test/site\"}",
            400 to "{\"error\":\"invalid_grant\"}",
        )) { SalesforceSDKManager.getInstance().useDPoP = true }
        mockkObject(DPoPKeyManager)
        val manager = NativeLoginManager(TEST_CLIENT_ID, TEST_REDIRECT_URI, "https://native.example.test", restClient = client)
        assertEquals(NativeLoginResult.UnknownError, manager.login(TEST_USERNAME, TEST_PASSWORD))
        assertEquals(2, requests.size)
        requests.forEach { Assert.assertNull(it.additionalHttpHeaders?.get("DPoP")) }
        verify(exactly = 0) { DPoPKeyManager.generateOrLoadKeyPair(any()) }
    }

    @Test
    fun test_givenNativeNonChallenge401WithNonce_whenAuthorizeOrToken_thenDoesNotReplayCredentials() = runTest {
        SalesforceSDKManager.getInstance().useDPoP = true
        for (flow in 0..2) {
            for (tokenEndpoint in listOf(false, true)) {
                for (body in listOf("{\"error\":\"invalid_client\"}", "{\"error\":\"invalid_grant\"}", "{}", "not-json",
                    "{\"error_description\":\"use_dpop_nonce\"}")) {
                    val requests = mutableListOf<RestRequest>()
                    val responses = buildList {
                        if (tokenEndpoint) add(200 to "{\"code\":\"test-code\",\"sfdc_community_url\":\"https://community.example.test/site\"}")
                        add(401 to body)
                        // A replay must be observable without throwing inside the transport mock.
                        add(400 to "{\"error\":\"invalid_grant\"}")
                    }
                    val client = scriptedNativeClient(requests, responses, List(responses.size) { "nonce" })
                    val manager = NativeLoginManager(TEST_CLIENT_ID, TEST_REDIRECT_URI, TEST_LOGIN_URL, restClient = client)
                    val result = when (flow) {
                        0 -> manager.login(TEST_USERNAME, TEST_PASSWORD)
                        1 -> manager.submitPasswordlessAuthorizationRequest("test-otp", "test-id", OtpVerificationMethod.Email)
                        else -> manager.completeRegistration("test-otp", "test-id", OtpVerificationMethod.Sms)
                    }
                    Assert.assertNotEquals(NativeLoginResult.Success, result)
                    assertEquals("Unrelated 401 must not replay credentials: flow=$flow token=$tokenEndpoint body=$body",
                        if (tokenEndpoint) 2 else 1, requests.size)
                }
            }
        }
    }

    @Test
    fun test_givenNativeNonceChallenges_whenMissingNonceOrRepeatedAtEitherEndpoint_thenStopsWithinRetryBound() = runTest {
        SalesforceSDKManager.getInstance().useDPoP = true
        for (tokenEndpoint in listOf(false, true)) {
            for (statusCode in listOf(400, 401)) {
                for (hasNonce in listOf(false, true)) {
                    val requests = mutableListOf<RestRequest>()
                    val responses = buildList {
                        if (tokenEndpoint) add(200 to """{"code":"test-code","sfdc_community_url":"https://community.example.test/site"}""")
                        repeat(2) { add(statusCode to """{"error":"use_dpop_nonce"}""") }
                    }
                    val client = scriptedNativeClient(requests, responses, List(responses.size) { if (hasNonce) "challenge-nonce" else null })
                    val manager = NativeLoginManager(TEST_CLIENT_ID, TEST_REDIRECT_URI, TEST_LOGIN_URL, restClient = client)
                    assertEquals(if (tokenEndpoint) NativeLoginResult.UnknownError else NativeLoginResult.InvalidCredentials,
                        manager.login(TEST_USERNAME, TEST_PASSWORD))
                    assertEquals((if (tokenEndpoint) 1 else 0) + (if (hasNonce) 2 else 1), requests.size)
                }
            }
        }
    }

    private fun proofClaims(proof: String) = JSONObject(String(Base64.decode(proof.split('.')[1], Base64.URL_SAFE)))

    @Test
    fun test_givenDelayedAuthorize_whenNewNativeAttemptSucceeds_thenOldResultCannotChangeSession() = runTest {
        verifySupersededNativeAttempt("authorize")
    }

    @Test
    fun test_givenDelayedToken_whenNewNativeAttemptSucceeds_thenOldResultCannotChangeSession() = runTest {
        verifySupersededNativeAttempt("token")
    }

    @Test
    fun test_givenDelayedIdentity_whenNewNativeAttemptSucceeds_thenOldResultCannotChangeSession() = runTest {
        verifySupersededNativeAttempt("identity")
    }

    @Test
    fun test_givenDelayedBearerIdentity_whenNewNativeAttemptSucceeds_thenOldResultCannotChangeSession() = runTest {
        verifySupersededNativeAttempt("identity", false)
    }

    @Test
    fun test_givenRetryingIdentity_whenSupersededBySuccess_thenStopsBeforeRefresh() = runTest {
        verifySupersededNativeAttempt("identity-retry")
    }

    @Test
    fun test_givenDelayedRefresh_whenSupersededBySuccess_thenStopsBeforeIdentityRetry() = runTest {
        verifySupersededNativeAttempt("refresh-retry")
    }

    @Test
    fun test_givenRetryingIdentity_whenCancelled_thenStopsBeforeRefresh() = runTest {
        verifySupersededNativeAttempt("identity-retry", cancelled = true)
    }

    @Test
    fun test_givenDelayedRefresh_whenCancelled_thenStopsBeforeIdentityRetry() = runTest {
        verifySupersededNativeAttempt("refresh-retry", cancelled = true)
    }

    @Test
    fun test_givenHeldNativeToken_whenFallbackIntentOnlyConstructed_thenNativeCanStillComplete() = runTest {
        verifySupersededNativeAttempt("token", fallbackLifecycle = "intent-only")
    }

    @Test
    fun test_givenHeldBearerIdentity_whenFallbackActivityReceivesIntentAndBrowserSucceeds_thenNativeCannotChangeSession() = runTest {
        verifySupersededNativeAttempt("identity", dpop = false, fallbackLifecycle = "browser-success")
    }

    @Test
    fun test_givenHeldNativeToken_whenFallbackActivityReceivesIntentAndBrowserSucceeds_thenNativeCannotChangeSession() = runTest {
        verifySupersededNativeAttempt("token", fallbackLifecycle = "browser-success")
    }

    @Test
    fun test_givenHeldNativeIdentity_whenFallbackActivityReceivesIntentAndBrowserSucceeds_thenNativeCannotChangeSession() = runTest {
        verifySupersededNativeAttempt("identity", fallbackLifecycle = "browser-success")
    }

    @Test
    fun test_givenFailedNativeAttempt_whenFallbackIntentConstructedThenFreshNativeSucceeds_thenCredentialsRemainIndependent() = runTest {
        verifySupersededNativeAttempt("authorize", fallbackLifecycle = "failed")
    }

    @Test
    fun test_givenCancelledNativeAttempt_whenFallbackIntentConstructedAndOldCallbackArrivesDuringFreshCompletion_thenAccountsSurvive() = runTest {
        verifySupersededNativeAttempt("identity", fallbackLifecycle = "cancel-during")
    }

    @Test
    fun test_givenCancelledNativeAttempt_whenFallbackIntentConstructedAndOldCallbackArrivesAfterFreshCompletion_thenAccountsSurvive() = runTest {
        verifySupersededNativeAttempt("identity", fallbackLifecycle = "cancel-after")
    }

    @Test
    fun test_givenCancelledNativeToken_whenFallbackIntentConstructedAndHeldCallbackArrivesAfterFreshSuccess_thenAccountsSurvive() = runTest {
        verifySupersededNativeAttempt("token", fallbackLifecycle = "cancel-after")
    }

    private suspend fun verifySupersededNativeAttempt(boundary: String, dpop: Boolean = true, cancelled: Boolean = false,
        fallbackLifecycle: String? = null) = kotlinx.coroutines.coroutineScope {
        val sdk = SalesforceSDKManager.getInstance()
        val originalDPoP = sdk.useDPoP
        sdk.useDPoP = dpop
        val originalBio = sdk.biometricAuthenticationManager
        sdk.biometricAuthenticationManager = null
        val originalHttp = HttpAccess.DEFAULT
        val entered = CompletableDeferred<Unit>()
        val releaseIdentity = java.util.concurrent.CountDownLatch(1)
        val freshIdentityEntered = CompletableDeferred<Unit>()
        val releaseFreshIdentity = java.util.concurrent.CountDownLatch(1)
        var delayed: (() -> Unit)? = null
        val aliases = mutableSetOf<String>()
        val oldIdentityCalls = java.util.concurrent.atomic.AtomicInteger()
        val oldRefreshCalls = java.util.concurrent.atomic.AtomicInteger()
        val nativeRequests = mutableListOf<Pair<Boolean, RestRequest>>()
        val otherId = "fallback-existing-${java.util.UUID.randomUUID()}"
        val otherAlias = DPoPKeyManager.aliasForCredentialsIdentifier(otherId)
        val otherAccount = UserAccountBuilder.getInstance()
            .populateFromUserAccount(UserAccountTest.createTestAccount())
            .credentialsIdentifier(otherId).tokenType("DPoP").build()
        val otherKey = if (fallbackLifecycle != null) {
            realUserAccountManager.createAccount(otherAccount)
            DPoPNonceCache.store(otherId, "identity.example.test", "existing-nonce")
            DPoPKeyManager.generateOrLoadKeyPair(otherAlias).public.encoded
        } else null
        mockkObject(DPoPKeyManager)
        every { DPoPKeyManager.generateOrLoadKeyPair(any()) } answers {
            synchronized(aliases) { aliases.add(firstArg()) }
            callOriginal()
        }
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val refresh = chain.request().url.encodedPath.endsWith(OAuth2.OAUTH_TOKEN_PATH)
            val old = refresh || chain.request().header("Authorization")?.endsWith("old-access") == true
            val identityCall = if (old && !refresh) oldIdentityCalls.incrementAndGet() else 0
            if (old && refresh) oldRefreshCalls.incrementAndGet()
            if (!old && fallbackLifecycle == "cancel-during") {
                freshIdentityEntered.complete(Unit)
                check(releaseFreshIdentity.await(30, java.util.concurrent.TimeUnit.SECONDS))
            }
            if (old && (boundary == "identity" ||
                        boundary == "identity-retry" && identityCall == 1 ||
                        boundary == "refresh-retry" && refresh)) {
                entered.complete(Unit)
                check(releaseIdentity.await(30, java.util.concurrent.TimeUnit.SECONDS))
            }
            if (old && refresh) {
                return@addInterceptor Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("scripted").body("""{"access_token":"old-access","token_type":"DPoP"}""".toResponseBody()).build()
            }
            if (old && identityCall == 1 && boundary.endsWith("-retry")) {
                return@addInterceptor Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(401).message("scripted").body("""{"error":"BAD_OAUTH_TOKEN"}""".toResponseBody()).build()
            }
            val suffix = if (old) "001" else "002"
            val name = if (old) "old" else "new"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("scripted")
                .body("""{"username":"$name@example.test","email":"$name@example.test","first_name":"Native","last_name":"Test","display_name":"Native Test","id":"https://identity.example.test/id/00D000000000001/005000000000$suffix","user_id":"005000000000$suffix","organization_id":"00D000000000001","nick_name":"$name","user_type":"Standard","language":"en_US","locale":"en_US","last_modified_date":"2026-01-01T00:00:00.000Z","is_salesforce_integration_user":false}""".toResponseBody()).build()
        }.build()
        HttpAccess.DEFAULT = mockk<HttpAccess>().apply { every { okHttpClient } returns http }
        fun client(old: Boolean): RestClient = mockk<RestClient>().apply {
            every { sendAsync(any(), any()) } answers {
                val request = firstArg<RestRequest>()
                nativeRequests.add(old to request)
                val callback = secondArg<RestClient.AsyncRequestCallback>()
                val authorize = request.path.contains(OAUTH_AUTH_PATH)
                val body = if (authorize) {
                    """{"code":"test-code","sfdc_community_url":"https://community.example.test/site"}"""
                } else {
                    """{"access_token":"${if (old) "old" else "new"}-access","refresh_token":"test-refresh","instance_url":"https://community.example.test","id":"https://identity.example.test/id/00D000000000001/005000000000${if (old) "001" else "002"}","token_type":"${if (dpop) "DPoP" else "Bearer"}","scope":"api"}"""
                }
                val deliver = {
                    callback.onSuccess(request, RestResponse(Response.Builder()
                        .request(Request.Builder().url(request.path).build()).protocol(Protocol.HTTP_1_1)
                        .code(if (old && fallbackLifecycle == "failed") 400 else 200)
                        .message("scripted")
                        .header("DPoP-Nonce", if (old) "abandoned-nonce" else "fresh-nonce")
                        .body((if (old && fallbackLifecycle == "failed") """{"error":"invalid_grant"}""" else body).toResponseBody()).build()))
                }
                if (old && boundary == if (authorize) "authorize" else "token") {
                    delayed = deliver
                    entered.complete(Unit)
                } else deliver()
                mockk<Call>(relaxed = true)
            }
        }
        val oldManager = createNativeLoginManagerForTest(client(true))
        // Different Experience Cloud path, client and redirect still share account-switch authority.
        val newManager = NativeLoginManager("successor-client", "successor-redirect",
            "https://native.example.test/other-site", restClient = client(false))
        val oldAttempt = async { oldManager.login(TEST_USERNAME, TEST_PASSWORD) }
        try {
            entered.await()
            var abandonedId: String? = null
            var abandonedKey: ByteArray? = null
            if (fallbackLifecycle != null) {
                val oldAlias = synchronized(aliases) { aliases.singleOrNull() }
                abandonedId = oldAlias?.removePrefix("dpop_")
                abandonedKey = oldAlias?.let { DPoPKeyManager.generateOrLoadKeyPair(it).public.encoded }
                abandonedId?.let { DPoPNonceCache.store(it, "identity.example.test", "abandoned-nonce") }
                if (fallbackLifecycle == "failed") {
                    checkNotNull(delayed).invoke()
                    delayed = null
                    assertEquals(NativeLoginResult.InvalidCredentials, oldAttempt.await())
                } else if (fallbackLifecycle != "browser-success" && fallbackLifecycle != "intent-only") oldAttempt.cancel()
                val fallback = oldManager.getFallbackWebAuthenticationIntent()
                assertEquals(sdk.webViewLoginActivityClass.name, fallback.component?.className)
                assertEquals(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP, fallback.flags)
                Assert.assertNull(fallback.getStringExtra("credentialsIdentifier"))
                Assert.assertFalse(fallback.getBooleanExtra(SHOW_BIOMETRIC, true))
                assertEquals(otherId, realUserAccountManager.currentUser.credentialsIdentifier)
                if (fallbackLifecycle == "browser-success" || fallbackLifecycle == "intent-only") {
                    if (fallbackLifecycle == "browser-success") {
                        instrumentation.runOnMainSync {
                            val original = object : com.salesforce.androidsdk.ui.LoginActivity() {
                                override val viewModel = mockk<com.salesforce.androidsdk.ui.LoginViewModel>(relaxed = true).apply {
                                    every { pendingServer } returns androidx.lifecycle.MediatorLiveData<String>()
                                }
                                fun receiveFallback(intent: android.content.Intent) = onNewIntent(intent)
                            }
                            val model = original.viewModel
                            val activity = spyk(original)
                            every { activity.viewModel } returns model
                            every { activity.webView } returns mockk(relaxed = true)
                            activity.receiveFallback(fallback)
                        }
                    }
                    // Simulate independent browser persistence/selection, not a new native attempt.
                    realUserAccountManager.switchToUser(otherAccount)
                    releaseIdentity.countDown()
                    delayed?.invoke()
                    assertEquals("Fallback launch must revoke held native completion authority",
                        if (fallbackLifecycle == "intent-only") NativeLoginResult.Success else NativeLoginResult.UnknownError,
                        oldAttempt.await())
                    assertEquals(if (fallbackLifecycle == "intent-only") 2 else 1, realUserAccountManager.authenticatedUsers.size)
                    if (fallbackLifecycle == "intent-only") {
                        assertEquals("old@example.test", realUserAccountManager.currentUser.username)
                    } else {
                        assertEquals(otherId, realUserAccountManager.currentUser.credentialsIdentifier)
                        assertEquals(otherAccount.authToken, realUserAccountManager.currentUser.authToken)
                        assertEquals(otherAccount.tokenType, realUserAccountManager.currentUser.tokenType)
                        oldAlias?.let { Assert.assertFalse(DPoPKeyManager.hasKeyPair(it)) }
                        abandonedId?.let { Assert.assertNull(DPoPNonceCache.get(it, "identity.example.test")) }
                    }
                    Assert.assertArrayEquals(otherKey, DPoPKeyManager.generateOrLoadKeyPair(otherAlias).public.encoded)
                    assertEquals("existing-nonce", DPoPNonceCache.get(otherId, "identity.example.test"))
                    return@coroutineScope
                }
            }
            if (cancelled) {
                oldAttempt.cancel()
                releaseIdentity.countDown()
                oldAttempt.join()
                assertEquals(1, oldIdentityCalls.get())
                assertEquals(if (boundary == "refresh-retry") 1 else 0, oldRefreshCalls.get())
                Assert.assertTrue(realUserAccountManager.authenticatedUsers.isNullOrEmpty())
                synchronized(aliases) { aliases.forEach {
                    Assert.assertFalse("Cancelled attempt key must be deleted", DPoPKeyManager.hasKeyPair(it))
                } }
                return@coroutineScope
            }
            val freshAttempt = async {
                newManager.submitPasswordlessAuthorizationRequest("test-otp", "test-id", OtpVerificationMethod.Email)
            }
            if (fallbackLifecycle == "cancel-during") {
                freshIdentityEntered.await()
                releaseIdentity.countDown()
                oldAttempt.join()
                assertEquals("Old cleanup must not persist an account during fresh identity", 1,
                    realUserAccountManager.authenticatedUsers.size)
                assertEquals(otherId, realUserAccountManager.currentUser.credentialsIdentifier)
                releaseFreshIdentity.countDown()
            }
            assertEquals(NativeLoginResult.Success, freshAttempt.await())
            val successor = realUserAccountManager.currentUser
            assertEquals("new@example.test", successor.username)
            assertEquals("successor-client", successor.clientId)
            assertEquals("successor-redirect", successor.redirectUri)
            assertEquals("https://native.example.test/other-site", successor.loginServer)
            val successorId = successor.credentialsIdentifier
            if (fallbackLifecycle != null) {
                Assert.assertNotEquals(abandonedId, successorId)
                val successorKey = DPoPKeyManager.generateOrLoadKeyPair(
                    DPoPKeyManager.aliasForCredentialsIdentifier(checkNotNull(successorId))).public.encoded
                Assert.assertFalse(checkNotNull(abandonedKey).contentEquals(successorKey))
                Assert.assertFalse(checkNotNull(otherKey).contentEquals(successorKey))
                val freshAuthorize = nativeRequests.first { !it.first }.second
                Assert.assertFalse("Fresh native authorization must not carry an abandoned nonce",
                    proofClaims(checkNotNull(freshAuthorize.additionalHttpHeaders["DPoP"])).has("nonce"))
                DPoPNonceCache.store(successorId, "identity.example.test", "successor-nonce")
            }
            releaseIdentity.countDown()
            delayed?.invoke()
            if (fallbackLifecycle?.startsWith("cancel-") == true) {
                oldAttempt.join()
                Assert.assertTrue(oldAttempt.isCancelled)
            } else assertEquals("Superseded result must be ignored",
                if (fallbackLifecycle == "failed") NativeLoginResult.InvalidCredentials else NativeLoginResult.UnknownError,
                oldAttempt.await())
            if (boundary.endsWith("-retry")) {
                assertEquals("Superseded identity must not be retried", 1, oldIdentityCalls.get())
                assertEquals("Superseded identity must not start another refresh",
                    if (boundary == "refresh-retry") 1 else 0, oldRefreshCalls.get())
            }
            assertEquals("Old completion must not create another account", if (fallbackLifecycle == null) 1 else 2,
                realUserAccountManager.authenticatedUsers.size)
            assertEquals("new@example.test", realUserAccountManager.currentUser.username)
            assertEquals(successorId, realUserAccountManager.currentUser.credentialsIdentifier)
            if (fallbackLifecycle != null) {
                assertEquals("new-access", realUserAccountManager.currentUser.authToken)
                val persistedOther = realUserAccountManager.authenticatedUsers.single { it.credentialsIdentifier == otherId }
                assertEquals(otherAccount.username, persistedOther.username)
                assertEquals(otherAccount.authToken, persistedOther.authToken)
                assertEquals("DPoP", persistedOther.tokenType)
                Assert.assertTrue("Existing account key must survive old cleanup", DPoPKeyManager.hasKeyPair(otherAlias))
                Assert.assertArrayEquals(otherKey, DPoPKeyManager.generateOrLoadKeyPair(otherAlias).public.encoded)
                assertEquals("existing-nonce", DPoPNonceCache.get(otherId, "identity.example.test"))
                assertEquals("successor-nonce", DPoPNonceCache.get(checkNotNull(successorId), "identity.example.test"))
                Assert.assertNull(DPoPNonceCache.get(checkNotNull(abandonedId), "identity.example.test"))
            }
            if (dpop) {
                val successorAlias = DPoPKeyManager.aliasForCredentialsIdentifier(checkNotNull(successorId))
                Assert.assertTrue("Successor key must survive stale cleanup", DPoPKeyManager.hasKeyPair(successorAlias))
                synchronized(aliases) { aliases.filter { it != successorAlias && it != otherAlias }.forEach {
                    Assert.assertFalse("Abandoned attempt key must be deleted", DPoPKeyManager.hasKeyPair(it))
                } }
            }
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    releaseIdentity.countDown()
                    releaseFreshIdentity.countDown()
                    oldAttempt.cancel()
                    oldAttempt.join()
                    val androidAccounts = android.accounts.AccountManager.get(sdk.appContext)
                    androidAccounts.getAccountsByType(sdk.accountType).forEach(androidAccounts::removeAccountExplicitly)
                    realUserAccountManager.clearCachedCurrentUser()
                    synchronized(aliases) { aliases.forEach(DPoPKeyManager::deleteKeyPair) }
                    synchronized(aliases) { aliases.forEach { DPoPNonceCache.clear(it.removePrefix("dpop_")) } }
                    if (fallbackLifecycle != null) {
                        DPoPKeyManager.deleteKeyPair(otherAlias)
                        DPoPNonceCache.clear(otherId)
                    }
                } finally {
                    HttpAccess.DEFAULT = originalHttp
                    sdk.biometricAuthenticationManager = originalBio
                    sdk.useDPoP = originalDPoP
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun test_givenDeliveredResponse_whenCancelledBeforeDispatch_thenDisposesResponse() = runTest {
        SalesforceSDKManager.getInstance().useDPoP = true
        val call = mockk<Call>(relaxed = true)
        val entered = CompletableDeferred<Unit>()
        lateinit var callback: RestClient.AsyncRequestCallback
        val client = mockk<RestClient>().apply {
            every { sendAsync(any(), any()) } answers {
                callback = secondArg()
                entered.complete(Unit)
                call
            }
        }
        val response = mockk<RestResponse>(relaxed = true)
        val manager = createNativeLoginManagerForTest(client)
        val job = launch { manager.login(TEST_USERNAME, TEST_PASSWORD) }
        entered.await()
        callback.onSuccess(null, response)
        job.cancel()
        advanceUntilIdle()
        job.join()
        verify(exactly = 1) { response.consumeQuietly() }
        verify(exactly = 1) { call.cancel() }
    }

    @Test
    fun test_givenPersistedNativeAccount_whenCompletionFailsBeforeSuccess_thenRetainsReferencedKey() = runTest {
        for (cancelled in listOf(false, true)) {
            SalesforceSDKManager.getInstance().useDPoP = true
            var identifier: String? = null
            mockkStatic("com.salesforce.androidsdk.auth.AuthenticationUtilitiesKt")
            coEvery {
                onAuthFlowComplete(
                    tokenResponse = any(), loginServer = any(), consumerKey = any(),
                    redirectUri = any(), onAuthFlowError = any(), onAuthFlowSuccess = any(),
                    buildAccountName = any(), nativeLogin = any(), tokenMigration = any(),
                    credentialsIdentifier = any(), context = any(), userAccountManager = any(),
                    blockIntegrationUser = any(), runtimeConfig = any(), updateLoggingPrefs = any(),
                    fetchUserIdentity = any(), fetchUserIdentityResult = any(), startMainActivity = any(),
                    setAdministratorPreferences = any(), addAccount = any(), persistScreenLockFeature = any(),
                    handleScreenLockPolicy = any(), handleBiometricAuthPolicy = any(),
                    handleDuplicateUserAccount = any(), onAuthFlowFinished = any(),
                )
            } coAnswers {
                identifier = checkNotNull(arg<String?>(9))
                val account = UserAccountBuilder.getInstance()
                    .populateFromUserAccount(UserAccountTest.createTestAccount())
                    .credentialsIdentifier(identifier).tokenType("DPoP").build()
                realUserAccountManager.createAccount(account)
                if (cancelled) throw kotlinx.coroutines.CancellationException("cancelled after persistence")
                throw IllegalStateException("failed after persistence, before success callback")
            }
            val requests = mutableListOf<RestRequest>()
            val client = scriptedNativeClient(requests, listOf(
                200 to "{\"code\":\"test-code\",\"sfdc_community_url\":\"https://community.example.test/site\"}",
                200 to "{\"access_token\":\"test-access\",\"instance_url\":\"https://community.example.test\",\"id\":\"https://identity.example.test/id/00D000000000001/005000000000001\",\"token_type\":\"DPoP\",\"scope\":\"api\"}",
            ))
            var persistedAccount: com.salesforce.androidsdk.accounts.UserAccount? = null
            try {
                val result = runCatching { createNativeLoginManagerForTest(client).login(TEST_USERNAME, TEST_PASSWORD) }
                if (cancelled) Assert.assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
                else assertEquals(NativeLoginResult.UnknownError, result.getOrThrow())
                persistedAccount = realUserAccountManager.authenticatedUsers
                    ?.singleOrNull { it.credentialsIdentifier == identifier }
                assertEquals(identifier, persistedAccount?.credentialsIdentifier)
                Assert.assertTrue("Persisted account must retain its key before the success callback",
                    DPoPKeyManager.hasKeyPair(DPoPKeyManager.aliasForCredentialsIdentifier(checkNotNull(identifier))))
            } finally {
                (persistedAccount ?: identifier?.let { credentialsIdentifier ->
                    realUserAccountManager.authenticatedUsers
                        ?.singleOrNull { it.credentialsIdentifier == credentialsIdentifier }
                })?.let {
                    realUserAccountManager.signoutUser(it, null, false, OAuth2.LogoutReason.USER_LOGOUT)
                }
                identifier?.let {
                    DPoPKeyManager.deleteKeyPair(DPoPKeyManager.aliasForCredentialsIdentifier(it))
                    DPoPNonceCache.clear(it)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun test_givenPendingNativeRequest_whenCancelled_thenCancelsTransportAndCleansOnlyItsKey() = runTest {
        SalesforceSDKManager.getInstance().useDPoP = true
        val call = mockk<Call>(relaxed = true)
        val entered = CompletableDeferred<Unit>()
        var callback: RestClient.AsyncRequestCallback? = null
        val aliases = mutableListOf<String>()
        mockkObject(DPoPKeyManager)
        every { DPoPKeyManager.generateOrLoadKeyPair(any()) } answers {
            aliases.add(firstArg())
            callOriginal()
        }
        val otherId = "other-${java.util.UUID.randomUUID()}"
        val otherAlias = DPoPKeyManager.aliasForCredentialsIdentifier(otherId)
        DPoPKeyManager.generateOrLoadKeyPair(otherAlias)
        DPoPNonceCache.store(otherId, "other.example.test", "other-nonce")
        val client = mockk<RestClient>().apply {
            every { sendAsync(any(), any()) } answers {
                callback = secondArg()
                entered.complete(Unit)
                call
            }
        }
        val manager = NativeLoginManager(TEST_CLIENT_ID, TEST_REDIRECT_URI, TEST_LOGIN_URL, restClient = client)
        val job = launch { manager.login(TEST_USERNAME, TEST_PASSWORD) }
        entered.await()
        val nativeAlias = aliases.last()
        job.cancel()
        advanceUntilIdle()
        job.join()
        try {
            verify(exactly = 1) { call.cancel() }
            Assert.assertFalse(DPoPKeyManager.hasKeyPair(nativeAlias))
            Assert.assertTrue(DPoPKeyManager.hasKeyPair(otherAlias))
            assertEquals("other-nonce", DPoPNonceCache.get(otherId, "other.example.test"))
        } finally {
            callback?.onSuccess(null, null)
            job.join()
            DPoPKeyManager.deleteKeyPair(otherAlias)
            DPoPNonceCache.clear(otherId)
        }
    }

    @Test
    fun test_givenPendingNativeCompletion_whenCancelled_thenCannotRetainAbandonedKey() = runTest {
        SalesforceSDKManager.getInstance().useDPoP = true
        val entered = CompletableDeferred<String>()
        mockkStatic("com.salesforce.androidsdk.auth.AuthenticationUtilitiesKt")
        coEvery {
            onAuthFlowComplete(
                tokenResponse = any(), loginServer = any(), consumerKey = any(),
                redirectUri = any(), onAuthFlowError = any(), onAuthFlowSuccess = any(),
                buildAccountName = any(), nativeLogin = any(), tokenMigration = any(),
                credentialsIdentifier = any(), context = any(), userAccountManager = any(),
                blockIntegrationUser = any(), runtimeConfig = any(), updateLoggingPrefs = any(),
                fetchUserIdentity = any(), fetchUserIdentityResult = any(), startMainActivity = any(),
                setAdministratorPreferences = any(), addAccount = any(), persistScreenLockFeature = any(),
                handleScreenLockPolicy = any(), handleBiometricAuthPolicy = any(),
                handleDuplicateUserAccount = any(), onAuthFlowFinished = any(),
            )
        } coAnswers {
            entered.complete(checkNotNull(arg<String?>(9)))
            kotlinx.coroutines.awaitCancellation()
        }
        val requests = mutableListOf<RestRequest>()
        val client = scriptedNativeClient(requests, listOf(
            200 to "{\"code\":\"test-code\",\"sfdc_community_url\":\"https://community.example.test/site\"}",
            200 to "{\"access_token\":\"test-access\",\"instance_url\":\"https://community.example.test\",\"id\":\"https://identity.example.test/id/00D000000000001/005000000000001\",\"token_type\":\"DPoP\",\"scope\":\"api\"}",
        ))
        val manager = NativeLoginManager(TEST_CLIENT_ID, TEST_REDIRECT_URI, TEST_LOGIN_URL, restClient = client)
        val job = launch { manager.login(TEST_USERNAME, TEST_PASSWORD) }
        val identifier = entered.await()
        job.cancel()
        job.join()
        val alias = DPoPKeyManager.aliasForCredentialsIdentifier(identifier)
        try {
            Assert.assertFalse("Cancelled completion must not retain its abandoned key", DPoPKeyManager.hasKeyPair(alias))
        } finally {
            DPoPKeyManager.deleteKeyPair(alias)
            DPoPNonceCache.clear(identifier)
        }
    }

    @Test
    fun test_givenFailedNativeLoginOnMain_whenCleaningCredentials_thenCleanupRunsOffMain() = runTest {
        verifyNativeCleanupOffMain(false)
    }

    @Test
    fun test_givenCancelledNativeLoginOnMain_whenCleaningCredentials_thenCleanupRunsOffMain() = runTest {
        verifyNativeCleanupOffMain(true)
    }

    private suspend fun verifyNativeCleanupOffMain(cancelled: Boolean) = kotlinx.coroutines.coroutineScope {
        val sdk = SalesforceSDKManager.getInstance()
        sdk.useDPoP = true
        val otherId = "cleanup-existing-${java.util.UUID.randomUUID()}"
        realUserAccountManager.createAccount(UserAccountBuilder.getInstance()
            .populateFromUserAccount(UserAccountTest.createTestAccount())
            .credentialsIdentifier(otherId).tokenType("DPoP").build())
        val otherAlias = DPoPKeyManager.aliasForCredentialsIdentifier(otherId)
        val otherKey = DPoPKeyManager.generateOrLoadKeyPair(otherAlias).public.encoded
        DPoPNonceCache.store(otherId, "native.example.test", "existing-nonce")
        val cleanupThreads = java.util.concurrent.CopyOnWriteArrayList<Pair<String, Boolean>>()
        val realAccounts = android.accounts.AccountManager.get(sdk.appContext)
        val persistedAccounts = realAccounts.getAccountsByType(sdk.accountType)
        val persistedReferences = persistedAccounts.associateWith {
            realAccounts.getUserData(it, AuthenticatorService.KEY_CREDENTIALS_IDENTIFIER)
        }
        val accounts = mockk<android.accounts.AccountManager>()
        mockkStatic(android.accounts.AccountManager::class)
        every { android.accounts.AccountManager.get(any()) } returns accounts
        every { accounts.getAccountsByType(sdk.accountType) } answers {
            cleanupThreads.add("accounts" to (Looper.myLooper() == Looper.getMainLooper()))
            persistedAccounts
        }
        every { accounts.getUserData(any(), AuthenticatorService.KEY_CREDENTIALS_IDENTIFIER) } answers {
            cleanupThreads.add("reference" to (Looper.myLooper() == Looper.getMainLooper()))
            persistedReferences[firstArg()]
        }
        mockkObject(DPoPKeyManager, DPoPNonceCache)
        every { DPoPKeyManager.deleteKeyPair(any()) } answers {
            cleanupThreads.add("key" to (Looper.myLooper() == Looper.getMainLooper()))
            callOriginal()
        }
        every { DPoPNonceCache.clear(any()) } answers {
            cleanupThreads.add("nonce" to (Looper.myLooper() == Looper.getMainLooper()))
            callOriginal()
        }
        val entered = CompletableDeferred<RestRequest>()
        lateinit var callback: RestClient.AsyncRequestCallback
        val call = mockk<Call>(relaxed = true)
        val client = mockk<RestClient>().apply {
            every { sendAsync(any(), any()) } answers {
                Assert.assertSame(Looper.getMainLooper(), Looper.myLooper())
                callback = secondArg()
                entered.complete(firstArg())
                call
            }
        }
        val manager = createNativeLoginManagerForTest(client)
        val attempt = async(Main) { manager.login(TEST_USERNAME, TEST_PASSWORD) }
        try {
            val request = entered.await()
            if (cancelled) {
                attempt.cancel()
                attempt.join()
                Assert.assertTrue(attempt.isCancelled)
                verify(exactly = 1) { call.cancel() }
            } else {
                callback.onSuccess(request, RestResponse(Response.Builder()
                    .request(Request.Builder().url(request.path).build()).protocol(Protocol.HTTP_1_1)
                    .code(400).message("scripted").body("{}".toResponseBody()).build()))
                assertEquals(NativeLoginResult.InvalidCredentials, attempt.await())
            }
            assertEquals(setOf("accounts", "reference", "key", "nonce"), cleanupThreads.map { it.first }.toSet())
            Assert.assertFalse("Native persisted-reference and key/nonce cleanup must run off Main: $cleanupThreads",
                cleanupThreads.any { it.second })
            Assert.assertArrayEquals(otherKey, DPoPKeyManager.generateOrLoadKeyPair(otherAlias).public.encoded)
            assertEquals("existing-nonce", DPoPNonceCache.get(otherId, "native.example.test"))
        } finally {
            attempt.cancel()
            attempt.join()
            if (cancelled) callback.onSuccess(null, null)
            DPoPKeyManager.deleteKeyPair(otherAlias)
            DPoPNonceCache.clear(otherId)
        }
    }

    @Test
    fun test_givenNativeDPoPTokens_whenLoginOnMain_thenCryptoRunsOffMainAndAccountUsesOriginalKey() = runTest {
        SalesforceSDKManager.getInstance().useDPoP = true
        val cryptoThreads = java.util.concurrent.CopyOnWriteArrayList<Pair<String, Boolean>>()
        mockkObject(DPoPKeyManager, DPoPProofBuilder)
        every { DPoPKeyManager.generateOrLoadKeyPair(any()) } answers {
            cryptoThreads.add("key" to (Looper.myLooper() == Looper.getMainLooper()))
            callOriginal()
        }
        every { DPoPProofBuilder.buildProof(any(), any(), any(), any(), any()) } answers {
            cryptoThreads.add("proof" to (Looper.myLooper() == Looper.getMainLooper()))
            callOriginal()
        }
        val originalBioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager
        SalesforceSDKManager.getInstance().biometricAuthenticationManager = null
        val nativeRequests = mutableListOf<RestRequest>()
        val identityRequests = mutableListOf<Request>()
        val originalHttpAccess = HttpAccess.DEFAULT
        val httpClient = OkHttpClient.Builder().addInterceptor { chain ->
            identityRequests.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("scripted")
                .body("""{"username":"native@example.test","email":"native@example.test","first_name":"Native","last_name":"Test","display_name":"Native Test","id":"https://identity.example.test/id/00D000000000001/005000000000001","user_id":"005000000000001","organization_id":"00D000000000001","nick_name":"native","user_type":"Standard","language":"en_US","locale":"en_US","last_modified_date":"2026-01-01T00:00:00.000Z","is_salesforce_integration_user":false}""".toResponseBody()).build()
        }.build()
        HttpAccess.DEFAULT = mockk<HttpAccess>().apply { every { okHttpClient } returns httpClient }
        val client = scriptedNativeClient(nativeRequests, listOf(
            200 to "{\"code\":\"test-code\",\"sfdc_community_url\":\"https://community.example.test/site\"}",
            200 to "{\"access_token\":\"test-access\",\"refresh_token\":\"test-refresh\",\"instance_url\":\"https://community.example.test\",\"id\":\"https://identity.example.test/id/00D000000000001/005000000000001\",\"token_type\":\"DPoP\",\"scope\":\"api refresh_token\"}",
        )) {
            Assert.assertSame("Native transport submission must return to the caller dispatcher",
                Looper.getMainLooper(), Looper.myLooper())
        }
        try {
            val manager = NativeLoginManager(TEST_CLIENT_ID, TEST_REDIRECT_URI, TEST_LOGIN_URL, restClient = client)
            assertEquals(NativeLoginResult.Success, withContext(Main) { manager.login(TEST_USERNAME, TEST_PASSWORD) })
            Assert.assertTrue(cryptoThreads.any { it.first == "key" })
            Assert.assertTrue(cryptoThreads.count { it.first == "proof" } >= 3)
            Assert.assertFalse("Native key generation and proof signing must run off Main: $cryptoThreads",
                cryptoThreads.any { it.second })
            Assert.assertTrue("Identity must be called with the login key", identityRequests.isNotEmpty())
            identityRequests.forEach { Assert.assertNotNull("Identity request requires DPoP", it.header("DPoP")) }
            val account = realUserAccountManager.currentUser
            Assert.assertFalse("Saved DPoP credentials must identify their original key", account.credentialsIdentifier.isNullOrBlank())
            assertEquals("DPoP", account.tokenType)
            val key = DPoPKeyManager.generateOrLoadKeyPair(DPoPKeyManager.aliasForCredentialsIdentifier(checkNotNull(account.credentialsIdentifier)))
            val expectedThumbprint = com.salesforce.androidsdk.auth.dpop.DPoPProofBuilder.jwkThumbprint(key.public as java.security.interfaces.ECPublicKey)
            val proofJwk = JSONObject(String(Base64.decode(checkNotNull(nativeRequests[0].additionalHttpHeaders["DPoP"]).split('.')[0], Base64.URL_SAFE))).getJSONObject("jwk")
            val canonicalJwk = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"${proofJwk.getString("x")}\",\"y\":\"${proofJwk.getString("y")}\"}"
            assertEquals(expectedThumbprint, Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(canonicalJwk.toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
            val allProofs = nativeRequests.map { checkNotNull(it.additionalHttpHeaders["DPoP"]) } +
                identityRequests.map { checkNotNull(it.header("DPoP")) }
            allProofs.forEach { proof ->
                val parts = proof.split('.')
                val header = JSONObject(String(Base64.decode(parts[0], Base64.URL_SAFE)))
                assertEquals("ES256", header.getString("alg"))
                assertEquals("dpop+jwt", header.getString("typ"))
                val jwk = header.getJSONObject("jwk")
                for (field in listOf("kty", "crv", "x", "y")) {
                    assertEquals("Every proof must use the original login key", proofJwk.getString(field), jwk.getString(field))
                }
                Assert.assertFalse("Proof must not expose private key", jwk.has("d"))
                val raw = Base64.decode(parts[2], Base64.URL_SAFE)
                assertEquals(64, raw.size)
                fun derInteger(bytes: ByteArray): ByteArray {
                    val stripped = bytes.dropWhile { it == 0.toByte() }.toByteArray().let {
                        if (it.isEmpty()) byteArrayOf(0) else it
                    }
                    val positive = if (stripped[0].toInt() and 0x80 != 0) byteArrayOf(0) + stripped else stripped
                    return byteArrayOf(2, positive.size.toByte()) + positive
                }
                val encoded = derInteger(raw.copyOfRange(0, 32)) + derInteger(raw.copyOfRange(32, 64))
                val verifier = java.security.Signature.getInstance("SHA256withECDSA")
                verifier.initVerify(key.public)
                verifier.update("${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8))
                Assert.assertTrue("Proof signature must verify against persisted original key",
                    verifier.verify(byteArrayOf(0x30, encoded.size.toByte()) + encoded))
                Assert.assertTrue(proofClaims(proof).getLong("iat") > 0)
            }
            assertEquals(allProofs.size, allProofs.map { proofClaims(it).getString("jti") }.toSet().size)
            val expectedAth = Base64.encodeToString(
                java.security.MessageDigest.getInstance("SHA-256").digest("test-access".toByteArray()),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
            identityRequests.forEach { request ->
                val claims = proofClaims(checkNotNull(request.header("DPoP")))
                assertEquals("DPoP test-access", request.header("Authorization"))
                assertEquals("GET", claims.getString("htm"))
                assertEquals(com.salesforce.androidsdk.auth.dpop.DPoPURLHelper.canonicalize(request.url.toString()), claims.getString("htu"))
                assertEquals(expectedAth, claims.getString("ath"))
            }
            DPoPKeyManager.clearInMemoryCache()
            realUserAccountManager.clearCachedCurrentUser()
            assertEquals(account.credentialsIdentifier, realUserAccountManager.currentUser.credentialsIdentifier)
            val reloadedKey = DPoPKeyManager.generateOrLoadKeyPair(DPoPKeyManager.aliasForCredentialsIdentifier(checkNotNull(account.credentialsIdentifier)))
            assertEquals(expectedThumbprint, com.salesforce.androidsdk.auth.dpop.DPoPProofBuilder.jwkThumbprint(reloadedKey.public as java.security.interfaces.ECPublicKey))
        } finally {
            // Remove the synthetic refresh token before logout, which otherwise revokes it
            // asynchronously after HttpAccess.DEFAULT has been restored.
            realUserAccountManager.currentAccount?.let {
                android.accounts.AccountManager.get(SalesforceSDKManager.getInstance().appContext)
                    .setPassword(it, null)
            }
            realUserAccountManager.signoutCurrentUser(null, false, OAuth2.LogoutReason.USER_LOGOUT)
            HttpAccess.DEFAULT = originalHttpAccess
            SalesforceSDKManager.getInstance().biometricAuthenticationManager = originalBioAuthManager
        }
    }

    @Test
    fun test_givenNativeDPoPAccount_whenSameUserReauthenticatesWithBearer_thenPersistedDPMarkerIsRemoved() = runTest {
        val sdk = SalesforceSDKManager.getInstance()
        val originalBio = sdk.biometricAuthenticationManager
        val originalHttp = HttpAccess.DEFAULT
        sdk.biometricAuthenticationManager = null
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("scripted")
                .body("""{"username":"native@example.test","email":"native@example.test","first_name":"Native","last_name":"Test","display_name":"Native Test","id":"https://identity.example.test/id/00D000000000001/005000000000001","user_id":"005000000000001","organization_id":"00D000000000001","nick_name":"native","user_type":"Standard","language":"en_US","locale":"en_US","last_modified_date":"2026-01-01T00:00:00.000Z","is_salesforce_integration_user":false}""".toResponseBody()).build()
        }.build()
        HttpAccess.DEFAULT = mockk<HttpAccess>().apply { every { okHttpClient } returns http }
        try {
            for (tokenType in listOf("DPoP", "DPoP", "Bearer")) {
                sdk.useDPoP = tokenType == "DPoP"
                val client = scriptedNativeClient(mutableListOf(), listOf(
                    200 to """{"code":"test-code","sfdc_community_url":"https://community.example.test/site"}""",
                    200 to """{"access_token":"test-access","instance_url":"https://community.example.test","id":"https://identity.example.test/id/00D000000000001/005000000000001","token_type":"$tokenType","scope":"api"}""",
                ))
                val manager = NativeLoginManager(TEST_CLIENT_ID, TEST_REDIRECT_URI, TEST_LOGIN_URL, restClient = client)
                assertEquals(NativeLoginResult.Success, manager.login(TEST_USERNAME, TEST_PASSWORD))
                realUserAccountManager.clearCachedCurrentUser()
                val account = realUserAccountManager.currentUser
                assertEquals(tokenType, account.tokenType)
                assertEquals("The same user's persisted binding must determine the DP marker", tokenType == "DPoP",
                    sdk.isUserFeatureRegistered(com.salesforce.androidsdk.app.Features.FEATURE_DPOP, account))
                assertEquals("DP must also match after reloading the account from AccountManager", tokenType == "DPoP",
                    account.featureFlags.contains(com.salesforce.androidsdk.app.Features.FEATURE_DPOP))
                assertEquals("Reauthentication replaces the account", 1, realUserAccountManager.authenticatedUsers.size)
            }
        } finally {
            realUserAccountManager.signoutCurrentUser(null, false, OAuth2.LogoutReason.USER_LOGOUT)
            HttpAccess.DEFAULT = originalHttp
            sdk.biometricAuthenticationManager = originalBio
        }
    }

    private fun requestBody(request: RestRequest): String = okio.Buffer().let {
        request.requestBody.writeTo(it)
        it.readUtf8()
    }

    @Test
    fun test_givenSameUserDPoPReauthentication_whenSuccessful_thenReclaimsOnlyPreviousKey() = runTest {
        verifySameUserReauthentication(false)
    }

    @Test
    fun test_givenPreviousKeyReferencedByAnotherAccount_whenReauthenticated_thenPreservesKeyAndNonce() = runTest {
        verifySameUserReauthentication(true)
    }

    @Test
    fun test_givenPersistedDuplicateReplacement_whenCompletionFails_thenReclaimsOnlyPreviousKey() = runTest {
        verifySameUserReauthentication(false, IllegalStateException("failed after replacement persistence"))
    }

    @Test
    fun test_givenPersistedDuplicateReplacement_whenCompletionCancels_thenReclaimsOnlyPreviousKey() = runTest {
        verifySameUserReauthentication(false, kotlinx.coroutines.CancellationException("cancelled after replacement persistence"))
    }

    @Test
    fun test_givenSharedPreviousKey_whenReplacementCompletionFails_thenPreservesReferencedKeysAndNonces() = runTest {
        verifySameUserReauthentication(true, IllegalStateException("failed after replacement persistence"))
    }

    @Test
    fun test_givenSharedPreviousKey_whenReplacementCompletionCancels_thenPreservesReferencedKeysAndNonces() = runTest {
        verifySameUserReauthentication(true, kotlinx.coroutines.CancellationException("cancelled after replacement persistence"))
    }

    private suspend fun verifySameUserReauthentication(sharedPreviousKey: Boolean, completionFailure: Exception? = null) {
        val sdk = SalesforceSDKManager.getInstance()
        sdk.useDPoP = true
        val originalBio = sdk.biometricAuthenticationManager
        sdk.biometricAuthenticationManager = null
        val originalHttp = HttpAccess.DEFAULT
        val identifiers = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("scripted")
                .body("""{"username":"native@example.test","email":"native@example.test","first_name":"Native","last_name":"Test","display_name":"Native Test","id":"https://identity.example.test/id/00D000000000001/005000000000001","user_id":"005000000000001","organization_id":"00D000000000001","nick_name":"native","user_type":"Standard","language":"en_US","locale":"en_US","last_modified_date":"2026-01-01T00:00:00.000Z","is_salesforce_integration_user":false}""".toResponseBody()).build()
        }.build()
        HttpAccess.DEFAULT = mockk<HttpAccess>().apply { every { okHttpClient } returns http }
        try {
            repeat(2) {
                val client = scriptedNativeClient(mutableListOf(), listOf(
                    200 to """{"code":"test-code","sfdc_community_url":"https://community.example.test/site"}""",
                    200 to """{"access_token":"test-access","instance_url":"https://community.example.test","id":"https://identity.example.test/id/00D000000000001/005000000000001","token_type":"DPoP","scope":"api"}""",
                ))
                var failedAfterPersistence = false
                if (it == 1 && completionFailure != null) {
                    val spyAccountManager = spyk(realUserAccountManager)
                    every { spyAccountManager.sendUserSwitchIntent(any(), any()) } answers {
                        val accounts = android.accounts.AccountManager.get(sdk.appContext)
                        val persistedIdentifier = SalesforceSDKManager.decrypt(
                            accounts.getUserData(checkNotNull(realUserAccountManager.currentAccount),
                                AuthenticatorService.KEY_CREDENTIALS_IDENTIFIER),
                            SalesforceSDKManager.encryptionKey,
                        )
                        Assert.assertNotEquals("Replacement must be persisted before failure", identifiers[0], persistedIdentifier)
                        identifiers.add(checkNotNull(persistedIdentifier))
                        DPoPNonceCache.store(persistedIdentifier, "identity.example.test", "saved-nonce")
                        failedAfterPersistence = true
                        throw completionFailure
                    }
                    val spySdk = spyk(sdk)
                    every { spySdk.userAccountManager } returns spyAccountManager
                    mockkObject(SalesforceSDKManager)
                    every { SalesforceSDKManager.getInstance() } returns spySdk
                }
                val result = runCatching { createNativeLoginManagerForTest(client).login(TEST_USERNAME, TEST_PASSWORD) }
                if (it == 1 && completionFailure != null) {
                    Assert.assertTrue("Completion must reach the post-persistence failure", failedAfterPersistence)
                    if (completionFailure is kotlinx.coroutines.CancellationException) {
                        Assert.assertSame(completionFailure, result.exceptionOrNull())
                    } else {
                        assertEquals(NativeLoginResult.UnknownError, result.getOrThrow())
                    }
                    return@repeat
                }
                assertEquals(NativeLoginResult.Success, result.getOrThrow())
                identifiers.add(checkNotNull(realUserAccountManager.currentUser.credentialsIdentifier))
                DPoPNonceCache.store(identifiers.last(), "identity.example.test", "saved-nonce")
                if (it == 0 && sharedPreviousKey) {
                    realUserAccountManager.createAccount(UserAccountBuilder.getInstance()
                        .populateFromUserAccount(UserAccountTest.createTestAccount())
                        .credentialsIdentifier(identifiers[0]).tokenType("DPoP").build())
                }
            }
            Assert.assertNotEquals(identifiers[0], identifiers[1])
            assertEquals(if (sharedPreviousKey) 2 else 1, realUserAccountManager.authenticatedUsers.size)
            realUserAccountManager.clearCachedCurrentUser()
            assertEquals(identifiers[1], realUserAccountManager.currentUser.credentialsIdentifier)
            Assert.assertTrue("New key must survive reauthentication", DPoPKeyManager.hasKeyPair(DPoPKeyManager.aliasForCredentialsIdentifier(identifiers[1])))
            assertEquals("saved-nonce", DPoPNonceCache.get(identifiers[1], "identity.example.test"))
            assertEquals("Only unreferenced previous keys may be reclaimed", sharedPreviousKey,
                DPoPKeyManager.hasKeyPair(DPoPKeyManager.aliasForCredentialsIdentifier(identifiers[0])) )
            assertEquals(if (sharedPreviousKey) "saved-nonce" else null,
                DPoPNonceCache.get(identifiers[0], "identity.example.test"))
        } finally {
            val accounts = android.accounts.AccountManager.get(sdk.appContext)
            accounts.getAccountsByType(sdk.accountType).forEach(accounts::removeAccountExplicitly)
            realUserAccountManager.clearCachedCurrentUser()
            identifiers.forEach {
                DPoPKeyManager.deleteKeyPair(DPoPKeyManager.aliasForCredentialsIdentifier(it))
                DPoPNonceCache.clear(it)
            }
            HttpAccess.DEFAULT = originalHttp
            sdk.biometricAuthenticationManager = originalBio
        }
    }

    /**
     * Verifies that the REST client received a login request with the expected
     * attestation parameter state.
     *
     * @param restClient The REST client mock to verify
     * @param expectedAttestationValue The expected attestation value if it should be included,
     *                                  or null if the attestation parameter should be excluded
     */
    private fun verifyLoginRequestAttestation(
        restClient: RestClient,
        expectedAttestationValue: String?
    ) {
        verify(exactly = 1) {
            restClient.sendAsync(match {
                val buffer = okio.Buffer()
                it.requestBody.writeTo(buffer)
                val bodyString = buffer.readUtf8()
                val pathMatches = com.salesforce.androidsdk.auth.dpop.DPoPURLHelper.canonicalize(it.path) == "$TEST_LOGIN_URL$OAUTH_AUTH_PATH"
                val attestationMatches = if (expectedAttestationValue != null) {
                    bodyString.contains("attestation=$expectedAttestationValue")
                } else {
                    !bodyString.contains("attestation=")
                }
                pathMatches && attestationMatches
            }, any())
        }
    }

    /**
     * Verifies that the REST client received a login request whose
     * `Authorization` header matches the expected value.
     *
     * @param restClient The REST client mock to verify
     * @param expectedAuthorizationHeader The expected `Authorization` header
     * value
     */
    private fun verifyLoginRequestAuthorizationHeader(
        restClient: RestClient,
        expectedAuthorizationHeader: String
    ) {
        verify(exactly = 1) {
            restClient.sendAsync(match {
                it.additionalHttpHeaders?.get("Authorization") == expectedAuthorizationHeader
            }, any())
        }
    }

    /**
     * Installs a spy over the real [SalesforceSDKManager] singleton so that
     * only [SalesforceSDKManager.appAttestationClient] is overridden.  All
     * other real behaviour (e.g. the real [android.content.Context] and
     * [com.salesforce.androidsdk.analytics.logger.SalesforceLogger]) is
     * preserved, preventing logger-related crashes during login.
     */
    private fun installAppAttestationClient(attestation: String?) {
        val mockAppAttestationClient = mockk<AppAttestationClient>(relaxed = true).apply {
            coEvery { fetchMobileAppAttestationChallenge() } returns TEST_CHALLENGE_VALUE
            coEvery {
                createAppAttestation(appAttestationChallenge = TEST_CHALLENGE_VALUE)
            } returns attestation
        }
        val realSdkManager = SalesforceSDKManager.getInstance()
        val spySdkManager = spyk(realSdkManager)
        every { spySdkManager.appAttestationClient } returns mockAppAttestationClient
        mockkObject(SalesforceSDKManager)
        every { SalesforceSDKManager.getInstance() } returns spySdkManager
    }

    private fun createRestClientStubbingFailedLoginResponse(): RestClient {
        val mockResponse = mockk<RestResponse>(relaxed = true).apply {
            every { isSuccess } returns false
        }
        return mockk<RestClient>(relaxed = true).apply {
            every { sendAsync(any(), any()) } answers {
                val callback = secondArg<RestClient.AsyncRequestCallback>()
                callback.onSuccess(firstArg(), mockResponse)
                mockk<Call>(relaxed = true)
            }
        }
    }

    private fun createNativeLoginManagerForTest(restClient: RestClient): NativeLoginManager =
        NativeLoginManager(
            clientId = TEST_CLIENT_ID,
            redirectUri = TEST_REDIRECT_URI,
            loginUrl = TEST_LOGIN_URL,
            restClient = restClient,
        )

    // endregion Helpers used by attestation and Authorization-header tests

    private fun addUserAccount() {
        UserAccountManager.getInstance().createAccount(UserAccountTest.createTestAccount())
    }

    private fun addNativeLoginUserAccount() {
        val account = UserAccountBuilder.getInstance()
            .populateFromUserAccount(UserAccountTest.createTestAccount())
            .nativeLogin(true)
            .build()
        UserAccountManager.getInstance().createAccount(account)
    }

    private companion object {
        const val TEST_CLIENT_ID = "clientId"
        const val TEST_REDIRECT_URI = "redirect"
        const val TEST_LOGIN_URL = "https://native.example.test/site"
        const val TEST_USERNAME = "TestUser@Example.com"
        const val TEST_PASSWORD = "test123456"
        const val TEST_CHALLENGE_VALUE = "__TEST_CHALLENGE_VALUE__"
        const val TEST_APP_ATTESTATION = "__TEST_APP_ATTESTATION__"

        /**
         * A username/password pair whose colon-concatenated, UTF-8-encoded
         * bytes Base64-encode to a value containing `/` and requiring `=`
         * padding under the standard alphabet, diverging from the URL-safe
         * alphabet's output for the same bytes.
         */
        const val TEST_DIVERGING_USERNAME = "regdemouser501@salesforce.com"
        const val TEST_DIVERGING_PASSWORD = "Winter2026!?!"
    }
}
