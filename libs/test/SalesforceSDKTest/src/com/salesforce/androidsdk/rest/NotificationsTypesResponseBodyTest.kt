package com.salesforce.androidsdk.rest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody.NotificationType
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody.NotificationType.ActionGroup
import com.salesforce.androidsdk.rest.NotificationsTypesResponseBody.NotificationType.ActionGroup.Action
import kotlinx.serialization.json.Json.Default.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for `NotificationsTypesResponseBodyTest`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class NotificationsTypesResponseBodyTest {

    @Suppress("ReplaceCallWithBinaryOperator")
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

        assertTrue(value == other)
        assertEquals(json, other.sourceJson)
        assertEquals(value.hashCode(), other.hashCode())

        val valueDefault = NotificationsTypesResponseBody()

        assertTrue(value.equals(value))
        assertFalse(value.equals(null))
        assertFalse(value.equals("Unrelated Type"))

        assertFalse((value.copy(notificationTypes = null).equals(value)))
        assertFalse(value.equals(value.copy(notificationTypes = null)))
        assertTrue(value.copy(notificationTypes = null).equals(value.copy(notificationTypes = null)))
        assertFalse(value.equals(value.copy(notificationTypes = arrayOf())))
        assertFalse(value.copy(notificationTypes = arrayOf()).equals(value))

        assertFalse(value == valueDefault)
        assertNotEquals(value.hashCode(), valueDefault.hashCode())

        value.notificationTypes?.first()?.let { notificationType ->
            assertTrue(notificationType.equals(notificationType))
            assertFalse(notificationType.equals(null))
            assertFalse(notificationType.equals("Unrelated Type"))

            assertFalse(notificationType.copy(actionGroups = null).equals(notificationType))
            assertFalse(notificationType.equals(notificationType.copy(actionGroups = null)))
            assertTrue(notificationType.copy(actionGroups = null).equals(notificationType.copy(actionGroups = null)))
            assertFalse(notificationType.equals(notificationType.copy(actionGroups = arrayOf())))
            assertFalse(notificationType.copy(actionGroups = arrayOf()).equals(notificationType))

            assertFalse(notificationType.hashCode() == notificationType.copy(actionGroups = null).hashCode())

            assertFalse(notificationType.equals(notificationType.copy(apiName = null)))
            assertFalse(notificationType.hashCode() == notificationType.copy(apiName = null).hashCode())
            assertFalse(notificationType.equals(notificationType.copy(label = null)))
            assertFalse(notificationType.hashCode() == notificationType.copy(label = null).hashCode())
            assertFalse(notificationType.equals(notificationType.copy(type = null)))
            assertFalse(notificationType.hashCode() == notificationType.copy(type = null).hashCode())

            notificationType.actionGroups?.first()?.let { actionGroup ->
                assertTrue(actionGroup.equals(actionGroup))
                assertFalse(actionGroup.equals(null))
                assertFalse(actionGroup.equals("Unrelated Type"))
                assertFalse(actionGroup.equals(actionGroup.copy(name = null)))
                assertFalse(actionGroup.hashCode() == actionGroup.copy(name = null).hashCode())

                assertFalse(actionGroup.copy(actions = null).equals(actionGroup))
                assertFalse(actionGroup.equals(actionGroup.copy(actions = null)))
                assertTrue(actionGroup.copy(actions = null).equals(actionGroup.copy(actions = null)))
                assertFalse(actionGroup.equals(actionGroup.copy(actions = arrayOf())))
                assertFalse(actionGroup.copy(actions = arrayOf()).equals(actionGroup))

                assertFalse(actionGroup.hashCode() == actionGroup.copy(actions = null).hashCode())

                actionGroup.actions?.first()?.let { action ->
                    assertTrue(action.equals(action))
                    assertFalse(action.equals(null))
                    assertFalse(action.equals("Unrelated Type"))
                    assertFalse(action.equals(action.copy(actionKey = null)))
                    assertFalse(action.hashCode() == action.copy(actionKey = null).hashCode())
                    assertFalse(action.equals(action.copy(label = null)))
                    assertFalse(action.hashCode() == action.copy(label = null).hashCode())
                    assertFalse(action.equals(action.copy(name = null)))
                    assertFalse(action.hashCode() == action.copy(name = null).hashCode())
                    assertFalse(action.equals(action.copy(type = null)))
                    assertFalse(action.hashCode() == action.copy(type = null).hashCode())
                }
            }
        }
    }
}