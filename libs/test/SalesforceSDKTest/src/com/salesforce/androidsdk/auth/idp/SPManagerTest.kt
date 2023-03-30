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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.*
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager.Status
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager.StatusUpdateCallback
import com.salesforce.androidsdk.util.LogUtil
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class SPManagerTest {

    companion object {
        const val MAX_INTENTS_RECORDED = 16
        const val MAX_UPDATES_RECORDED = 16
        const val TIMEOUT: Long = 3
    }

    lateinit var context: Context
    lateinit var recordedIntents: BlockingQueue<Intent>
    lateinit var recordedUpdates: BlockingQueue<Status>
    lateinit var testReceiver: TestReceiver
    lateinit var testStatusUpdateCallback: TestStatusUpdateCallback

    inner class TestReceiver(): BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            recordedIntents.put(intent)
        }
    }

    inner class TestStatusUpdateCallback() : StatusUpdateCallback {
        override fun onStatusUpdate(status: Status) {
            recordedUpdates.put(status)
        }
    }

    @Before
    fun setup() {
        context = getInstrumentation().targetContext
        recordedIntents = ArrayBlockingQueue(MAX_INTENTS_RECORDED)
        recordedUpdates = ArrayBlockingQueue(MAX_UPDATES_RECORDED)
        testReceiver = TestReceiver()
        context.registerReceiver(testReceiver, IntentFilter("com.salesforce.IDP_LOGIN_REQUEST"))
        context.registerReceiver(testReceiver, IntentFilter("com.salesforce.IDP_LOGIN_RESPONSE"))
        context.registerReceiver(testReceiver, IntentFilter("com.salesforce.SP_LOGIN_REQUEST"))
        context.registerReceiver(testReceiver, IntentFilter("com.salesforce.SP_LOGIN_RESPONSE"))
        testStatusUpdateCallback = TestStatusUpdateCallback()
    }

    @After
    fun teardown() {
        context.unregisterReceiver(testReceiver)
    }

    @Test
    fun testKickOffSPInitiatedLoginFlow() {
        val spManager = SPManager("idp-app")
        spManager.kickOffSPInitiatedLoginFlow(context, testStatusUpdateCallback)
        waitForStatusUpdate(Status.LOGIN_REQUEST_SENT_TO_IDP)
        val activeFlow = spManager.getActiveFlow()
        Assert.assertNotNull(activeFlow)
        Assert.assertTrue(activeFlow?.firstMessage is IDPSPMessage.SPLoginRequest)
    }

    fun waitForStatusUpdate(expectedStatusUpdate: Status) {
        val actualStatusUpdate = recordedUpdates.poll(TIMEOUT, TimeUnit.SECONDS)
        Assert.assertEquals(expectedStatusUpdate, actualStatusUpdate)
    }

    fun waitForMessage(expectedMessage: IDPSPMessage) {
        val actualIntent = recordedIntents.poll(TIMEOUT, TimeUnit.SECONDS)
        Assert.assertEquals(
            LogUtil.intentToString(expectedMessage.toIntent()),
            LogUtil.intentToString(actualIntent)
        )
    }
}
