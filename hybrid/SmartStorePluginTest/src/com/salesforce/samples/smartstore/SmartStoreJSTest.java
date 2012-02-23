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

	private boolean stressTestsActive = true; //TODO setup property instead
	private SalesforceDroidGapActivity activity;

	public SmartStoreJSTest() {
		super("com.salesforce.samples.smartstore", SalesforceDroidGapActivity.class);
		String stressTestProp = System.getProperty("com.salesforce.SmartStoreJSTest.stress_test_active");
		if ((null != stressTestProp) && stressTestProp.equalsIgnoreCase("true")) {
			stressTestsActive = true;
		}
	}
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		activity = getActivity();
		// Block until the javascript has notified the container that it's ready
		TestRunnerPlugin.readyForTests.take(); 
	}


    public void testRegisterRemoveSoup()  {
        runTest("testRegisterRemoveSoup");
    }

    public void testRegisterBogusSoup()  {
        runTest("testRegisterBogusSoup");
    }

    public void testRegisterSoupNoIndices()  {
        runTest("testRegisterSoupNoIndices");
    }

    public void testUpsertSoupEntries()  {
        runTest("testUpsertSoupEntries");
    }

    public void testUpsertToNonexistentSoup()  {
        runTest("testUpsertToNonexistentSoup");
    }

    public void testRetrieveSoupEntries()  {
        runTest("testRetrieveSoupEntries");
    }

    public void testRemoveFromSoup()  {
        runTest("testRemoveFromSoup");
    }

    public void testQuerySoup()  {
        runTest("testQuerySoup");
    }

    public void testQuerySoupDescending()  {
        runTest("testQuerySoupDescending");
    }
    
    public void testQuerySoupBadQuerySpec()  {
        runTest("testQuerySoupBadQuerySpec");
    }

    public void testQuerySoupEndKeyNoBeginKey()  {
        runTest("testQuerySoupEndKeyNoBeginKey");
    }

    public void testQuerySoupBeginKeyNoEndKey()  {
        runTest("testQuerySoupBeginKeyNoEndKey");
    }

    public void testManipulateCursor()  {
        runTest("testManipulateCursor");
    }

    public void testArbitrarySoupNames()  {
        runTest("testArbitrarySoupNames");
    }

    public void testQuerySpecFactories()  {
        runTest("testQuerySpecFactories");
    }

    public void testLikeQuerySpecStartsWith()  {
        runTest("testLikeQuerySpecStartsWith");
    }

    public void testLikeQuerySpecEndsWith()  {
        runTest("testLikeQuerySpecEndsWith");
    }

    public void testLikeQueryInnerText()  {
        runTest("testLikeQueryInnerText");
    }

    public void testCompoundQueryPath()  {
        runTest("testCompoundQueryPath");
    }

    public void testEmptyQuerySpec()  {
        runTest("testEmptyQuerySpec");
    }

    public void testIntegerQuerySpec()  {
        runTest("testIntegerQuerySpec");
    }
    
    public void testNumerousFields()  {
    	if (stressTestsActive)
    		runTest("SmartStoreLoadTestSuite","testNumerousFields");
    }
    
    public void testIncreasingFieldLength() {
    	if (stressTestsActive)
    		runTest("SmartStoreLoadTestSuite","testIncreasingFieldLength");
    }

    public void testAddAndRetrieveManyEntries()  {
    	if (stressTestsActive)
    		runTest("SmartStoreLoadTestSuite","testAddAndRetrieveManyEntries");
    }

    
    private void runTest(String suiteClassName, String testName)  {
    	String jsCmd = "navigator.testrunner.setTestSuite('" + suiteClassName + "');" +
    			"navigator.testrunner.testSuite.startTest('" + testName + "');";
    	activity.sendJavascript(jsCmd);

		// Block until test completes
		TestResult result = null;
		try {
			result = TestRunnerPlugin.testResults.take();
		}
		catch (InterruptedException intEx) {
			
		}
		assertNotNull("No test result",result);
		assertEquals("Wrong test completed", testName, result.testName);
		assertTrue(result.testName + " " + result.message, result.success);
    }
    
	/**
	 * Helper method: runs javascript test and wait for it to complete
	 * @param testName
	 * @
	 */
	private void runTest(String testName)  {
		this.runTest("SmartStoreTestSuite",testName);
	}
	
	
}
