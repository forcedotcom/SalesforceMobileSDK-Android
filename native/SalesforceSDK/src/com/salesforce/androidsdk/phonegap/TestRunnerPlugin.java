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
package com.salesforce.androidsdk.phonegap;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/**
 * PhoneGap plugin to run javascript tests.
 */
public class TestRunnerPlugin extends ForcePlugin {

	private static final String TAG = "TestRunnerPlugin";
	
	// Keys in json from/to javascript
	private static final String TEST_NAME = "testName";
	private static final String SUCCESS = "success";
	private static final String MESSAGE = "message";
	private static final String DURATION = "testDuration";
	
	// To synchronize with the tests
	public final static BlockingQueue<Boolean> readyForTests = new ArrayBlockingQueue<Boolean>(1);
	public final static BlockingQueue<TestResult> testResults = new ArrayBlockingQueue<TestResult>(1);
	
	/**
	 * Supported plugin actions that the client can take.
	 */
	enum Action {
		onReadyForTests,
		onTestComplete
	};

    @Override
    public boolean execute(String actionStr, JavaScriptPluginVersion jsVersion, JSONArray args, CallbackContext callbackContext) throws JSONException {
    	// Figure out action
    	Action action = null;
    	try {
    		action = Action.valueOf(actionStr);
			switch(action) {
				case onReadyForTests:            onReadyForTests(args, callbackContext); return true;
				case onTestComplete:             onTestComplete(args, callbackContext); return true;
				default: return false;
	    	}
    	}
    	catch (IllegalArgumentException e) {
    		return false;
    	}
    	catch (InterruptedException e) {
    		callbackContext.error(e.getMessage()); 
    		return true; 
    	}
    }

	/**
	 * Native implementation of onTestComplete
	 * @param args
	 * @param callbackContext
	 * @throws JSONException
	 * @throws InterruptedException 
	 */
	private void onTestComplete(JSONArray args,  CallbackContext callbackContext) throws JSONException, InterruptedException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String testName = arg0.getString(TEST_NAME);
		boolean success = arg0.getBoolean(SUCCESS);
		String message = stripHtml(arg0.getString(MESSAGE));
        int durationMsec =  arg0.getInt(DURATION);
        double duration = durationMsec / 1000.0;
        
		TestResult testResult = new TestResult(testName, success, message, duration);
		testResults.put(testResult);

		Log.w(TAG,testResult.testName + " completed in " + testResult.duration);
	
        
		callbackContext.success();
	}

	private String stripHtml(String message) {
		return message.replaceAll("<[^>]+>", "|").replaceAll("[|]+"," ");
	}
	
	/**
	 * Native implementation of onReadyForTests
	 * @param args
	 * @param callbackContext
	 */
	private void onReadyForTests(JSONArray args,  CallbackContext callbackContext)  {
		readyForTests.add(Boolean.TRUE);
		callbackContext.success();
	}
	
	public static class TestResult {
		public final String testName;
		public final boolean success;
		public final String message;
		public final double duration; //time in seconds
		
		public TestResult(String testName, boolean success, String message, double duration) {
			this.testName = testName;
			this.success = success;
			this.message = message;
			this.duration = duration;
		}
		
	}
}
