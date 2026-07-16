package com.salesforce.androidsdk.app

import android.app.Activity
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceSDKManagerAccountTypeTest {

    @Test
    fun accountTypeThrowsWhenAppUsesLegacyValue() {
        val manager = createManager(LEGACY_ACCOUNT_TYPE)

        val exception = assertThrows(IllegalStateException::class.java) {
            manager.accountType
        }

        assertEquals(
            "No app specific account type found. To ensure users can log in, " +
                    "override the \"account_type\" value in your strings.xml.",
            exception.message,
        )
    }

    @Test
    fun accountTypeReturnsAppSpecificValue() {
        val accountType = "com.example.app.login"
        val manager = createManager(accountType)

        assertEquals(accountType, manager.accountType)
    }

    private fun createManager(accountType: String): SalesforceSDKManager {
        val context = mockk<Context>(relaxed = true) {
            every { getString(R.string.account_type) } returns accountType
        }
        return SalesforceSDKManager(context, Activity::class.java)
    }
}
