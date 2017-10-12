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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager;
import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.auth.AuthenticatorService;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class handles upgrades from one version to another.
 *
 * @author bhariharan
 */
public class SalesforceSDKUpgradeManager {

    private static final String VERSION_SHARED_PREF = "version_info";
    private static final String ACC_MGR_KEY = "acc_mgr_version";
    private static final String SHARED_PREF_6_0 = "upgrade_6_0";
    private static final String UPGRADE_REQUIRED_KEY = "passcode_upgrade_required";
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
         * We need to update this variable, since the app will not
         * have this value set for a first time install.
         */
        if (TextUtils.isEmpty(installedVersion)) {
            installedVersion = getInstalledAccMgrVersion();
        }
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
            SalesforceSDKLogger.e(TAG, "Failed to parse installed version", e);
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

    /**
     * Returns if re-encryption is required in an app with passcode enabled.
     *
     * @return True - if re-encryption is required, False - otherwise.
     */
    public boolean isPasscodeUpgradeRequired() {
        final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(SHARED_PREF_6_0,
                Context.MODE_PRIVATE);
        return sp.getBoolean(UPGRADE_REQUIRED_KEY, false);
    }

    private void createUpgradeSharedPref() {
        final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(SHARED_PREF_6_0,
                Context.MODE_PRIVATE);
        sp.edit().putBoolean(UPGRADE_REQUIRED_KEY, true).commit();
    }

    /**
     * Wipes the shared pref file once passcode upgrade is complete.
     */
    public void wipeUpgradeSharedPref() {
        final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(SHARED_PREF_6_0,
                Context.MODE_PRIVATE);
        sp.edit().clear().commit();
    }

    private void upgradeTo6Dot0() {
        final Context context = SalesforceSDKManager.getInstance().getAppContext();
        final PasscodeManager passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();
        final String newEncryptionKey = SalesforceSDKManager.getEncryptionKey();
        String oldEncryptionkey = null;

        /*
         * Checks if passcode is enabled or not. If passcode is not enabled, the data is
         * re-encrypted right away. If passcode is enabled, the data is re-encrypted after
         * the passcode screen is dismissed, since we need the passcode to compute the key.
         */
        if (!passcodeManager.hasStoredPasscode(context)) {
            oldEncryptionkey = passcodeManager.getLegacyEncryptionKey("");
            upgradeTo6Dot0(oldEncryptionkey, newEncryptionKey);
        } else {
            createUpgradeSharedPref();
        }
    }

    /**
     * Upgrade steps for older versions of the Mobile SDK to Mobile SDK 6.0.
     *
     * @param oldKey Old encryption key.
     * @param newKey New encryption key.
     */
    public void upgradeTo6Dot0(String oldKey, String newKey) {
        reEncryptAccountInfo(oldKey, newKey);
        reEncryptAnalyticsData(oldKey, newKey);
    }

