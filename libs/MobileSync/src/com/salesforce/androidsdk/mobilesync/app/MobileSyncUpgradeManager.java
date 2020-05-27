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
package com.salesforce.androidsdk.mobilesync.app;

import com.salesforce.androidsdk.mobilesync.manager.LayoutSyncManager;
import com.salesforce.androidsdk.mobilesync.manager.MetadataSyncManager;
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger;
import com.salesforce.androidsdk.smartstore.app.SmartStoreUpgradeManager;

/**
 * This class handles upgrades from one version to another.
 *
 * @author bhariharan
 */
public class MobileSyncUpgradeManager extends SmartStoreUpgradeManager {

    /**
     * Key in shared preference file for mobile sync version.
     */
    private static final String MOBILE_SYNC_KEY = "mobile_sync_version";
    private static final String TAG = "MobileSyncUpgradeManager";

    private static MobileSyncUpgradeManager INSTANCE = null;

    /**
     * Returns an instance of this class.
     *
     * @return Instance of this class.
     */
    public static synchronized MobileSyncUpgradeManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MobileSyncUpgradeManager();
        }
        return INSTANCE;
    }

    @Override
    public void upgrade() {
        super.upgrade();
        upgradeSObject();
    }

    /**
     * Upgrades mobile sync data from existing client version to the current version.
     */
    protected synchronized void upgradeSObject() {
        final String installedVersion = getInstalledSobjectVersion();
        if (installedVersion.equals(MobileSyncSDKManager.SDK_VERSION)) {
            return;
        }

        // Update shared preference file to reflect the latest version.
        writeCurVersion(MOBILE_SYNC_KEY, MobileSyncSDKManager.SDK_VERSION);

        // If the installed version < v8.2.0, we need to delete the old layout SmartStore file.
        try {
            final String majorVersionNum = installedVersion.substring(0, 3);
            double installedVerDouble = Double.parseDouble(majorVersionNum);
            if (installedVerDouble < 8.2) {
                upgradeTo8Dot2();
            }
        } catch (Exception e) {
            MobileSyncLogger.e(TAG, "Failed to parse installed version.");
        }
    }

    /**
     * Returns the currently installed version of mobile sync.
     *
     * @return Currently installed version of mobile sync.
     */
    public String getInstalledSobjectVersion() {
        return getInstalledVersion(MOBILE_SYNC_KEY);
    }

    private void upgradeTo8Dot2() {
        LayoutSyncManager.getInstance().getSmartStore().dropSoup("sfdcLayouts");
        LayoutSyncManager.reset();
        MetadataSyncManager.getInstance().getSmartStore().dropSoup("sfdcMetadata");
        MetadataSyncManager.reset();
    }
}
