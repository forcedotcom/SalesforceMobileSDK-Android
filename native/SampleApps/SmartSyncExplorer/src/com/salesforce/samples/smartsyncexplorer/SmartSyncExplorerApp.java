/*
 * Copyright (c) 2014, salesforce.com, inc.
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
package com.salesforce.samples.smartsyncexplorer;

import java.util.List;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.RestrictionEntry;
import android.content.RestrictionsManager;

import com.salesforce.androidsdk.app.SalesforceSDKManager.KeyInterface;
import com.salesforce.androidsdk.auth.LoginServerManager;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.app.SmartSyncUpgradeManager;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.ui.sfhybrid.SalesforceDroidGapActivity;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.samples.smartsyncexplorer.ui.MainActivity;

/**
 * Application class for our application.
 */
public class SmartSyncExplorerApp extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		SDKManagerUsingWorkProfile.initNative(getApplicationContext(), new KeyImpl(),
				MainActivity.class);

		/*
		 * Un-comment the line below to enable push notifications in this app.
		 * Replace 'pnInterface' with your implementation of 'PushNotificationInterface'.
		 * Add your Google package ID in 'bootonfig.xml', as the value
		 * for the key 'androidPushNotificationClientId'.
		 */
		// SmartSyncSDKManager.getInstance().setPushNotificationReceiver(pnInterface);
	}
	
	
	public static class SDKManagerUsingWorkProfile extends SmartSyncSDKManager {

	    private LoginServerManagerUsingWorkProfile loginServerManagerUsingWorkProfile;
		
		protected SDKManagerUsingWorkProfile(Context context, KeyInterface keyImpl,
				Class<? extends Activity> mainActivity,
				Class<? extends Activity> loginActivity) {
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
	    		INSTANCE = new SDKManagerUsingWorkProfile(context, keyImpl, mainActivity, loginActivity);
	    	}
			initInternal(context);

	        // Upgrade to the latest version.
	        SmartSyncUpgradeManager.getInstance().upgradeSObject();
	        EventsObservable.get().notifyEvent(EventType.AppCreateComplete);
		}

	    public static void initHybrid(Context context, KeyInterface keyImpl) {
	    	SDKManagerUsingWorkProfile.init(context, keyImpl, SalesforceDroidGapActivity.class,
	    			LoginActivity.class);
	    }

	    public static void initHybrid(Context context, KeyInterface keyImpl,
	    		Class<? extends Activity> loginActivity) {
	    	SDKManagerUsingWorkProfile.init(context, keyImpl, SalesforceDroidGapActivity.class,
	    			loginActivity);
	    }

	    public static void initHybrid(Context context, KeyInterface keyImpl,
	    		Class<? extends SalesforceDroidGapActivity> mainActivity,
	    		Class<? extends Activity> loginActivity) {
	    	SDKManagerUsingWorkProfile.init(context, keyImpl, mainActivity, loginActivity);
	    }
	    
	    public static void initNative(Context context, KeyInterface keyImpl,
	    		Class<? extends Activity> mainActivity) {
	    	SDKManagerUsingWorkProfile.init(context, keyImpl, mainActivity, LoginActivity.class);
	    }

	    public static void initNative(Context context, KeyInterface keyImpl,
	    		Class<? extends Activity> mainActivity, Class<? extends Activity> loginActivity) {
	    	SDKManagerUsingWorkProfile.init(context, keyImpl, mainActivity, loginActivity);
	    }		

		@Override
		public synchronized LoginServerManager getLoginServerManager() {
	        if (loginServerManagerUsingWorkProfile == null) {
	        	loginServerManagerUsingWorkProfile = new LoginServerManagerUsingWorkProfile(context);
	        }
	        return loginServerManagerUsingWorkProfile;
		}
	}
	
	public static class LoginServerManagerUsingWorkProfile extends LoginServerManager {

		public LoginServerManagerUsingWorkProfile(Context ctx) {
			super(ctx);
			
			String loginHostFromRestrictions = getLoginHostFromRestrictions(ctx);;
			if (loginHostFromRestrictions != null) {
				setSelectedLoginServer(getLoginServerFromURL(loginHostFromRestrictions));
			}
		}

		private String getLoginHostFromRestrictions(Context ctx) {
			RestrictionsManager restrictionsManager =
	                (RestrictionsManager) ctx.getSystemService(Context.RESTRICTIONS_SERVICE);
	        List<RestrictionEntry> restrictions =
	                restrictionsManager.getManifestRestrictions("com.salesforce.samples.smartsyncexplorer");
	        if (restrictions != null) {
		        for (RestrictionEntry restriction : restrictions) {
		        	// XXX hard-coded constant
		            if ("loginHost".equals(restriction.getKey())) {
		                return restriction.getSelectedString();
		            }
		        }
	        }			
	        
	        return null;
		}
		
		
	}
}


