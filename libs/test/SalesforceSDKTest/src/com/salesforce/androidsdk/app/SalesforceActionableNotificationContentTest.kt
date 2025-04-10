package com.salesforce.androidsdk.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.push.SalesforceActionableNotificationContent
import com.salesforce.androidsdk.push.SalesforceActionableNotificationContent.Sfdc
import com.salesforce.androidsdk.push.SalesforceActionableNotificationContent.Sfdc.Act
import com.salesforce.androidsdk.push.SalesforceActionableNotificationContent.Sfdc.Act.Properties
import com.salesforce.androidsdk.push.SalesforceActionableNotificationContent.Sfdc.Act.Properties.Group
import kotlinx.serialization.json.Json.Default.encodeToString
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for `SalesforceActionableNotificationContent`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceActionableNotificationContentTest {

    @Test
    fun testEquals() {

        val value = SalesforceActionableNotificationContent(
            sfdc = Sfdc(
                notifType = "test_notif_type",
                nid = "test_nid",
                oid = "test_oid",
                type = 1,
                alertTitle = "test_alert_title",
                sid = "test_sid",
                rid = "test_rid",
                targetPageRef = "test_target_page_ref",
                badge = 1,
                uid = "test_uid",
                act = Act(
                    group = "test_group",
                    type = "test_type",
                    description = "test_description",
                    properties = Properties(
                        group = Group(
                            type = "test_type",
                            description = "test_description",
                            notification = true
                        ),
                    ),
                ),
                alertBody = "test_alert_body",
                alert = "test_alert",
                cid = "test_cid",
                timestamp = 1
            )
        )

        val json = encodeToString(
            SalesforceActionableNotificationContent.serializer(),
            value
        )

        val other = SalesforceActionableNotificationContent.fromJson(
            json
        )

        Assert.assertTrue(value == other)
        Assert.assertEquals(json, other.sourceJson)
        Assert.assertEquals(value.hashCode(), other.hashCode())

        val valueDefault = SalesforceActionableNotificationContent(
            sfdc = Sfdc()
        )

        Assert.assertFalse(value == valueDefault)
        Assert.assertNotEquals(value.hashCode(), valueDefault.hashCode())
    }
}