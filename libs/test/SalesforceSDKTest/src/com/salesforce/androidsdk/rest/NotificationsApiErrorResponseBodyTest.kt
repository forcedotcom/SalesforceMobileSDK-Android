package com.salesforce.androidsdk.rest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for `NotificationsApiErrorResponseBody`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class NotificationsApiErrorResponseBodyTest {

    @Test
    fun testNotificationsApiException() {

        val value = NotificationsApiErrorResponseBody(
            message = "test_message",
            errorCode = "test_error_code",
            messageCode = "test_message_code"
        )

        val json = Json.encodeToString(
            JsonArray.serializer(),
            JsonArray(
                listOf(Json.encodeToJsonElement(value))
            )
        )

        val other = NotificationsApiErrorResponseBody.fromJson(
            json
        )

        Assert.assertTrue(arrayOf(value).contentEquals(other))
        Assert.assertEquals(
            json,
            other.first().sourceJson
        )
        Assert.assertEquals(value.hashCode(), other.first().hashCode())

        val valueDefault = NotificationsApiErrorResponseBody()

        Assert.assertFalse(value == valueDefault)
        Assert.assertNotEquals(value.hashCode(), valueDefault.hashCode())
    }
}