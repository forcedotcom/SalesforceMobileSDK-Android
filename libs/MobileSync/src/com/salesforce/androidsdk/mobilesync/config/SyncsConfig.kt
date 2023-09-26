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
package com.salesforce.androidsdk.mobilesync.config

import android.content.Context
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncState.Type.syncDown
import com.salesforce.androidsdk.mobilesync.util.SyncState.Type.syncUp
import com.salesforce.androidsdk.smartstore.config.StoreConfig
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.util.JSONObjectHelper
import com.salesforce.androidsdk.util.ResourceReaderHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Class encapsulating syncs definition.
 *
 * Config expected in a resource or assets file in JSON with the following:
 * {
 * syncs: [
 * {
 * syncType: syncUp | syncDown
 * syncName: xxx
 * soupName: yyy
 * target: { depends on target - see SyncTarget.java  }
 * options: { also depends on target - see SyncOptions.java }
 * }
 * ]
 * }
 */
class SyncsConfig private constructor(str: String?) {
    private var syncConfigs: JSONArray? = null

    /**
     * Constructor for config stored in resource file
     * @param ctx Context.
     * @param resourceId Id of resource file.
     */
    constructor(ctx: Context?, resourceId: Int) : this(
        ResourceReaderHelper.readResourceFile(
            ctx,
            resourceId
        )
    )

    /**
     * Constructor for config stored in asset file
     * @param ctx Context.
     * @param assetPath Path of assets file.
     */
    constructor(ctx: Context?, assetPath: String?) : this(
        ResourceReaderHelper.readAssetFile(
            ctx,
            assetPath
        )
    )

    init {
        try {
            syncConfigs = if (str == null) {
                null
            } else {
                val config = JSONObject(str)
                config.getJSONArray(SYNCS)
            }
        } catch (e: JSONException) {
            MobileSyncLogger.e(TAG, "Unhandled exception parsing json", e)
        }
    }

    /**
     * Return true if syncs are defined in config
     * @return
     */
    fun hasSyncs() = syncConfigs?.let { it.length() > 0 } == true

    /**
     * Create the syncs from the config in the given store
     * NB: only feedback is through the logs - the config is static so getting it right is something the developer should do while writing the app
     * @param store
     */
    fun createSyncs(store: SmartStore) {
        val syncConfigs = syncConfigs ?: return
        val syncManager = SyncManager.getInstance(null, null, store)
        JSONObjectHelper.toList<JSONObject>(syncConfigs).forEach { syncConfig ->
            try {
                val syncName = syncConfig.getString(SYNC_NAME)
                // Leaving sync alone if it already exists
                if (syncManager.hasSyncWithName(syncName)) {
                    MobileSyncLogger.d(TAG, "Sync already exists:$syncName - skipping")
                } else {
                    val syncType = SyncState.Type.valueOf(syncConfig.getString(SYNC_TYPE))
                    val targetJson = syncConfig.optJSONObject(TARGET)
                        ?: throw SyncManager.MobileSyncException("Target not defined in config")
                    val optionsJson = syncConfig.optJSONObject(OPTIONS)
                        ?: throw SyncManager.MobileSyncException("Options not defined in config")
                    val options = SyncOptions.fromJSON(optionsJson)
                    val soupName = syncConfig.getString(StoreConfig.SOUP_NAME)
                    MobileSyncLogger.d(TAG, "Creating sync:$syncName")
                    when (syncType) {
                        syncDown -> {
                            val target = SyncDownTarget.fromJSON(targetJson)
                            syncManager.createSyncDown(target, options, soupName, syncName)
                        }

                        syncUp -> {
                            val target = SyncUpTarget.fromJSON(targetJson)
                            syncManager.createSyncUp(target, options, soupName, syncName)
                        }
                    }
                }
            } catch (e: JSONException) {
                MobileSyncLogger.e(TAG, "Unhandled exception parsing json", e)
            }
        }
    }

    companion object {
        private const val TAG = "SyncsConfig"
        const val SYNCS = "syncs"
        const val TARGET = "target"
        const val OPTIONS = "options"
        const val SYNC_NAME = "syncName"
        const val SYNC_TYPE = "syncType"
    }
}