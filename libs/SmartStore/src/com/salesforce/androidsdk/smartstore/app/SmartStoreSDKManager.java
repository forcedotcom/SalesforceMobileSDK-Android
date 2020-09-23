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
package com.salesforce.androidsdk.smartstore.app;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.smartstore.R;
import com.salesforce.androidsdk.smartstore.config.StoreConfig;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.KeyValueEncryptedFileStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.ui.KeyValueStoreInspectorActivity;
import com.salesforce.androidsdk.smartstore.ui.SmartStoreInspectorActivity;
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.ManagedFilesHelper;

import net.sqlcipher.database.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static com.salesforce.androidsdk.smartstore.store.KeyValueEncryptedFileStore.KEY_VALUE_STORES;

/**
 * SDK Manager for all native applications that use SmartStore
 */
public class SmartStoreSDKManager extends SalesforceSDKManager {

    private static final String TAG = "SmartStoreSDKManager";
    public static final String GLOBAL_SUFFIX = "_global";

    /**
     * Protected constructor.
     *
     * @param context       Application context.
     * @param mainActivity  Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
     */
    protected SmartStoreSDKManager(Context context, Class<? extends Activity> mainActivity,
                                   Class<? extends Activity> loginActivity) {
        super(context, mainActivity, loginActivity);
    }

    private static void init(Context context, Class<? extends Activity> mainActivity,
                             Class<? extends Activity> loginActivity) {
        if (INSTANCE == null) {
            INSTANCE = new SmartStoreSDKManager(context, mainActivity, loginActivity);
        }

        // Upgrade to the latest version.
        SmartStoreUpgradeManager.getInstance().upgrade();
        initInternal(context);
        EventsObservable.get().notifyEvent(EventType.AppCreateComplete);
    }

    /**
     * Initializes components required for this class
     * to properly function. This method should be called
     * by native apps using the Salesforce Mobile SDK.
     *
     * @param context      Application context.
     * @param mainActivity Activity that should be launched after the login flow.
     */
    public static void initNative(Context context, Class<? extends Activity> mainActivity) {
        SmartStoreSDKManager.init(context, mainActivity, LoginActivity.class);
    }

    /**
     * Initializes components required for this class
     * to properly function. This method should be called
     * by native apps using the Salesforce Mobile SDK.
     *
     * @param context       Application context.
     * @param mainActivity  Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
     */
    public static void initNative(Context context, Class<? extends Activity> mainActivity,
                                  Class<? extends Activity> loginActivity) {
        SmartStoreSDKManager.init(context, mainActivity, loginActivity);
    }

    /**
     * Returns a singleton instance of this class.
     *
     * @return Singleton instance of SalesforceSDKManagerWithSmartStore.
     */
    public static SmartStoreSDKManager getInstance() {
        if (INSTANCE != null) {
            return (SmartStoreSDKManager) INSTANCE;
        } else {
            throw new RuntimeException("Applications need to call SalesforceSDKManagerWithSmartStore.init() first.");
        }
    }

    @Override
    protected void cleanUp(UserAccount userAccount) {
        if (userAccount != null) {
            // NB if database file was already deleted, we still need to call DBOpenHelper.deleteDatabase to clean up the DBOpenHelper cache
            DBOpenHelper.deleteAllDatabases(getAppContext(), userAccount);
            removeAllKeyValueStores(userAccount);
        } else {
            DBOpenHelper.deleteAllUserDatabases(getAppContext());
        }
        super.cleanUp(userAccount);
    }

    /**
     * Return default database used by smart store in the global context
     *
     * @return SmartStore instance
     */
    public SmartStore getGlobalSmartStore() {
        return getGlobalSmartStore(null);
    }

    /**
     * Returns the database used by smart store in the global context.
     *
     * @param dbName The database name. This must be a valid file name without a
     *               filename extension such as ".db". Pass 'null' for default.
     * @return SmartStore instance.
     */

