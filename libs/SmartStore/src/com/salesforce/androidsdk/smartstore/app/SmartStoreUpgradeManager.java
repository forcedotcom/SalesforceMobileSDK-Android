/*
 * Copyright (c) 2012, salesforce.com, inc.
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

import android.text.TextUtils;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKUpgradeManager;

import java.io.File;

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

    private static final String DB_NAME_2DOT3_FORMAT = "smartstore%s.db";

    private static SmartStoreUpgradeManager instance = null;

    /**
     * Returns an instance of this class.
     *
     * @return Instance of this class.
     */
    public static synchronized SmartStoreUpgradeManager getInstance() {
        if (instance == null) {
            instance = new SmartStoreUpgradeManager();
        }
        return instance;
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
        String installedVersion = getInstalledSmartStoreVersion();
        if (installedVersion.equals(SmartStoreSDKManager.SDK_VERSION)) {
            return;
        }

        // Update shared preference file to reflect the latest version.
        writeCurVersion(SMART_STORE_KEY, SmartStoreSDKManager.SDK_VERSION);

        /*
         * We need to update this variable, since the app will not
         * have this value set for a first time install.
         */
        if (TextUtils.isEmpty(installedVersion)) {
            installedVersion = getInstalledSmartStoreVersion();
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

    @Override
    protected void upgradeTo2Dot2() {
    	super.upgradeTo2Dot2();

    	/*
    	 * Checks if a database exists. If it does, renames the existing
    	 * database to the new format for the current user.
    	 * If not, nothing is done.
    	 */
    	final String oldDbName = String.format(DB_NAME_2DOT3_FORMAT, "");
    	if (SmartStoreSDKManager.getInstance().getAppContext().getDatabasePath(oldDbName).exists()) {
    		final UserAccount curAccount = SmartStoreSDKManager.getInstance().getUserAccountManager().getCurrentUser();

    		/*
    		 * If no account exists at this point, there is nothing
    		 * to be done here.
    		 */
    		if (curAccount != null) {
        		final String dbPath = curAccount.getCommunityLevelFilenameSuffix(null);
        		if (!TextUtils.isEmpty(dbPath)) {
        			final String newDbName = String.format(DB_NAME_2DOT3_FORMAT, dbPath);
        			final String dbDir = SmartStoreSDKManager.getInstance().getAppContext().getApplicationInfo().dataDir
        					+ "/databases";
        			final File from = new File(dbDir, oldDbName);
        			final File to = new File(dbDir, newDbName);
        			from.renameTo(to);
        		}
    		}
    	}
    }
}
