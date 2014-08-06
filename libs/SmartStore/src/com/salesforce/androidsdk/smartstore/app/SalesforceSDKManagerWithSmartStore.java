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
package com.salesforce.androidsdk.smartstore.app;

import java.util.List;

import net.sqlcipher.database.SQLiteDatabase;
import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.ui.sfhybrid.SalesforceDroidGapActivity;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Super class for all force applications that use the smartstore.
 */
public class SalesforceSDKManagerWithSmartStore extends SalesforceSDKManager {

    /**
     * Protected constructor.
     *
     * @param context Application context.
     * @param keyImpl Implementation of KeyInterface. 
     * @param mainActivity Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
     */
    protected SalesforceSDKManagerWithSmartStore(Context context, KeyInterface keyImpl,
    		Class<? extends Activity> mainActivity, Class<? extends Activity> loginActivity) {
    	super(context, keyImpl, mainActivity, loginActivity);
    }

	/**
	 * Initializes components required for this class
	 * to properly function. This method should be called
	 * by apps using the Salesforce Mobile SDK.
	 *
	 * @param context Application context.
     * @param keyImpl Implementation of KeyInterface.
     * @param mainActivity Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
	 */
	private static void init(Context context, KeyInterface keyImpl,
			Class<? extends Activity> mainActivity, Class<? extends Activity> loginActivity) {
		if (INSTANCE == null) {
    		INSTANCE = new SalesforceSDKManagerWithSmartStore(context, keyImpl, mainActivity, loginActivity);
    	}
		initInternal(context);

        // Upgrade to the latest version.
        UpgradeManagerWithSmartStore.getInstance().upgradeSmartStore();
        EventsObservable.get().notifyEvent(EventType.AppCreateComplete);
	}

	/**
	 * Initializes components required for this class
	 * to properly function. This method should be called
	 * by hybrid apps using the Salesforce Mobile SDK.
	 *
	 * @param context Application context.
     * @param keyImpl Implementation of KeyInterface.
	 */
    public static void initHybrid(Context context, KeyInterface keyImpl) {
    	SalesforceSDKManagerWithSmartStore.init(context, keyImpl,
    			SalesforceDroidGapActivity.class, LoginActivity.class);
    }

	/**
	 * Initializes components required for this class
	 * to properly function. This method should be called
	 * by hybrid apps using the Salesforce Mobile SDK.
	 *
	 * @param context Application context.
     * @param keyImpl Implementation of KeyInterface.
     * @param loginActivity Login activity.
	 */
    public static void initHybrid(Context context, KeyInterface keyImpl,
    		Class<? extends Activity> loginActivity) {
    	SalesforceSDKManagerWithSmartStore.init(context, keyImpl,
    			SalesforceDroidGapActivity.class, loginActivity);
    }

	/**
	 * Initializes components required for this class
	 * to properly function. This method should be called
	 * by hybrid apps that use a subclass of SalesforceDroidGapActivity.
	 *
	 * @param context Application context.
     * @param keyImpl Implementation of KeyInterface.
     * @param mainActivity Main activity.
     * @param loginActivity Login activity.
	 */
    public static void initHybrid(Context context, KeyInterface keyImpl,
    		Class<? extends SalesforceDroidGapActivity> mainActivity,
    		Class<? extends Activity> loginActivity) {
    	SalesforceSDKManagerWithSmartStore.init(context, keyImpl,
    			mainActivity, loginActivity);
    }
    
	/**
	 * Initializes components required for this class
	 * to properly function. This method should be called
	 * by native apps using the Salesforce Mobile SDK.
	 *
	 * @param context Application context.
     * @param keyImpl Implementation of KeyInterface.
     * @param mainActivity Activity that should be launched after the login flow.
	 */
    public static void initNative(Context context, KeyInterface keyImpl,
    		Class<? extends Activity> mainActivity) {
    	SalesforceSDKManagerWithSmartStore.init(context, keyImpl, mainActivity,
    			LoginActivity.class);
    }

