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

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;
import com.salesforce.androidsdk.smartsync.SmartSyncUserAccountManager;
import com.salesforce.androidsdk.smartsync.manager.CacheManager;
import com.salesforce.androidsdk.smartsync.manager.MetadataManager;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.ui.sfhybrid.SalesforceDroidGapActivity;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Super class for all applications that use the SmartSync SDK.
 */
public class SmartSyncSDKManager extends SalesforceSDKManagerWithSmartStore {

    /**
     * Protected constructor.
     *
     * @param context Application context.
     * @param keyImpl Implementation of KeyInterface. 
     * @param mainActivity Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
     */
    protected SmartSyncSDKManager(Context context, KeyInterface keyImpl,
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
    		INSTANCE = new SmartSyncSDKManager(context, keyImpl, mainActivity, loginActivity);
    	}
		initInternal(context);

        // Upgrade to the latest version.
        SmartSyncUpgradeManager.getInstance().upgradeSObject();
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
    	SmartSyncSDKManager.init(context, keyImpl, SalesforceDroidGapActivity.class,
    			LoginActivity.class);
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
    	SmartSyncSDKManager.init(context, keyImpl, SalesforceDroidGapActivity.class,
    			loginActivity);
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
    	SmartSyncSDKManager.init(context, keyImpl, mainActivity, loginActivity);
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
    	SmartSyncSDKManager.init(context, keyImpl, mainActivity, LoginActivity.class);
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
    	SmartSyncSDKManager.init(context, keyImpl, mainActivity, loginActivity);
    }

    /**
     * Returns a singleton instance of this class.
     *
     * @return Singleton instance of SmartSyncSDKManager.
     */
    public static SmartSyncSDKManager getInstance() {
    	if (INSTANCE != null) {
    		return (SmartSyncSDKManager) INSTANCE;
    	} else {
            throw new RuntimeException("Applications need to call SmartSyncSDKManager.init() first.");
    	}
    }

    @Override
    protected void cleanUp(Activity frontActivity, Account account) {
    	final UserAccount userAccount = SmartSyncUserAccountManager.getInstance().buildUserAccount(account);
    	MetadataManager.reset(userAccount);

    	/*
    	 * We don't have to do a hard reset on the cache manager here, since
    	 * the underlying database will be wiped in the super class.
    	 */
    	CacheManager.softReset(userAccount);
    	SyncManager.reset();
        super.cleanUp(frontActivity, account);
    }

    @Override
    public UserAccountManager getUserAccountManager() {
    	return SmartSyncUserAccountManager.getInstance();
    }
}
