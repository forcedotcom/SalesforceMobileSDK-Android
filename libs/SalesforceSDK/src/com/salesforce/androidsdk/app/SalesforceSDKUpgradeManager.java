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

import static com.salesforce.androidsdk.security.ScreenLockManager.MOBILE_POLICY_PREF;
import static com.salesforce.androidsdk.security.ScreenLockManager.SCREEN_LOCK;
import static com.salesforce.androidsdk.security.ScreenLockManager.SCREEN_LOCK_TIMEOUT;

import android.content.Context;
import android.content.SharedPreferences;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;

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
        String installedVersionStr = getInstalledAccMgrVersion();
        if (installedVersionStr.equals(SalesforceSDKManager.SDK_VERSION)) {
            return;
        }

        // Update shared preference file to reflect the latest version.
        writeCurVersion(ACC_MGR_KEY, SalesforceSDKManager.SDK_VERSION);

        try {
            final SdkVersion installedVersion = SdkVersion.parseFromString(installedVersionStr);
            if (installedVersion.isLessThan(new SdkVersion(9, 2, 0, false))) {
                upgradeFromBefore9_2_0To10_1_1PasscodeFixes();
            }
            // Already incorporated into 9.2 upgrade.
            else if (installedVersion.isGreaterThanOrEqualTo(new SdkVersion(9, 2, 0, false))
                    && installedVersion.isLessThan(new SdkVersion(10, 2, 0, false))
            ) {
                upgradeFromVersions9_2_0Thru10_1_1To10_1_1PasscodeFixes();
            }
        } catch (Exception e) {
            SalesforceSDKLogger.e(
                    TAG,
                    "Failed to parse installed version. Error message: " + e.getMessage()
            );
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

    // TODO: Remove upgrade step in Mobile SDK 11.0
    private void upgradeFromBefore9_2_0To10_1_1PasscodeFixes() {
        final String KEY_PASSCODE ="passcode";
        final String KEY_TIMEOUT = "access_timeout";
        final String KEY_PASSCODE_LENGTH = "passcode_length";
        final String KEY_FAILED_ATTEMPTS = "failed_attempts";
        final String KEY_PASSCODE_LENGTH_KNOWN = "passcode_length_known";
        final String KEY_BIOMETRIC_ALLOWED = "biometric_allowed";
        final String KEY_BIOMETRIC_ENROLLMENT = "biometric_enrollment";
        final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
        final Context ctx = SalesforceSDKManager.getInstance().getAppContext();

        final SharedPreferences globalPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE);
        if (globalPrefs.contains(KEY_TIMEOUT) && globalPrefs.contains(KEY_PASSCODE_LENGTH)) {
            SharedPreferences.Editor globalEditor = globalPrefs.edit();
            // Check that Passcode was enabled
            int timeout = globalPrefs.getInt(KEY_TIMEOUT, 0);
            if (timeout != 0) {
                globalEditor.putBoolean(SCREEN_LOCK, true);
                globalEditor.putInt(SCREEN_LOCK_TIMEOUT, timeout);
            }

            globalEditor.remove(KEY_PASSCODE);
            globalEditor.remove(KEY_TIMEOUT);
            globalEditor.remove(KEY_FAILED_ATTEMPTS);
            globalEditor.remove(KEY_PASSCODE_LENGTH);
            globalEditor.remove(KEY_PASSCODE_LENGTH_KNOWN);
            globalEditor.remove(KEY_BIOMETRIC_ALLOWED);
            globalEditor.remove(KEY_BIOMETRIC_ENROLLMENT);
            globalEditor.remove(KEY_BIOMETRIC_ENABLED);
            globalEditor.apply();

            // Set which users should have screen lock
            final UserAccountManager manager = SalesforceSDKManager.getInstance().getUserAccountManager();
            final List<UserAccount> accounts = manager.getAuthenticatedUsers();

            if (accounts != null) {
                for (UserAccount account : accounts) {
                    final SharedPreferences orgPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                            + account.getOrgLevelFilenameSuffix(), Context.MODE_PRIVATE);
                    if (orgPrefs.contains(KEY_TIMEOUT) && orgPrefs.contains(KEY_PASSCODE_LENGTH)) {
                        // Check that Passcode was enabled
                        int userTimeout = orgPrefs.getInt(KEY_TIMEOUT, 0);
                        if (userTimeout != 0) {
                            // Set screen lock key at user level
                            final SharedPreferences userPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                                    + account.getUserLevelFilenameSuffix(), Context.MODE_PRIVATE);
                            userPrefs.edit().putBoolean(SCREEN_LOCK, true).putInt(SCREEN_LOCK_TIMEOUT, userTimeout).apply();
                        }

                        // Delete passcode keys at org level
                        SharedPreferences.Editor orgEditor = orgPrefs.edit();
                        orgEditor.remove(KEY_PASSCODE);
                        orgEditor.remove(KEY_TIMEOUT);
                        orgEditor.remove(KEY_FAILED_ATTEMPTS);
                        orgEditor.remove(KEY_PASSCODE_LENGTH);
                        orgEditor.remove(KEY_PASSCODE_LENGTH_KNOWN);
                        orgEditor.remove(KEY_BIOMETRIC_ALLOWED);
                        orgEditor.remove(KEY_BIOMETRIC_ENROLLMENT);
                        orgEditor.remove(KEY_BIOMETRIC_ENABLED);
                        orgEditor.apply();
                    }
                }
            }
        }
    }

    // TODO: Remove upgrade step in Mobile SDK 12.0
    private void upgradeFromVersions9_2_0Thru10_1_1To10_1_1PasscodeFixes() {
        final Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        final SharedPreferences globalPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE);
        if (globalPrefs.contains(SCREEN_LOCK)) {
            final UserAccountManager manager = SalesforceSDKManager.getInstance().getUserAccountManager();
            final List<UserAccount> accounts = manager.getAuthenticatedUsers();
            if (accounts != null) {
                HttpAccess.init(ctx); // only needed because we need to hit the network for this upgrade step.
                Executors.newSingleThreadExecutor().execute(() -> {
                    int lowestTimeout = Integer.MAX_VALUE;

                    // Get and set connected app mobile policy timeout per user.
                    for (UserAccount account : accounts) {
                        try {
                            final OAuth2.IdServiceResponse response = OAuth2.callIdentityService(HttpAccess.DEFAULT,
                                    account.getIdUrl(), account.getAuthToken());

                            if (response.mobilePolicy && response.screenLockTimeout != -1) {
                                final SharedPreferences userPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                                        + account.getUserLevelFilenameSuffix(), Context.MODE_PRIVATE);
                                int timeoutInMills = response.screenLockTimeout * 1000 * 60;
                                userPrefs.edit().putInt(SCREEN_LOCK_TIMEOUT, timeoutInMills).apply();

                                if (lowestTimeout == Integer.MAX_VALUE || timeoutInMills < lowestTimeout) {
                                    lowestTimeout = timeoutInMills;
                                }
                            }
                        } catch (IOException e) {
                            SalesforceSDKLogger.e(TAG, "Exception throw retrieving mobile policy", e);
                        }
                    }

                    // Set timeout or remove block.
                    if (lowestTimeout < Integer.MAX_VALUE && lowestTimeout > 0) {
                        globalPrefs.edit().putInt(SCREEN_LOCK_TIMEOUT, lowestTimeout).apply();
                    } else {
                        globalPrefs.edit().remove(SCREEN_LOCK).apply();
                    }
                });
            }
        }
    }
}
