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

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.security.Encryptor;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.SmartStore;

/**
 * Super class for all force applications that use the smartstore.
 */
public class ForceAppWithSmartStore extends ForceApp {

    /**
     * Protected constructor.
     *
     * @param context Application context.
     * @param key Key used for encryption - must be Base64 encoded.
     *
     * 			  {@link Encryptor#isBase64Encoded(String)} can be used to
     * 		      determine whether the generated key is Base64 encoded.
     *
     * 		      {@link Encryptor#hash(String, String)} can be used to
     * 		      generate a Base64 encoded string.
     *
     * 		      For example:
     * 			  <code>
     * 			  Encryptor.hash(name + "12s9adfgret=6235inkasd=012", name + "12kl0dsakj4-cuygsdf625wkjasdol8");
     * 			  </code>
     *
     * @param loginOptions Login options used - must be non null for a native app, can be null for a hybrid app.
     */
    protected ForceAppWithSmartStore(Context context, String key, LoginOptions loginOptions) {
    	super(context, key, loginOptions);
    }

	/**
	 * Initializes components required for this class
	 * to properly function. This method should be called
	 * by apps using the Salesforce Mobile SDK.
	 *
	 * @param context Application context.
     * @param key Key used for encryption.
     * @param loginOptions Login options used - must be non null for a native app, can be null for a hybrid app.
	 */
	public static void init(Context context, String key, LoginOptions loginOptions) {
		ForceApp.init(context, key, loginOptions);

        // Upgrade to the latest version.
        UpgradeManagerWithSmartStore.getInstance().upgradeSmartStore();
	}

    /**
     * Returns a singleton instance of this class.
     *
     * @param context Application context.
     * @return Singleton instance of ForceApp.
     */
    public static ForceAppWithSmartStore getInstance() {
    	if (INSTANCE != null) {
    		return (ForceAppWithSmartStore) INSTANCE;
    	} else {
            throw new RuntimeException("Applications need to call ForceAppWithSmartStore.init() first.");
    	}
    }

    @Override
    protected void cleanUp(Activity frontActivity) {

        // Reset smartstore.
        if (hasSmartStore()) {
        	DBOpenHelper.deleteDatabase(INSTANCE.getAppContext());
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
