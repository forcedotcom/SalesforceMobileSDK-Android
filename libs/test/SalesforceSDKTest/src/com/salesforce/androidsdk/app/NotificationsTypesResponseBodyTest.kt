package com.salesforce.androidsdk.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.app.PushMessagingTest.Companion.NOTIFICATIONS_TYPES_JSON
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for `PushMessaging`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class NotificationsTypesResponseBodyTest {

    @Test
    fun testEquals() {
        val a = NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON)
        val b = NotificationsTypesResponseBody.fromJson(NOTIFICATIONS_TYPES_JSON)

        Assert.assertTrue(a == b)
        Assert.assertTrue(a.hashCode() == b.hashCode())
    }
}