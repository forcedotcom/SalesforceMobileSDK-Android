package com.salesforce.androidsdk.auth.idp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.util.LogUtil
import org.junit.Assert
import java.lang.RuntimeException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

internal open class IDPSPManagerTestCase {
    companion object {
        const val MAX_EVENTS = 16
        const val TIMEOUT_MS: Long = 500 // we don't go to the server so async operations should complete quickly
    }

    lateinit var context: Context
    lateinit var recordedEvents: BlockingQueue<String>

    fun recordEvent(event: String) {
        Log.i(this::class.java.simpleName, "recording event   [$event]")
        recordedEvents.add(event)
    }

    open fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        recordedEvents = ArrayBlockingQueue(MAX_EVENTS)
    }

    fun sendBroadcast(context:Context, intent: Intent) {
        recordEvent("sendBroadcast ${LogUtil.intentToString(intent)}")
    }

    open fun startActivity(context:Context, intent: Intent) {
        recordEvent("startActivity ${LogUtil.intentToString(intent)}")
    }

    fun throwOnSend(context: Context, intent: Intent) {
        throw RuntimeException()
    }

    fun waitForEvent(expectedEvent: String) {
        Log.i(this::class.java.simpleName, "waiting for event [$expectedEvent]")
        val actualEvent = recordedEvents.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (actualEvent == null) {
            Assert.fail("received no event")
        } else {
            Log.i(this::class.java.simpleName, "received event    [$actualEvent]")
            Assert.assertTrue(actualEvent.startsWith(expectedEvent))
        }
    }

    fun expectNoEvent() {
        Assert.assertNull(recordedEvents.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
    }

    fun buildUser(orgId: String, userId:String):UserAccount {
        return UserAccount(Bundle().apply {
            putString("orgId", orgId)
            putString("userId", userId)
        })
    }

    fun checkActiveFlow(idpSpManager: IDPSPManager,
                        expectedAction: String,
                        expectedMessageIndex: Int):IDPSPMessage? {
        val activeFlow = idpSpManager.getActiveFlow()
        Assert.assertNotNull("Active flow not expected to be null", activeFlow)
        activeFlow?.let {
            Assert.assertTrue("Not enough messages in active flow",
                it.messages.size > expectedMessageIndex)
            Assert.assertEquals(
                "Wrong message type at index $expectedMessageIndex",
                expectedAction, it.messages[expectedMessageIndex].action
            )
        }
        return activeFlow?.messages?.get(expectedMessageIndex)
    }

    fun checkActiveFlow(idpSpManager: IDPSPManager,
                        expectedMessage: IDPSPMessage,
                        expectedMessageIndex: Int) {
        val activeFlow = idpSpManager.getActiveFlow()
        Assert.assertNotNull("Active flow not expected to be null", activeFlow)
        activeFlow?.let {
            Assert.assertTrue("Not enough messages in active flow",
                it.messages.size > expectedMessageIndex)
            Assert.assertEquals(
                "Wrong message at index $expectedMessageIndex",
                expectedMessage.toString(), it.messages[expectedMessageIndex].toString()
            )
        }
    }
}