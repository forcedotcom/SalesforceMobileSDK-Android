/*
 * Copyright (c) 2017-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.analytics.logger;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;
import java.util.Set;

/**
 * Tests for SalesforceLogger.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SalesforceLoggerTest {

    private static final String TEST_COMPONENT_1 = "TestComponent1";
    private static final String TEST_COMPONENT_2 = "TestComponent2";
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int VISIBLE_CHARS = 4;

    private Context targetContext;
    private final Random random = new Random();

    @Before
    public void setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SalesforceLogger.flushComponents();
        SalesforceLogger.resetLoggerPrefs(targetContext);
        final Set<String> components = SalesforceLogger.getComponents();
        Assert.assertNull("No components should be returned", components);
    }

    @After
    public void tearDown() {
        SalesforceLogger.flushComponents();
        SalesforceLogger.resetLoggerPrefs(targetContext);
    }

    /**
     * Test for adding a single component.
     */
    @Test
    public void testAddSingleComponent() {
        final SalesforceLogger logger = SalesforceLogger.getLogger(TEST_COMPONENT_1, targetContext);
        Assert.assertNotNull("SalesforceLogger instance should not be null", logger);
        final Set<String> components = SalesforceLogger.getComponents();
        Assert.assertEquals("Number of components should be 1", 1, components.size());
    }

    /**
     * Test for adding multiple components.
     */
    @Test
    public void testAddMultipleComponents() {
        SalesforceLogger logger = SalesforceLogger.getLogger(TEST_COMPONENT_1, targetContext);
        Assert.assertNotNull("SalesforceLogger instance should not be null", logger);
        logger = SalesforceLogger.getLogger(TEST_COMPONENT_2, targetContext);
        Assert.assertNotNull("SalesforceLogger instance should not be null", logger);
        final Set<String> components = SalesforceLogger.getComponents();
        Assert.assertEquals("Number of components should be 2", 2, components.size());
        Assert.assertTrue("Component should be present in results", components.contains(TEST_COMPONENT_1));
        Assert.assertTrue("Component should be present in results", components.contains(TEST_COMPONENT_2));
    }

    /**
     * Test for setting log level.
     */
    @Test
    public void testSetLogLevel() {
        final SalesforceLogger logger = SalesforceLogger.getLogger(TEST_COMPONENT_1, targetContext);
        Assert.assertNotNull("SalesforceLogger instance should not be null", logger);
        SalesforceLogger.Level logLevel = logger.getLogLevel();
        Assert.assertNotSame("Log levels should not be same", SalesforceLogger.Level.VERBOSE, logLevel);
        logger.setLogLevel(SalesforceLogger.Level.VERBOSE);
        logLevel = logger.getLogLevel();
        Assert.assertEquals("Log levels should be the same", SalesforceLogger.Level.VERBOSE, logLevel);
    }

    /**
     * Test that null input returns null.
     */
    @Test
    public void testRedactNull() {
        Assert.assertNull("Redact of null should return null", SalesforceLogger.redact(null));
    }

    /**
     * Test that an empty string is unchanged.
     */
    @Test
    public void testRedactEmptyString() {
        Assert.assertEquals("Empty string should be unchanged", "", SalesforceLogger.redact(""));
    }

    /**
     * Test that a message without sensitive data is unchanged.
     */
    @Test
    public void testRedactNonSensitiveMessage() {
        final String message = "User logged in successfully";
        Assert.assertEquals("Non-sensitive message should be unchanged", message, SalesforceLogger.redact(message));
    }

    /**
     * Test that access_token is redacted in JSON.
     */
    @Test
    public void testRedactAccessToken() {
        final String value = randomString(23);
        final String input = "{\"access_token\":\"" + value + "\"}";
        final String expected = "{\"access_token\":\"" + expectedMask(value) + "\"}";
        Assert.assertEquals("access_token should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that refresh_token is redacted in JSON.
     */
    @Test
    public void testRedactRefreshToken() {
        final String value = randomString(43);
        final String input = "{\"refresh_token\":\"" + value + "\"}";
        final String expected = "{\"refresh_token\":\"" + expectedMask(value) + "\"}";
        Assert.assertEquals("refresh_token should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that id_token is redacted in JSON.
     */
    @Test
    public void testRedactIdToken() {
        final String value = randomString(51);
        final String input = "{\"id_token\":\"" + value + "\"}";
        final String expected = "{\"id_token\":\"" + expectedMask(value) + "\"}";
        Assert.assertEquals("id_token should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that csrf_token is redacted in JSON.
     */
    @Test
    public void testRedactCsrfToken() {
        final String value = randomString(10);
        final String input = "{\"csrf_token\":\"" + value + "\"}";
        final String expected = "{\"csrf_token\":\"" + expectedMask(value) + "\"}";
        Assert.assertEquals("csrf_token should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that sid is redacted in JSON.
     */
    @Test
    public void testRedactSid() {
        final String value = randomString(17);
        final String input = "{\"sid\":\"" + value + "\"}";
        final String expected = "{\"sid\":\"" + expectedMask(value) + "\"}";
        Assert.assertEquals("sid should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that lightning_sid is redacted in JSON.
     */
    @Test
    public void testRedactLightningSid() {
        final String value = randomString(17);
        final String input = "{\"lightning_sid\":\"" + value + "\"}";
        final String expected = "{\"lightning_sid\":\"" + expectedMask(value) + "\"}";
        Assert.assertEquals("lightning_sid should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that visualforce_sid is redacted in JSON.
     */
    @Test
    public void testRedactVisualforceSid() {
        final String value = randomString(10);
        final String input = "{\"visualforce_sid\":\"" + value + "\"}";
        final String expected = "{\"visualforce_sid\":\"" + expectedMask(value) + "\"}";
        Assert.assertEquals("visualforce_sid should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that content_sid is redacted in JSON.
     */
    @Test
    public void testRedactContentSid() {
        final String value = randomString(15);
        final String input = "{\"content_sid\":\"" + value + "\"}";
        final String expected = "{\"content_sid\":\"" + expectedMask(value) + "\"}";
        Assert.assertEquals("content_sid should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that parent_sid is redacted in JSON.
     */
    @Test
    public void testRedactParentSid() {
        final String value = randomString(14);
        final String input = "{\"parent_sid\":\"" + value + "\"}";
        final String expected = "{\"parent_sid\":\"" + expectedMask(value) + "\"}";
        Assert.assertEquals("parent_sid should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that beacon_child_consumer_secret is redacted in JSON.
     */
    @Test
    public void testRedactBeaconChildConsumerSecret() {
        final String value = randomString(11);
        final String input = "{\"beacon_child_consumer_secret\":\"" + value + "\"}";
        final String expected = "{\"beacon_child_consumer_secret\":\"" + expectedMask(value) + "\"}";
        Assert.assertEquals("beacon_child_consumer_secret should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that multiple sensitive keys in one JSON message are all redacted.
     */
    @Test
    public void testRedactMultipleJsonKeys() {
        final String accessValue = randomString(8);
        final String refreshValue = randomString(10);
        final String input = "{\"access_token\":\"" + accessValue + "\",\"refresh_token\":\"" + refreshValue
                + "\",\"instance_url\":\"https://na1.salesforce.com\"}";
        final String expected = "{\"access_token\":\"" + expectedMask(accessValue)
                + "\",\"refresh_token\":\"" + expectedMask(refreshValue)
                + "\",\"instance_url\":\"https://na1.salesforce.com\"}";
        Assert.assertEquals("Multiple sensitive keys should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that JSON keys with spaces around colon are redacted.
     */
    @Test
    public void testRedactJsonWithSpaces() {
        final String value = randomString(8);
        final String input = "{\"access_token\" : \"" + value + "\"}";
        final String expected = "{\"access_token\" : \"" + expectedMask(value) + "\"}";
        Assert.assertEquals("JSON with spaces around colon should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that access_token in a URL query parameter is redacted.
     */
    @Test
    public void testRedactUrlAccessToken() {
        final String value = randomString(15);
        final String input = "https://instance.salesforce.com/secur/frontdoor.jsp?access_token=" + value;
        final String expected = "https://instance.salesforce.com/secur/frontdoor.jsp?access_token=" + expectedMask(value);
        Assert.assertEquals("URL access_token should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that sid in a URL query parameter is redacted.
     */
    @Test
    public void testRedactUrlSid() {
        final String value = randomString(12);
        final String input = "https://instance.salesforce.com/secur/frontdoor.jsp?sid=" + value + "&retURL=/home";
        final String expected = "https://instance.salesforce.com/secur/frontdoor.jsp?sid=" + expectedMask(value)
                + "&retURL=/home";
        Assert.assertEquals("URL sid should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that token in a URL query parameter is redacted.
     */
    @Test
    public void testRedactUrlToken() {
        final String value = randomString(6);
        final String input = "https://example.com/callback?token=" + value + "&state=xyz";
        final String expected = "https://example.com/callback?token=" + expectedMask(value) + "&state=xyz";
        Assert.assertEquals("URL token should be redacted", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that non-sensitive URL parameters are preserved.
     */
    @Test
    public void testRedactUrlPreservesNonSensitive() {
        final String input = "https://example.com/path?display=touch&retURL=/home";
        Assert.assertEquals("Non-sensitive URL params should be unchanged", input, SalesforceLogger.redact(input));
    }

    /**
     * Test redaction with a realistic full token endpoint response.
     */
    @Test
    public void testRedactFullTokenResponse() {
        final String accessVal = randomString(21);
        final String refreshVal = randomString(13);
        final String idTokenVal = randomString(20);
        final String lightningSidVal = randomString(9);
        final String csrfVal = randomString(7);
        final String input = "parsedResponse-->{\"access_token\":\"" + accessVal + "\"," +
                "\"refresh_token\":\"" + refreshVal + "\"," +
                "\"instance_url\":\"https://na1.salesforce.com\"," +
                "\"id\":\"https://login.salesforce.com/id/00Dxx/005xx\"," +
                "\"id_token\":\"" + idTokenVal + "\"," +
                "\"lightning_sid\":\"" + lightningSidVal + "\"," +
                "\"csrf_token\":\"" + csrfVal + "\"}";
        final String result = SalesforceLogger.redact(input);
        Assert.assertFalse("access_token value should not appear", result.contains(accessVal));
        Assert.assertFalse("refresh_token value should not appear", result.contains(refreshVal));
        Assert.assertFalse("id_token value should not appear", result.contains(idTokenVal));
        Assert.assertFalse("lightning_sid value should not appear", result.contains(lightningSidVal));
        Assert.assertFalse("csrf_token value should not appear", result.contains(csrfVal));
        Assert.assertTrue("instance_url value should be preserved", result.contains("https://na1.salesforce.com"));
        Assert.assertTrue("id value should be preserved", result.contains("https://login.salesforce.com/id/00Dxx/005xx"));
        Assert.assertTrue("Masked output should contain stars", result.contains("***"));
    }

    /**
     * Test that mixed JSON and URL content is fully redacted.
     */
    @Test
    public void testRedactMixedContent() {
        final String jsonTokenVal = randomString(12);
        final String urlSidVal = randomString(10);
        final String input = "Response: {\"access_token\":\"" + jsonTokenVal + "\"} from https://example.com?sid=" + urlSidVal;
        final String result = SalesforceLogger.redact(input);
        Assert.assertFalse("JSON token should not appear", result.contains(jsonTokenVal));
        Assert.assertFalse("URL sid should not appear", result.contains(urlSidVal));
    }

    /**
     * Test that values with 4 or fewer characters are fully masked.
     */
    @Test
    public void testRedactShortValue() {
        final String value = randomString(2);
        final String input = "{\"sid\":\"" + value + "\"}";
        final String expected = "{\"sid\":\"" + expectedMask(value) + "\"}";
        Assert.assertEquals("Short values should be fully masked", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that values with exactly 4 characters are fully masked.
     */
    @Test
    public void testRedactExactly4CharValue() {
        final String value = randomString(4);
        final String input = "{\"sid\":\"" + value + "\"}";
        final String expected = "{\"sid\":\"" + expectedMask(value) + "\"}";
        Assert.assertEquals("4-char values should be fully masked", expected, SalesforceLogger.redact(input));
    }

    /**
     * Test that values with 5 characters show only the last 4.
     */
    @Test
    public void testRedact5CharValue() {
        final String value = randomString(5);
        final String input = "{\"sid\":\"" + value + "\"}";
        final String expected = "{\"sid\":\"" + expectedMask(value) + "\"}";
        Assert.assertEquals("5-char values should show last 4", expected, SalesforceLogger.redact(input));
    }

    /**
     * Generates a random alphanumeric string of the given length.
     */
    private String randomString(int length) {
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /**
     * Computes the expected masked value: stars replacing all but the last 4 characters.
     * Values of 4 or fewer characters are fully masked with stars.
     */
    private static String expectedMask(String value) {
        if (value.length() <= VISIBLE_CHARS) {
            return "*".repeat(value.length());
        }
        return "*".repeat(value.length() - VISIBLE_CHARS) + value.substring(value.length() - VISIBLE_CHARS);
    }
}
