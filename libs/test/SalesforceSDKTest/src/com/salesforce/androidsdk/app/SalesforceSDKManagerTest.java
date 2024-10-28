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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.salesforce.androidsdk.MainActivity;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.accounts.UserAccountManagerTest;
import com.salesforce.androidsdk.accounts.UserAccountTest;
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager;
import com.salesforce.androidsdk.analytics.manager.AnalyticsManager;
import com.salesforce.androidsdk.analytics.model.DeviceAppAttributes;
import com.salesforce.androidsdk.ui.LoginActivity;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A class that contains tests for functionality exposed in SalesforceSDKManager.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SalesforceSDKManagerTest {

    private static final String TAG = "SFSDKManagerTest";
    private static final String TEST_APP_NAME = "OverridenAppName";

    /**
     * Test for setting the analytics app name before 'SalesforceSDKManager.init()'
     * has been called.
     */
    @Test
    public void testOverrideAiltnAppNameBeforeSDKManagerInit() {
        SalesforceSDKTestManager.setAiltnAppName(TEST_APP_NAME);
        SalesforceSDKTestManager.init(getInstrumentation().getTargetContext(), MainActivity.class);
        SalesforceSDKTestManager.getInstance().setIsTestRun(true);
        compareAiltnAppNames(TEST_APP_NAME);
    }

    /**
     * Test for setting the analytics app name after 'SalesforceSDKManager.init()'
     * has been called.
     */
    @Test
    public void testOverrideAiltnAppNameAfterSDKManagerInit() {
        SalesforceSDKTestManager.init(getInstrumentation().getTargetContext(), MainActivity.class);
        SalesforceSDKTestManager.setAiltnAppName(TEST_APP_NAME);
        SalesforceSDKTestManager.getInstance().setIsTestRun(true);
        compareAiltnAppNames(TEST_APP_NAME);
    }

    /**
     * Test for default analytics app name.
     */
    @Test
    public void testDefaultAiltnAppName() {
        SalesforceSDKTestManager.init(getInstrumentation().getTargetContext(), MainActivity.class);
        SalesforceSDKTestManager.getInstance().setIsTestRun(true);
        compareAiltnAppNames(getDefaultAppName());
    }

    /**
     * Test for setting an invalid analytics app name.
     */
    @Test
    public void testOverrideInvalidAiltnAppName() {
        SalesforceSDKTestManager.init(getInstrumentation().getTargetContext(), MainActivity.class);
        SalesforceSDKTestManager.setAiltnAppName(null);
        SalesforceSDKTestManager.getInstance().setIsTestRun(true);
        compareAiltnAppNames(getDefaultAppName());
    }

    /**
     * Test the default theme value.
     */
    @Test
    public void testDefaultTheme() {
        int currentNightMode = getInstrumentation().getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        Boolean isDarkTheme = currentNightMode == Configuration.UI_MODE_NIGHT_YES;

        SalesforceSDKTestManager.init(getInstrumentation().getTargetContext(), MainActivity.class);
        Assert.assertEquals("Default theme does not match OS value.", isDarkTheme,
                SalesforceSDKTestManager.getInstance().isDarkTheme());
    }

    /**
     * Test setting dark theme.
     */
    @Test
    public void testSetDarkTheme() {
        SalesforceSDKTestManager.init(getInstrumentation().getTargetContext(), MainActivity.class);
        SalesforceSDKTestManager.getInstance().setTheme(SalesforceSDKManager.Theme.DARK);
        Assert.assertTrue("Dark theme not successfully set.",
                SalesforceSDKTestManager.getInstance().isDarkTheme());
    }

    /**
     * Test changing theme multiple times.
     */
    @Test
    public void testChangingTheme() {
        SalesforceSDKTestManager.init(getInstrumentation().getTargetContext(), MainActivity.class);
        SalesforceSDKTestManager.getInstance().setTheme(SalesforceSDKManager.Theme.DARK);
        SalesforceSDKTestManager.getInstance().setTheme(SalesforceSDKManager.Theme.LIGHT);
        Assert.assertFalse("Latest theme value not returned.",
                SalesforceSDKTestManager.getInstance().isDarkTheme());
    }

    private void compareAiltnAppNames(String expectedAppName) {
        final UserAccountManager userAccMgr = SalesforceSDKTestManager.getInstance().getUserAccountManager();
        final Context targetContext = getInstrumentation().getTargetContext();
        UserAccountManager.getInstance().createAccount(UserAccountTest.createTestAccount());
        final AccountManager accMgr = AccountManager.get(targetContext);
        final UserAccount curUser = userAccMgr.getCurrentUser();
        Assert.assertNotNull("Current user should NOT be null", curUser);
        SalesforceAnalyticsManager.reset(curUser);
        final SalesforceAnalyticsManager analyticsManager = SalesforceAnalyticsManager.getInstance(userAccMgr.getCurrentUser());
        Assert.assertNotNull("SalesforceAnalyticsManager instance should NOT be null", analyticsManager);
        final AnalyticsManager manager = analyticsManager.getAnalyticsManager();
        Assert.assertNotNull("AnalyticsManager instance should NOT be null", manager);
        final DeviceAppAttributes deviceAppAttributes = analyticsManager.getAnalyticsManager().getDeviceAppAttributes();
        Assert.assertNotNull("Device attributes should NOT be null", deviceAppAttributes);
        final String ailtnAppName = deviceAppAttributes.getAppName();
        Assert.assertEquals("DeviceAppAttributes - App names do NOT match", expectedAppName, ailtnAppName);
        Assert.assertEquals("SalesforceSDKManager - App names do NOT match", expectedAppName, SalesforceSDKTestManager.getAiltnAppName());
        SalesforceAnalyticsManager.reset(curUser);
        for (Account acc: accMgr.getAccountsByType(UserAccountManagerTest.TEST_ACCOUNT_TYPE)) {
            accMgr.removeAccountExplicitly(acc);
        }
        SalesforceSDKTestManager.resetAiltnAppName();
        SalesforceSDKTestManager.resetInstance();
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

    /**
     * Mock version of SalesforceSDKManager.
     */
    private static class SalesforceSDKTestManager extends SalesforceSDKManager {

        // We don't want to be using INSTANCE defined in SalesforceSDKManager
        // Otherwise tests in other suites could fail after we call resetInstance(...)
        private static SalesforceSDKTestManager TEST_INSTANCE = null;

        /**
         * Protected constructor.
         *
         * @param context Application context.
         * @param mainActivity Activity to be launched after the login flow.
         * @param loginActivity Login activity.
         */
        protected SalesforceSDKTestManager(Context context, Class<? extends Activity> mainActivity,
                                           Class<? extends Activity> loginActivity) {
            super(context, mainActivity, loginActivity);
        }

        /**
         * Initializes this component.
         *
         * @param context Application context.
         * @param mainActivity Activity to be launched after the login flow.
         */
        public static void init(Context context, Class<? extends Activity> mainActivity) {
            if (TEST_INSTANCE == null) {
                TEST_INSTANCE = new SalesforceSDKTestManager(context, mainActivity, LoginActivity.class);
            }
            initInternal(context);
        }

        /**
         * Returns a singleton instance of this class.
         *
         * @return Singleton instance of SalesforceSDKManager.
         */
        public static SalesforceSDKManager getInstance() {
            if (TEST_INSTANCE != null) {
                return TEST_INSTANCE;
            } else {
                throw new RuntimeException("Applications need to call SalesforceSDKManager.init() first.");
            }
        }


        /**
         * Resets the app name to be used by the analytics framework.
         * This is meant to be used ONLY by tests.
         */
        public static void resetAiltnAppName() {
            AILTN_APP_NAME = null;
        }

        /**
         * Resets the current instance being used.
         * This is meant to be used ONLY by tests.
         */
        public static void resetInstance() {
            TEST_INSTANCE = null;
        }
    }
}
