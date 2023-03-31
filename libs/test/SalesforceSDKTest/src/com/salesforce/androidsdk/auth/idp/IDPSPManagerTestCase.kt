package com.salesforce.androidsdk.auth.idp

import android.content.Context
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import org.junit.Assert
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

open class IDPSPManagerTestCase {
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

    fun waitForEvent(expectedEvent: String) {
        val actualEvent = recordedEvents.poll(TIMEOUT, TimeUnit.SECONDS)
        Assert.assertEquals(expectedEvent, actualEvent)
    }

    fun buildUser(orgId: String, userId:String):UserAccount {
        return UserAccount(Bundle().apply {
            putString("orgId", orgId)
            putString("userId", userId)
        })
    }
}