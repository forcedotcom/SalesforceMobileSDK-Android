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
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.smartstore.R;
import com.salesforce.androidsdk.smartstore.config.StoreConfig;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.ui.SmartStoreInspectorActivity;
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

import net.sqlcipher.database.SQLiteOpenHelper;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * SDK Manager for all native applications that use SmartStore
 */
public class SmartStoreSDKManager extends SalesforceSDKManager {

    private static final String TAG = "SmartStoreSDKManager";

    private static final String FEATURE_SMART_STORE_USER = "US";
    private static final String FEATURE_SMART_STORE_GLOBAL = "GS";

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

    /**
     * Protected constructor.
     *
     * @param context       Application context.
     * @param keyImpl       Implementation of KeyInterface.
     * @param mainActivity  Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
     * @deprecated Will be removed in Mobile SDK 7.0. Use {@link #SmartStoreSDKManager(Context, Class, Class)} instead.
     */
    @Deprecated
    protected SmartStoreSDKManager(Context context, KeyInterface keyImpl,
                                   Class<? extends Activity> mainActivity, Class<? extends Activity> loginActivity) {
        super(context, keyImpl, mainActivity, loginActivity);
    }

    /**
     * Initializes components required for this class
     * to properly function. This method should be called
     * by apps using the Salesforce Mobile SDK.
     *
     * @param context       Application context.
     * @param keyImpl       Implementation of KeyInterface.
     * @param mainActivity  Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
     */
    private static void init(Context context, KeyInterface keyImpl,
                             Class<? extends Activity> mainActivity, Class<? extends Activity> loginActivity) {
        if (INSTANCE == null) {
            INSTANCE = new SmartStoreSDKManager(context, keyImpl, mainActivity, loginActivity);
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
        SmartStoreSDKManager.init(context, null, mainActivity, LoginActivity.class);
    }

    /**
     * Initializes components required for this class
     * to properly function. This method should be called
     * by native apps using the Salesforce Mobile SDK.
     *
     * @param context      Application context.
     * @param keyImpl      Implementation of KeyInterface.
     * @param mainActivity Activity that should be launched after the login flow.
     * @deprecated Will be removed in Mobile SDK 7.0. Use {@link #initNative(Context, Class)} instead.
     */
    @Deprecated
    public static void initNative(Context context, KeyInterface keyImpl, Class<? extends Activity> mainActivity) {
        SmartStoreSDKManager.init(context, keyImpl, mainActivity, LoginActivity.class);
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
        SmartStoreSDKManager.init(context, null, mainActivity, loginActivity);
    }

    /**
     * Initializes components required for this class
     * to properly function. This method should be called
     * by native apps using the Salesforce Mobile SDK.
     *
     * @param context       Application context.
     * @param keyImpl       Implementation of KeyInterface.
     * @param mainActivity  Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
     * @deprecated Will be removed in Mobile SDK 7.0. Use {@link #initNative(Context, Class, Class)} instead.
     */
    @Deprecated
    public static void initNative(Context context, KeyInterface keyImpl,
                                  Class<? extends Activity> mainActivity, Class<? extends Activity> loginActivity) {
        SmartStoreSDKManager.init(context, keyImpl, mainActivity, loginActivity);
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
            DBOpenHelper.deleteDatabase(getAppContext(), userAccount);
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
        SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_SMART_STORE_GLOBAL);
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
        return getSmartStore(getUserAccountManager().getCurrentUser());
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
        SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_SMART_STORE_USER);
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
        return hasSmartStore(getUserAccountManager().getCurrentUser(), null);
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
        removeSmartStore(getUserAccountManager().getCurrentUser());
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
     * @throws JSONException
     */
    public List<String> getGlobalStoresPrefixList(){
        UserAccount userAccount = getUserAccountManager().getCurrentUser();
        String communityId = userAccount!=null?userAccount.getCommunityId():null;
        List<String> globalDBNames = DBOpenHelper.getGlobalDatabasePrefixList(context,getUserAccountManager().getCurrentUser(),communityId);
        return globalDBNames;
    }

    /**
     * Returns a list of store names for current user.
     * @return
     * @throws JSONException
     */
    public List<String> getUserStoresPrefixList() {
        UserAccount userAccount = getUserAccountManager().getCurrentUser();
        String communityId = userAccount!=null?userAccount.getCommunityId():null;
        List<String> userDBName = DBOpenHelper.getUserDatabasePrefixList(context,getUserAccountManager().getCurrentUser(),communityId);
        return userDBName;
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
        List<String> globalDBNames = this.getUserStoresPrefixList();
        for(String storeName : globalDBNames) {
            removeSmartStore(storeName,
                    UserAccountManager.getInstance().getCurrentUser(),
                    UserAccountManager.getInstance().getCurrentUser().getCommunityId());
        }
    }

    /**
     * Setup global store using config found in res/raw/globalstore.json
     */
    public void setupGlobalStoreFromDefaultConfig() {
        SmartStoreLogger.d(TAG, "Setting up global store using config found in res/raw/globalstore.json");
        setupStoreFromConfig(getGlobalSmartStore(), R.raw.globalstore);
    }

    /**
     * Setup user store using config found in res/raw/userstore.json
     */
    public void setupUserStoreFromDefaultConfig() {
        SmartStoreLogger.d(TAG, "Setting up user store using config found in res/raw/userstore.json");
        setupStoreFromConfig(getSmartStore(), R.raw.userstore);
    }

    /**
     * Setup given store using config found in given json resource file
     *
     * @param store
     * @param resourceId
     */
    private void setupStoreFromConfig(SmartStore store, int resourceId) {
        StoreConfig config = new StoreConfig(context, resourceId);
        config.registerSoups(store);
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

        return devActions;
    }

    @Override
    public List<String> getDevSupportInfos() {
        List<String> devSupportInfos = new ArrayList<>(super.getDevSupportInfos());
        devSupportInfos.addAll(Arrays.asList(
                "SQLCipher version", getSmartStore().getSQLCipherVersion(),
                "SQLCipher Compile Options", TextUtils.join(", ", getSmartStore().getCompileOptions()),
                "User Stores", TextUtils.join(", ", getUserStoresPrefixList()),
                "Global Stores", TextUtils.join(", ", getGlobalStoresPrefixList())
        ));
        return devSupportInfos;
    }
}
