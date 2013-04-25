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

import android.app.Activity;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Helper class for common functions performed by Salesforce activities.
 *
 * @author bhariharan
 */
public class SalesforceActivityHelper {

	/**
	 * Initializes rest client and calls the activity's onResume() method
	 * upon successful completion.
	 *
	 * @param activity Activity.
	 */
	public static void initializeRestClient(final Activity activity) {

		// Gets login options.
		final String accountType = SalesforceSDKManager.getInstance().getAccountType();
    	final LoginOptions loginOptions = SalesforceSDKManager.getInstance().getLoginOptions();

		// Gets a rest client.
		new ClientManager(activity, accountType, loginOptions, SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked()).getRestClient(activity, new RestClientCallback() {

			@Override
			public void authenticatedRestClient(RestClient client) {
				if (client == null) {
					SalesforceSDKManager.getInstance().logout(activity);
					return;
				}
				activity.onResume(client);

				// Lets observers know that rendition is complete.
				EventsObservable.get().notifyEvent(EventType.RenditionComplete);
			}
		});
	}
}
