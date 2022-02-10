/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.samples.restexplorer;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.ui.LoginActivity;

import java.util.LinkedHashMap;

/**
 * Application class for the rest explorer app.
 */
public class RestExplorerApp extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		/*
		 * Extend SalesforceSDKManager to add custom dev menu options to the app.
		 *
		 * Normal use would be: SalesforceSDKManager.initNative(getApplicationContext(), ExplorerActivity.class);
		 */
		RestExplorerSDKManager.initNative(getApplicationContext(), ExplorerActivity.class);

		/*
         * Uncomment the following line to enable IDP login flow. This will allow the user to
         * either authenticate using the current app or use a designated IDP app for login.
         * Replace 'idpAppURIScheme' with the URI scheme of the IDP app meant to be used.
         */
        // SalesforceSDKManager.getInstance().setIDPAppURIScheme(idpAppURIScheme);

		/*
		 * Un-comment the line below to enable push notifications in this app.
		 * Replace 'pnInterface' with your implementation of 'PushNotificationInterface'.
		 * Add your Google package ID in 'bootconfig.xml', as the value
		 * for the key 'androidPushNotificationClientId'.
		 */
		// SalesforceSDKManager.getInstance().setPushNotificationReceiver(pnInterface);
	}

	static class RestExplorerSDKManager extends SalesforceSDKManager {
		LinkedHashMap<String, DevActionHandler> devActions;

		/**
		 * Protected constructor.
		 *
		 * @param context       Application context.
		 * @param mainActivity  Activity that should be launched after the login flow.
		 * @param loginActivity Login activity.
		 */
		private RestExplorerSDKManager(Context context, Class<? extends Activity> mainActivity, Class<? extends Activity> loginActivity) {
			super(context, mainActivity, loginActivity);
		}

		/**
		 * Initializes required components. Native apps must call one overload of
		 * this method before using the Salesforce Mobile SDK.
		 *
		 * @param context Application context.
		 * @param mainActivity Activity that should be launched after the login flow.
		 */
		public static void initNative(Context context, Class<? extends Activity> mainActivity) {
			if (SalesforceSDKManager.INSTANCE == null) {
				SalesforceSDKManager.INSTANCE = new RestExplorerSDKManager(context, mainActivity, LoginActivity.class);
			}
			initInternal(context);
		}

		@Override
		public LinkedHashMap<String, DevActionHandler> getDevActions(Activity frontActivity) {
			if (devActions == null) {
				devActions = super.getDevActions(frontActivity);
			}

			return devActions;
		}

		public void addDevAction(Activity frontActivity, String name, DevActionHandler handler) {
			if (devActions == null) {
				devActions = super.getDevActions(frontActivity);
			}

			devActions.put(name, handler);
		}
	}
}
