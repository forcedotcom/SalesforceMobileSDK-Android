package com.salesforce.androidsdk.auth.idp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.util.LogUtil
import org.junit.Assert
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

internal open class IDPSPManagerTestCase {
    companion object {
        const val MAX_EVENTS = 16
        const val TIMEOUT: Long = 3
    }

    lateinit var context: Context
    lateinit var recordedEvents: BlockingQueue<String>

    open fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        recordedEvents = ArrayBlockingQueue(MAX_EVENTS)
    }

    fun sendBroadcast(context:Context, intent: Intent) {
        recordedEvents.add("sendBroadcast ${LogUtil.intentToString(intent)}")
    }

    fun waitForEvent(expectedEvent: String) {
        val actualEvent = recordedEvents.poll(TIMEOUT, TimeUnit.SECONDS)
        Log.i(this::class.java.simpleName, "received event $actualEvent")
        Log.i(this::class.java.simpleName, "expected event $expectedEvent")
        Assert.assertTrue(actualEvent.startsWith(expectedEvent))
    }

    fun expectNoEvent() {
        Assert.assertNull(recordedEvents.poll(TIMEOUT, TimeUnit.SECONDS))
    }

    fun buildUser(orgId: String, userId:String):UserAccount {
        return UserAccount(Bundle().apply {
            putString("orgId", orgId)
            putString("userId", userId)
        })
    }
}