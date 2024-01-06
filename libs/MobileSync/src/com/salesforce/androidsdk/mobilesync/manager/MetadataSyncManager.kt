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
import com.salesforce.androidsdk.mobilesync.model.Metadata
import com.salesforce.androidsdk.mobilesync.target.MetadataSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.Constants.Mode.CACHE_FIRST
import com.salesforce.androidsdk.mobilesync.util.Constants.Mode.CACHE_ONLY
import com.salesforce.androidsdk.mobilesync.util.Constants.Mode.SERVER_FIRST
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartStore

/**
 * Provides an easy way to fetch metadata using [com.salesforce.androidsdk.mobilesync.target.MetadataSyncDownTarget].
 * This class handles creating a soup, storing synched data and reading it into
 * a meaningful data structure, i.e. [com.salesforce.androidsdk.mobilesync.model.Metadata].
 *
 * @author bhariharan
 */
class MetadataSyncManager private constructor(
    /**
     * Returns the SmartStore instance used by this instance of MetadataSyncManager.
     *
     * @return SmartStore instance.
     */
    val smartStore: SmartStore,
    /**
     * Returns the SyncManager instance used by this instance of MetadataSyncManager.
     *
     * @return SyncManager instance.
     */
    val syncManager: SyncManager
) {

    /**
     * Fetches metadata for the specified object type using the specified
     * mode and triggers the supplied callback once complete.
     *
     * @param objectType Object type.
     * @param mode Fetch mode. See [com.salesforce.androidsdk.mobilesync.util.Constants.Mode] for available modes.
     * @param syncCallback Metadata sync callback.
     */
    fun fetchMetadata(
        objectType: String, mode: Constants.Mode,
        syncCallback: MetadataSyncCallback
    ) {
        when (mode) {
            CACHE_ONLY -> fetchFromCache(objectType, syncCallback, false)
            CACHE_FIRST -> fetchFromCache(objectType, syncCallback, true)
            SERVER_FIRST -> fetchFromServer(objectType, syncCallback)
        }
    }

    init {
        initializeSoup()
    }

    private fun fetchFromServer(objectType: String, syncCallback: MetadataSyncCallback) {
        val target: SyncDownTarget = MetadataSyncDownTarget(objectType)
        val options: SyncOptions = SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE)
        try {
            syncManager.syncDown(
                target,
                options,
                SOUP_NAME,
                object : SyncManager.SyncUpdateCallback {
                    override fun onUpdate(sync: SyncState) {
                        if (SyncState.Status.DONE == sync.status) {
                            fetchFromCache(objectType, syncCallback, false)
                        }
                    }
                })
        } catch (e: Exception) {
            MobileSyncLogger.e(TAG, "Exception occurred while reading metadata from the server", e)
        }
    }

    private fun fetchFromCache(
        objectType: String, syncCallback: MetadataSyncCallback,
        fallbackOnServer: Boolean
    ) {
        val querySpec = QuerySpec.buildSmartQuerySpec(
            String.format(
                QUERY,
                objectType
            ), 1
        )
        try {
            val results = smartStore.query(querySpec, 0)
            when {
                results == null || results.length() == 0 -> {
                    if (fallbackOnServer) {
                        fetchFromServer(objectType, syncCallback)
                    } else {
                        onSyncComplete(syncCallback, null)
                    }
                }

                else -> {
                    onSyncComplete(
                        syncCallback,
                        Metadata.fromJSON(results.optJSONArray(0).optJSONObject(0))
                    )
                }
            }
        } catch (e: Exception) {
            MobileSyncLogger.e(TAG, "Exception occurred while reading metadata from the cache", e)
        }
    }

    private fun onSyncComplete(syncCallback: MetadataSyncCallback?, metadata: Metadata?) {
        syncCallback?.onSyncComplete(metadata)
    }

    private fun initializeSoup() {
        if (!smartStore.hasSoup(SOUP_NAME)) {
            smartStore.registerSoup(SOUP_NAME, INDEX_SPECS)
        }
    }

    /**
     * Callback interface for metadata sync.
     *
     * @author bhariharan
     */
    interface MetadataSyncCallback {
        /**
         * Callback triggered when metadata sync completes.
         *
         * @param metadata Metadata.
         */
        fun onSyncComplete(metadata: Metadata?)
    }

    companion object {
        const val SOUP_NAME = "sfdcMetadata"
        const val QUERY =
            "SELECT {$SOUP_NAME:_soup} FROM {$SOUP_NAME} WHERE {$SOUP_NAME:${Constants.ID}} = '%s'"
        private const val TAG = "MetadataSyncManager"
        private val INDEX_SPECS = arrayOf(
            IndexSpec(Constants.ID, SmartStore.Type.json1)
        )
        private val INSTANCES: MutableMap<String, MetadataSyncManager> = HashMap()

        /**
         * Returns the instance of this class associated with current user.
         *
         * @return Instance of this class.
         */
        @Synchronized
        @JvmStatic
        fun getInstance(): MetadataSyncManager {
            return getInstance(null, null)
        }

        /**
         * Returns the instance of this class associated with this user account.
         *
         * @param account User account.
         * @return Instance of this class.
         */
        @Synchronized
        fun getInstance(account: UserAccount?): MetadataSyncManager {
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
        fun getInstance(account: UserAccount?, communityId: String?): MetadataSyncManager {
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
        ): MetadataSyncManager {
            val user =
                account ?: MobileSyncSDKManager.getInstance().userAccountManager.cachedCurrentUser
            val store =
                smartStore ?: MobileSyncSDKManager.getInstance().getSmartStore(user, communityId)
            val syncManager: SyncManager =
                SyncManager.getInstance(user, communityId, store)
            val uniqueId = ((if (user != null) user.userId else "") + ":"
                    + store.database.path)
            val instance = INSTANCES[uniqueId] ?: MetadataSyncManager(store, syncManager).also {
                INSTANCES[uniqueId] = it
            }
            SalesforceSDKManager.getInstance()
                .registerUsedAppFeature(Features.FEATURE_METADATA_SYNC)
            return instance
        }

        /**
         * Resets all the metadata sync managers.
         */
        @Synchronized
        @JvmStatic
        fun reset() {
            INSTANCES.clear()
        }

        /**
         * Resets the metadata sync manager for this user account.
         *
         * @param account User account.
         */
        @Synchronized
        @JvmStatic
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