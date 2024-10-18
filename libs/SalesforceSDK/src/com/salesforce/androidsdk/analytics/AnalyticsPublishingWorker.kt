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
package com.salesforce.androidsdk.analytics

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
import androidx.work.ExistingWorkPolicy.REPLACE
import androidx.work.ListenableWorker.Result.success
import androidx.work.NetworkType.CONNECTED
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager.getInstance
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.analytics.AnalyticsPublishingWorker.Companion.enqueueAnalyticsPublishWorkRequest
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager.SalesforceAnalyticsPublishingType.PublishDisabled
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager.SalesforceAnalyticsPublishingType.PublishOnAppBackground
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager.SalesforceAnalyticsPublishingType.PublishPeriodically
import java.util.UUID
import java.util.concurrent.TimeUnit.HOURS

/**
 * An Android background tasks worker which publishes stored analytics.
 * This class is intended to be instantiated by the background tasks work
 * manager.
 *
 * [enqueueAnalyticsPublishWorkRequest] is used internally by the Salesforce
 * Mobile SDK to enqueue analytics publish work requests to match the current
 * analytics publishing configuration. Only one request may be active at a time.
 * Only the Salesforce Mobile SDK should call this method as it is not intended
 * for public use.
 *
 * @param context The Android context provided by the work manager
 * @param workerParams The worker parameters provided by the work manager
 * @see [SalesforceAnalyticsManager.analyticsPublishingType]
 * @see <a href='https://developer.android.com/guide/background'>Android
 * Background Tasks</a>
 */
internal class AnalyticsPublishingWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(
    context,
    workerParams
) {

    override fun doWork() =

        // Publish all stored analytics for the current user.
        UserAccountManager
            .getInstance()
            .cachedCurrentUser
            .let { userAccount ->

                SalesforceAnalyticsManager
                    .getInstance(userAccount ?: return@let null)
                    .publishAllEvents()
            }.run { success() /* Though not having a user cancels analytics publishing, it's not a failure. */ }

    companion object {

        /**
         * The Android background tasks name of the publish analytics work
         * request.
         * Note: This name is also used for one-time work, yet maintains this
         * value for backwards compatibility and to ensure both periodic and one
         * time work are mutually exclusive.
         */
        private const val PUBLISH_ANALYTICS_WORK_NAME = "SalesforceAnalyticsPublishingPeriodicWork"

        /**
         * Enqueues a persistent background tasks work request to publish stored
         * analytics for the current user.
         *
         * If a work request is already queued, it will be cancelled before
         * the replacement is enqueued.
         *
         * Note, background periodic publishing starts or resumes the host app
         * and may incur licensing costs if authorization token refresh is
         * required.
         *
         * Only the Salesforce Mobile SDK should call this method as it is not
         * intended for public use.
         *
         * @param context The Android context
         * @param periodicBackgroundPublishingHoursInterval The interval for
         * periodic background publishing in hours
         * @return UUID The worker's unique id, which may be used for
         * cancellation
         */
        fun enqueueAnalyticsPublishWorkRequest(
            context: Context,
            periodicBackgroundPublishingHoursInterval: Long = SalesforceAnalyticsManager.getPublishPeriodicallyFrequencyHours().toLong()
        ): UUID? = when (SalesforceAnalyticsManager.analyticsPublishingType()) {

            PublishDisabled -> null

            PublishOnAppBackground -> OneTimeWorkRequest.Builder(
                AnalyticsPublishingWorker::class.java
            ).setConstraints(
                Constraints.Builder().setRequiredNetworkType(CONNECTED).build()
            ).build().also { publishAnalyticsOneTimeWorkRequest ->
                runCatching {
                    getInstance(context)
                }.getOrNull()?.enqueueUniqueWork(
                    PUBLISH_ANALYTICS_WORK_NAME,
                    REPLACE,
                    publishAnalyticsOneTimeWorkRequest
                )
            }.id

            PublishPeriodically -> PeriodicWorkRequest.Builder(
                AnalyticsPublishingWorker::class.java,
                periodicBackgroundPublishingHoursInterval,
                HOURS
            ).setConstraints(
                Constraints.Builder().setRequiredNetworkType(CONNECTED).build()
            ).build().also { publishAnalyticsPeriodicWorkRequest ->
                runCatching {
                    getInstance(context)
                }.getOrNull()?.enqueueUniquePeriodicWork(
                    PUBLISH_ANALYTICS_WORK_NAME,
                    CANCEL_AND_REENQUEUE,
                    publishAnalyticsPeriodicWorkRequest
                )
            }.id
        }
    }
}
