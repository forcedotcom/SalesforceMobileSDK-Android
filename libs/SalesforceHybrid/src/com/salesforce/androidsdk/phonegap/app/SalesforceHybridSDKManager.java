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
package com.salesforce.androidsdk.phonegap.app;

import android.app.Activity;
import android.content.Context;

import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.phonegap.ui.SalesforceDroidGapActivity;
import com.salesforce.androidsdk.smartstore.config.StoreConfig;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger;
import com.salesforce.androidsdk.mobilesync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.mobilesync.config.SyncsConfig;
import com.salesforce.androidsdk.mobilesync.util.SmartSyncLogger;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * SDK Manager for all hybrid applications
 */
public class SalesforceHybridSDKManager extends SmartSyncSDKManager {

    private static final String TAG = "SalesforceHybridSDKManager";

    /**
     * Paths to the assets files containing configs for SmartStore/SmartSync in hybrid apps.
     */
    private enum ConfigAssetPath {

        globalStore("globalstore.json"),
        userStore("userstore.json"),
        globalSyncs("globalsyncs.json"),
        userSyncs("usersyncs.json");

        String path;

        ConfigAssetPath(String fileName) {
            path = "www" + System.getProperty("file.separator") + fileName;
        }
    }

    /**
     * Protected constructor.
     *
     * @param context Application context.
     * @param mainActivity Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
     */
    protected SalesforceHybridSDKManager(Context context, Class<? extends Activity> mainActivity,
                                         Class<? extends Activity> loginActivity) {
        super(context, mainActivity, loginActivity);
    }

	private static void init(Context context, Class<? extends Activity> mainActivity,
							 Class<? extends Activity> loginActivity) {
		if (INSTANCE == null) {
    		INSTANCE = new SalesforceHybridSDKManager(context, mainActivity, loginActivity);
    	}

		// Upgrade to the latest version.
		SalesforceHybridUpgradeManager.getInstance().upgrade();
		initInternal(context);
        EventsObservable.get().notifyEvent(EventType.AppCreateComplete);
	}

    /**
     * Initializes components required for this class
     * to properly function. This method should be called
     * by hybrid apps using the Salesforce Mobile SDK.
     *
     * @param context Application context.
     */
    public static void initHybrid(Context context) {
        SalesforceHybridSDKManager.init(context, SalesforceDroidGapActivity.class,
                LoginActivity.class);
    }

    /**
     * Initializes components required for this class
     * to properly function. This method should be called
     * by hybrid apps using the Salesforce Mobile SDK.
     *
     * @param context Application context.
     * @param loginActivity Login activity.
     */
    public static void initHybrid(Context context, Class<? extends Activity> loginActivity) {
        SalesforceHybridSDKManager.init(context, SalesforceDroidGapActivity.class,
                loginActivity);
    }

    /**
     * Initializes components required for this class
     * to properly function. This method should be called
     * by hybrid apps that use a subclass of SalesforceDroidGapActivity.
     *
     * @param context Application context.
     * @param mainActivity Main activity.
     * @param loginActivity Login activity.
     */
    public static void initHybrid(Context context, Class<? extends SalesforceDroidGapActivity> mainActivity,
                                  Class<? extends Activity> loginActivity) {
        SalesforceHybridSDKManager.init(context, mainActivity, loginActivity);
    }
    
    /**
     * Returns a singleton instance of this class.
     *
     * @return Singleton instance of SalesforceHybridSDKManager.
     */
    public static SalesforceHybridSDKManager getInstance() {
    	if (INSTANCE != null) {
    		return (SalesforceHybridSDKManager) INSTANCE;
    	} else {
            throw new RuntimeException("Applications need to call SalesforceHybridSDKManager.init() first.");
    	}
    }

	@Override
	public final String getUserAgent(String qualifier) {
		if (qualifier == null) {
			qualifier = "";
		}
		final BootConfig config = BootConfig.getBootConfig(context);
		if (config.isLocal()) {
			qualifier = qualifier + "Local";
		} else {
			qualifier = qualifier + "Remote";
		}
		return super.getUserAgent(qualifier);
	}

    public String getAppType() {
        return "Hybrid";
    }

	@Override
	public boolean isHybrid() {
		return true;
	}

	/**
	 * Setup global store using config found in assets/www/globalstore.json
	 */
	public void setupGlobalStoreFromDefaultConfig() {
		SmartStoreLogger.d(TAG, "Setting up global store using config found in " + ConfigAssetPath.globalStore.path);
		setupStoreFromConfig(getGlobalSmartStore(), ConfigAssetPath.globalStore.path);
	}

	/**
	 * Setup user store using config found in assets/www/userstore.json
	 */
	public void setupUserStoreFromDefaultConfig() {
		SmartStoreLogger.d(TAG, "Setting up user store using config found in " + ConfigAssetPath.userStore.path);
		setupStoreFromConfig(getSmartStore(), ConfigAssetPath.userStore.path);
	}

    /**
	 * Setup given store using config found in given json assets file
	 *
	 * @param store
	 * @param assetPath
	 */
	private void setupStoreFromConfig(SmartStore store, String assetPath) {
		StoreConfig config = new StoreConfig(context, assetPath);
		config.registerSoups(store);
	}

    /**
     * Setup global syncs using config found in assets/www/globalsyncs.json
     */
    public void setupGlobalSyncsFromDefaultConfig() {
        SmartSyncLogger.d(TAG, "Setting up global syncs using config found in " + ConfigAssetPath.globalSyncs.path);
        setupSyncsFromConfig(getGlobalSmartStore(), ConfigAssetPath.globalSyncs.path);
    }

    /**
     * Setup user syncs using config found in assets/www/usersyncs.json
     */
    public void setupUserSyncsFromDefaultConfig() {
        SmartSyncLogger.d(TAG, "Setting up user syncs using config found in " + ConfigAssetPath.userSyncs.path);
        setupSyncsFromConfig(getSmartStore(), ConfigAssetPath.userSyncs.path);
    }

    /**
     * Setup syncs in given store using config found in given json assets file
     *
     * @param store
     * @param assetPath
     */
    private void setupSyncsFromConfig(SmartStore store, String assetPath) {
        SyncsConfig config = new SyncsConfig(context, assetPath);
        config.createSyncs(store);
    }
}
