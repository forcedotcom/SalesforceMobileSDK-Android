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

import android.content.Intent
import android.os.Bundle
import org.junit.Assert
import org.junit.Test

class IDPSPMessageTest {

    @Test
    fun testIDPToSPRequestToBundle() {
        val message = IDPSPMessage.IDPToSPRequest("some-uuid", "some-org-id", "some-user-id")
        val bundle = message.toBundle()

        Assert.assertEquals("some-uuid", bundle.getString("uuid"))
        Assert.assertEquals("some-org-id", bundle.getString("org_id"))
        Assert.assertEquals("some-user-id", bundle.getString("user_id"))
    }

    @Test
    fun testSPToIDPResponseToBundle() {
        val message = IDPSPMessage.SPToIDPResponse("some-uuid")
        val bundle = message.toBundle()

        Assert.assertEquals("some-uuid", bundle.getString("uuid"))
    }

    @Test
    fun testSPToIDPRequestToBundle() {
        val message = IDPSPMessage.SPToIDPRequest("some-uuid", "some-code-challenge")
        val bundle = message.toBundle()

        Assert.assertEquals("some-uuid", bundle.getString("uuid"))
        Assert.assertEquals("some-code-challenge", bundle.getString("code_challenge"))
    }

    @Test
    fun testIDPToSPResponseToBundle() {
        val message = IDPSPMessage.IDPToSPResponse("some-uuid", "some-code", "some-login-url")
        val bundle = message.toBundle()

        Assert.assertEquals("some-uuid", bundle.getString("uuid"))
        Assert.assertEquals("some-code", bundle.getString("code"))
        Assert.assertEquals("some-login-url", bundle.getString("login_url"))
    }

    @Test
    fun testIDPToSPRequestFromBundle() {
        val bundle = Bundle().apply {
            putString("uuid", "some-uuid")
            putString("org_id", "some-org-id")
            putString("user_id", "some-user-id")
        }
        val message = IDPSPMessage.IDPToSPRequest.fromBundle(bundle)

        Assert.assertEquals("some-uuid", message?.uuid)
        Assert.assertEquals("some-org-id", message?.orgId)
        Assert.assertEquals("some-user-id", message?.userId)
    }

    @Test
    fun testSPToIDPResponseFromBundle() {
        val bundle = Bundle().apply {
            putString("uuid", "some-uuid")
            putString("error", "some-error")
        }
        val message = IDPSPMessage.SPToIDPResponse.fromBundle(bundle)

        Assert.assertEquals("some-uuid", message?.uuid)
        Assert.assertEquals("some-error", message?.error)
    }

    @Test
    fun testSPToIDPRequestFromBundle() {
        val bundle = Bundle().apply {
            putString("uuid", "some-uuid")
            putString("code_challenge", "some-code-challenge")
        }
        val message = IDPSPMessage.SPToIDPRequest.fromBundle(bundle)

        Assert.assertEquals("some-uuid", message?.uuid)
        Assert.assertEquals("some-code-challenge", message?.codeChallenge)
    }

    @Test
    fun testIDPToSPResponseFromBundle() {
        val bundle = Bundle().apply {
            putString("uuid", "some-uuid")
            putString("code", "some-code")
            putString("login_url", "some-login-url")
            putString("error", "some-error")
        }
        val message = IDPSPMessage.IDPToSPResponse.fromBundle(bundle)

        Assert.assertEquals("some-uuid", message?.uuid)
        Assert.assertEquals("some-code", message?.code)
        Assert.assertEquals("some-login-url", message?.loginUrl)
        Assert.assertEquals("some-error", message?.error)
    }

    @Test
    fun testIDPToSPRequestToIntentFromIntent() {
        val message = IDPSPMessage.IDPToSPRequest("some-uuid", "some-org-id", "some-user-id")

        val intent = message.toIntent()
        Assert.assertEquals("com.salesforce.androidsdk.IDP_TO_SP_REQUEST", intent.action)
        Assert.assertEquals("some-uuid", intent.getStringExtra("uuid"))
        Assert.assertEquals("some-org-id", intent.getStringExtra("org_id"))
        Assert.assertEquals("some-user-id", intent.getStringExtra("user_id"))

        val recreatedMessage = IDPSPMessage.fromIntent(intent)
        Assert.assertTrue(recreatedMessage is IDPSPMessage.IDPToSPRequest)
        val typedRecreatedMessage = recreatedMessage as IDPSPMessage.IDPToSPRequest
        Assert.assertEquals(typedRecreatedMessage.uuid, message.uuid)
        Assert.assertEquals(typedRecreatedMessage.orgId, message.orgId)
        Assert.assertEquals(typedRecreatedMessage.userId, message.userId)
    }

