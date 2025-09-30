package com.salesforce.androidsdk.rest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for `ApiVersionStringsTest`.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class ApiVersionStringsTest {

    @Test
    fun testApiVersionStrings() {
        Assert.assertEquals(ApiVersionStrings.VERSION_NUMBER, ApiVersionStrings.getVersionNumber(null))
    }
}
