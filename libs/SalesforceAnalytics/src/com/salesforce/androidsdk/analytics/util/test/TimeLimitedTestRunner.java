/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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

package com.salesforce.androidsdk.analytics.util.test;

import android.test.AndroidTestRunner;

import com.salesforce.androidsdk.analytics.util.SalesforceAnalyticsLogger;

import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A TestRunner that limits the lifetime of the test run.
 *
 * @deprecated Will be removed in Mobile SDK 7.0. Use the standard Android test runner instead.
 */
@Deprecated
public class TimeLimitedTestRunner extends AndroidTestRunner {

    private static final String TAG = "TimeLimitedTestRunner";

    public TimeLimitedTestRunner(int maxRuntime, TimeUnit maxUnits) {
        this.maxRuntime = maxRuntime;
        this.maxRuntimeUnit = maxUnits;
    }

    private final long maxRuntime;
    private final TimeUnit maxRuntimeUnit;

    @Override
    public void runTest() {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<Boolean> f = exec.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                TimeLimitedTestRunner.super.runTest();
                return true;
            }
        });
        try {
            f.get(maxRuntime, maxRuntimeUnit);
        } catch (TimeoutException ex) {
            SalesforceAnalyticsLogger.e(null, TAG, String.format(Locale.US, "TestRunner has timed out after: %d %s.",
                    maxRuntime, maxRuntimeUnit.name()), ex);
        } catch (Exception ex){
            SalesforceAnalyticsLogger.e(null, TAG, "TestRunner did not complete successfully, check the exception logged above", ex);
        }
    }
}
