package com.salesforce.androidsdk.util

import android.os.Bundle
import org.junit.Assert

class BundleTestHelper {
    companion object {
        @JvmStatic
        fun checkSameBundle(message: String, expected: Bundle?, actual: Bundle?) {
            // Check if both bundles are null
            if (expected == null && actual == null) {
                return
            }

            // Fail if one is null and the other is not
            if (expected == null || actual == null) {
                Assert.fail("$message: One of the bundles is null.")
                return
            }

            // Check if both bundles contain the same keys
            for (key in expected.keySet()) {
                if (!actual.containsKey(key)) {
                    Assert.fail("$message: Key $key is missing in the actual bundle.")
                }
            }

            for (key in actual.keySet()) {
                if (!expected.containsKey(key)) {
                    Assert.fail("$message: Key $key is missing in the expected bundle.")
                }
            }


            // Check if both bundles contain the same values
            for (key in expected.keySet()) {
                @Suppress("DEPRECATION") val value1 = expected[key]
                @Suppress("DEPRECATION") val value2 = actual[key]

                // If both values are bundles, compare them recursively
                if (value1 is Bundle && value2 is Bundle) {
                    checkSameBundle(message, value1 as Bundle?, value2 as Bundle?)
                } else if (value1 == null && value2 != null || value1 != null && value1 != value2) {
                    Assert.fail("$message: Values for key $key don't match: $value1 vs $value2")
                }
            }
        }
    }
}