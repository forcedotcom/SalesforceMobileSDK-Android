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
package com.salesforce.androidsdk.util;

import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import com.salesforce.androidsdk.phonegap.TestRunnerPlugin;
import com.salesforce.androidsdk.phonegap.TestRunnerPlugin.TestResult;
import com.salesforce.androidsdk.ui.SalesforceDroidGapActivity;

/**
 * Extend this class to run tests written in JavaScript
 */
public class JSTestCase extends
		ActivityInstrumentationTestCase2<SalesforceDroidGapActivity> {

	private SalesforceDroidGapActivity activity;
    private String jsSuite;
	
	public JSTestCase(String jsSuite) {
		super("com.salesforce.androidsdk.tests", SalesforceDroidGapActivity.class);
        this.jsSuite = jsSuite;
	}
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		activity = getActivity();
		// Block until the javascript has notified the container that it's ready
		TestRunnerPlugin.readyForTests.take(); 
	}

	/**
	 * Helper method: runs javascript test and wait for it to complete
	 * @param testName the name of the test method in the test suite
	 * @
	 */
    protected void runTest(String testName)  {
        // Debug.startMethodTracing(new File(getActivity().getFilesDir(), getClass().getSimpleName() + "_" + testName + ".trace").getAbsolutePath());
    		
        String jsCmd = "navigator.testrunner.setTestSuite('" + jsSuite + "');" +
            "navigator.testrunner.startTest('" + testName + "');";
        activity.sendJavascript(jsCmd);
        Log.i(getClass().getSimpleName(), "running test:" + testName);
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
			
        // Debug.stopMethodTracing();
    }
}
