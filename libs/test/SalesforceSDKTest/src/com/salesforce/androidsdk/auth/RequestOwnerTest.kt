package com.salesforce.androidsdk.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RequestOwnerTest {

    private val owner = UserAccountBuilder.getInstance().orgId("ownerOrg").userId("ownerUser").build()
    private val otherUser = UserAccountBuilder.getInstance().orgId("otherOrg").userId("otherUser").build()
    private val users = mockk<UserAccountManager>(relaxed = true)

    @Before
    fun setUp() {
        RestClient.clearCaches()
        mockkObject(SalesforceSDKManager)
        val manager = mockk<SalesforceSDKManager>(relaxed = true)
        every { SalesforceSDKManager.getInstance() } returns manager
        every { manager.userAccountManager } returns users
        every { manager.appContext } returns InstrumentationRegistry.getInstrumentation().targetContext
        every { users.currentUser } returns otherUser
        every { users.getUserFromOrgAndUserId("ownerOrg", "ownerUser") } returns owner
    }

    @After
    fun tearDown() {
        RestClient.clearCaches()
        unmockkAll()
    }

    @Test
    fun test_givenSdkNotInitialized_whenConstructingClientInfo_thenDoesNotRequireSdk() {
        every { SalesforceSDKManager.getInstance() } throws IllegalStateException("SDK not initialized")

        assertEquals(owner.userId, clientInfo().userId)
    }

    @Test
    fun test_givenRetainedClientAndDifferentCurrentUser_whenBuildRequest_thenTagsBoundOwner() {
        val request = restClient().buildRequest(RestRequest(RestRequest.RestMethod.GET, "/services/data/"))

        assertOwner(owner, request)
        assertSame(request.tag(UserAccount::class.java), request.newBuilder().build().tag(UserAccount::class.java))
    }

    @Test
    fun test_givenRemovedAccount_whenBuildRequest_thenRetainsClientIdentity() {
        val client = restClient()
        every { users.getUserFromOrgAndUserId("ownerOrg", "ownerUser") } returns null
        RestClient.clearCaches(owner)

        val request = client.buildRequest(RestRequest(RestRequest.RestMethod.GET, "/services/data/"))

        assertOwner(owner, request)
    }

    @Test
    fun test_givenRepeatedRequests_whenBuildRequest_thenDoesNotResolveAccounts() {
        val client = restClient()
        repeat(3) {
            assertOwner(owner, client.buildRequest(RestRequest(RestRequest.RestMethod.GET, "/services/data/")))
        }

        verify(exactly = 0) { users.getUserFromOrgAndUserId(any(), any()) }
        verify(exactly = 0) { users.authenticatedUsers }
        verify(exactly = 0) { users.currentUser }
    }

    @Test
    fun test_givenChangedClientInfo_whenBuildRequest_thenTagsReplacementIdentity() {
        val client = restClient()
        client.setClientInfo(clientInfo(otherUser))
        every { users.getUserFromOrgAndUserId(any(), any()) } returns null

        assertOwner(otherUser, client.buildRequest(RestRequest(RestRequest.RestMethod.GET, "/services/data/")))
    }

    @Test
    fun test_givenTwoRetainedClients_whenCurrentUserChanges_thenOwnersRemainIndependent() {
        val first = restClient()
        val second = RestClient(clientInfo(otherUser), null, HttpAccess.DEFAULT, null)
        every { users.currentUser } returns owner

        assertOwner(otherUser, second.buildRequest(RestRequest(RestRequest.RestMethod.GET, "/services/data/")))
        assertOwner(owner, first.buildRequest(RestRequest(RestRequest.RestMethod.GET, "/services/data/")))
        assertOwner(otherUser, second.buildRequest(RestRequest(RestRequest.RestMethod.GET, "/services/data/")))
        verify(exactly = 0) { users.currentUser }
    }

    @Test
    fun test_givenBoundOwner_whenFeaturesChange_thenUserAgentIsComputedForEachRequest() {
        val manager = SalesforceSDKManager.getInstance()
        var userAgent = "owner-features-before"
        every { manager.getUserAgent("", any()) } answers {
            val user = secondArg<UserAccount>()
            assertEquals(owner.orgId, user.orgId)
            assertEquals(owner.userId, user.userId)
            userAgent
        }
        val client = restClient()
        // Exercise both the default interceptor and the client-bound interceptor: each must
        // respect the request tag rather than the current user's feature set.
        assertEquals(2, client.okHttpClient.networkInterceptors.size)
        client.okHttpClient.networkInterceptors.forEach { interceptor ->
            val request = client.buildRequest(RestRequest(RestRequest.RestMethod.GET, "/services/data/"))
            val chain = mockk<Interceptor.Chain> {
                every { request() } returns request
                every { proceed(any()) } answers {
                    val decorated = firstArg<Request>()
                    assertEquals(userAgent, decorated.header("User-Agent"))
                    Response.Builder().request(decorated).protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK").body("".toResponseBody()).build()
                }
            }
            userAgent = "owner-features-before"
            interceptor.intercept(chain).close()
            userAgent = "owner-features-after"
            interceptor.intercept(chain).close()
        }
        verify(exactly = 0) { users.currentUser }
    }

    @Test
    fun test_givenUnauthenticatedClient_whenBuildRequest_thenRemainsUntagged() {
        val client = RestClient(RestClient.UnauthenticatedClientInfo(), null, HttpAccess.DEFAULT, null)
        val request = client.buildRequest(RestRequest(RestRequest.RestMethod.GET, "https://instance.example.com/"))

        assertNull(request.tag(UserAccount::class.java))
        verify(exactly = 0) { users.getUserFromOrgAndUserId(any(), any()) }
    }

    @Test
    fun test_givenKnownOwner_whenRevoke_thenTagsOwner() {
        val requests = mutableListOf<Request>()
        val access = capturingAccess(requests)
        OAuth2.revokeRefreshToken(
            access, URI("https://login.example.com"), "__REFRESH_TOKEN__",
            OAuth2.LogoutReason.UNKNOWN, owner,
        )

        assertSame(owner, requests.single().tag(UserAccount::class.java))
    }

    @Test
    fun test_givenKnownOwner_whenIdentity_thenTagsOwner() {
        val requests = mutableListOf<Request>()
        val access = capturingAccess(requests)
        OAuth2.callIdentityService(
            access, "https://login.example.com/id/ownerOrg/ownerUser",
            "__ACCESS_TOKEN__", null, null, owner,
        )

        assertSame(owner, requests.single().tag(UserAccount::class.java))
    }

    @Test
    fun test_givenLegacyOAuthCalls_whenIdentityAndRevoke_thenRemainUntagged() {
        val requests = mutableListOf<Request>()
        val access = capturingAccess(requests)

        OAuth2.callIdentityService(access, "https://login.example.com/id/ownerOrg/ownerUser", "__ACCESS_TOKEN__")
        OAuth2.callIdentityService(access, "https://login.example.com/id/ownerOrg/ownerUser", "__ACCESS_TOKEN__", null, null)
        OAuth2.revokeRefreshToken(access, URI("https://login.example.com"), "__REFRESH_TOKEN__", OAuth2.LogoutReason.UNKNOWN)

        requests.forEach { assertNull(it.tag(UserAccount::class.java)) }
    }

    @Test
    fun test_givenAuthenticationCredentials_whenFetchingIdentity_thenOnlyResolvedOwnerIsTagged() = runBlocking {
        val requests = mutableListOf<Request>()
        val access = capturingAccess(requests)
        mockkObject(HttpAccess.DEFAULT)
        every { HttpAccess.DEFAULT.okHttpClient } returns access.okHttpClient
        val credentials = OAuth2.TokenEndpointResponse(
            mapOf(
                "id" to "https://login.example.com/id/ownerOrg/ownerUser",
                "instance_url" to "https://instance.example.com",
                "access_token" to "__ACCESS_TOKEN__",
            ),
            emptyList(),
        )

        fetchUserIdentityWithRetry(credentials, "https://login.example.com", "__CLIENT_ID__")
        assertSame(owner, requests.single().tag(UserAccount::class.java))

        every { users.getUserFromOrgAndUserId("ownerOrg", "ownerUser") } returns null
        fetchUserIdentityWithRetry(credentials, "https://login.example.com", "__CLIENT_ID__")
        assertNull(requests.last().tag(UserAccount::class.java))
    }

    @Test
    fun test_givenDuplicateAccount_whenRevokingReplacedCredentials_thenPassesReplacedOwner() {
        val duplicate = UserAccountBuilder.getInstance()
            .orgId("ownerOrg").userId("ownerUser")
            .instanceServer("https://instance.example.com").refreshToken("__OLD_REFRESH_TOKEN__").build()
        val replacement = UserAccountBuilder.getInstance()
            .orgId("ownerOrg").userId("ownerUser").refreshToken("__NEW_REFRESH_TOKEN__").build()
        every { users.authenticatedUsers } returns mutableListOf(duplicate)
        val revoked = CountDownLatch(1)
        var revokedOwner: UserAccount? = null
        mockkStatic(OAuth2::class)
        every { OAuth2.revokeRefreshToken(any(), any(), any(), any(), any()) } answers {
            revokedOwner = arg(4)
            revoked.countDown()
        }

        handleDuplicateUserAccount(users, replacement, null)

        assertTrue("Expected replaced credentials to be revoked", revoked.await(5, TimeUnit.SECONDS))
        assertSame(duplicate, revokedOwner)
    }

    private fun capturingAccess(requests: MutableList<Request>) = mockk<HttpAccess> {
        every { okHttpClient } returns mockk {
            every { newCall(any()) } answers {
                val request = firstArg<Request>()
                requests += request
                mockk {
                    every { execute() } answers { Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"username":"test@example.com","email":"test@example.com","first_name":"Test","last_name":"User","display_name":"Test User","id":"https://login.example.com/id/ownerOrg/ownerUser","user_id":"ownerUser","organization_id":"ownerOrg","nick_name":"test","user_type":"Standard","language":"en_US","locale":"en_US","last_modified_date":"2026-01-01T00:00:00Z"}""".toResponseBody())
                        .build()
                    }
                }
            }
        }
    }

    private fun assertOwner(expected: UserAccount, request: Request) {
        val actual = request.tag(UserAccount::class.java)
        assertEquals(expected.orgId, actual?.orgId)
        assertEquals(expected.userId, actual?.userId)
    }

    private fun clientInfo(user: UserAccount = owner) = RestClient.ClientInfo(
            URI("https://instance.example.com"), URI("https://login.example.com"),
            URI("https://login.example.com/id/ownerOrg/ownerUser"),
            "account", "test@example.com", user.userId, user.orgId,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null,
        )

    private fun restClient() = RestClient(clientInfo(), "__ACCESS_TOKEN__", HttpAccess.DEFAULT, null)
}
