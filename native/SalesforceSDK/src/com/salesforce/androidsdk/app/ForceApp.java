/*
 * Copyright (c) 2011, salesforce.com, inc.
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

import com.salesforce.androidsdk.auth.AbstractLoginActivity;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.security.AbstractPasscodeActivity;
import com.salesforce.androidsdk.security.Encryptor;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.security.PasscodeManager.HashConfig;

/**
 * Super class for all force applications.
 * You should extend this class or make sure to initialize HttpAccess in your application's onCreate method.
 */
public abstract class ForceApp extends Application implements OnAccountsUpdateListener  {

	// current SDK version
    public static final String SDK_VERSION = "0.9";

	// instance of the ForceApp for this process
    public static ForceApp APP;
    
    // passcode manager
    private PasscodeManager passcodeManager;
    
    // to avoid logout re-entry
    private boolean inLogout = false;
    
    
    @Override
    public void onCreate() {
        super.onCreate();
        APP = this;

        // Initialize encryption module
        Encryptor.init(this);
        
        // Initialize the http client        
        HttpAccess.init(this, getUserAgent());
        
        // Initialize the passcode manager
		passcodeManager = new PasscodeManager(this, getLockTimeoutMinutes(),
				getPasscodeActivityClass(), getVerificationHashConfig(),
				getEncryptionHashConfig());
		
		// Listen for accounts update
		AccountManager.get(this).addOnAccountsUpdatedListener(this, null, false);
    }
    
    @Override
    public void onTerminate() {
    	AccountManager.get(this).removeOnAccountsUpdatedListener(this);
        super.onTerminate();
    }    

	/**
	 * @return passcodeManager
	 */
	public PasscodeManager getPasscodeManager() {
		return passcodeManager;
	}

    /**
     * @return hash salts + key to use for creating the hash of the passcode used for encryption
	 * Unique for installation.
     */
    protected HashConfig getEncryptionHashConfig() {
		return new HashConfig(getUuId("eprefix"), getUuId("esuffix"), getUuId("ekey"));
	}

    /**
     * @return hash salt + key to use for creating the hash of the passcode used for verification
     * Unique to the installation.
     */
	protected HashConfig getVerificationHashConfig() {
		return new HashConfig(getUuId("vprefix"), getUuId("vsuffix"), getUuId("vkey"));
	}
	
	@Override
    public void onAccountsUpdated(Account[] accounts) {
		if (inLogout) {
			return;
		}
		
        // see if there's an entry for our account type, if not fire the callback
        for (Account a : accounts) {
            if (getAccountType().equals(a.type)) return;
        }
        logout(null);
    }	
	
	/**
     * Wipe out stored auth (remove account) and restart app
     */
    public void logout(Activity frontActivity) {
    	inLogout = true;
    	
    	// Finish front activity if specified
    	if (frontActivity != null) {
    		frontActivity.finish();
    	}
    	
    	// Remove account if any
    	new ClientManager(this).removeAccountAsync(null);
    	
        // Clear cookies 
        CookieSyncManager.createInstance(this);
        CookieManager.getInstance().removeAllCookie();
    	
        // Restart application
        Intent i = new Intent(this, getMainActivityClass());
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        
        inLogout = false;
    }
    
    /**
     * @return user agent string to use for all requests
	 */
	public final String getUserAgent() {
        //set a user agent string based on the mobile sdk version
        // We are building a user agent of the form:
		// SalesforceMobileSDK/1.0 android/3.2.0 

	    return "SalesforceMobileSDK/" + SDK_VERSION + " android/"+ Build.VERSION.RELEASE;
	}

    @Override
    public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass()).append(": {\n")
		  .append("   accountType: ").append(getAccountType()).append("\n")
		  .append("   userAgent: ").append(getUserAgent()).append("\n")
		  .append("   loginActivityClass: ").append(getLoginActivityClass()).append("\n")
		  .append("   passcodeActivityClass: ").append(getPasscodeActivityClass()).append("\n")
		  .append("   isFileSystemEncrypted: ").append(Encryptor.isFileSystemEncrypted()).append("\n")
		  .append("   lockTimeoutMinutes: ").append(getLockTimeoutMinutes()).append("\n")
		  .append("   hasStoredPasscode: ").append(passcodeManager.hasStoredPasscode(this)).append("\n")
		  .append("}\n");
		return sb.toString();

    }

    /**
	 * If you return 0, the user will not have to enter a passcode
	 * Only use that option if file system encryption is on
	 * @return lock timeout in minutes or 0 for never
	 *  
	 */
	abstract public int getLockTimeoutMinutes();
	

    /**
     * @return class for main activity
     */
	abstract public Class<? extends Activity> getMainActivityClass();
	
	/**
     * @return class for login activity
     */
	abstract public Class<? extends AbstractLoginActivity> getLoginActivityClass();

    /**
     * @return class for passcode activity
     */
    abstract public Class<? extends AbstractPasscodeActivity> getPasscodeActivityClass();
    
    /**
     * @return account type (should match authenticator.xml)
     */
    abstract public String getAccountType();

	/*
	 * Random keys persisted encrypted in a private preference file
	 * This is provided as an example.
	 * We recommend you provide you own implementation for creating the HashConfig's.
	 * 
	 */
	private Map<String, String> uuids = new HashMap<String, String>();
	private synchronized String getUuId(String name) {
		if (uuids.get(name) != null) return uuids.get(name);
		SharedPreferences sp = getSharedPreferences("uuids", Context.MODE_PRIVATE);
		if (!sp.contains(name)) {
			String uuid = UUID.randomUUID().toString();
			Editor e = sp.edit();
			e.putString(name, Encryptor.encrypt(uuid, getKey(name)));
			e.commit();
		}
		return Encryptor.decrypt(sp.getString(name, null), getKey(name));
	}
	
	/**
	 * This function must return the same value for name even when application is restarted 
	 * @param name
	 * @return key for encrypting salts and keys 
	 */
	protected abstract String getKey(String name);
}
