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
package com.salesforce.androidsdk.app;

import java.io.File;
import java.util.Map;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.auth.AuthenticatorService;
import com.salesforce.androidsdk.config.AdminSettingsManager;
import com.salesforce.androidsdk.config.LoginServerManager;
import com.salesforce.androidsdk.push.PushMessaging;
import com.salesforce.androidsdk.security.PasscodeManager;

/**
 * This class handles upgrades from one version to another.
 *
 * @author bhariharan
 */
public class UpgradeManager {

    /**
     * Name of the shared preference file that contains version information.
     */
    private static final String VERSION_SHARED_PREF = "version_info";

    /**
     * Key in shared preference file for account manager version.
     */
    private static final String ACC_MGR_KEY = "acc_mgr_version";

    private static UpgradeManager instance = null;

    /**
     * Returns an instance of this class.
     *
     * @return Instance of this class.
     */
    public static synchronized UpgradeManager getInstance() {
        if (instance == null) {
            instance = new UpgradeManager();
        }
        return instance;
    }

    /**
     * Upgrades account manager data from existing client
     * version to the current version.
     */
    public synchronized void upgradeAccMgr() {
        String installedVersion = getInstalledAccMgrVersion();
        if (installedVersion.equals(SalesforceSDKManager.SDK_VERSION)) {
            return;
        }

        // Update shared preference file to reflect the latest version.
        writeCurVersion(ACC_MGR_KEY, SalesforceSDKManager.SDK_VERSION);

        /*
         * We need to update this variable, since the app will not
         * have this value set for a first time install.
         */
        if (TextUtils.isEmpty(installedVersion)) {
            installedVersion = getInstalledAccMgrVersion();
        }

        /*
         * If the installed version < v2.2.0, we need to store the current
         * user's user ID and org ID in a shared preference file, to
         * support fast user switching.
         */
        try {
        	final String majorVersionNum = installedVersion.substring(0, 3);
            double installedVerDouble = Double.parseDouble(majorVersionNum);
            if (installedVerDouble < 2.2) {
            	upgradeTo2Dot2();
            }
        } catch (NumberFormatException e) {
        	Log.e("UpgradeManager:upgradeAccMgr", "Failed to parse installed version.");
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
        final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(VERSION_SHARED_PREF, Context.MODE_PRIVATE);
        return sp.getString(key, "");
    }

    /**
     * Upgrade steps for older versions of the Mobile SDK to Mobile SDK 2.2.
     */
    protected void upgradeTo2Dot2() {

    	// Creates the current user shared pref file.
        final AccountManager accountManager = AccountManager.get(SalesforceSDKManager.getInstance().getAppContext());
        final Account[] accounts = accountManager.getAccountsByType(SalesforceSDKManager.getInstance().getAccountType());
        if (accounts != null && accounts.length > 0) {
        	final Account account = accounts[0];
            final String orgId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account,
            		AuthenticatorService.KEY_ORG_ID), SalesforceSDKManager.getInstance().getPasscodeHash());
    		final String userId = SalesforceSDKManager.decryptWithPasscode(accountManager.getUserData(account,
    				AuthenticatorService.KEY_USER_ID), SalesforceSDKManager.getInstance().getPasscodeHash());
        	SalesforceSDKManager.getInstance().getUserAccountManager().storeCurrentUserInfo(userId, orgId);

    		/*
    		 * Renames push notification shared prefs file to new format,
    		 * if the application is registered for push notifications.
    		 */
        	final String oldFilename = PushMessaging.GCM_PREFS + ".xml";
    		final String sharedPrefDir = SalesforceSDKManager.getInstance().
    				getAppContext().getApplicationInfo().dataDir
    				+ "/shared_prefs";
    		final File from = new File(sharedPrefDir, oldFilename);
    		if (from.exists()) {
    			final String newFilename = PushMessaging.GCM_PREFS + SalesforceSDKManager.getInstance().
               		getUserAccountManager().buildUserAccount(account).getUserLevelFilenameSuffix() + ".xml";
        		final File to = new File(sharedPrefDir, newFilename);
        		from.renameTo(to);
    		}

    		/*
    		 * Copies admin prefs for current account from old file to new file.
    		 * We pass in a 'null' account in the getter, since we want the contents
    		 * from the default storage path. We pass the correct account to
    		 * the setter, to migrate the contents to the correct storage path.
    		 */
    		final Map<String, String> prefs = SalesforceSDKManager.getInstance().getAdminSettingsManager().getPrefs(null);
    		SalesforceSDKManager.getInstance().getAdminSettingsManager().setPrefs(prefs,
    				SalesforceSDKManager.getInstance().getUserAccountManager().buildUserAccount(account));
    		final SharedPreferences settings = SalesforceSDKManager.getInstance()
        			.getAppContext().getSharedPreferences(AdminSettingsManager.FILENAME_ROOT,
        			Context.MODE_PRIVATE);
    		final Editor edit = settings.edit();
    		edit.clear();
    		edit.commit();

    		/*
    		 * Copies the passcode/PIN policies from the existing file to
    		 * an org-specific file.
    		 */
    		final PasscodeManager passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();
    		final UserAccountManager userAccMgr = SalesforceSDKManager.getInstance().getUserAccountManager();
    		int timeoutMs = passcodeManager.getTimeoutMs();
    		int passcodeLength = passcodeManager.getMinPasscodeLength();
    		passcodeManager.storeMobilePolicyForOrg(userAccMgr.getCurrentUser(),
    				timeoutMs, passcodeLength);
        }

        // Removes the old shared pref file for custom URL.
    	final SharedPreferences settings = SalesforceSDKManager.getInstance()
    			.getAppContext().getSharedPreferences(LoginServerManager.LEGACY_SERVER_URL_PREFS_SETTINGS,
    			Context.MODE_PRIVATE);
		final Editor edit = settings.edit();
		edit.clear();
		edit.commit();
    }
}
