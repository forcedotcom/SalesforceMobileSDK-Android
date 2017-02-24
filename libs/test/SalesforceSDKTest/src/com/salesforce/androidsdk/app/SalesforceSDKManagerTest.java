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
package com.salesforce.androidsdk.app;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.salesforce.androidsdk.KeyImpl;
import com.salesforce.androidsdk.MainActivity;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager;
import com.salesforce.androidsdk.analytics.model.DeviceAppAttributes;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManagerTest;

import static android.content.ContentValues.TAG;

/**
 * A class that contains tests for functionality exposed in SalesforceSDKManager.
 *
 * @author bhariharan
 */
public class SalesforceSDKManagerTest extends InstrumentationTestCase {

    private static final String TEST_APP_NAME = "OverridenAppName";

    /**
     * Test for setting the analytics app name before 'SalesforceSDKManager.init()'
     * has been called.
     */
    public void testOverrideAiltnAppNameBeforeSDKManagerInit() {
        SalesforceSDKManager.setAiltnAppName(TEST_APP_NAME);
        SalesforceSDKManager.initNative(getInstrumentation().getTargetContext(),
                new KeyImpl(), MainActivity.class);
        SalesforceSDKManager.getInstance().setIsTestRun(true);
        compareAiltnAppNames(TEST_APP_NAME);
    }

    /**
     * Test for setting the analytics app name after 'SalesforceSDKManager.init()'
     * has been called.
     */
    public void testOverrideAiltnAppNameAfterSDKManagerInit() {
        SalesforceSDKManager.initNative(getInstrumentation().getTargetContext(),
                new KeyImpl(), MainActivity.class);
        SalesforceSDKManager.setAiltnAppName(TEST_APP_NAME);
        SalesforceSDKManager.getInstance().setIsTestRun(true);
        compareAiltnAppNames(TEST_APP_NAME);
    }

    /**
     * Test for default analytics app name.
     */
    public void testDefaultAiltnAppName() {
        SalesforceSDKManager.initNative(getInstrumentation().getTargetContext(),
                new KeyImpl(), MainActivity.class);
        SalesforceSDKManager.getInstance().setIsTestRun(true);
        compareAiltnAppNames(getDefaultAppName());
    }

    /**
     * Test for setting an invalid analytics app name.
     */
    public void testOverrideInvalidAiltnAppName() {
        SalesforceSDKManager.initNative(getInstrumentation().getTargetContext(),
                new KeyImpl(), MainActivity.class);
        SalesforceSDKManager.setAiltnAppName(null);
        SalesforceSDKManager.getInstance().setIsTestRun(true);
        compareAiltnAppNames(getDefaultAppName());
    }

    private String compareAiltnAppNames(String expectedAppName) {
        final UserAccountManager userAccMgr = SalesforceSDKManager.getInstance().getUserAccountManager();
        final ClientManager.LoginOptions loginOptions = new ClientManager.LoginOptions(ClientManagerTest.TEST_LOGIN_URL,
                ClientManagerTest.TEST_PASSCODE_HASH, ClientManagerTest.TEST_CALLBACK_URL,
                ClientManagerTest.TEST_CLIENT_ID, ClientManagerTest.TEST_SCOPES);
        final ClientManager clientManager = new ClientManager(getInstrumentation().getTargetContext(),
                ClientManagerTest.TEST_ACCOUNT_TYPE, loginOptions, true);
        final AccountManager accMgr = clientManager.getAccountManager();
        final SalesforceAnalyticsManager analyticsManager = SalesforceAnalyticsManager.getInstance(userAccMgr.getCurrentUser());
        final DeviceAppAttributes deviceAppAttributes = analyticsManager.getAnalyticsManager().getDeviceAppAttributes();
        final String ailtnAppName = deviceAppAttributes.getAppName();
        assertEquals("DeviceAppAttributes - App names do not match", expectedAppName, ailtnAppName);
        assertEquals("SalesforceSDKManager - App names do not match", expectedAppName, SalesforceSDKManager.getAiltnAppName());
        clientManager.removeAccounts(accMgr.getAccountsByType(ClientManagerTest.TEST_ACCOUNT_TYPE));
    }

    private String getDefaultAppName() {
        String ailtnAppName = null;
        try {
            final Context targetContext = getInstrumentation().getTargetContext();
            final PackageInfo packageInfo = targetContext.getPackageManager().getPackageInfo(targetContext.getPackageName(), 0);
            ailtnAppName = targetContext.getString(packageInfo.applicationInfo.labelRes);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Package not found", e);
        }
        return ailtnAppName;
    }
}
