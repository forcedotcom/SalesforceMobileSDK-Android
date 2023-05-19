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
import androidx.work.ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
import androidx.work.ListenableWorker.Result.success
import androidx.work.PeriodicWorkRequest.Builder
import androidx.work.WorkManager.getInstance
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.analytics.AnalyticsPublishingWorker.Companion.reEnqueueAnalyticsPublishPeriodicWorkRequest
import java.util.concurrent.TimeUnit.HOURS

/**
 * An Android background tasks worker which publishes stored analytics.
 * This class is intended to be instantiated by the background tasks work
 * manager.
 *
 * Use [reEnqueueAnalyticsPublishPeriodicWorkRequest] to enqueue a analytics
 * publish periodic work request.  Previous requests will be cancelled.
 *
 * @param context The Android context provided by the work manager
 * @param workerParams The worker parameters provided by the work manager
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

        private const val publishAnalyticsPeriodicWorkName = "SalesforceAnalyticsPublishingPeriodicWork"

        /**
         * Enqueues a persistent background tasks periodic work request to
         * publish stored analytics for the current user at the provided
         * interval.
         *
         * If a work request is already queued, it will be cancelled before
         * the replacement is enqueued.
         *
         * @param context The Android context
         * @param publishHoursInterval The interval at which to publish in hours
         * @return UUID The worker's unique id, which may be used for
         * cancellation
         */
        fun reEnqueueAnalyticsPublishPeriodicWorkRequest(
            context: Context,
            publishHoursInterval: Long
        ) = Builder(
            AnalyticsPublishingWorker::class.java,
            publishHoursInterval,
            HOURS
        ).build().also { publishAnalyticsPeriodicWorkRequest ->
            runCatching {
                getInstance(context)
            }.getOrNull()?.enqueueUniquePeriodicWork(
                publishAnalyticsPeriodicWorkName,
                CANCEL_AND_REENQUEUE,
                publishAnalyticsPeriodicWorkRequest
            )
        }.id
    }
}
