package com.salesforce.androidsdk.rest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for `NotificationsApiException`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class NotificationsApiExceptionTest {

    @Test
    fun testNotificationsApiException() {

        val value = NotificationsApiException(
            message = "test_message",
            errorCode = "test_error_code",
            messageCode = "test_message_code",
            source = "test_source"
        )

        Assert.assertEquals(value.message, "test_message")
        Assert.assertEquals(value.errorCode, "test_error_code")
        Assert.assertEquals(value.messageCode, "test_message_code")
        Assert.assertEquals(value.source, "test_source")
    }
}