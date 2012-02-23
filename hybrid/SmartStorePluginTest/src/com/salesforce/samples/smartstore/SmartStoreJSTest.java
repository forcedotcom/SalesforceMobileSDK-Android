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

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import com.salesforce.androidsdk.phonegap.TestRunnerPlugin;
import com.salesforce.androidsdk.phonegap.TestRunnerPlugin.TestResult;
import com.salesforce.androidsdk.ui.SalesforceDroidGapActivity;

/**
 * Running javascript tests for SmartStore plugin
 */
public class SmartStoreJSTest extends
		ActivityInstrumentationTestCase2<SalesforceDroidGapActivity> {

	private SalesforceDroidGapActivity activity;
	
	private Set<String> suitesToRun;

	public SmartStoreJSTest() {
		super("com.salesforce.samples.smartstore", SalesforceDroidGapActivity.class);

		//TODO add test suites as desired for individual runs
//		String[] suiteNames = {"SmartStoreTestSuite", "SmartStoreLoadTestSuite" };
		String[] suiteNames = {"SmartStoreTestSuite"}; 
		suitesToRun = new TreeSet<String>(Arrays.asList(suiteNames));
	}
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		activity = getActivity();
		// Block until the javascript has notified the container that it's ready
		TestRunnerPlugin.readyForTests.take(); 
	}


    public void testRegisterRemoveSoup()  {
        runTest("SmartStoreTestSuite","testRegisterRemoveSoup");
    }

    public void testRegisterBogusSoup()  {
        runTest("SmartStoreTestSuite","testRegisterBogusSoup");
    }

    public void testRegisterSoupNoIndices()  {
        runTest("SmartStoreTestSuite","testRegisterSoupNoIndices");
    }

    public void testUpsertSoupEntries()  {
        runTest("SmartStoreTestSuite","testUpsertSoupEntries");
    }

    public void testUpsertToNonexistentSoup()  {
        runTest("SmartStoreTestSuite","testUpsertToNonexistentSoup");
    }

    public void testRetrieveSoupEntries()  {
        runTest("SmartStoreTestSuite","testRetrieveSoupEntries");
    }

    public void testRemoveFromSoup()  {
        runTest("SmartStoreTestSuite","testRemoveFromSoup");
    }

    public void testQuerySoup()  {
        runTest("SmartStoreTestSuite","testQuerySoup");
    }

    public void testQuerySoupDescending()  {
        runTest("SmartStoreTestSuite","testQuerySoupDescending");
    }
    
    public void testQuerySoupBadQuerySpec()  {
        runTest("SmartStoreTestSuite","testQuerySoupBadQuerySpec");
    }

    public void testQuerySoupEndKeyNoBeginKey()  {
        runTest("SmartStoreTestSuite","testQuerySoupEndKeyNoBeginKey");
    }

    public void testQuerySoupBeginKeyNoEndKey()  {
        runTest("SmartStoreTestSuite","testQuerySoupBeginKeyNoEndKey");
    }

    public void testManipulateCursor()  {
        runTest("SmartStoreTestSuite","testManipulateCursor");
    }

    public void testArbitrarySoupNames()  {
        runTest("SmartStoreTestSuite","testArbitrarySoupNames");
    }

    public void testQuerySpecFactories()  {
        runTest("SmartStoreTestSuite","testQuerySpecFactories");
    }

    public void testLikeQuerySpecStartsWith()  {
        runTest("SmartStoreTestSuite","testLikeQuerySpecStartsWith");
    }

    public void testLikeQuerySpecEndsWith()  {
        runTest("SmartStoreTestSuite","testLikeQuerySpecEndsWith");
    }

    public void testLikeQueryInnerText()  {
        runTest("SmartStoreTestSuite","testLikeQueryInnerText");
    }

    public void testCompoundQueryPath()  {
        runTest("SmartStoreTestSuite","testCompoundQueryPath");
    }

    public void testEmptyQuerySpec()  {
        runTest("SmartStoreTestSuite","testEmptyQuerySpec");
    }

    public void testIntegerQuerySpec()  {
        runTest("SmartStoreTestSuite","testIntegerQuerySpec");
    }
    
    //============= Load Tests ================
    
    public void testNumerousFields()  {
    	runTest("SmartStoreLoadTestSuite","testNumerousFields");
    }
    
    public void testIncreasingFieldLength() {
    	runTest("SmartStoreLoadTestSuite","testIncreasingFieldLength");
    }

    public void testAddAndRetrieveManyEntries()  {
    	runTest("SmartStoreLoadTestSuite","testAddAndRetrieveManyEntries");
    }

	/**
	 * Helper method: runs javascript test and wait for it to complete
	 * @param suiteClassName  the name of the javascript test suite class
	 * @param testName the name of the test method in the test suite
	 * @
	 */
    private void runTest(String suiteClassName, String testName)  {
    	if (suitesToRun.contains(suiteClassName)) {
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
    	} else {
	    	Log.w("SmartStoreJSTest.runTest", "Skipping suite: " + suiteClassName);
    	}
    }
    

	
	
}