    private void reEncryptAccountInfo(String oldKey, String newKey) {
        final AccountManager acctManager = AccountManager.get(SalesforceSDKManager.getInstance().getAppContext());
        if (acctManager != null) {
            final Account[] accounts = acctManager.getAccountsByType(SalesforceSDKManager.getInstance().getAccountType());
            if (accounts != null && accounts.length > 0) {
                for (final Account account : accounts) {

                    // Grab existing data stored in AccountManager.
                    final String authToken = Encryptor.decrypt(acctManager.getUserData(account, AccountManager.KEY_AUTHTOKEN), oldKey);
                    final String refreshToken = Encryptor.decrypt(acctManager.getPassword(account), oldKey);
                    final String loginServer = Encryptor.decrypt(acctManager.getUserData(account, AuthenticatorService.KEY_LOGIN_URL), oldKey);
                    final String idUrl = Encryptor.decrypt(acctManager.getUserData(account, AuthenticatorService.KEY_ID_URL), oldKey);
                    final String instanceServer = Encryptor.decrypt(acctManager.getUserData(account, AuthenticatorService.KEY_INSTANCE_URL), oldKey);
                    final String orgId = Encryptor.decrypt(acctManager.getUserData(account, AuthenticatorService.KEY_ORG_ID), oldKey);
                    final String userId = Encryptor.decrypt(acctManager.getUserData(account, AuthenticatorService.KEY_USER_ID), oldKey);
                    final String username = Encryptor.decrypt(acctManager.getUserData(account, AuthenticatorService.KEY_USERNAME), oldKey);
                    final String clientId = Encryptor.decrypt(acctManager.getUserData(account, AuthenticatorService.KEY_CLIENT_ID), oldKey);
                    final String lastName = Encryptor.decrypt(acctManager.getUserData(account, AuthenticatorService.KEY_LAST_NAME), oldKey);
                    final String email = Encryptor.decrypt(acctManager.getUserData(account, AuthenticatorService.KEY_EMAIL), oldKey);
                    final String encFirstName =  acctManager.getUserData(account, AuthenticatorService.KEY_FIRST_NAME);
                    String firstName = null;
                    if (encFirstName != null) {
                        firstName = Encryptor.decrypt(encFirstName, oldKey);
                    }
                    final String encDisplayName =  acctManager.getUserData(account, AuthenticatorService.KEY_DISPLAY_NAME);
                    String displayName = null;
                    if (encDisplayName != null) {
                        displayName = Encryptor.decrypt(encDisplayName, oldKey);
                    }
                    final String encPhotoUrl = acctManager.getUserData(account, AuthenticatorService.KEY_PHOTO_URL);
                    String photoUrl = null;
                    if (encPhotoUrl != null) {
                        photoUrl = Encryptor.decrypt(encPhotoUrl, oldKey);
                    }
                    final String encThumbnailUrl = acctManager.getUserData(account, AuthenticatorService.KEY_THUMBNAIL_URL);
                    String thumbnailUrl = null;
                    if (encThumbnailUrl != null) {
                        thumbnailUrl = Encryptor.decrypt(encThumbnailUrl, oldKey);
                    }
                    final List<String> additionalOauthKeys = SalesforceSDKManager.getInstance().getAdditionalOauthKeys();
                    Map<String, String> values = null;
                    if (additionalOauthKeys != null && !additionalOauthKeys.isEmpty()) {
                        values = new HashMap<>();
                        for (final String key : additionalOauthKeys) {
                            final String encValue = acctManager.getUserData(account, key);
                            if (encValue != null) {
                                final String value = Encryptor.decrypt(encValue, oldKey);
                                values.put(key, value);
                            }
                        }
                    }
                    final String encCommunityId = acctManager.getUserData(account, AuthenticatorService.KEY_COMMUNITY_ID);
                    String communityId = null;
                    if (encCommunityId != null) {
                        communityId = Encryptor.decrypt(encCommunityId, oldKey);
                    }
                    final String encCommunityUrl = acctManager.getUserData(account, AuthenticatorService.KEY_COMMUNITY_URL);
                    String communityUrl = null;
                    if (encCommunityUrl != null) {
                        communityUrl = Encryptor.decrypt(encCommunityUrl, oldKey);
                    }

                    // Encrypt data with new hash and put it back in AccountManager.
                    acctManager.setUserData(account, AccountManager.KEY_AUTHTOKEN, Encryptor.encrypt(authToken, newKey));
                    acctManager.setPassword(account, Encryptor.encrypt(refreshToken, newKey));
                    acctManager.setUserData(account, AuthenticatorService.KEY_LOGIN_URL, Encryptor.encrypt(loginServer, newKey));
                    acctManager.setUserData(account, AuthenticatorService.KEY_ID_URL, Encryptor.encrypt(idUrl, newKey));
                    acctManager.setUserData(account, AuthenticatorService.KEY_INSTANCE_URL, Encryptor.encrypt(instanceServer, newKey));
                    acctManager.setUserData(account, AuthenticatorService.KEY_ORG_ID, Encryptor.encrypt(orgId, newKey));
                    acctManager.setUserData(account, AuthenticatorService.KEY_USER_ID, Encryptor.encrypt(userId, newKey));
                    acctManager.setUserData(account, AuthenticatorService.KEY_USERNAME, Encryptor.encrypt(username, newKey));
                    acctManager.setUserData(account, AuthenticatorService.KEY_CLIENT_ID, Encryptor.encrypt(clientId, newKey));
                    acctManager.setUserData(account, AuthenticatorService.KEY_LAST_NAME, Encryptor.encrypt(lastName, newKey));
                    acctManager.setUserData(account, AuthenticatorService.KEY_EMAIL, Encryptor.encrypt(email, newKey));
                    if (firstName != null) {
                        acctManager.setUserData(account, AuthenticatorService.KEY_FIRST_NAME, Encryptor.encrypt(firstName, newKey));
                    }
                    if (displayName != null) {
                        acctManager.setUserData(account, AuthenticatorService.KEY_DISPLAY_NAME, Encryptor.encrypt(displayName, newKey));
                    }
                    if (photoUrl != null) {
                        acctManager.setUserData(account, AuthenticatorService.KEY_PHOTO_URL, Encryptor.encrypt(photoUrl, newKey));
                    }
                    if (thumbnailUrl != null) {
                        acctManager.setUserData(account, AuthenticatorService.KEY_THUMBNAIL_URL, Encryptor.encrypt(thumbnailUrl, newKey));
                    }
                    if (values != null && !values.isEmpty()) {
                        for (final String key : additionalOauthKeys) {
                            final String value = values.get(key);
                            if (value != null) {
                                acctManager.setUserData(account, key, Encryptor.encrypt(value, newKey));
                            }
                        }
                    }
                    if (communityId != null) {
                        acctManager.setUserData(account, AuthenticatorService.KEY_COMMUNITY_ID, Encryptor.encrypt(communityId, newKey));
                    }
                    if (communityUrl != null) {
                        acctManager.setUserData(account, AuthenticatorService.KEY_COMMUNITY_URL, Encryptor.encrypt(communityUrl, newKey));
                    }
                }
            }
        }
    }

    private void reEncryptAnalyticsData(String oldKey, String newKey) {
        final UserAccountManager userAccountManager = SalesforceSDKManager.getInstance().getUserAccountManager();
        final List<UserAccount> userAccounts = userAccountManager.getAuthenticatedUsers();
        if (userAccounts != null) {
            for (final UserAccount account : userAccounts) {
                if (account != null) {
                    final SalesforceAnalyticsManager analyticsManager = SalesforceAnalyticsManager.getInstance(account);
                    if (analyticsManager != null) {
                        analyticsManager.getAnalyticsManager().changeEncryptionKey(oldKey, newKey);
                    }
                }
            }
        }
    }
}