    @Test
    fun testSPToIDPResponseToIntentFromIntent() {
        val message = IDPSPMessage.SPToIDPResponse("some-uuid")

        val intent = message.toIntent()
        Assert.assertEquals("com.salesforce.androidsdk.SP_TO_IDP_RESPONSE", intent.action)
        Assert.assertEquals("some-uuid", intent.getStringExtra("uuid"))

        val recreatedMessage = IDPSPMessage.fromIntent(intent)
        Assert.assertTrue(recreatedMessage is IDPSPMessage.SPToIDPResponse)
        val typedRecreatedMessage = recreatedMessage as IDPSPMessage.SPToIDPResponse
        Assert.assertEquals(typedRecreatedMessage.uuid, message.uuid)
    }

    @Test
    fun testSPToIDPRequestToIntentFromIntent() {
        val message = IDPSPMessage.SPToIDPRequest("some-uuid", "some-code-challenge")

        val intent = message.toIntent()
        Assert.assertEquals("com.salesforce.androidsdk.SP_TO_IDP_REQUEST", intent.action)
        Assert.assertEquals("some-uuid", intent.getStringExtra("uuid"))
        Assert.assertEquals("some-code-challenge", intent.getStringExtra("code_challenge"))

        val recreatedMessage = IDPSPMessage.fromIntent(intent)
        Assert.assertTrue(recreatedMessage is IDPSPMessage.SPToIDPRequest)
        val typedRecreatedMessage = recreatedMessage as IDPSPMessage.SPToIDPRequest
        Assert.assertEquals(typedRecreatedMessage.uuid, message.uuid)
        Assert.assertEquals(typedRecreatedMessage.codeChallenge, message.codeChallenge)
    }

    @Test
    fun testSPToIDPRequestFromLaunchIntent() {
        val message = IDPSPMessage.SPToIDPRequest("some-uuid", "some-code-challenge")
        val intent = message.toIntent().apply {
            putExtra(IDPSPManager.SRC_APP_PACKAGE_NAME_KEY, "spAppPackageName")
            // Intent action needs to be ACTION_VIEW, so passing message action through extras
            putExtra(IDPSPMessage.ACTION_KEY, message.action)
            action = Intent.ACTION_VIEW
            setClassName("idpAppPackageName", IDPAuthCodeActivity::class.java.name)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val recreatedMessage = IDPSPMessage.fromIntent(intent)
        Assert.assertTrue(recreatedMessage is IDPSPMessage.SPToIDPRequest)
        val typedRecreatedMessage = recreatedMessage as IDPSPMessage.SPToIDPRequest
        Assert.assertEquals(typedRecreatedMessage.uuid, message.uuid)
        Assert.assertEquals(typedRecreatedMessage.codeChallenge, message.codeChallenge)
    }

    @Test
    fun testIDPToSPResponseToIntentFromIntent() {
        val message = IDPSPMessage.IDPToSPResponse("some-uuid", "some-code", "some-login-url", "some-error")

        val intent = message.toIntent()
        Assert.assertEquals("com.salesforce.androidsdk.IDP_TO_SP_RESPONSE", intent.action)
        Assert.assertEquals("some-uuid", intent.getStringExtra("uuid"))
        Assert.assertEquals("some-code", intent.getStringExtra("code"))
        Assert.assertEquals("some-login-url", intent.getStringExtra("login_url"))
        Assert.assertEquals("some-error", intent.getStringExtra("error"))

        val recreatedMessage = IDPSPMessage.fromIntent(intent)
        Assert.assertTrue(recreatedMessage is IDPSPMessage.IDPToSPResponse)
        val typedRecreatedMessage = recreatedMessage as IDPSPMessage.IDPToSPResponse
        Assert.assertEquals(typedRecreatedMessage.uuid, message.uuid)
        Assert.assertEquals(typedRecreatedMessage.code, message.code)
        Assert.assertEquals(typedRecreatedMessage.loginUrl, message.loginUrl)
        Assert.assertEquals(typedRecreatedMessage.error, message.error)
    }
}