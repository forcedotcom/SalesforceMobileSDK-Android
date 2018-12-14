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

    private Context targetContext;

    @Before
    public void setUp() throws Exception {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SalesforceLogger.flushComponents();
        SalesforceLogger.resetLoggerPrefs(targetContext);
        final Set<String> components = SalesforceLogger.getComponents();
        Assert.assertNull("No components should be returned", components);
    }

    @After
    public void tearDown() throws Exception {
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
}
