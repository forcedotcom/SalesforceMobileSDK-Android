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

import android.content.Intent;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import com.salesforce.androidsdk.reactnative.bridge.SalesforceTestBridge;
import com.salesforce.androidsdk.reactnative.util.SalesforceReactTestActivity;

import junit.framework.Assert;

import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public abstract class ReactTestCase {

    private static final long TEST_TIMEOUT_SECONDS = 5;
    public static final String TEST_SUITE = "testSuite";
    public static final String TEST_NAME = "testName";

    @Rule
    public ActivityTestRule<SalesforceReactTestActivity> mActivityRule = new ActivityTestRule<SalesforceReactTestActivity>(
            SalesforceReactTestActivity.class, false, false) {
    };

    protected void runReactNativeTest(String testSuite, String testName) throws InterruptedException {
        Intent intent = new Intent();
        intent.putExtra(TEST_SUITE, testSuite);
        intent.putExtra(TEST_NAME,testName);
        mActivityRule.launchActivity(intent);

        Assert.assertTrue(testName + " failed", true == SalesforceTestBridge.testCompleted.poll(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }
}