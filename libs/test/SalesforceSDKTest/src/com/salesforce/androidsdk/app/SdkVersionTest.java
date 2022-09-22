package com.salesforce.androidsdk.app;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SdkVersionTest {
    @Test
    public void testParse() {
        String versionStr = " 0.0000.000000000 ";
        Assert.assertEquals(
                "Expected string \"" + versionStr + "\" to parse to 0.0.0",
                SdkVersion.parseFromString(versionStr),
                new SdkVersion(0, 0, 0, false)
        );

        versionStr = "999999999.999999999.999999999.dev";
        Assert.assertEquals(
                "Expected string \"" + versionStr + "\" to parse to 999999999.999999999.999999999.dev",
                SdkVersion.parseFromString(versionStr),
                new SdkVersion(999999999, 999999999, 999999999, true)
        );

        versionStr = "1.-88.3.dev";
        try {
            SdkVersion.parseFromString(versionStr);
            Assert.fail("Expected parsing version string \"" + versionStr + "\" to fail.");
        } catch (@NonNull final IllegalArgumentException ignored) {
        }

        versionStr = "1234567890.1.1";
        try {
            SdkVersion.parseFromString(versionStr);
            Assert.fail("Expected parsing version string \"" + versionStr + "\" to fail.");
        } catch (@NonNull final IllegalArgumentException ignored) {
        }
    }

    @Test
    public void testConstructor() {
        try {
            new SdkVersion(-1, 0, 0, false);
            Assert.fail("Expected -1 as major version to fail.");
        } catch (@NonNull final IllegalArgumentException ignored) {
        }

        try {
            new SdkVersion(0, 0, 0, false);
            new SdkVersion(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
        } catch (@NonNull final IllegalArgumentException e) {
            Assert.fail("Creating instance of SdkVersion failed when was expected to succeed. Error: " + e.getMessage());
        }
    }

    @Test
    public void testComparison() {
        final SdkVersion v1_2_3_dev = new SdkVersion(1, 2, 3, true);
        final SdkVersion v2_2_3_dev = new SdkVersion(2, 2, 3, true);
        final SdkVersion v2_2_3 = new SdkVersion(2, 2, 3, false);
        final SdkVersion v2_2_4_dev = new SdkVersion(2, 2, 4, true);
        final SdkVersion v2_2_4 = new SdkVersion(2, 2, 4, false);

        final SdkVersion[] expected = new SdkVersion[]{
                v1_2_3_dev,
                v2_2_3_dev,
                v2_2_3,
                v2_2_4_dev,
                v2_2_4
        };
        final SdkVersion[] actual = new SdkVersion[]{
                v2_2_4_dev,
                v2_2_3,
                v1_2_3_dev,
                v2_2_4,
                v2_2_3_dev
        };

        Arrays.sort(actual);

        Assert.assertArrayEquals(
                "Expected sorted versions to be in correct order.",
                expected,
                actual
        );

        Assert.assertTrue(
                "Expected 1.1.1.dev to be less than 1.1.1",
                new SdkVersion(1, 1, 1, true)
                        .isLessThan(new SdkVersion(1, 1, 1, false))
        );

        Assert.assertFalse(
                "Expected 1.1.1.dev to NOT be greater than or equal to 1.1.1",
                new SdkVersion(1, 1, 1, true)
                        .isGreaterThanOrEqualTo(new SdkVersion(1, 1, 1, false))
        );

        Assert.assertTrue(
                "Expected 1.1.0 to be greater than 1.0.99999",
                new SdkVersion(1, 1, 0, false)
                        .isGreaterThan(new SdkVersion(1, 0, 99999, false))
        );

        Assert.assertFalse(
                "Expected 1.1.0 to be NOT less than or equal to 1.0.99999",
                new SdkVersion(1, 1, 0, false)
                        .isLessThanOrEqualTo(new SdkVersion(1, 0, 99999, false))
        );

        Assert.assertTrue(
                "Expected 2.2.0 to be greater than or equal to 2.1.10000",
                new SdkVersion(2, 2, 0, false)
                        .isGreaterThanOrEqualTo(new SdkVersion(2, 1, 10000, false))
        );

        Assert.assertTrue(
                "Expected 3.2.20000 to be less than or equal to 3.3.0",
                new SdkVersion(3, 2, 20000, false)
                        .isLessThanOrEqualTo(new SdkVersion(3, 3, 0, false))
        );

        Assert.assertEquals(
                "Expected two different SdkVersion objects with version 8.8.8.dev to be equal",
                new SdkVersion(8, 8, 8, true),
                new SdkVersion(8, 8, 8, true)
        );

        final SdkVersion selfEquals = new SdkVersion(1000, 1, 5, true);
        Assert.assertEquals(
                "Expected SdkVersion object to be equal to itself",
                selfEquals,
                selfEquals
        );
    }

    @Test
    public void testToString() {
        Assert.assertEquals(SdkVersion.parseFromString("00010.08.002.dev").toString(), "10.8.2.dev");
    }
}
