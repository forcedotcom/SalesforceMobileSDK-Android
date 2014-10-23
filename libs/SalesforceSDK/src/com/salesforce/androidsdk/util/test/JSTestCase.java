/*
 * Copyright (c) 2012, salesforce.com, inc.
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
package com.salesforce.androidsdk.util.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import android.app.Instrumentation;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.phonegap.TestRunnerPlugin;
import com.salesforce.androidsdk.phonegap.TestRunnerPlugin.TestResult;
import com.salesforce.androidsdk.ui.sfhybrid.SalesforceDroidGapActivity;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Extend this class to run tests written in JavaScript
 */
public abstract class JSTestCase extends InstrumentationTestCase {

    private String jsSuite;
    private static Map<String, Map<String, TestResult>> testResults; 
    
    
    public JSTestCase(String jsSuite) {
    	this.jsSuite = jsSuite;
    }
    
    @Override
    public void setUp() throws Exception {
    	
    	if (testResults == null || !testResults.containsKey(jsSuite)) {
    		if (testResults == null) {
    			testResults = new HashMap<String, Map<String, TestResult>>();
    		}
    		
    		if (!testResults.containsKey(jsSuite)) {
    			testResults.put(jsSuite, new HashMap<String, TestResult>());
    		}

	        // Wait for app initialization to complete
			EventsListenerQueue eq = new EventsListenerQueue();
	        if (SalesforceSDKManager.getInstance() == null) {
	            eq.waitForEvent(EventType.AppCreateComplete, 5000);
	        }			

			// Start main activity
    		Instrumentation instrumentation = getInstrumentation();
			final Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.setClassName(instrumentation.getTargetContext(), SalesforceSDKManager.getInstance().getMainActivityClass().getName());
			SalesforceDroidGapActivity activity = (SalesforceDroidGapActivity) instrumentation.startActivitySync(intent);
	
			// Block until the javascript has notified the container that it's ready
			TestRunnerPlugin.readyForTests.take();

			// Now run all the tests and collect the resuts in testResults
			for (String testName : getTestNames()) {
		        String jsCmd = "navigator.testrunner.setTestSuite('" + jsSuite + "');" +
		            "navigator.testrunner.startTest('" + testName + "');";
		        activity.sendJavascript(jsCmd);
		        Log.i(getClass().getSimpleName(), "running test:" + testName);
		        
		        // Block until test completes or times out
		        TestResult result = null;
	            int timeout = getMaxRuntimeInSecondsForTest(testName);
		        try {
					result = TestRunnerPlugin.testResults.poll(timeout, TimeUnit.SECONDS);
		            if (result == null) {
		            	result = new TestResult(testName, false, "Timeout (" + timeout + " seconds) exceeded", timeout);
		            }
		        }
		        catch (Exception e) {
	            	result = new TestResult(testName, false, "Test failed", timeout);
		        }
		        Log.i(getClass().getSimpleName(), "done running test:" + testName);
		        
		        // Save result
		        testResults.get(jsSuite).put(testName, result);
			}
			
			// Cleanup
			eq.tearDown();
			activity.finish();
    	}
    }

    /**
     * @return all the javascript test names in the suite
     */
    protected abstract List<String> getTestNames();
    
    /**
     * @param testName
     * @return maximum time the test should be allowed to run in seconds
     */
    protected int getMaxRuntimeInSecondsForTest(String testName) {
    	return 5;
    }
    
	/**
	 * Helper method: no longer actually run the javascript test, instead asserts based on saved results
	 * @param testName the name of the test method in the test suite
	 * @
	 */
    protected void runTest(String testName)  {
    	TestResult result = testResults.get(jsSuite).get(testName);
        assertNotNull("No test result", result);
        assertTrue(result.testName + " " + result.message, result.success);
    }
}
