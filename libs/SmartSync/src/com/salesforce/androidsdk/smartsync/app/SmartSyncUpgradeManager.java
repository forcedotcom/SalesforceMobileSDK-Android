/*
 * Copyright (c) 2014, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartsync.app;

import android.text.TextUtils;

import com.salesforce.androidsdk.smartstore.app.UpgradeManagerWithSmartStore;

/**
 * This class handles upgrades from one version to another.
 *
 * @author bhariharan
 */
public class SmartSyncUpgradeManager extends UpgradeManagerWithSmartStore {

    /**
     * Key in shared preference file for smart sync version.
     */
    private static final String SMART_SYNC_KEY = "smart_sync_version";

    private static SmartSyncUpgradeManager INSTANCE = null;

    /**
     * Returns an instance of this class.
     *
     * @return Instance of this class.
     */
    public static synchronized SmartSyncUpgradeManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SmartSyncUpgradeManager();
        }
        return INSTANCE;
    }

    /**
     * Upgrades smart sync data from existing client version to the current version.
     */
    public synchronized void upgradeSObject() {
        String installedVersion = getInstalledSobjectVersion();
        if (installedVersion.equals(SmartSyncSDKManager.SDK_VERSION)) {
            return;
        }

        // Update shared preference file to reflect the latest version.
        writeCurVersion(SMART_SYNC_KEY, SmartSyncSDKManager.SDK_VERSION);

        /*
         * We need to update this variable, since the app will not
         * have this value set for a first time install.
         */
        if (TextUtils.isEmpty(installedVersion)) {
            installedVersion = getInstalledSobjectVersion();
        }
    }

    /**
     * Returns the currently installed version of smart sync.
     *
     * @return Currently installed version of smart sync.
     */
    public String getInstalledSobjectVersion() {
        return getInstalledVersion(SMART_SYNC_KEY);
    }
}
