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
package com.salesforce.androidsdk.ui;

import android.app.Activity;
import android.content.IntentFilter;
import android.os.Bundle;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.TokenRevocationReceiver;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Native application main activity should extend this activity or duplicate its functionality
 */
public abstract class NativeMainActivity extends Activity {

	private PasscodeManager passcodeManager;
    private TokenRevocationReceiver tokenRevocationReceiver;
	
    /**************************************************************************************************
    *
    * Abstract methods: to be implemented by subclass
    *
    **************************************************************************************************/

	/**
	 * @return LoginOptions to use for this application
	 */
	protected abstract LoginOptions getLoginOptions();

	/**
	 * Method is called after the activity resumes once we have a RestClient
	 * 
	 * @param client
	 */
	protected abstract void onResume(RestClient client);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Passcode manager
		passcodeManager = ForceApp.APP.getPasscodeManager();
		tokenRevocationReceiver = new TokenRevocationReceiver(this);

		// Let observers know
		EventsObservable.get().notifyEvent(EventType.MainActivityCreateComplete, this);
	}
	
	@Override 
	public void onResume() {
		super.onResume();
		registerReceiver(tokenRevocationReceiver, new IntentFilter(ClientManager.ACCESS_TOKEN_REVOKE_INTENT));

		// Bring up passcode screen if needed
		if (passcodeManager.onResume(this)) {
		
			// Login options
			String accountType = ForceApp.APP.getAccountType();
	    	LoginOptions loginOptions = getLoginOptions();
			
			// Get a rest client
			new ClientManager(this, accountType, loginOptions, ForceApp.APP.shouldLogoutWhenTokenRevoked()).getRestClient(this, new RestClientCallback() {

				@Override
				public void authenticatedRestClient(RestClient client) {
					if (client == null) {
						ForceApp.APP.logout(NativeMainActivity.this);
						return;
					}

					onResume(client);
					// Let observers know
					EventsObservable.get().notifyEvent(EventType.RenditionComplete);
				}
			});
		}
	}

	@Override
	public void onUserInteraction() {
		passcodeManager.recordUserInteraction();
	}
	
    @Override
    public void onPause() {
        super.onPause();
    	passcodeManager.onPause(this);
    	unregisterReceiver(tokenRevocationReceiver);
    }
}
