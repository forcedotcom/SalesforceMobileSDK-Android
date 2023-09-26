/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.manager

import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.mobilesync.app.Features
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.model.Layout
import com.salesforce.androidsdk.mobilesync.target.LayoutSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartStore

/**
 * Provides an easy way to fetch layout data using [com.salesforce.androidsdk.mobilesync.target.LayoutSyncDownTarget].
 * This class handles creating a soup, storing synched data and reading it into
 * a meaningful data structure, i.e. [com.salesforce.androidsdk.mobilesync.model.Layout].
 *
 * @author bhariharan
 */
class LayoutSyncManager private constructor(
    /**
     * Returns the SmartStore instance used by this instance of LayoutSyncManager.
     *
     * @return SmartStore instance.
     */
    val smartStore: SmartStore,
    /**
     * Returns the SyncManager instance used by this instance of LayoutSyncManager.
     *
     * @return SyncManager instance.
     */
    val syncManager: SyncManager
) {

    /**
     * Fetches layout data for the specified parameters using the specified sync mode
     * and triggers the supplied callback once complete.
     *
     * @param objectAPIName Object API name.
     * @param formFactor Form factor. Could be "Large", "Medium" or "Small". Default value is "Large".
     * @param layoutType Layout type. Could be "Compact" or "Full". Default value is "Full".
     * @param mode Mode. Could be "Create", "Edit" or "View". Default value is "View".
     * @param recordTypeId Record type ID. Default will be used if not supplied.
     * @param syncMode Sync fetch mode. See [com.salesforce.androidsdk.mobilesync.util.Constants.Mode] for available modes.
     * @param syncCallback Layout sync callback.
     */
    fun fetchLayout(
        objectAPIName: String, formFactor: String, layoutType: String, mode: String,
        recordTypeId: String?, syncMode: Constants.Mode, syncCallback: LayoutSyncCallback
    ) {
        when (syncMode) {
            Constants.Mode.CACHE_ONLY -> fetchFromCache(
                objectAPIName,
                formFactor,
                layoutType,
                mode,
                recordTypeId,
                syncCallback,
                false
            )

            Constants.Mode.CACHE_FIRST -> fetchFromCache(
                objectAPIName,
                formFactor,
                layoutType,
                mode,
                recordTypeId,
                syncCallback,
                true
            )

            Constants.Mode.SERVER_FIRST -> fetchFromServer(
                objectAPIName,
                formFactor,
                layoutType,
                mode,
                recordTypeId,
                syncCallback
            )
        }
    }

    init {
        initializeSoup()
    }

    private fun fetchFromServer(
        objectAPIName: String, formFactor: String,
        layoutType: String, mode: String,
        recordTypeId: String?, syncCallback: LayoutSyncCallback
    ) {
        val target: SyncDownTarget =
            LayoutSyncDownTarget(objectAPIName, formFactor, layoutType, mode, recordTypeId)
        val options: SyncOptions = SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE)
        try {
            syncManager.syncDown(
                target,
                options,
                SOUP_NAME,
                object : SyncManager.SyncUpdateCallback {
                    override fun onUpdate(sync: SyncState) {
                        if (SyncState.Status.DONE == sync.status) {
                            fetchFromCache(
                                objectAPIName, formFactor, layoutType, mode, recordTypeId,
                                syncCallback, false
                            )
                        }
                    }
                })
        } catch (e: Exception) {
            MobileSyncLogger.e(
                TAG,
                "Exception occurred while reading layout data from the server",
                e
            )
        }
    }

    private fun fetchFromCache(
        objectAPIName: String, formFactor: String, layoutType: String, mode: String,
        recordTypeId: String?, syncCallback: LayoutSyncCallback, fallbackOnServer: Boolean
    ) {
        val querySpec = QuerySpec.buildSmartQuerySpec(
            String.format(
                QUERY,
                objectAPIName, formFactor, layoutType, mode, recordTypeId
            ), 1
        )
        try {
            val results = smartStore.query(querySpec, 0)
            if ((results == null) || (results.length() == 0)) {
                if (fallbackOnServer) {
                    fetchFromServer(
                        objectAPIName,
                        formFactor,
                        layoutType,
                        mode,
                        recordTypeId,
                        syncCallback
                    )
                } else {
                    onSyncComplete(
                        objectAPIName,
                        formFactor,
                        layoutType,
                        mode,
                        recordTypeId,
                        syncCallback,
                        null
                    )
                }
            } else {
                onSyncComplete(
                    objectAPIName, formFactor, layoutType, mode, recordTypeId, syncCallback,
                    Layout.fromJSON(results.optJSONArray(0).optJSONObject(0))
                )
            }
        } catch (e: Exception) {
            MobileSyncLogger.e(
                TAG,
                "Exception occurred while reading layout data from the cache",
                e
            )
        }
    }

    private fun onSyncComplete(
        objectAPIName: String, formFactor: String, layoutType: String, mode: String,
        recordTypeId: String?, syncCallback: LayoutSyncCallback?, layout: Layout?
    ) {
        syncCallback?.onSyncComplete(
            objectAPIName,
            formFactor,
            layoutType,
            mode,
            recordTypeId,
            layout
        )
    }

    private fun initializeSoup() {
        if (!smartStore.hasSoup(SOUP_NAME)) {
            smartStore.registerSoup(SOUP_NAME, INDEX_SPECS)
        }
    }

    /**
     * Callback interface for layout sync.
     *
     * @author bhariharan
     */
    interface LayoutSyncCallback {
        /**
         * Callback triggered when layout sync completes.
         *
         * @param objectAPIName Object API name.
         * @param formFactor Form factor.
         * @param layoutType Layout type.
         * @param mode Mode.
         * @param recordTypeId Record type ID.
         * @param layout Layout.
         */
        fun onSyncComplete(
            objectAPIName: String?, formFactor: String?, layoutType: String?,
            mode: String?, recordTypeId: String?, layout: Layout?
        )
    }

    companion object {
        const val SOUP_NAME = "sfdcLayouts"
        const val QUERY =
            "SELECT {$SOUP_NAME:_soup} FROM {$SOUP_NAME} WHERE {$SOUP_NAME:${Constants.ID}} = '%s-%s-%s-%s-%s'"
        private const val TAG = "LayoutSyncManager"
        private val INDEX_SPECS = arrayOf(
            IndexSpec(Constants.ID, SmartStore.Type.json1)
        )
        private val INSTANCES = HashMap<String, LayoutSyncManager>()

        /**
         * Returns the instance of this class associated with current user.
         *
         * @return Instance of this class.
         */
        @Synchronized
        @JvmStatic
        fun getInstance(): LayoutSyncManager {
            return getInstance(null, null)
        }

        /**
         * Returns the instance of this class associated with this user account.
         *
         * @param account User account.
         * @return Instance of this class.
         */
        @Synchronized
        fun getInstance(account: UserAccount?): LayoutSyncManager {
            return getInstance(account, null)
        }

        /**
         * Returns the instance of this class associated with this user and community.
         *
         * @param account User account.
         * @param communityId Community ID.
         * @return Instance of this class.
         */
        @Synchronized
        fun getInstance(account: UserAccount?, communityId: String?): LayoutSyncManager {
            return getInstance(account, communityId, null)
        }

        /**
         * Returns the instance of this class associated with this user, community and SmartStore.
         *
         * @param account User account. Pass null to use current user.
         * @param communityId Community ID. Pass null if not applicable.
         * @param smartStore SmartStore instance. Pass null to use current user default SmartStore.
         * @return Instance of this class.
         */
        @Synchronized
        fun getInstance(
            account: UserAccount?, communityId: String?,
            smartStore: SmartStore?
        ): LayoutSyncManager {
            val user =
                account ?: MobileSyncSDKManager.getInstance().userAccountManager.cachedCurrentUser
            val store =
                smartStore ?: MobileSyncSDKManager.getInstance().getSmartStore(user, communityId)
            val syncManager: SyncManager =
                SyncManager.getInstance(user, communityId, store)
            val uniqueId = ((if (user != null) user.userId else "") + ":"
                    + store.database.path)
            val instance = INSTANCES[uniqueId] ?: LayoutSyncManager(
                store,
                syncManager
            ).also { INSTANCES[uniqueId] = it }
            SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_LAYOUT_SYNC)
            return instance
        }

        /**
         * Resets all the layout sync managers.
         */
        @Synchronized
        @JvmStatic
        fun reset() {
            INSTANCES.clear()
        }

        /**
         * Resets the layout sync manager for this user account.
         *
         * @param account User account.
         */
        @Synchronized
        fun reset(account: UserAccount?) {
            if (account != null) {
                val keysToRemove: MutableSet<String> = HashSet()
                for (key in INSTANCES.keys) {
                    if (key.startsWith(account.userId)) {
                        keysToRemove.add(key)
                    }
                }
                INSTANCES.keys.removeAll(keysToRemove)
            }
        }
    }
}