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
package com.salesforce.androidsdk.smartstore.app;

import com.salesforce.androidsdk.app.SalesforceSDKUpgradeManager;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger;

/**
 * This class handles upgrades from one version to another.
 *
 * @author bhariharan
 */
public class SmartStoreUpgradeManager extends SalesforceSDKUpgradeManager {

    /**
     * Key in shared preference file for smart store version.
     */
    private static final String SMART_STORE_KEY = "smart_store_version";
    private static final String TAG = "SmartStoreUpgradeManager";

    private static SmartStoreUpgradeManager INSTANCE = null;

    /**
     * Returns an instance of this class.
     *
     * @return Instance of this class.
     */
    public static synchronized SmartStoreUpgradeManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SmartStoreUpgradeManager();
        }
        return INSTANCE;
    }

    @Override
    public void upgrade() {
        super.upgrade();
        upgradeSmartStore();
    }

    /**
     * Upgrades smartstore data from existing client
     * version to the current version.
     */
    protected synchronized void upgradeSmartStore() {
        final String installedVersion = getInstalledSmartStoreVersion();
        if (installedVersion.equals(SmartStoreSDKManager.SDK_VERSION)) {
            return;
        }

        // Update shared preference file to reflect the latest version.
        writeCurVersion(SMART_STORE_KEY, SmartStoreSDKManager.SDK_VERSION);

        // If the installed version < v8.2.0, we need to migrate encryption keys.
        try {
            final String majorVersionNum = installedVersion.substring(0, 3);
            double installedVerDouble = Double.parseDouble(majorVersionNum);
            if (installedVerDouble < 8.2) {
                upgradeTo8Dot2();
            }
        } catch (Exception e) {
            SmartStoreLogger.e(TAG, "Failed to parse installed version.");
        }
    }

    /**
     * Returns the currently installed version of smartstore.
     *
     * @return Currently installed version of smartstore.
     */
    public String getInstalledSmartStoreVersion() {
        return getInstalledVersion(SMART_STORE_KEY);
    }

    private void upgradeTo8Dot2() {
        DBOpenHelper.upgradeTo8Dot2();
    }
}
