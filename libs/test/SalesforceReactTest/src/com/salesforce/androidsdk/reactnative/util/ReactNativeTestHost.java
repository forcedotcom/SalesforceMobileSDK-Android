/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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

import android.app.Application;

import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactInstanceManagerBuilder;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModuleCallExceptionHandler;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.shell.MainReactPackage;
import com.salesforce.androidsdk.reactnative.app.SalesforceReactSDKManager;

import java.util.Arrays;
import java.util.List;

/**
 * Subclass of ReactNativeHost used for testing
 *
 * In addition to the SalesforceReact react packages, it loads SalesforceReactTestPackage (which brings SalesforceTestBridge)
 * It creates an ReactInstanceManager which handles error through NativeModuleCallExceptionTestHandler
 * That way the current test running is marked as failed if any javascript error takes place
 *
 */
public class ReactNativeTestHost extends ReactNativeHost {

    private final Application mApplication;

    protected ReactNativeTestHost(Application application) {
        super(application);
        mApplication = application;
    }


    @Override
    public boolean getUseDeveloperSupport() {
        return false;
    }

    @Override
    protected List<ReactPackage> getPackages() {
        return Arrays.<ReactPackage>asList(
                new MainReactPackage(),
                SalesforceReactSDKManager.getInstance().getReactPackage(),
                new SalesforceReactTestPackage()
        );
    }

    @Override
    protected ReactInstanceManager createReactInstanceManager() {
        ReactInstanceManagerBuilder builder = ReactInstanceManager.builder()
                .setApplication(mApplication)
                .setJavaScriptExecutorFactory(getJavaScriptExecutorFactory())
                .setUIImplementationProvider(getUIImplementationProvider())
                .setInitialLifecycleState(LifecycleState.BEFORE_CREATE)
                // Always reading from bundle
                // NB: Bundle is generated during build
                .setBundleAssetName("index.android.bundle")
                .setUseDeveloperSupport(false)
                .setNativeModuleCallExceptionHandler(new NativeModuleCallExceptionTestHandler());

        for (ReactPackage reactPackage : getPackages()) {
            builder.addPackage(reactPackage);
        }

        return builder.build();
    }


    /**
     * Implementation of NativeModuleCallExceptionHandler used for testing (when using bundle)
     *
     * Marks the current test running is marked as failed if any javascript error takes place
     */
    static class NativeModuleCallExceptionTestHandler implements NativeModuleCallExceptionHandler {

        @Override
        public void handleException(Exception e) {
            TestResult.recordTestResult(TestResult.failure(e.getMessage()));
        }
    }
}
