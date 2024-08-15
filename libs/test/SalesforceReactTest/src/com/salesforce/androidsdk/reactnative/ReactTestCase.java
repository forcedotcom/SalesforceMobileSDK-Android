/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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

package com.salesforce.androidsdk.reactnative;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import com.salesforce.androidsdk.reactnative.util.ReactTestActivity;
import com.salesforce.androidsdk.reactnative.util.TestResult;

import org.junit.Assert;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public abstract class ReactTestCase {

    private static final long TEST_TIMEOUT_SECONDS = 120;
    public static final String TEST_NAME = "testName";

    // Dismissing system dialog if shown
    // See https://stackoverflow.com/questions/39457305/android-testing-waited-for-the-root-of-the-view-hierarchy-to-have-window-focus
    private void dismissSystemDialog() {
        UiDevice device = UiDevice.getInstance(getInstrumentation());
        UiObject okButton = device.findObject(new UiSelector().textContains("OK"));
        try {
            okButton.click();
        } catch (UiObjectNotFoundException e) {
            // Nothing to do
        }
    }

    protected void runReactNativeTest(String testName) throws InterruptedException {
        TestResult result = getTestResult(testName);
        if (result == null) {
            Assert.fail(testName + " timed out");
        }
        else {
            Assert.assertTrue(result.message, result.status);
        }
    }

    private TestResult getTestResult(String testName) throws InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, ReactTestActivity.class);
        intent.putExtra(TEST_NAME, testName);
        try (ActivityScenario<ReactTestActivity> ignored = ActivityScenario.launch(intent)) {
            dismissSystemDialog();
            return TestResult.waitForTestResult(getTestTimeoutSeconds());
        }
    }

    protected long getTestTimeoutSeconds() {
        return TEST_TIMEOUT_SECONDS;
    }
}