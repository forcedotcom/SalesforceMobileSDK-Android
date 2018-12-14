/*
 * Copyright (c) 2016-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.analytics;

import android.content.Context;
import android.content.Intent;
import androidx.core.app.JobIntentService;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;

/**
 * A service that publishes stored data when an intent is triggered.
 *
 * @author bhariharan
 */
public class AnalyticsPublisherService extends JobIntentService {

    private static final String ACTION_PUBLISH = "com.salesforce.androidsdk.analytics.action.ANALYTICS_PUBLISH";
    private static final int JOB_ID = 81;

    /**
     * Starts this service to publish stored events. If the service is already
     * performing a task this action will be queued.
     *
     * @param context Context.
     */
    public static void startActionPublish(Context context) {
        final Intent intent = new Intent(context, AnalyticsPublisherService.class);
        intent.setAction(ACTION_PUBLISH);
        enqueueWork(context, AnalyticsPublisherService.class, JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_PUBLISH.equals(action)) {
                handleActionPublish();
            }
        }
    }

    /**
     * Handles the publish action in the provided background thread.
     */
    private void handleActionPublish() {
        final UserAccount userAccount = UserAccountManager.getInstance().getCachedCurrentUser();
        if (userAccount != null) {
            final SalesforceAnalyticsManager analyticsManager = SalesforceAnalyticsManager.getInstance(userAccount);
            analyticsManager.publishAllEvents();
        }
    }
}
