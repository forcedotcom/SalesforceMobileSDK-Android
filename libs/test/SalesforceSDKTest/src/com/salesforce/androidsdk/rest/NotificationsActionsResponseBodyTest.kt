package com.salesforce.androidsdk.rest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlinx.serialization.json.Json.Default.encodeToString
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for `NotificationsActionsResponseBody`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class NotificationsActionsResponseBodyTest {

    @Test
    fun testEquals() {

        val value = NotificationsActionsResponseBody(
            message = "test_message"
        )

        val json = encodeToString(
            NotificationsActionsResponseBody.serializer(),
            value
        )

        val other = NotificationsActionsResponseBody.fromJson(
            json
        )

        Assert.assertTrue(value == other)
        Assert.assertEquals(json, other.sourceJson)
        Assert.assertEquals(value.hashCode(), other.hashCode())

        val valueDefault = NotificationsActionsResponseBody(
            message = "default_message"
        )

        Assert.assertFalse(value == valueDefault)
        Assert.assertNotEquals(value.hashCode(), valueDefault.hashCode())
    }
}