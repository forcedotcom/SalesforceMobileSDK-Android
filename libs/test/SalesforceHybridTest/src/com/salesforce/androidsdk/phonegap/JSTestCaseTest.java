/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the
 * following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 * the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 * promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.phonegap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.phonegap.plugin.TestRunnerPlugin;
import com.salesforce.androidsdk.phonegap.plugin.TestRunnerPlugin.TestResult;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class JSTestCaseTest {

    @After
    public void tearDown() {
        TestRunnerPlugin.testResults.clear();
    }

    @Test
    public void testResultQueueAcceptsLateAndCurrentResults() {
        TestResult lateResult = new TestResult("timedOutTest", true, "", 31);
        TestResult currentResult = new TestResult("currentTest", true, "", 1);

        assertTrue(TestRunnerPlugin.testResults.offer(lateResult));
        assertTrue(TestRunnerPlugin.testResults.offer(currentResult));
    }

    @Test
    public void pollForTestResultIgnoresLateResultFromTimedOutTest() throws InterruptedException {
        LinkedBlockingQueue<TestResult> results = new LinkedBlockingQueue<>();
        results.add(new TestResult("timedOutTest", true, "", 31));
        results.add(new TestResult("currentTest", true, "", 1));

        TestResult result = JSTestCase.pollForTestResult(
                results,
                "currentTest",
                1,
                TimeUnit.SECONDS
        );

        assertEquals("currentTest", result.testName);
    }
}
