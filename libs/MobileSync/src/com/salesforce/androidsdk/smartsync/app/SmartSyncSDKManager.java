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
package com.salesforce.androidsdk.smartsync.app;

import android.app.Activity;
import android.content.Context;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager;
import com.salesforce.androidsdk.smartsync.R;
import com.salesforce.androidsdk.smartsync.config.SyncsConfig;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.util.SmartSyncLogger;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * SDK Manager for all native applications that use SmartSync
 */
public class SmartSyncSDKManager extends SmartStoreSDKManager {

	private static final String TAG = "SmartSyncSDKManager";

	/**
	 * Protected constructor.
     *
	 * @param context Application context.
	 * @param mainActivity Activity that should be launched after the login flow.
	 * @param loginActivity Login activity.
	 */
	protected SmartSyncSDKManager(Context context, Class<? extends Activity> mainActivity,
                                  Class<? extends Activity> loginActivity) {
		super(context, mainActivity, loginActivity);
	}

	private static void init(Context context, Class<? extends Activity> mainActivity,
							 Class<? extends Activity> loginActivity) {
		if (INSTANCE == null) {
    		INSTANCE = new SmartSyncSDKManager(context, mainActivity, loginActivity);
    	}

		// Upgrade to the latest version.
		SmartSyncUpgradeManager.getInstance().upgrade();
		initInternal(context);
        EventsObservable.get().notifyEvent(EventType.AppCreateComplete);
	}

    /**
     * Initializes components required for this class
     * to properly function. This method should be called
     * by native apps using the Salesforce Mobile SDK.
     *
     * @param context Application context.
     * @param mainActivity Activity that should be launched after the login flow.
     */
    public static void initNative(Context context, Class<? extends Activity> mainActivity) {
        SmartSyncSDKManager.init(context, mainActivity, LoginActivity.class);
    }

    /**
     * Initializes components required for this class
     * to properly function. This method should be called
     * by native apps using the Salesforce Mobile SDK.
     *
     * @param context Application context.
     * @param mainActivity Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
     */
    public static void initNative(Context context, Class<? extends Activity> mainActivity,
                                  Class<? extends Activity> loginActivity) {
        SmartSyncSDKManager.init(context, mainActivity, loginActivity);
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
    protected void cleanUp(UserAccount userAccount) {
    	SyncManager.reset(userAccount);
        super.cleanUp(userAccount);
    }

	/**
	 * Setup global syncs using config found in res/raw/globalsyncs.json
	 */
	public void setupGlobalSyncsFromDefaultConfig() {
		SmartSyncLogger.d(TAG, "Setting up global syncs using config found in res/raw/globalsyncs.json");
		SyncsConfig config = new SyncsConfig(context, R.raw.globalsyncs);
		if (config.hasSyncs()) {
			config.createSyncs(getGlobalSmartStore());
		}
	}

	/**
	 * Setup user syncs using config found in res/raw/usersyncs.json
	 */
	public void setupUserSyncsFromDefaultConfig() {
		SmartSyncLogger.d(TAG, "Setting up user syncs using config found in res/raw/usersyncs.json");
		SyncsConfig config = new SyncsConfig(context, R.raw.usersyncs);
		if (config.hasSyncs()) {
			config.createSyncs(getSmartStore());
		}
	}
}
