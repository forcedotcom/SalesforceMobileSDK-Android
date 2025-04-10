package com.salesforce.androidsdk.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody.NotificationType
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody.NotificationType.ActionGroup
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody.NotificationType.ActionGroup.Action
import kotlinx.serialization.json.Json.Default.encodeToString
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for `NotificationsTypesResponseBodyTest`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class NotificationsTypesResponseBodyTest {

    @Test
    fun testEquals() {

        val value = NotificationsTypesResponseBody(
            notificationTypes = arrayOf(
                NotificationType(
                    type = "test_type",
                    actionGroups = arrayOf(
                        ActionGroup(
                            actions = arrayOf(
                                Action(
                                    type = "test_type",
                                    actionKey = "test_action_key",
                                    label = "test_label",
                                    name = "test_name"
                                )
                            ),
                            name = "test_name"
                        )
                    ),
                    apiName = "test_api_name",
                    label = "test_label"
                )
            )
        )

        val json = encodeToString(
            NotificationsTypesResponseBody.serializer(),
            value
        )

        val other = NotificationsTypesResponseBody.fromJson(
            json
        )

        Assert.assertTrue(value == other)
        Assert.assertEquals(json, other.sourceJson)
        Assert.assertEquals(value.hashCode(), other.hashCode())

        val valueDefault = NotificationsTypesResponseBody()

        @Suppress("ReplaceCallWithBinaryOperator")
        Assert.assertTrue(value.equals(value))
        Assert.assertFalse(value.equals(null))
        Assert.assertFalse(value.equals("Unrelated Type"))
        Assert.assertFalse(value == valueDefault)
        Assert.assertNotEquals(value.hashCode(), valueDefault.hashCode())
    }
}