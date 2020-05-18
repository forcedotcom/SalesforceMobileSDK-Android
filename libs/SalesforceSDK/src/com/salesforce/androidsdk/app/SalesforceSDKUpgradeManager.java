/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import android.content.Context;
import android.content.SharedPreferences;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.security.SalesforceKeyGenerator;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.util.List;

/**
 * This class handles upgrades from one version to another.
 *
 * @author bhariharan
 */
public class SalesforceSDKUpgradeManager {

    private static final String VERSION_SHARED_PREF = "version_info";
    private static final String ACC_MGR_KEY = "acc_mgr_version";
    private static final String TAG = "SalesforceSDKUpgradeManager";

    private static SalesforceSDKUpgradeManager INSTANCE = null;

    /**
     * Returns an instance of this class.
     *
     * @return Instance of this class.
     */
    public static synchronized SalesforceSDKUpgradeManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SalesforceSDKUpgradeManager();
        }
        return INSTANCE;
    }

    /**
     * Upgrade method.
     */
    public void upgrade() {
        upgradeAccMgr();
    }

    /**
     * Upgrades account manager data from existing client
     * version to the current version.
     */
    protected synchronized void upgradeAccMgr() {
        String installedVersion = getInstalledAccMgrVersion();
        if (installedVersion.equals(SalesforceSDKManager.SDK_VERSION)) {
            return;
        }

        // Update shared preference file to reflect the latest version.
        writeCurVersion(ACC_MGR_KEY, SalesforceSDKManager.SDK_VERSION);

        /*
         * If the installed version < v7.1.0, we need to store the current
         * user's user ID and org ID in a shared preference file, to
         * support fast user switching.
         */
        try {
            final String majorVersionNum = installedVersion.substring(0, 3);
            double installedVerDouble = Double.parseDouble(majorVersionNum);
            if (installedVerDouble < 7.1) {
                upgradeTo7Dot1();
            }
            if (installedVerDouble < 8.2) {
                upgradeTo8Dot2();
            }
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Failed to parse installed version.");
        }
    }

    /**
     * Writes the current version to the shared preference file.
     *
     * @param key Key to update.
     * @param value New version number.
     */
    protected synchronized void writeCurVersion(String key, String value) {
        final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(VERSION_SHARED_PREF, Context.MODE_PRIVATE);
        sp.edit().putString(key, value).commit();
    }

    /**
     * Returns the currently installed version of account manager.
     *
     * @return Currently installed version of account manager.
     */
    public String getInstalledAccMgrVersion() {
        return getInstalledVersion(ACC_MGR_KEY);
    }

    /**
     * Returns the currently installed version of the specified key.
     *
     * @return Currently installed version of the specified key.
     */
    protected String getInstalledVersion(String key) {
        final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(VERSION_SHARED_PREF,
                Context.MODE_PRIVATE);
        return sp.getString(key, "");
    }

    private void upgradeTo7Dot1() {
        SalesforceKeyGenerator.upgradeTo7Dot1();
    }

    private void upgradeTo8Dot2() {
        ClientManager.upgradeTo8Dot2();
        migrateAnalyticsData();
    }

    private void migrateAnalyticsData() {
        final List<UserAccount> userAccounts = UserAccountManager.getInstance().getAuthenticatedUsers();

        // Migrating an unauthenticated user's analytics data.
        SalesforceAnalyticsManager.upgradeTo8Dot2(null, SalesforceSDKManager.getInstance().getAppContext());

        // Migrating each individual user's analytics data.
        if (userAccounts != null) {
            for (final UserAccount userAccount : userAccounts) {
                if (userAccount != null) {
                    SalesforceAnalyticsManager.upgradeTo8Dot2(userAccount,
                            SalesforceSDKManager.getInstance().getAppContext());
                }
            }
        }
    }
}
