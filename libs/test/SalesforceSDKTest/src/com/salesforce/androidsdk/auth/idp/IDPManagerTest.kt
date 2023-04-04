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
    }

    @Before
    override fun setup() {
        super.setup()
    }

    @Test
    fun testKickOffIDPInitiatedLoginFlow() {
        val allowedSPApps = listOf(SPConfig("some-sp", "c", "client-id", "callback-url", arrayOf("api")))
        val idpManager = IDPManager(allowedSPApps, TestSDKManager(buildUser("some-org-id", "some-user-id")), this::sendBroadcast)
        idpManager.kickOffIDPInitiatedLoginFlow(context, "some-sp", TestStatusUpdateCallback())

        // Checking active flow
        val activeFlow = idpManager.getActiveFlow()
        Assert.assertNotNull(activeFlow)
        val idpLoginRequest = activeFlow?.firstMessage
        Assert.assertNotNull(idpLoginRequest)
        Assert.assertTrue(idpLoginRequest is IDPLoginRequest)
        Assert.assertEquals("some-org-id", (idpLoginRequest as IDPLoginRequest).orgId)
        Assert.assertEquals("some-user-id", idpLoginRequest.userId)

        // Checking events
        waitForEvent("sendBroadcast Intent { act=com.salesforce.IDP_LOGIN_REQUEST pkg=some-sp (has extras) } extras = { org_id = some-org-id user_id = some-user-id uuid = ${idpLoginRequest.uuid} src_app_package_name = com.salesforce.androidsdk.tests }")
        waitForEvent("status LOGIN_REQUEST_SENT_TO_SP")

        // Faking a response from the sp
        val response = IDPLoginResponse(idpLoginRequest.uuid)
        idpManager.onReceive(context, response.toIntent().apply {
            putExtra("src_app_package_name", "some-sp")
        })

        // Checking events
        waitForEvent("status SP_LOGIN_COMPLETE")

        // Checking active flow
        Assert.assertEquals(2, activeFlow.messages.size)
        val idpLoginResponse = activeFlow.messages.get(1)
        Assert.assertNotNull(idpLoginResponse)
        Assert.assertTrue(idpLoginResponse is IDPLoginResponse)
        Assert.assertEquals(response.toString(), idpLoginResponse.toString())

        //  NB it won't attempt to start the activity in the SP app because the flow was
        //     started using a context that is not an activity

        // Expect no more events
        expectNoEvent()
    }

    @Test
    fun testSPInitiatedLoginFlowIDPNotLoggedInt() {
        val allowedSPApps = listOf(SPConfig("some-sp", "c", "client-id", "callback-url", arrayOf("api")))
        val idpManager = IDPManager(allowedSPApps, TestSDKManager(null), this::sendBroadcast)

        // Faking a request from the sp
        val request = SPLoginRequest(codeChallenge = "some-challenge")
        idpManager.onReceive(context, request.toIntent().apply {
            putExtra("src_app_package_name", "some-sp")
        })

        // Checking there is no active flow - we did not initiate
        Assert.assertNull(idpManager.getActiveFlow())

        // Expecting an error response to be sent back - since we setup the IDP not to have a current user
        waitForEvent("sendBroadcast Intent { act=com.salesforce.SP_LOGIN_RESPONSE pkg=some-sp (has extras) } extras = { login_url = null code = null uuid = ${request.uuid} error = IDP app not logged in src_app_package_name = com.salesforce.androidsdk.tests }")
    }

    @Test
    fun testSPInitiatedLoginFlowBadSP() {
        val allowedSPApps = listOf(SPConfig("some-sp", "c", "client-id", "callback-url", arrayOf("api")))
        val idpManager = IDPManager(allowedSPApps, TestSDKManager(null), this::sendBroadcast)

        // Faking a request from the sp
        val request = SPLoginRequest(codeChallenge = "some-challenge")
        idpManager.onReceive(context, request.toIntent().apply {
            putExtra("src_app_package_name", "some-other-sp")
        })

        // Checking there is no active flow - we did not initiate
        Assert.assertNull(idpManager.getActiveFlow())

        // Expect no more events
        expectNoEvent()
    }
}
