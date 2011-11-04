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

import android.app.Application;
import android.content.Intent;

import com.salesforce.androidsdk.auth.AbstractLoginActivity;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.security.Encryptor;

/**
 * Super class for all force applications.
 * You should extend this class or make sure to initialize HttpAccess in your application's onCreate method.
 */
public abstract class ForceApp extends Application  {

	// instance of the ForceApp for this process
    public static ForceApp APP;
    
    @Override
    public void onCreate() {
        super.onCreate();
        APP = this;

        // Initialize encryption module
        Encryptor.init(this);
        
        // Initialize the http client        
        HttpAccess.init(this);
    }

    /**
     * Remove user account and launch the login activity with a clean task stack.
     */
    public void logout(String accountType) {
    	new ClientManager(this, accountType).removeAccountAsync(null);
    	Intent i = new Intent(this, getLoginActivityClass());
    	i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	this.startActivity(i);
    }
    
    /**
     * @return class for login activity
     */
    abstract public Class<? extends AbstractLoginActivity> getLoginActivityClass();
    
    /**
     * Return key to encrypt auth tokens when storing them in the acccount manager.
     * Return null when no encryption should be used.
     * 
     * If the device has filesystem encryption turned on, you can return null, and let the tokens be stored in clear.
     * If the device doesn't have filesystem encryption, and you return null, the login flow will fail when trying to 
     * create an account.
     *
     * FIXME should be abstract
     * 
	 * @return 
     */
    public String getPasscodeHash() {
    	if (Encryptor.isFileSystemEncrypted()) {
    		return null;
    	}
    	else {
    		return "q21opx09asd1!sad9p-=2#";
    	}
    }
    
}
