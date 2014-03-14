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

import net.sqlcipher.database.SQLiteDatabase;
import android.app.Activity;
import android.content.Context;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.smartstore.store.DBHelper;
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
    	SalesforceSDKManagerWithSmartStore.init(context, keyImpl, SalesforceDroidGapActivity.class, LoginActivity.class);
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
    public static void initHybrid(Context context, KeyInterface keyImpl, Class<? extends Activity> loginActivity) {
    	SalesforceSDKManagerWithSmartStore.init(context, keyImpl, SalesforceDroidGapActivity.class, loginActivity);
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
    public static void initHybrid(Context context, KeyInterface keyImpl, Class<? extends SalesforceDroidGapActivity> mainActivity, Class<? extends Activity> loginActivity) {
    	SalesforceSDKManagerWithSmartStore.init(context, keyImpl, mainActivity, loginActivity);
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
    public static void initNative(Context context, KeyInterface keyImpl, Class<? extends Activity> mainActivity) {
    	SalesforceSDKManagerWithSmartStore.init(context, keyImpl, mainActivity, LoginActivity.class);
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
    public static void initNative(Context context, KeyInterface keyImpl, Class<? extends Activity> mainActivity, Class<? extends Activity> loginActivity) {
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
    protected void cleanUp(Activity frontActivity) {

        // Reset smartstore.
        if (hasSmartStore()) {
        	DBHelper.INSTANCE.reset(INSTANCE.getAppContext());
        }
        super.cleanUp(frontActivity);
    }

    @Override
    public synchronized void changePasscode(String oldPass, String newPass) {
    	if (isNewPasscode(oldPass, newPass)) {
	        if (hasSmartStore()) {

	            // If the old passcode is null, use the default key.
	            final SQLiteDatabase db = DBOpenHelper.getOpenHelper(context).getWritableDatabase(getEncryptionKeyForPasscode(oldPass));

	            // If the new passcode is null, use the default key.
	            SmartStore.changeKey(db, getEncryptionKeyForPasscode(newPass));
	        }
	        super.changePasscode(oldPass, newPass);
		}
    }

    /**
     * Returns the database used for smart store.
     *
     * @return SmartStore instance.
     */
    public SmartStore getSmartStore() {
        final String passcodeHash = getPasscodeHash();
        final SQLiteDatabase db = DBOpenHelper.getOpenHelper(context).getWritableDatabase(passcodeHash == null ? getEncryptionKeyForPasscode(null) : passcodeHash);
        return new SmartStore(db);
    }

    /**
     * Returns whether smart store is enabled.
     *
     * @return True - if the application has a smart store database, False - otherwise.
     */
    public boolean hasSmartStore() {
        return context.getDatabasePath(DBOpenHelper.DB_NAME).exists();
    }
}
