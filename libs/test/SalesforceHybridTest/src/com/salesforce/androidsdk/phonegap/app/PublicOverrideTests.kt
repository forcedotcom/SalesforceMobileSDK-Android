package com.salesforce.androidsdk.phonegap.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.phonegap.ui.SalesforceDroidGapActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class PublicOverrideTests {
    @Test
    fun overrideSalesforceDroidGapActivity() {
        class Override : SalesforceDroidGapActivity() {
            override val unauthenticatedStartPage: String
                get() = ""
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // Instantiate to ensure this compiles.
            Override()
        }
    }
}