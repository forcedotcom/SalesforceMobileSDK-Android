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
            recordedEvents.put("status ${status}")
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
            onUserCreated: (UserAccount) -> Unit
        ) {
            recordedEvents.put("logging in with $loginUrl $code $codeVerifier")
            onUserCreated(buildUser("org-for-$code", "user-for-$code"))
        }
    }

    @Test
    fun testKickOffSPInitiatedLoginFlowWithIDPReturningError() {
        val spManager = SPManager("some-idp", TestSDKMgr(buildUser("some-org-id", "some-user-id")), this::sendBroadcast)
        spManager.kickOffSPInitiatedLoginFlow(context, TestStatusUpdateCallback())

        // Checking active flow
        val activeFlow = spManager.getActiveFlow() as? SPLoginFlow
        Assert.assertNotNull(activeFlow)
        val spLoginRequest = activeFlow?.firstMessage
        Assert.assertNotNull(spLoginRequest)
        Assert.assertTrue(spLoginRequest is SPLoginRequest)

        // Checking events
        waitForEvent("sendBroadcast Intent { act=com.salesforce.SP_LOGIN_REQUEST pkg=some-idp (has extras) } extras = { uuid = ${spLoginRequest!!.uuid} src_app_package_name = com.salesforce.androidsdk.tests code_challenge = ")
        waitForEvent("status LOGIN_REQUEST_SENT_TO_IDP")

        // Faking a response from the idp with an error
        val response = SPLoginResponse(spLoginRequest.uuid, error="some-error")
        spManager.onReceive(context, response.toIntent().apply {
            putExtra("src_app_package_name", "some-idp")
        })

        // Waiting for status update
        waitForEvent("status ERROR_RESPONSE_RECEIVED_FROM_IDP")

        // Checking active flow
        Assert.assertEquals(2, activeFlow.messages.size)
        val spLoginResponse = activeFlow.messages.get(1)
        Assert.assertNotNull(spLoginResponse)
        Assert.assertTrue(spLoginResponse is SPLoginResponse)
        Assert.assertEquals(response.toString(), spLoginResponse.toString())

        // Expect no more events
        expectNoEvent()
    }

    @Test
    fun testKickOffSPInitiatedLoginFlowWithIDPReturningCode() {
        val spManager = SPManager("some-idp", TestSDKMgr(buildUser("some-org-id", "some-user-id")), this::sendBroadcast)
        spManager.kickOffSPInitiatedLoginFlow(context, TestStatusUpdateCallback())

        // Checking active flow
        val activeFlow = spManager.getActiveFlow() as? SPLoginFlow
        Assert.assertNotNull(activeFlow)
        val spLoginRequest = activeFlow?.firstMessage
        Assert.assertNotNull(spLoginRequest)
        Assert.assertTrue(spLoginRequest is SPLoginRequest)

        // Checking events
        waitForEvent("sendBroadcast Intent { act=com.salesforce.SP_LOGIN_REQUEST pkg=some-idp (has extras) } extras = { uuid = ${spLoginRequest!!.uuid} src_app_package_name = com.salesforce.androidsdk.tests code_challenge = ")
        waitForEvent("status LOGIN_REQUEST_SENT_TO_IDP")

        // Faking a response from the idp with a code
        val response = SPLoginResponse(spLoginRequest.uuid, loginUrl = "some-login-url", code = "some-code")
        spManager.onReceive(context, response.toIntent().apply {
            putExtra("src_app_package_name", "some-idp")
        })

        // Waiting for status update
        waitForEvent("status AUTH_CODE_RECEIVED_FROM_IDP")

        // Checking active flow
        Assert.assertEquals(2, activeFlow.messages.size)
        val spLoginResponse = activeFlow.messages.get(1)
        Assert.assertNotNull(spLoginResponse)
        Assert.assertTrue(spLoginResponse is SPLoginResponse)
        Assert.assertEquals(response.toString(), spLoginResponse.toString())

        // Make sure login with code happens
        waitForEvent("logging in with some-login-url some-code ${activeFlow.codeVerifier}")
        waitForEvent("switched to org-for-some-code user-for-some-code")

        // Waiting for last status update
        waitForEvent("status LOGIN_COMPLETE")
    }

    @Test
    fun testIDPInitiatedFlowForExistingUser() {
        val spManager = SPManager("some-idp", TestSDKMgr(buildUser("some-org-id", "some-user-id")), this::sendBroadcast)

        // Faking a request from the idp
        val request = IDPLoginRequest(orgId = "some-org-id", userId = "some-user-id")
        spManager.onReceive(context, request.toIntent().apply {
            putExtra("src_app_package_name", "some-idp")
        })

        // Checking there is no active flow - we had the user, we did not need to start a sp login flow
        Assert.assertNull(spManager.getActiveFlow())

        // Checking that sp switched to requested user and responded back to the IDP app
        waitForEvent("switched to some-org-id some-user-id")
        waitForEvent("sendBroadcast Intent { act=com.salesforce.IDP_LOGIN_RESPONSE pkg=some-idp (has extras) } extras = { uuid = ${request.uuid} src_app_package_name = com.salesforce.androidsdk.tests }")
    }

    @Test
    fun testIDPInitiatedFlowForNewUser() {
        val spManager = SPManager("some-idp", TestSDKMgr(null), this::sendBroadcast)

        // Faking a request from the idp
        val request = IDPLoginRequest(orgId = "some-other-org-id", userId = "some-other-user-id")
        spManager.onReceive(context, request.toIntent().apply {
            putExtra("src_app_package_name", "some-idp")
        })

        // Checking active flow
        val activeFlow = spManager.getActiveFlow() as? SPLoginFlow
        Assert.assertNotNull(activeFlow)
        val idpLoginRequest = activeFlow?.messages?.get(0)
        Assert.assertNotNull(idpLoginRequest)
        Assert.assertTrue(idpLoginRequest is IDPLoginRequest)
        Assert.assertEquals(request.toString(), idpLoginRequest.toString())

        // Checking that sp responded with a SP_LOGIN_REQUEST
        waitForEvent("sendBroadcast Intent { act=com.salesforce.SP_LOGIN_REQUEST pkg=some-idp (has extras) } extras = { uuid = ${request.uuid} src_app_package_name = com.salesforce.androidsdk.tests code_challenge = ")

        // Checking active flow - we did not have the user, we had to start a sp login flow
        Assert.assertEquals(2, activeFlow?.messages?.size)
        val spLoginRequest = activeFlow?.messages?.get(1)
        Assert.assertNotNull(spLoginRequest)
        Assert.assertTrue(spLoginRequest is SPLoginRequest)

        // Faking a response from the idp
        val response = SPLoginResponse(request.uuid, loginUrl = "some-login-url",  code = "some-code")
        spManager.onReceive(context, response.toIntent().apply {
            putExtra("src_app_package_name", "some-idp")
        })

        // Checking active flow - we did not have the user, we had to start a sp login flow
        Assert.assertEquals(3, activeFlow?.messages?.size)
        val spLoginResponse = activeFlow?.messages?.get(2)
        Assert.assertNotNull(spLoginResponse)
        Assert.assertTrue(spLoginResponse is SPLoginResponse)
        Assert.assertEquals(response.toString(), spLoginResponse.toString())

        // Make sure login with code happens
        waitForEvent("logging in with some-login-url some-code ${activeFlow?.codeVerifier}")
        waitForEvent("switched to org-for-some-code user-for-some-code")

        // Expecting a response sent back to IDP
        waitForEvent("sendBroadcast Intent { act=com.salesforce.IDP_LOGIN_RESPONSE pkg=some-idp (has extras) } extras = { uuid = ${request?.uuid} src_app_package_name = com.salesforce.androidsdk.tests }")
    }
}
