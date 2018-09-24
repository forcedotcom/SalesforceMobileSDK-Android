/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.push;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.support.v4.app.JobIntentService;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

public class SFDCRegistrationIntentService extends JobIntentService {

    private static final String TAG = "RegIntentService";
    private static final String FCM = "FCM";

    @Override
    protected void onHandleWork(Intent intent) {
        try {

            /*
             * Initializes the push configuration for this application and kicks off
             * Firebase initialization flow. This is required before attempting to
             * register for FCM. The other alternative is to supply a 'google-services.json'
             * file and use the Google Services plugin to initialize, but this approach
             * only works for apps. Since we're a library project, the programmatic
             * approach works better for us.
             */
            final Context context = SalesforceSDKManager.getInstance().getAppContext();
            final String pushClientId = BootConfig.getBootConfig(context).getPushNotificationClientId();
            final FirebaseOptions firebaseOptions = new FirebaseOptions.Builder().
                    setGcmSenderId(pushClientId).setApplicationId(context.getPackageName()).build();

            // Fetches the app's unique name to supply to Firebase.
            String appName = FirebaseApp.DEFAULT_APP_NAME;
            try {
                final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                appName = context.getString(packageInfo.applicationInfo.labelRes);
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
                SalesforceSDKLogger.w(TAG, "Package info could not be retrieved", e);
            }

            /*
             * Ensures that Firebase initialization occurs only once for this app. If an exception
             * isn't thrown, this means that the initialization has already been completed.
             */
            try {
                FirebaseApp.getInstance(appName);
            } catch (IllegalStateException e) {
                SalesforceSDKLogger.w(TAG, "Firebase hasn't been initialized yet", e);
                FirebaseApp.initializeApp(context, firebaseOptions, appName);
            }

            // Fetches an instance ID from Firebase once the initialization is complete.
            final FirebaseInstanceId instanceID = FirebaseInstanceId.getInstance(FirebaseApp.getInstance(appName));
            final String token = instanceID.getToken(BootConfig.getBootConfig(this).getPushNotificationClientId(), FCM);
            final UserAccount account = SalesforceSDKManager.getInstance().getUserAccountManager().getCurrentUser();

            // Store the new token.
            PushMessaging.setRegistrationId(this, token, account);

            // Send it to SFDC.
            PushMessaging.registerSFDCPush(this, account);
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Error during FCM registration", e);
        }
    }
}
