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

import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import com.salesforce.androidsdk.reactnative.util.TestCredentials;
import com.salesforce.androidsdk.reactnative.bridge.SalesforceTestBridge;
import com.salesforce.androidsdk.reactnative.util.TestActivity;
import com.salesforce.androidsdk.rest.ClientManager;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.salesforce.androidsdk.rest.ClientManagerTest.TEST_CALLBACK_URL;
import static com.salesforce.androidsdk.rest.ClientManagerTest.TEST_SCOPES;

@LargeTest
@RunWith(AndroidJUnit4.class)
public abstract class ReactTestCase {

    private static final long TEST_TIMEOUT_SECONDS = 5;

    @Rule
    public ActivityTestRule<TestActivity> mActivityRule = new ActivityTestRule<TestActivity>(
            TestActivity.class, false, false) {
    };


    @Before
    public void setUp() {
        Context targetContext = InstrumentationRegistry.getContext();
        final ClientManager.LoginOptions loginOptions = new ClientManager.LoginOptions(TestCredentials.LOGIN_URL,
                TEST_CALLBACK_URL, TestCredentials.CLIENT_ID, TEST_SCOPES);
        final ClientManager clientManager = new ClientManager(targetContext,
                TestCredentials.ACCOUNT_TYPE, loginOptions, true);
        clientManager.createNewAccount(TestCredentials.ACCOUNT_NAME,
                TestCredentials.USERNAME, TestCredentials.REFRESH_TOKEN,
                "", TestCredentials.INSTANCE_URL,
                TestCredentials.LOGIN_URL, TestCredentials.IDENTITY_URL,
                TestCredentials.CLIENT_ID, TestCredentials.ORG_ID,
                TestCredentials.USER_ID, null, null, null,
                null, null, null, null, null, null);
    }

    protected void runReactNativeTest(String testSuite, String testName) throws InterruptedException {
        Intent intent = new Intent();
        intent.putExtra("testSuite", testSuite);
        intent.putExtra("testName",testName);
        mActivityRule.launchActivity(intent);

        Assert.assertTrue(testName + " failed", SalesforceTestBridge.testCompleted.poll(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }
}