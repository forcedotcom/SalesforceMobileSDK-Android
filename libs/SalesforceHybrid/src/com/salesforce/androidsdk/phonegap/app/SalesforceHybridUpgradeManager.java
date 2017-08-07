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
package com.salesforce.androidsdk.phonegap.app;

import com.salesforce.androidsdk.app.UUIDManager;
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger;
import com.salesforce.androidsdk.smartsync.app.SmartSyncUpgradeManager;

/**
 * This class handles upgrades from one version to another.
 *
 * @author bhariharan
 */
public class SalesforceHybridUpgradeManager extends SmartSyncUpgradeManager {

    private static final String TAG = "SalesforceHybridUpgradeManager";

    private static SalesforceHybridUpgradeManager INSTANCE = null;

    /**
     * Returns an instance of this class.
     *
     * @return Instance of this class.
     */
    public static synchronized SalesforceHybridUpgradeManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SalesforceHybridUpgradeManager();
        }
        return INSTANCE;
    }

    @Override
    public void upgrade() {
        super.upgrade();
    }

    @Override
    protected synchronized void upgradeAccMgr() {
        super.upgradeAccMgr();
        final String installedVersion = getInstalledAccMgrVersion();
        try {
            final String majorVersionNum = installedVersion.substring(0, 3);
            double installedVerDouble = Double.parseDouble(majorVersionNum);

            /*
             * If the installed version < v6.0.0, we need to perform a migration step
             * from the old encryption key to the new encryption key for hybrid apps.
             */
            if (installedVerDouble < 6.0) {
                upgradeTo6Dot0();
            }
        } catch (NumberFormatException e) {
            SalesforceHybridLogger.e(TAG, "Failed to parse installed version", e);
        }
    }

    /**
     * Upgrade steps for older versions of the Mobile SDK to Mobile SDK 6.0.
     */
    protected void upgradeTo6Dot0() {
        UUIDManager.upgradeTo6Dot0();
    }
}