	/**
	 * Initializes components required for this class
	 * to properly function. This method should be called
	 * by native apps using the Salesforce Mobile SDK.
	 *
	 * @param context Application context.
     * @param keyImpl Implementation of KeyInterface.
     * @param mainActivity Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
	 */
    public static void initNative(Context context, KeyInterface keyImpl,
    		Class<? extends Activity> mainActivity, Class<? extends Activity> loginActivity) {
    	SalesforceSDKManagerWithSmartStore.init(context, keyImpl, mainActivity, loginActivity);
    }

    /**
     * Returns a singleton instance of this class.
     *
     * @param context Application context.
     * @return Singleton instance of SalesforceSDKManagerWithSmartStore.
     */
    public static SalesforceSDKManagerWithSmartStore getInstance() {
    	if (INSTANCE != null) {
    		return (SalesforceSDKManagerWithSmartStore) INSTANCE;
    	} else {
            throw new RuntimeException("Applications need to call SalesforceSDKManagerWithSmartStore.init() first.");
    	}
    }

    @Override
    protected void cleanUp(Activity frontActivity, Account account) {
    	if (account != null) {
    		final UserAccount userAccount = getUserAccountManager().buildUserAccount(account);
    		if (userAccount != null && hasSmartStore(userAccount)) {
    			DBOpenHelper.deleteDatabase(getAppContext(), userAccount);
    		}
    	}

        /*
         * Checks how many accounts are left that are authenticated. If only one
         * account is left, this is the account that is being removed. In this
         * case, we can safely reset all DBs.
         */
        final List<UserAccount> users = getUserAccountManager().getAuthenticatedUsers();
        if (users != null && users.size() == 1) {
			DBOpenHelper.deleteDatabase(getAppContext(), users.get(0));
        }
        super.cleanUp(frontActivity, account);
    }

    @Override
    public synchronized void changePasscode(String oldPass, String newPass) {
    	if (isNewPasscode(oldPass, newPass)) {
    		final UserAccountManager accMgr = getUserAccountManager();
    		final List<UserAccount> accounts = accMgr.getAuthenticatedUsers();
    		if (accounts != null) {
    			for (final UserAccount account : accounts) {
    				if (hasSmartStore(account)) {

    		            // If the old passcode is null, use the default key.
    		            final SQLiteDatabase db = DBOpenHelper.getOpenHelper(context, account).getWritableDatabase(getEncryptionKeyForPasscode(oldPass));

    		            // If the new passcode is null, use the default key.
    		            SmartStore.changeKey(db, getEncryptionKeyForPasscode(newPass));
    		        }
    			}
    		}
	        super.changePasscode(oldPass, newPass);
		}
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
     * @param account UserAccount instance.
     * @param communityId Community ID.
     * @return SmartStore instance.
     */
    public SmartStore getSmartStore(UserAccount account, String communityId) {
    	final String passcodeHash = getPasscodeHash();
        final SQLiteDatabase db = DBOpenHelper.getOpenHelper(context,
        		account, communityId).getWritableDatabase(passcodeHash == null ?
        		getEncryptionKeyForPasscode(null) : passcodeHash);
        return new SmartStore(db);
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
     * @param account UserAccount instance.
     * @param communityId Community ID.
     * @return True - if the user has a smart store database, False - otherwise.
     */
    public boolean hasSmartStore(UserAccount account, String communityId) {
    	String dbName = String.format(DBOpenHelper.DB_NAME, "");
    	if (account != null) {
        	final String dbPath = account.getCommunityLevelFilenameSuffix(communityId);
    		if (!TextUtils.isEmpty(dbPath)) {
    			dbName = String.format(DBOpenHelper.DB_NAME, dbPath);
    		}
    	}
        return context.getDatabasePath(dbName).exists();
    }
}
