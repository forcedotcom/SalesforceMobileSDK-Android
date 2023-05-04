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

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.*
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.idp.IDPSPMessage.*
import com.salesforce.androidsdk.auth.idp.interfaces.IDPManager.Status
import com.salesforce.androidsdk.auth.idp.interfaces.IDPManager.StatusUpdateCallback
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class IDPManagerTest : IDPSPManagerTestCase() {

    inner class TestStatusUpdateCallback : StatusUpdateCallback {
        override fun onStatusUpdate(status: Status) {
            recordedEvents.put("status ${status}")
        }
    }

    inner class TestSDKManager(val user: UserAccount?) : IDPManager.SDKManager {
        override fun getCurrentUser(): UserAccount? {
            return user
        }

        override fun generateAuthCode(
            context: Context,
            userAccount: UserAccount,
            spConfig: SPConfig,
            codeChallenge: String,
            onResult: (result: IDPAuthCodeHelper.Result) -> Unit
        ) {
            if (codeChallenge == "bad-challenge") {
                onResult(
                    IDPAuthCodeHelper.Result(
                        false,
                        error = "some-idp-error"
                    )
                )

            } else {
                onResult(
                    IDPAuthCodeHelper.Result(
                        true,
                        code = "some-code",
                        loginUrl = "some-login-url"
                    )
                )
            }
        }
    }

    @Before
    override fun setup() {
        super.setup()
    }

    @Test
    fun testKickOffIDPInitiatedLoginFlowForExistingSPUser() {
        val idpManager = kickOffIDPInitiatedLoginFlow()

        // Simulating a success response from the sp (that would happen if user already exists there)
        val uuid = (idpManager.getActiveFlow() as IDPInitiatedLoginFlow).messages.first().uuid
        val idpLoginResponse = IDPLoginResponse(uuid)
        idpManager.onReceive(context, idpLoginResponse.toIntent().apply {
            putExtra("src_app_package_name", "some-sp")
        })

        // Make sure we get a status update to indicate sp is ready
        waitForEvent("status SP_LOGIN_COMPLETE")

        // Make sure the response also made it to the list of messages for the active flow
        checkActiveFlow(idpManager, idpLoginResponse, 1)

        //  NB it won't attempt to start the activity in the SP app because the flow was
        //     started using a context that is not an activity

        // Make sure there are no more events
        expectNoEvent()
    }

    @Test
    fun testKickOffIDPInitiatedLoginFlowForNewSPUser() {
        val idpManager = kickOffIDPInitiatedLoginFlow()

        // Simulate a login request from the sp app - that would happen if if does not have that user
        val uuid = (idpManager.getActiveFlow() as IDPInitiatedLoginFlow).messages.first().uuid
        simulateSPLoginRequestDuringIDPInitiatedFlow(idpManager, uuid)

        // Make sure we get a status update indicating we are getting the auth code from server
        waitForEvent("status GETTING_AUTH_CODE_FROM_SERVER")

        // Make sure a response with code is sent back
        waitForEvent("sendBroadcast Intent { act=com.salesforce.SP_LOGIN_RESPONSE pkg=some-sp (has extras) } extras = { login_url = some-login-url code = some-code uuid = ${uuid} error = null src_app_package_name = com.salesforce.androidsdk.tests }")

        // Make sure we get a status update indicating we have sent the auth code to the sp app
        waitForEvent("status AUTH_CODE_SENT_TO_SP")

        // Simulating a success response from the sp
        val idpLoginResponse = IDPLoginResponse(uuid)
        idpManager.onReceive(context, idpLoginResponse.toIntent().apply {
            putExtra("src_app_package_name", "some-sp")
        })

        // Make sure we get a status update to indicate sp is ready
        waitForEvent("status SP_LOGIN_COMPLETE")

        //  NB it won't attempt to start the activity in the SP app because the flow was
        //     started using a context that is not an activity

        // Make sure there are no more events
        expectNoEvent()
    }

    @Test
    fun testKickOffIDPInitiatedLoginFlowForNewSPUserWithIDPFailing() {
        val idpManager = kickOffIDPInitiatedLoginFlow()

        // Simulate a login request from the sp app with a bad challenge
        // The bad challenge causes our TestSDKManager to fail to get an auth code
        val uuid = (idpManager.getActiveFlow() as IDPInitiatedLoginFlow).messages.first().uuid
        simulateSPLoginRequestDuringIDPInitiatedFlow(idpManager, uuid, "bad-challenge")

        // Make sure we get a status update indicating we are getting the auth code from server
        waitForEvent("status GETTING_AUTH_CODE_FROM_SERVER")

        // Make sure we get a status update indicating that we got an error from the server
        waitForEvent("status ERROR_RECEIVED_FROM_SERVER")

        // Make sure there are no more events
        expectNoEvent()
    }

    @Test
    fun testKickOffIDPInitiatedLoginFlowForNewSPUserWithSPFailing() {
        val idpManager = kickOffIDPInitiatedLoginFlow()

        // Simulate a login request from the sp app - that would happen if if does not have that user
        val uuid = (idpManager.getActiveFlow() as IDPInitiatedLoginFlow).messages.first().uuid
        simulateSPLoginRequestDuringIDPInitiatedFlow(idpManager, uuid)

        // Make sure we get a status update indicating we are getting the auth code from server
        waitForEvent("status GETTING_AUTH_CODE_FROM_SERVER")

        // Make sure a response with code is sent back
        waitForEvent("sendBroadcast Intent { act=com.salesforce.SP_LOGIN_RESPONSE pkg=some-sp (has extras) } extras = { login_url = some-login-url code = some-code uuid = ${uuid} error = null src_app_package_name = com.salesforce.androidsdk.tests }")

        // Make sure we get a status update indicating we have sent the auth code to the sp app
        waitForEvent("status AUTH_CODE_SENT_TO_SP")

        // Simulating an error response from the sp (that would happen if user already exists there)
        val idpLoginResponse = IDPLoginResponse(uuid, error = "some-sp-error")
        idpManager.onReceive(context, idpLoginResponse.toIntent().apply {
            putExtra("src_app_package_name", "some-sp")
        })

        // Make sure we get a status update to indicate sp failed to login
        waitForEvent("status ERROR_RECEIVED_FROM_SP")

        // Make sure there are no more events
        expectNoEvent()
    }


    @Test
    fun testSPInitiatedLoginFlowWithIDPReturningCode() {
        val allowedSPApps = listOf(SPConfig("some-sp", "c", "client-id", "callback-url", arrayOf("api")))
        val idpManager = IDPManager(allowedSPApps, TestSDKManager(buildUser("some-org-id", "some-user-id")), this::sendBroadcast, this::startActivity)

        // Simulate sp login request
        val uuid = simulateInitialSPLoginRequest(idpManager)

        // Make sure a response with code is sent back
        waitForEvent("sendBroadcast Intent { act=com.salesforce.SP_LOGIN_RESPONSE pkg=some-sp (has extras) } extras = { login_url = some-login-url code = some-code uuid = ${uuid} error = null src_app_package_name = com.salesforce.androidsdk.tests }")

        // Make sure there are no more events
        expectNoEvent()
    }

    @Test
    fun testSPInitiatedLoginFlowWithIDPReturningError() {
        val allowedSPApps = listOf(SPConfig("some-sp", "c", "client-id", "callback-url", arrayOf("api")))
        val idpManager = IDPManager(allowedSPApps, TestSDKManager(buildUser("some-org-id", "some-user-id")), this::sendBroadcast, this::startActivity)

        // Simulate sp login request
        val uuid = simulateInitialSPLoginRequest(idpManager, "bad-challenge")

        // Make sure an error response is sent back
        waitForEvent("sendBroadcast Intent { act=com.salesforce.SP_LOGIN_RESPONSE pkg=some-sp (has extras) } extras = { login_url = null code = null uuid = ${uuid} error = some-idp-error src_app_package_name = com.salesforce.androidsdk.tests }")

        // Make sure there are no more events
        expectNoEvent()
    }

    @Test
    fun testSPInitiatedLoginFlowWithIDPNotLoggedIn() {
        val allowedSPApps = listOf(SPConfig("some-sp", "c", "client-id", "callback-url", arrayOf("api")))
        val idpManager = IDPManager(allowedSPApps, TestSDKManager(null), this::sendBroadcast, this::startActivity)

        // Simulate sp login request
        val uuid = simulateInitialSPLoginRequest(idpManager)

        // Make an error response is sent back - since we setup the idp not to have a current user
        waitForEvent("sendBroadcast Intent { act=com.salesforce.SP_LOGIN_RESPONSE pkg=some-sp (has extras) } extras = { login_url = null code = null uuid = ${uuid} error = IDP app not logged in src_app_package_name = com.salesforce.androidsdk.tests }")

        // Make sure there is still no active flow
        Assert.assertNull(idpManager.getActiveFlow())

        // Make sure there are no more events
        expectNoEvent()
    }

    @Test
    fun testSPInitiatedLoginFlowWithBadSP() {
        val allowedSPApps = listOf(SPConfig("some-sp", "c", "client-id", "callback-url", arrayOf("api")))
        val idpManager = IDPManager(allowedSPApps, TestSDKManager(null), this::sendBroadcast, this::startActivity)

        // Simulate a request from an unknown sp
        val spLoginRequest = SPLoginRequest(codeChallenge = "some-challenge")
        idpManager.onReceive(context, spLoginRequest.toIntent().apply {
            putExtra("src_app_package_name", "some-other-sp")
        })

        // Make sure there is no active flow - we did not initiate
        Assert.assertNull(idpManager.getActiveFlow())

        // Make sure there are no more events
        expectNoEvent()
    }

    fun kickOffIDPInitiatedLoginFlow():IDPManager {
        // Set up idp manager
        val allowedSPApps = listOf(SPConfig("some-sp", "c", "client-id", "callback-url", arrayOf("api")))
        val idpManager = IDPManager(allowedSPApps, TestSDKManager(buildUser("some-org-id", "some-user-id")), this::sendBroadcast, this::startActivity)
        idpManager.kickOffIDPInitiatedLoginFlow(context, "some-sp", TestStatusUpdateCallback())

        // Make sure we have an active flow with a IDPLoginRequest as first message
        val firstRequestInActiveFlow = checkActiveFlow(idpManager, IDPLoginRequest.ACTION, 0)

        // Make sure the IDPLoginRequest was sent to the idp
        waitForEvent("sendBroadcast Intent { act=com.salesforce.IDP_LOGIN_REQUEST pkg=some-sp (has extras) } extras = { org_id = some-org-id user_id = some-user-id uuid = ${firstRequestInActiveFlow?.uuid} src_app_package_name = com.salesforce.androidsdk.tests }")

        // Make sure the idp got a status update as well
        waitForEvent("status LOGIN_REQUEST_SENT_TO_SP")

        return idpManager
    }

    // Simulate a login request from the sp during sp initiated login
    fun simulateInitialSPLoginRequest(idpManager: IDPManager, challenge: String = "some-challenge"):String {
        val spLoginRequest = SPLoginRequest(codeChallenge = challenge)
        idpManager.onReceive(context, spLoginRequest.toIntent().apply {
            putExtra("src_app_package_name", "some-sp")
        })

        // Make sure there is no active flow - we did not initiate
        Assert.assertNull(idpManager.getActiveFlow())

        return spLoginRequest.uuid
    }

    // Simulate a login request from the sp during idp initiated login
    fun simulateSPLoginRequestDuringIDPInitiatedFlow(idpManager: IDPManager, uuid: String, challenge: String = "some-challenge") {
        val spLoginRequest = SPLoginRequest(uuid, challenge)
        idpManager.onReceive(context, spLoginRequest.toIntent().apply {
            putExtra("src_app_package_name", "some-sp")
        })

        // Make sure the request also made it to the list of messages for the active flow
        checkActiveFlow(idpManager, spLoginRequest, 1)
    }

}
