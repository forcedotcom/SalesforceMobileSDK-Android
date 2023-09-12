/*
 * Copyright (c) 2023-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.auth.idp

import android.app.Activity
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.*
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.idp.IDPSPMessage.*
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager.Status
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager.StatusUpdateCallback
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SPManagerTest : IDPSPManagerTestCase() {

    inner class TestStatusUpdateCallback : StatusUpdateCallback {
        override fun onStatusUpdate(status: Status) {
            recordedEvents.put("status $status")
        }
    }

    @Before
    override fun setup() {
        super.setup()
    }

    inner class TestSDKMgr(val user:UserAccount?) : SPManager.SDKManager {
        override fun getCurrentUser(): UserAccount? {
            return user
        }

        override fun getUserFromOrgAndUserId(orgId: String, userId: String): UserAccount? {
            return if (orgId == user?.orgId && userId == user.userId) {
                user
            } else {
                null
            }
        }

        override fun switchToUser(user: UserAccount) {
            recordedEvents.put("switched to ${user.orgId} ${user.userId}")
        }

        override fun getMainActivityClass(): Class<out Activity>? {
            return null
        }

        override fun loginWithAuthCode(
            context: Context,
            loginUrl: String,
            code: String,
            codeVerifier: String,
            onResult: (SPAuthCodeHelper.Result) -> Unit
        ) {
            recordedEvents.put("logging in with $loginUrl $code $codeVerifier")
            if (code == "bad-code") {
                onResult(SPAuthCodeHelper.Result(success = false, error = "some-sp-error"))
            } else {
                val newUser = buildUser("org-for-$code", "user-for-$code")
                onResult(SPAuthCodeHelper.Result(success = true, user = newUser))
            }
        }
    }

    @Test
    fun testKickOffSPInitiatedLoginFlowWithIDPReturningError() {
        val spManager = kickOffSPInitiatedLoginFlow()

        // Simulate idp responding with error
        val uuid = (spManager.getActiveFlow() as SPLoginFlow).messages.first().uuid
        simulateIDPToSPResponseWithError(spManager, uuid)
    }

    @Test
    fun testKickOffSPInitiatedLoginFlowWithIDPReturningCode() {
        val spManager = kickOffSPInitiatedLoginFlow()

        // Simulate idp responding with code
        val uuid = (spManager.getActiveFlow() as SPLoginFlow).messages.first().uuid
        simulateIDPToSPResponseWithCode(spManager, uuid, spInitiated = true)
    }

    @Test
    fun testKickOffSPInitiatedLoginFlowWithIDPReturningBadCode() {
        val spManager = kickOffSPInitiatedLoginFlow()

        // Simulate idp responding with bad code
        val uuid = (spManager.getActiveFlow() as SPLoginFlow).messages.first().uuid
        simulateIDPToSPResponseFromIDPWithBadCode(spManager, uuid, spInitiated = true)
    }

    @Test
    fun testKickOffSPInitiatedLoginFlowWithNotExistentIDP() {
        val spManager = SPManager("some-idp", TestSDKMgr(buildUser("some-org-id", "some-user-id")),
            // the test context is not an activity so sendBroadcast will be used to send the login request to the IDP app
            // so throwing an exception there
            this::throwOnSend, this::throwOnSend)
        spManager.kickOffSPInitiatedLoginFlow(context, TestStatusUpdateCallback())

        // Make sure the sp got a status update indicating a non-existent IDP
        waitForEvent("status FAILED_TO_SEND_REQUEST_TO_IDP")

        // Make sure there are no more events
        expectNoEvent()
    }

    @Test
    fun testIDPInitiatedFlowForExistingUser() {
        // Set up sp manager with a current user
        val spManager = SPManager("some-idp", TestSDKMgr(buildUser("some-org-id", "some-user-id")), this::sendBroadcast, this::startActivity)

        // Simulate a request from the idp
        val ipdLoginRequest = IDPToSPRequest(orgId = "some-org-id", userId = "some-user-id")
        spManager.onReceive(context, ipdLoginRequest.toIntent().apply {
            putExtra("src_app_package_name", "some-idp")
        })

        // Make sure there is no active flow - we had the user, we did not need to start a sp login flow
        Assert.assertNull(spManager.getActiveFlow())

        // Make sure the sp app switches to requested user
        waitForEvent("switched to some-org-id some-user-id")

        // Make sure sp app sends a response to the idp app
        waitForEvent("sendBroadcast Intent { act=com.salesforce.androidsdk.SP_TO_IDP_RESPONSE pkg=some-idp (has extras) } extras = { uuid = ${ipdLoginRequest.uuid} error = null src_app_package_name = com.salesforce.androidsdk.tests }")

        // Make sure there is still no active flow
        Assert.assertNull(spManager.getActiveFlow())

        // Make sure there are no more events
        expectNoEvent()
    }

    @Test
    fun testIDPInitiatedFlowForNewUser() {
        // Set up sp manager with no current user
        val spManager = SPManager("some-idp", TestSDKMgr(null), this::sendBroadcast, this::startActivity)

        // Simulate idp login request
        val uuid = simulateIDPToSPRequest(spManager)

        // Make sure sp app sends a SPToIDPRequest back to the idp app
        waitForEvent("sendBroadcast Intent { act=com.salesforce.androidsdk.SP_TO_IDP_REQUEST pkg=some-idp (has extras) } extras = { uuid = $uuid src_app_package_name = com.salesforce.androidsdk.tests code_challenge = ")

        // Make sure the SPToIDPRequest was added to the list of messages for the active flow
        checkActiveFlow(spManager, SPToIDPRequest.ACTION, 1)

        // Simulate idp responding with a code
        simulateIDPToSPResponseWithCode(spManager, uuid, spInitiated = false)
    }

    @Test
    fun testIDPInitiatedFlowForNewUserWithBadCode() {
        // Set up sp manager with no current user
        val spManager = SPManager("some-idp", TestSDKMgr(null), this::sendBroadcast, this::startActivity)

        // Simulate idp login request
        val uuid = simulateIDPToSPRequest(spManager)

        // Make sure sp app sends a SPToIDPRequest back to the idp app
        waitForEvent("sendBroadcast Intent { act=com.salesforce.androidsdk.SP_TO_IDP_REQUEST pkg=some-idp (has extras) } extras = { uuid = $uuid src_app_package_name = com.salesforce.androidsdk.tests code_challenge = ")

        // Make sure the SPToIDPRequest was added to the list of messages for the active flow
        checkActiveFlow(spManager, SPToIDPRequest.ACTION, 1)

        // Simulate idp responding with a bad code
        simulateIDPToSPResponseFromIDPWithBadCode(spManager, uuid, spInitiated = false)
    }

    private fun kickOffSPInitiatedLoginFlow():SPManager {
        // Set up sp manager with a current user
        val spManager = SPManager("some-idp", TestSDKMgr(buildUser("some-org-id", "some-user-id")), this::sendBroadcast, this::startActivity)
        spManager.kickOffSPInitiatedLoginFlow(context, TestStatusUpdateCallback())

        // Make sure we have an active flow with a SPToIDPRequest as first message
        val firstRequestInActiveFlow = checkActiveFlow(spManager, SPToIDPRequest.ACTION, 0)

        // Make sure the SPToIDPRequest was sent to the idp
        val codeChallenge = (spManager.getActiveFlow() as SPLoginFlow).codeChallenge
        waitForEvent("sendBroadcast Intent { act=com.salesforce.androidsdk.SP_TO_IDP_REQUEST pkg=some-idp (has extras) } extras = { uuid = ${firstRequestInActiveFlow!!.uuid} src_app_package_name = com.salesforce.androidsdk.tests code_challenge = $codeChallenge")

        // Make sure the sp got a status update as well
        waitForEvent("status LOGIN_REQUEST_SENT_TO_IDP")

        return spManager
    }

    private fun simulateIDPToSPResponseFromIDPWithBadCode(spManager: SPManager, uuid: String, spInitiated: Boolean) {
        // Simulate a response from the idp with a bad code
        val idpToSpResponse = IDPToSPResponse(uuid, loginUrl = "some-login-url",  code = "bad-code")
        spManager.onReceive(context, idpToSpResponse.toIntent().apply {
            putExtra("src_app_package_name", "some-idp")
        })

        if (spInitiated) {
            // Make sure we got a status update for the response
            waitForEvent("status AUTH_CODE_RECEIVED_FROM_IDP")
        }

        // Make sure the response also made it to the list of messages for the active flow
        checkActiveFlow(spManager, idpToSpResponse, if (spInitiated) 1 else 2)

        // Make sure the login with code fails
        val codeVerifier = (spManager.getActiveFlow() as SPLoginFlow).codeVerifier
        waitForEvent("logging in with some-login-url bad-code $codeVerifier")

        if (spInitiated) {
            // Make sure we got a status update to indicate that login failed
            waitForEvent("status FAILED_TO_EXCHANGE_AUTHORIZATION_CODE")
        } else {
            // Make sure sp app sends an error response to the idp app
            waitForEvent("sendBroadcast Intent { act=com.salesforce.androidsdk.SP_TO_IDP_RESPONSE pkg=some-idp (has extras) } extras = { uuid = $uuid error = some-sp-error src_app_package_name = com.salesforce.androidsdk.tests }")

            // Make sure the response also made it to the list of messages for the active flow
            checkActiveFlow(spManager, SPToIDPResponse.ACTION, 3)
        }

        // Make sure there are no more events
        expectNoEvent()
    }

    private fun simulateIDPToSPResponseWithCode(spManager: SPManager, uuid: String, spInitiated:Boolean) {
        // Simulate a response from the idp with a code
        val idpToSpResponse = IDPToSPResponse(uuid, loginUrl = "some-login-url",  code = "some-code")
        spManager.onReceive(context, idpToSpResponse.toIntent().apply {
            putExtra("src_app_package_name", "some-idp")
        })

        if (spInitiated) {
            // Make sure we get a status update for the success response
            waitForEvent("status AUTH_CODE_RECEIVED_FROM_IDP")
        }

        // Make sure the response also made it to the list of messages for the active flow
        checkActiveFlow(spManager, idpToSpResponse, if(spInitiated)  1 else 2)

        // Make sure login with code happens
        val codeVerifier = (spManager.getActiveFlow() as SPLoginFlow).codeVerifier
        waitForEvent("logging in with some-login-url some-code $codeVerifier")

        // Make sure the sp app switches to the new user
        waitForEvent("switched to org-for-some-code user-for-some-code")

        // Make sure sp app sends a response to the idp app
        waitForEvent("sendBroadcast Intent { act=com.salesforce.androidsdk.SP_TO_IDP_RESPONSE pkg=some-idp (has extras) } extras = { uuid = $uuid error = null src_app_package_name = com.salesforce.androidsdk.tests }")

        // Make sure the response also made it to the list of messages for the active flow
        checkActiveFlow(spManager, SPToIDPResponse.ACTION, if (spInitiated) 2 else 3)

        if (spInitiated) {
            // Make sure we got a status update to indicate that login is complete
            waitForEvent("status LOGIN_COMPLETE")
        }

        // Make sure there are no more events
        expectNoEvent()
    }

    private fun simulateIDPToSPResponseWithError(spManager: SPManager, uuid:String) {
        // Simulate a response from the idp with an error
        val idpToSpResponse = IDPToSPResponse(uuid, error="some-error")
        spManager.onReceive(context, idpToSpResponse.toIntent().apply {
            putExtra("src_app_package_name", "some-idp")
        })

        // Make sure we got a status update for the error response
        waitForEvent("status ERROR_RECEIVED_FROM_IDP")

        // Make sure the response also made it to the list of messages for the active flow
        checkActiveFlow(spManager, idpToSpResponse, 1)

        // Make sure there are no more events
        expectNoEvent()
    }

    private fun simulateIDPToSPRequest(spManager: SPManager):String {
        // Simulate a request from the idp
        val idpToSpRequest = IDPToSPRequest(orgId = "some-other-org-id", userId = "some-other-user-id")
        spManager.onReceive(context, idpToSpRequest.toIntent().apply {
            putExtra("src_app_package_name", "some-idp")
        })

        // Make sure we have an active flow with the IDPToSPRequest as first message
        checkActiveFlow(spManager, idpToSpRequest, 0)

        return idpToSpRequest.uuid
    }

}
