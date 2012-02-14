/*
 * Copyright (c) 2011, salesforce.com, inc.
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
package com.salesforce.samples.smartstore;

import android.test.ActivityInstrumentationTestCase2;

import com.salesforce.androidsdk.phonegap.TestRunnerPlugin;
import com.salesforce.androidsdk.phonegap.TestRunnerPlugin.TestResult;
import com.salesforce.androidsdk.ui.SalesforceDroidGapActivity;

/**
 * Running javascript tests for SmartStore plugin
 */
public class SmartStoreJSTest extends
		ActivityInstrumentationTestCase2<SalesforceDroidGapActivity> {

	private SalesforceDroidGapActivity activity;

	public SmartStoreJSTest() {
		super("com.salesforce.samples.smartstore", SalesforceDroidGapActivity.class);
	}
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		activity = getActivity();
		// Block until the javascript has notified the container that it's ready
		TestRunnerPlugin.readyForTests.take(); 
	}


    public void testRegisterRemoveSoup() throws InterruptedException {
        runTest("testRegisterRemoveSoup");
    }

    public void testRegisterBogusSoup() throws InterruptedException {
        runTest("testRegisterBogusSoup");
    }

    public void testRegisterSoupNoIndices() throws InterruptedException {
        runTest("testRegisterSoupNoIndices");
    }

    public void testUpsertSoupEntries() throws InterruptedException {
        runTest("testUpsertSoupEntries");
    }

    public void testUpsertToNonexistentSoup() throws InterruptedException {
        runTest("testUpsertToNonexistentSoup");
    }

    public void testRetrieveSoupEntries() throws InterruptedException {
        runTest("testRetrieveSoupEntries");
    }

    public void testRemoveFromSoup() throws InterruptedException {
        runTest("testRemoveFromSoup");
    }

    public void testQuerySoup() throws InterruptedException {
        runTest("testQuerySoup");
    }

    public void testQuerySoupDescending() throws InterruptedException {
        runTest("testQuerySoupDescending");
    }
    
    public void testQuerySoupBadQuerySpec() throws InterruptedException {
        runTest("testQuerySoupBadQuerySpec");
    }

    public void testQuerySoupEndKeyNoBeginKey() throws InterruptedException {
        runTest("testQuerySoupEndKeyNoBeginKey");
    }

    public void testQuerySoupBeginKeyNoEndKey() throws InterruptedException {
        runTest("testQuerySoupBeginKeyNoEndKey");
    }

    public void testManipulateCursor() throws InterruptedException {
        runTest("testManipulateCursor");
    }

    public void testArbitrarySoupNames() throws InterruptedException {
        runTest("testArbitrarySoupNames");
    }

    public void testQuerySpecFactories() throws InterruptedException {
        runTest("testQuerySpecFactories");
    }

    public void testLikeQuerySpecStartsWith() throws InterruptedException {
        runTest("testLikeQuerySpecStartsWith");
    }

    public void testLikeQuerySpecEndsWith() throws InterruptedException {
        runTest("testLikeQuerySpecEndsWith");
    }

    public void testLikeQueryInnerText() throws InterruptedException {
        runTest("testLikeQueryInnerText");
    }

    public void testCompoundQueryPath() throws InterruptedException {
        runTest("testCompoundQueryPath");
    }

    public void testEmptyQuerySpec() throws InterruptedException {
        runTest("testEmptyQuerySpec");
    }

    public void testIntegerQuerySpec() throws InterruptedException {
        runTest("testIntegerQuerySpec");
    }
    
	/**
	 * Helper method: runs javascript test and wait for it to complete
	 * @param testName
	 * @throws InterruptedException
	 */
	private void runTest(String testName) throws InterruptedException {
		activity.sendJavascript("testSuite.startTest('" + testName + "');");
		// Block until test completes
		TestResult result = TestRunnerPlugin.testResults.take();
		assertEquals("Wrong test completed", testName, result.testName);
		assertTrue(result.testName + " " + result.message, result.success);
	}
	
	
}