    public SmartStore getGlobalSmartStore(String dbName) {
        SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_SMART_STORE_GLOBAL);
        if (TextUtils.isEmpty(dbName)) {
            dbName = DBOpenHelper.DEFAULT_DB_NAME;
        }
        final SQLiteOpenHelper dbOpenHelper = DBOpenHelper.getOpenHelper(context,
                dbName, null, null);
        return new SmartStore(dbOpenHelper, getEncryptionKey());
    }

    /**
     * Returns the database used by smart store for the current user.
     *
     * @return SmartStore instance.
     */
    public SmartStore getSmartStore() {
        return getSmartStore(getUserAccountManager().getCachedCurrentUser());
    }

    /**
     * Returns the database used by smart store for a specified user.
     *
     * @param account UserAccount instance.
     * @return SmartStore instance.
     */
    public SmartStore getSmartStore(UserAccount account) {
        return getSmartStore(account, null);
    }

    /**
     * Returns the database used by smart store for a specified user in the
     * specified community.
     *
     * @param account     UserAccount instance.
     * @param communityId Community ID.
     * @return SmartStore instance.
     */
    public SmartStore getSmartStore(UserAccount account, String communityId) {
        return getSmartStore(DBOpenHelper.DEFAULT_DB_NAME, account, communityId);
    }

    /**
     * Returns the database used by smart store for a specified database name and
     * user in the specified community.
     *
     * @param dbNamePrefix The database name. This must be a valid file name without a
     *                     filename extension such as ".db".
     * @param account      UserAccount instance.
     * @param communityId  Community ID.
     * @return SmartStore instance.
     */
    public SmartStore getSmartStore(String dbNamePrefix, UserAccount account, String communityId) {
        if (TextUtils.isEmpty(dbNamePrefix)) {
            dbNamePrefix = DBOpenHelper.DEFAULT_DB_NAME;
        }
        SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_SMART_STORE_USER);
        final SQLiteOpenHelper dbOpenHelper = DBOpenHelper.getOpenHelper(context,
                dbNamePrefix, account, communityId);
        SmartStore store = new SmartStore(dbOpenHelper, getEncryptionKey());

        return store;
    }

    /**
     * Returns whether global smart store is enabled or not.
     *
     * @param dbName Database name. This must be a valid file name without a
     *               filename extension such as ".db". Pass 'null' for default.
     * @return True - if the specified global database exists, False - otherwise.
     */
    public boolean hasGlobalSmartStore(String dbName) {
        if (TextUtils.isEmpty(dbName)) {
            dbName = DBOpenHelper.DEFAULT_DB_NAME;
        }
        return DBOpenHelper.smartStoreExists(context, dbName, null, null);
    }

    /**
     * Returns whether smart store is enabled for the current user or not.
     *
     * @return True - if the user has a smart store database, False - otherwise.
     */
    public boolean hasSmartStore() {
        return hasSmartStore(getUserAccountManager().getCachedCurrentUser(), null);
    }

    /**
     * Returns whether smart store is enabled for the specified user or not.
     *
     * @param account UserAccount instance.
     * @return True - if the user has a smart store database, False - otherwise.
     */
    public boolean hasSmartStore(UserAccount account) {
        return hasSmartStore(account, null);
    }

    /**
     * Returns whether smart store is enabled for the specified community or not.
     *
     * @param account     UserAccount instance.
     * @param communityId Community ID.
     * @return True - if the user has a smart store database, False - otherwise.
     */
    public boolean hasSmartStore(UserAccount account, String communityId) {
        return hasSmartStore(DBOpenHelper.DEFAULT_DB_NAME, account, communityId);
    }

    /**
     * Returns whether smart store is enabled for the specified database or not.
     *
     * @param dbNamePrefix The database name. This must be a valid file name without a
     *                     filename extension such as ".db".
     * @param account      UserAccount instance.
     * @param communityId  Community ID.
     * @return True - if the user has a smart store database, False - otherwise.
     */
    public boolean hasSmartStore(String dbNamePrefix, UserAccount account, String communityId) {
        if (TextUtils.isEmpty(dbNamePrefix)) {
            dbNamePrefix = DBOpenHelper.DEFAULT_DB_NAME;
        }
        return DBOpenHelper.smartStoreExists(context, dbNamePrefix, account, communityId);
    }

    /**
     * Removes the global smart store.
     *
     * @param dbName Database name. This must be a valid file name without a
     *               filename extension such as ".db". Pass 'null' for default.
     */
    public void removeGlobalSmartStore(String dbName) {
        if (TextUtils.isEmpty(dbName)) {
            dbName = DBOpenHelper.DEFAULT_DB_NAME;
        }
        DBOpenHelper.deleteDatabase(context, dbName, null, null);
    }

    /**
     * Removes the default smart store for the current user.
     */
    public void removeSmartStore() {
        removeSmartStore(getUserAccountManager().getCachedCurrentUser());
    }

    /**
     * Removes the default smart store for the specified user.
     *
     * @param account UserAccount instance.
     */
    public void removeSmartStore(UserAccount account) {
        removeSmartStore(account, null);
    }

    /**
     * Removes the default smart store for the specified user and community.
     *
     * @param account     UserAccount instance.
     * @param communityId Community ID.
     */
    public void removeSmartStore(UserAccount account, String communityId) {
        removeSmartStore(DBOpenHelper.DEFAULT_DB_NAME, account, communityId);
    }

    /**
     * Removes the named smart store for the specified user and community.
     *
     * @param dbNamePrefix The database name. This must be a valid file name without a
     *                     filename extension such as ".db".
     * @param account      UserAccount instance.
     * @param communityId  Community ID.
     */
    public void removeSmartStore(String dbNamePrefix, UserAccount account, String communityId) {
        if (TextUtils.isEmpty(dbNamePrefix)) {
            dbNamePrefix = DBOpenHelper.DEFAULT_DB_NAME;
        }
        DBOpenHelper.deleteDatabase(context, dbNamePrefix, account, communityId);
    }

    /**
     * Returns a list of global store names.
     * @return
     */
    public List<String> getGlobalStoresPrefixList(){
        UserAccount userAccount = getUserAccountManager().getCachedCurrentUser();
        String communityId = userAccount!=null?userAccount.getCommunityId():null;
        List<String> globalDBNames = DBOpenHelper.getGlobalDatabasePrefixList(context,getUserAccountManager().getCachedCurrentUser(),communityId);
        return globalDBNames;
    }

    /**
     * Returns a list of store names for current user.
     * @return
     */
    public List<String> getUserStoresPrefixList() {
        return getUserStoresPrefixList(getUserAccountManager().getCachedCurrentUser());
    }

    /**
     * Returns a list of store names for given user.
     * @param account user account
     * @return
     */
    public List<String> getUserStoresPrefixList(UserAccount account) {
        if (account != null) {
            return DBOpenHelper.getUserDatabasePrefixList(context, account, account.getCommunityId());
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * Removes all the global stores.
     *
     */
    public void removeAllGlobalStores() {
        List<String> globalDBNames = this.getGlobalStoresPrefixList();
        for(String storeName : globalDBNames) {
            removeGlobalSmartStore(storeName);
        }
    }

    /**
     * Removes all the stores for current user.
     *
     */
    public void removeAllUserStores() {
        removeAllUserStores(getUserAccountManager().getCachedCurrentUser());
    }

    /**
     * Removes all the stores for current user.
     * @param account user account
     */
    public void removeAllUserStores(UserAccount account) {
        DBOpenHelper.deleteAllDatabases(getAppContext(), account);
    }

    /**
     * Setup global store using config found in res/raw/globalstore.json
     */
    public void setupGlobalStoreFromDefaultConfig() {
        SmartStoreLogger.d(TAG, "Setting up global store using config found in res/raw/globalstore.json");
        StoreConfig config = new StoreConfig(context, R.raw.globalstore);
        if (config.hasSoups()) {
            config.registerSoups(getGlobalSmartStore());
        }
    }

    /**
     * Setup user store using config found in res/raw/userstore.json
     */
    public void setupUserStoreFromDefaultConfig() {
        SmartStoreLogger.d(TAG, "Setting up user store using config found in res/raw/userstore.json");
        StoreConfig config = new StoreConfig(context, R.raw.userstore);
        if (config.hasSoups()) {
            config.registerSoups(getSmartStore());
        }
    }

    @Override
    protected LinkedHashMap<String, DevActionHandler> getDevActions(final Activity frontActivity) {
        LinkedHashMap<String, DevActionHandler> devActions = super.getDevActions(frontActivity);

        devActions.put(
                "Inspect SmartStore", new DevActionHandler() {
                    @Override
                    public void onSelected() {
                        frontActivity.startActivity(SmartStoreInspectorActivity.getIntent(frontActivity, false, DBOpenHelper.DEFAULT_DB_NAME));
                    }
                });

        devActions.put("Inspect KeyValue Store", new DevActionHandler() {
            @Override
            public void onSelected() {
                frontActivity.startActivity(KeyValueStoreInspectorActivity.getIntent(frontActivity));
            }
        });

        return devActions;
    }

    @Override
    public List<String> getDevSupportInfos() {
        List<String> devSupportInfos = new ArrayList<>(super.getDevSupportInfos());
        devSupportInfos.addAll(Arrays.asList(
                "SQLCipher version", getSmartStore().getSQLCipherVersion(),
                "SQLCipher Compile Options", TextUtils.join(", ", getSmartStore().getCompileOptions()),
                "SQLCipher Runtime Setting", TextUtils.join(", ", getSmartStore().getRuntimeSettings()),
                "User SmartStores", TextUtils.join(", ", getUserStoresPrefixList()),
                "Global SmartStores", TextUtils.join(", ", getGlobalStoresPrefixList()),
                "User Key-Value Stores", TextUtils.join(", ", getKeyValueStoresPrefixList()),
                "Global Key-Value Stores", TextUtils.join(", ", getGlobalKeyValueStoresPrefixList())
        ));
        return devSupportInfos;
    }

    /**
     * Get key value store with given name for current user
     * @param storeName store name
     * @return a KeyValueEncryptedFileStore
     */
    public KeyValueEncryptedFileStore getKeyValueStore(String storeName) {
        return getKeyValueStore(storeName, getUserAccountManager().getCachedCurrentUser(), null);
    }

    /**
     * Get key value store with given name for given user
     * @param storeName store name
     * @param account user account
     * @return a KeyValueEncryptedFileStore
     */
    public KeyValueEncryptedFileStore getKeyValueStore(String storeName, UserAccount account) {
        return getKeyValueStore(storeName, account, null);
    }

    /**
     * Get key value store with given name for given user / community
     * @param storeName store name
     * @param account user account
     * @param communityId community id
     * @return a KeyValueEncryptedFileStore
     */
    public KeyValueEncryptedFileStore getKeyValueStore(String storeName, UserAccount account, String communityId) {
        String suffix = account.getCommunityLevelFilenameSuffix(communityId);
        return new KeyValueEncryptedFileStore(
            getAppContext(),
            storeName + suffix,
            getEncryptionKey());
    }

    /**
     * Return whether there is a key value store with given name for current user
     */
    public boolean hasKeyValueStore(String storeName) {
        return hasKeyValueStore(storeName, getUserAccountManager().getCachedCurrentUser(), null);
    }

    /**
     * Return whether there is a key value store with given name for given user
     */
    public boolean hasKeyValueStore(String storeName, UserAccount account) {
        return hasKeyValueStore(storeName, account, null);

    }

    /**
     * Return whether there is a key value store with given name for given user / community id
     */
    public boolean hasKeyValueStore(String storeName, UserAccount account, String communityId) {
        String suffix = account.getCommunityLevelFilenameSuffix(communityId);
        return KeyValueEncryptedFileStore.hasKeyValueStore(getAppContext(), storeName + suffix);
    }

    /**
     * Remove key value store with given name for current user
     */
    public void removeKeyValueStore(String storeName) {
        removeKeyValueStore(storeName, getUserAccountManager().getCachedCurrentUser(), null);
    }

    /**
     * Remove key value store with given name for given user
     */
    public void removeKeyValueStore(String storeName, UserAccount account) {
        removeKeyValueStore(storeName, account, null);
    }

    /**
     * Remove key value store with given name for given user / community id
     */
    public void removeKeyValueStore(String storeName, UserAccount account, String communityId) {
        String suffix = account.getCommunityLevelFilenameSuffix(communityId);
        KeyValueEncryptedFileStore.removeKeyValueStore(getAppContext(), storeName + suffix);
    }

  /**
     * Returns a list of key value store names for current user.
     *
     * @return list of store names
     */
    public List<String> getKeyValueStoresPrefixList() {
        return getKeyValueStoresPrefixList(getUserAccountManager().getCachedCurrentUser());
    }

    /**
     * Returns a list of key value store names for given user.
     *
     * @param account user account
     * @return list of store names
     */
    public List<String> getKeyValueStoresPrefixList(UserAccount account) {
        if (account == null) {
            return new ArrayList<>();
        } else {
            return ManagedFilesHelper.getPrefixList(getAppContext(), KEY_VALUE_STORES,
                account.getCommunityLevelFilenameSuffix(), "", null);
        }
    }

    /**
     * Removes all the key value stores for current user.
     */
    public void removeAllKeyValueStores() {
        removeAllKeyValueStores(getUserAccountManager().getCachedCurrentUser());
    }

    /**
     * Removes all the key value stores for given user.
     *
     * @param account user account
     */
    public void removeAllKeyValueStores(UserAccount account) {
        if (account != null) {
            ManagedFilesHelper.deleteFiles(ManagedFilesHelper
                .getFiles(getAppContext(), KEY_VALUE_STORES,
                    account.getUserLevelFilenameSuffix(), "", null));
        }
    }

    /**
     * Get global key value store with given name
     * @param storeName store name
     * @return a KeyValueEncryptedFileStore
     */
    public KeyValueEncryptedFileStore getGlobalKeyValueStore(String storeName) {
        return new KeyValueEncryptedFileStore(
            getAppContext(),
            storeName + GLOBAL_SUFFIX,
            getEncryptionKey());
    }

    /**
     * Return whether there is a global key value store with given name
     */
    public boolean hasGlobalKeyValueStore(String storeName) {
        return KeyValueEncryptedFileStore.hasKeyValueStore(getAppContext(), storeName + GLOBAL_SUFFIX);
    }

    /**
     * Remove global key value store with given name
     */
    public void removeGlobalKeyValueStore(String storeName) {
        KeyValueEncryptedFileStore.removeKeyValueStore(getAppContext(), storeName + GLOBAL_SUFFIX);
    }

    /**
     * Returns a list of global key value store names.
     * @return
     */
    public List<String> getGlobalKeyValueStoresPrefixList(){
        return ManagedFilesHelper.getPrefixList(getAppContext(), KEY_VALUE_STORES,
            GLOBAL_SUFFIX, "", null);
    }

    /**
     * Removes all the global key value stores.
     *
     */
    public void removeAllGlobalKeyValueStores() {
        ManagedFilesHelper.deleteFiles(ManagedFilesHelper
            .getFiles(getAppContext(), KEY_VALUE_STORES, GLOBAL_SUFFIX,"", null));
    }

}
