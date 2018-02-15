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

package com.salesforce.androidsdk.reactnative.util;

import android.app.Activity;
import android.os.Bundle;

import com.facebook.react.ReactActivityDelegate;

import javax.annotation.Nullable;

import static com.salesforce.androidsdk.reactnative.ReactTestCase.TEST_NAME;

/**
 * Subclass of ReactActivityDelegate used for testing
 *
 * It loads the registered app for the test (if the test is called testxxx, we expected a registered app called xxx)
 * Also it destroys the instance manager when destroyed so that a fresh one gets build for the next test
 */
public class ReactActivityTestDelegate extends ReactActivityDelegate {

    public static final String TEST_PREFIX = "test";

    private Activity activity;
    private String testName;

    public ReactActivityTestDelegate(Activity activity, @Nullable String mainComponentName) {
        super(activity, "xxx" /* the value to use will be computed in loadApp - but we need a non-null value otherwise loadApp won't be called */);
        this.activity = activity;
    }

    @Override
    protected void loadApp(String appKey) {
        Bundle extras = activity.getIntent().getExtras();
        testName = extras.getString(TEST_NAME);
        super.loadApp(testName.substring(TEST_PREFIX.length()));
    }

    @Override
    protected void onDestroy() {
        getReactNativeHost().getReactInstanceManager().destroy();
        super.onDestroy();
    }
}
