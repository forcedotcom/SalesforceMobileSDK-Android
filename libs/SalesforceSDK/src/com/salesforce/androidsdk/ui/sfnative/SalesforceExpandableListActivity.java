/*
 * Copyright (c) 2013, salesforce.com, inc.
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
package com.salesforce.androidsdk.ui.sfnative;

import android.app.ExpandableListActivity;
import android.content.IntentFilter;
import android.os.Bundle;

import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.UserSwitchReceiver;

/**
 * Abstract base class for all Salesforce expandable list activities.
 *
 * @author bhariharan
 */
public abstract class SalesforceExpandableListActivity extends ExpandableListActivity {

	private PasscodeManager passcodeManager;
    private UserSwitchReceiver userSwitchReceiver;

	/**
	 * Method that is called after the activity resumes once we have a RestClient.
	 *
	 * @param client RestClient instance.
	 */
	public abstract void onResume(RestClient client);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Gets an instance of the passcode manager.
		passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();
        userSwitchReceiver = new ActivityUserSwitchReceiver();
        registerReceiver(userSwitchReceiver, new IntentFilter(UserAccountManager.USER_SWITCH_INTENT_ACTION));

		// Lets observers know that activity creation is complete.
		EventsObservable.get().notifyEvent(EventType.MainActivityCreateComplete, this);
	}

	@Override 
	public void onResume() {
		super.onResume();

		// Brings up the passcode screen if needed.
		if (passcodeManager.onResume(this)) {

			// Gets login options.
			final String accountType = SalesforceSDKManager.getInstance().getAccountType();
	    	final LoginOptions loginOptions = SalesforceSDKManager.getInstance().getLoginOptions();

			// Gets a rest client.
			new ClientManager(this, accountType, loginOptions, SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked()).getRestClient(this, new RestClientCallback() {

				@Override
				public void authenticatedRestClient(RestClient client) {
					if (client == null) {
						SalesforceSDKManager.getInstance().logout(SalesforceExpandableListActivity.this);
						return;
					}
					onResume(client);

					// Lets observers know that rendition is complete.
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
    }

    @Override
    public void onDestroy() {
    	unregisterReceiver(userSwitchReceiver);
    	super.onDestroy();
    }

    /**
     * Refreshes the client if the user has been switched.
     */
	protected void refreshIfUserSwitched() {
		if (passcodeManager.onResume(this)) {

			// Gets login options.
			final String accountType = SalesforceSDKManager.getInstance().getAccountType();
	    	final LoginOptions loginOptions = SalesforceSDKManager.getInstance().getLoginOptions();

			// Gets a rest client.
			new ClientManager(this, accountType, loginOptions,
					SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked()).getRestClient(this, new RestClientCallback() {

				@Override
				public void authenticatedRestClient(RestClient client) {
					if (client == null) {
						SalesforceSDKManager.getInstance().logout(SalesforceExpandableListActivity.this);
						return;
					}
					onResume(client);

					// Lets observers know that rendition is complete.
					EventsObservable.get().notifyEvent(EventType.RenditionComplete);
				}
			});
		}
	}

    /**
     * Acts on the user switch event.
     *
     * @author bhariharan
     */
    private class ActivityUserSwitchReceiver extends UserSwitchReceiver {

		@Override
		protected void onUserSwitch() {
			refreshIfUserSwitched();
		}
    }
}
