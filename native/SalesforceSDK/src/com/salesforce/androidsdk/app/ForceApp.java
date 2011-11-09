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

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Build;

import com.salesforce.androidsdk.auth.AbstractLoginActivity;
import com.salesforce.androidsdk.auth.AuthenticatorService;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.security.Encryptor;
import com.salesforce.androidsdk.security.PasscodeManager;

/**
 * Super class for all force applications.
 * You should extend this class or make sure to initialize HttpAccess in your application's onCreate method.
 */
public abstract class ForceApp extends Application  {

	// current SDK version
    public static final String SDK_VERSION = "0.9";

	// instance of the ForceApp for this process
    public static ForceApp APP;
    
    // passcode manager
    private PasscodeManager passcodeManager;
    
    
    @Override
    public void onCreate() {
        super.onCreate();
        APP = this;

        // Initialize encryption module
        Encryptor.init(this);
        
        // Initialize the http client        
        HttpAccess.init(this, getUserAgent());
        
        // Initialize the passcode manager
        passcodeManager = new PasscodeManager(this, getLockTimeoutMinutes(), getPasscodeActivityClass());
    }

	/**
     * Remove user account and launch the login activity with a clean task stack.
     */
    public void logout() {
    	new ClientManager(this, getAccountType(), null /* we just want to removed accounts, we don't need the actual value */).removeAccountAsync(null);
    	Intent i = new Intent(this, getLoginActivityClass());
    	i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	i.putExtra(AuthenticatorService.PASSCODE_HASH, getPasscodeManager().getUserPasscode());
    	this.startActivity(i);
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

	/**
	 * @return passcodeManager
	 */
	public PasscodeManager getPasscodeManager() {
		return passcodeManager;
	}
	
	/**
	 * @return lock timeout in minutes or 0 for never 
	 */
	public int getLockTimeoutMinutes() {
		if (Encryptor.isFileSystemEncrypted()) {
			return 0; // never
		}
		else {
			return 5;
		}
	}
	
    /**
     * @return class for login activity
     */
    public abstract Class<? extends AbstractLoginActivity> getLoginActivityClass();

    /**
     * @return class for passcode activity
     */
    public abstract Class<? extends Activity> getPasscodeActivityClass();
    
    /**
     * @return account type (should match authenticator.xml)
     */
    public abstract String getAccountType();
}
