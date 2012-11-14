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
package com.salesforce.androidsdk.app;

import net.sqlcipher.database.SQLiteDatabase;
import android.app.Activity;

import com.salesforce.androidsdk.store.DBOpenHelper;
import com.salesforce.androidsdk.store.SmartStore;

/**
 * Super class for all force applications that use the smartstore.
 */
public abstract class ForceAppWithSmartStore extends ForceApp {

	@Override
	public void onCreate() {
		super.onCreate();

        // Upgrade to the latest version.
        UpgradeManagerWithSmartStore.getInstance().upgradeSmartStore();
	}
	
    @Override
    protected void cleanUp(Activity frontActivity) {

        // Reset smartstore.
        if (hasSmartStore()) {
        	DBOpenHelper.deleteDatabase(this);
        }
        
        super.cleanUp(frontActivity);
    }
	
    @Override
    public synchronized void changePasscode(String oldPass, String newPass) {
    	if (isNewPasscode(oldPass, newPass)) {
	        if (hasSmartStore()) {
	
	            // If the old passcode is null, use the default key.
	            final SQLiteDatabase db = DBOpenHelper.getOpenHelper(ForceApp.APP).getWritableDatabase(ForceApp.APP.getEncryptionKeyForPasscode(oldPass));
	
	            // If the new passcode is null, use the default key.
	            SmartStore.changeKey(db, ForceApp.APP.getEncryptionKeyForPasscode(newPass));
	        }
	        super.changePasscode(oldPass, newPass);
		}
    }

    /**
     * @return the database used that contains the smart store
     */
    public SmartStore getSmartStore() {
        String passcodeHash = getPasscodeHash();
        SQLiteDatabase db = DBOpenHelper.getOpenHelper(this).getWritableDatabase(passcodeHash == null ? getEncryptionKeyForPasscode(null) : passcodeHash);
        return new SmartStore(db);
    }
    
    /**
     * @return true if the application has a smartstore database
     */
    public boolean hasSmartStore() {
        return getDatabasePath(DBOpenHelper.DB_NAME).exists();
    }

}
