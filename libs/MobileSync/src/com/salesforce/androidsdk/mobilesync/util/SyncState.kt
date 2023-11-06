/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.util

import com.salesforce.androidsdk.mobilesync.manager.SyncManager.MobileSyncException
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONException
import org.json.JSONObject

/**
 * State of a sync-down or sync-up
 */
class SyncState(
    val id: Long,
    val type: Type,
    val name: String?,
    val target: SyncTarget,
    val options: SyncOptions,
    val soupName: String
) {
    var status: Status? = null
        set(status) {
            if (field != Status.RUNNING && status == Status.RUNNING) {
                startTime = System.currentTimeMillis()
            }
            if (field == Status.RUNNING && (status == Status.DONE || status == Status.FAILED || status == Status.STOPPED)) {
                endTime = System.currentTimeMillis()
            }
            field = status
        }
    var progress = 0
    var totalSize = 0
    var maxTimeStamp: Long = 0

    // Start and end time in milliseconds since 1970
    internal var startTime: Long = 0
    internal var endTime: Long = 0

    //Error return from SFDC API
    var error: String? = null

    /**
     * @return json representation of sync
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun asJSON(): JSONObject {
        return with(JSONObject()) {
            put(SmartStore.SOUP_ENTRY_ID, id)
            put(SYNC_TYPE, type.name)
            name?.let { put(SYNC_NAME, it) }
            put(SYNC_TARGET, target.asJSON())
            put(SYNC_OPTIONS, options.asJSON())
            put(SYNC_SOUP_NAME, soupName)
            status?.let { put(SYNC_STATUS, it.name) }
            put(SYNC_PROGRESS, progress)
            put(SYNC_TOTAL_SIZE, totalSize)
            put(SYNC_MAX_TIME_STAMP, maxTimeStamp)
            put(SYNC_START_TIME, startTime)
            put(SYNC_END_TIME, endTime)
            put(SYNC_ERROR, error)
        }
    }

    override fun toString(): String {
        return try {
            asJSON().toString().replace("\n".toRegex(), " ")
        } catch (e: JSONException) {
            super.toString()
        }
    }

    /**
     * Save SyncState to db
     * @param store
     * @throws JSONException
     */
    @Throws(JSONException::class, MobileSyncException::class)
    fun save(store: SmartStore) {
        store.update(
            SYNCS_SOUP,
            asJSON(),
            id
        ) ?: throw MobileSyncException("Failed to save sync state")
    }

    val mergeMode: MergeMode
        get() = options.mergeMode ?: MergeMode.OVERWRITE

    fun getStartTime(): Double {
        return startTime.toDouble()
    }

    fun getEndTime(): Double {
        return endTime.toDouble()
    }

    val isDone: Boolean
        get() = status == Status.DONE

    fun hasFailed(): Boolean {
        return status == Status.FAILED
    }

    val isStopped: Boolean
        get() = status == Status.STOPPED
    val isRunning: Boolean
        get() = status == Status.RUNNING

    @Throws(JSONException::class)
    fun copy(): SyncState {
        return fromJSON(asJSON())
    }

    /**
     * Enum for sync type
     */
    enum class Type {
        syncDown, syncUp
    }

    /**
     * Enum for sync status
     *
     */
    enum class Status {
        NEW, STOPPED, RUNNING, DONE, FAILED
    }

    /**
     * Enum for merge modes
     */
    enum class MergeMode {
        OVERWRITE, LEAVE_IF_CHANGED
    }

    companion object {
        // SmartStore
        const val SYNCS_SOUP = "syncs_soup"
        const val SYNC_NAME = "name"
        const val SYNC_TYPE = "type"
        const val SYNC_TARGET = "target"
        const val SYNC_OPTIONS = "options"
        const val SYNC_SOUP_NAME = "soupName"
        const val SYNC_STATUS = "status"
        const val SYNC_PROGRESS = "progress"
        const val SYNC_TOTAL_SIZE = "totalSize"
        const val SYNC_MAX_TIME_STAMP = "maxTimeStamp"
        const val SYNC_START_TIME = "startTime"
        const val SYNC_END_TIME = "endTime"
        const val SYNC_ERROR = "error"

        /**
         * Create syncs soup if needed
         * @param store
         */
        @Throws(MobileSyncException::class)
        @JvmStatic
        fun setupSyncsSoupIfNeeded(store: SmartStore) {
            if (store.hasSoup(SYNCS_SOUP) && store.getSoupIndexSpecs(SYNCS_SOUP).size == 3) {
                return
            }
            val indexSpecs = arrayOf(
                IndexSpec(SYNC_TYPE, SmartStore.Type.json1),
                IndexSpec(SYNC_NAME, SmartStore.Type.json1),
                IndexSpec(SYNC_STATUS, SmartStore.Type.json1)
            )

            // Syncs soup exists but doesn't have all the required indexes
            if (store.hasSoup(SYNCS_SOUP)) {
                try {
                    store.alterSoup(SYNCS_SOUP, indexSpecs, true /* reindexing to json1 is quick */)
                } catch (e: JSONException) {
                    throw MobileSyncException(cause = e)
                }
            } else {
                store.registerSoup(SYNCS_SOUP, indexSpecs)
            }
        }

        /**
         * Cleanup syncs soup if needed
         * At startup, no sync could be running already
         * If a sync is in the running state, we change it to stopped
         * @param store
         */
        @Throws(MobileSyncException::class)
        @JvmStatic
        fun cleanupSyncsSoupIfNeeded(store: SmartStore) {
            try {
                val syncs = getSyncsWithStatus(store, Status.RUNNING)
                for (sync in syncs) {
                    sync.status = Status.STOPPED
                    sync.save(store)
                }
            } catch (e: JSONException) {
                throw MobileSyncException(cause = e)
            }
        }

        /**
         * Get syncs with given status in the given store
         * @param store
         * @param status
         * @return list of SyncState
         * @throws JSONException
         */
        @Throws(JSONException::class)
        fun getSyncsWithStatus(store: SmartStore, status: Status): List<SyncState> {
            val syncs: MutableList<SyncState> = ArrayList()
            val query = QuerySpec.buildSmartQuerySpec(
                String.format(
                    "select {%1\$s:%2\$s} from {%1\$s} where {%1\$s:%3\$s} = '%4\$s'",
                    SYNCS_SOUP,
                    SmartSqlHelper.SOUP,
                    SYNC_STATUS,
                    status.name
                ), Int.MAX_VALUE
            )
            val rows = store.query(query, 0)
            for (i in 0 until rows.length()) {
                syncs.add(fromJSON(rows.getJSONArray(i).getJSONObject(0)))
            }
            return syncs
        }

        /**
         * Create sync state in database for a sync down and return corresponding SyncState
         * NB: Throws exception if there is already a sync with the same name (when name is not null)
         *
         * @param store
         * @param target
         * @param options
         * @param soupName
         * @param name
         * @return
         * @throws JSONException
         */
        @JvmStatic
        @Throws(JSONException::class, MobileSyncException::class)
        fun createSyncDown(
            store: SmartStore,
            target: SyncDownTarget,
            options: SyncOptions,
            soupName: String,
            name: String?
        ): SyncState {
            val sync = with(JSONObject()) {
                put(SYNC_TYPE, Type.syncDown)
                if (name != null) put(SYNC_NAME, name)
                put(SYNC_TARGET, target.asJSON())
                put(SYNC_OPTIONS, options.asJSON())
                put(SYNC_SOUP_NAME, soupName)
                put(SYNC_STATUS, Status.NEW.name)
                put(SYNC_PROGRESS, 0)
                put(SYNC_TOTAL_SIZE, -1)
                put(SYNC_MAX_TIME_STAMP, -1)
                put(SYNC_START_TIME, 0)
                put(SYNC_END_TIME, 0)
                put(SYNC_ERROR, "")
            }
            if (name != null && hasSyncWithName(store, name)) {
                throw MobileSyncException("Failed to create sync down: there is already a sync with name:$name")
            }
            val syncSaved = store.upsert(SYNCS_SOUP, sync)
                ?: throw MobileSyncException("Failed to create sync down")
            return fromJSON(syncSaved)
        }

        /**
         * Create sync state in database for a sync up and return corresponding SyncState
         * NB: Throws exception if there is already a sync with the same name (when name is not null)
         *
         * @param store
         * @param target
         * @param options
         * @param soupName
         * @param name
         * @return
         * @throws JSONException
         */
        @JvmStatic
        @Throws(JSONException::class, MobileSyncException::class)
        fun createSyncUp(
            store: SmartStore,
            target: SyncUpTarget,
            options: SyncOptions,
            soupName: String,
            name: String?
        ): SyncState {
            val sync = JSONObject()
            with(sync) {
                put(SYNC_TYPE, Type.syncUp)
                if (name != null) put(SYNC_NAME, name)
                put(SYNC_TARGET, target.asJSON())
                put(SYNC_SOUP_NAME, soupName)
                put(SYNC_OPTIONS, options.asJSON())
                put(SYNC_STATUS, Status.NEW.name)
                put(SYNC_PROGRESS, 0)
                put(SYNC_TOTAL_SIZE, -1)
                put(SYNC_MAX_TIME_STAMP, -1)
                put(SYNC_START_TIME, 0)
                put(SYNC_END_TIME, 0)
                put(SYNC_ERROR, "")
            }
            if (name != null && hasSyncWithName(store, name)) {
                throw MobileSyncException("Failed to create sync up: there is already a sync with name:$name")
            }
            val syncSaved = store.upsert(SYNCS_SOUP, sync)
                ?: throw MobileSyncException("Failed to create sync up")
            return fromJSON(syncSaved)
        }

        /**
         * Build SyncState from json
         * @param sync
         * @return
         * @throws JSONException
         */
        @Throws(JSONException::class, MobileSyncException::class)
        fun fromJSON(sync: JSONObject): SyncState {
            val id = sync.getLong(SmartStore.SOUP_ENTRY_ID)
            val type = Type.valueOf(sync.getString(SYNC_TYPE))
            val name = JSONObjectHelper.optString(sync, SYNC_NAME)
            val jsonOptions = sync.optJSONObject(SYNC_OPTIONS)
                ?: throw MobileSyncException("No options specified")
            val options = SyncOptions.fromJSON(jsonOptions)
            val jsonTarget =
                sync.optJSONObject(SYNC_TARGET) ?: throw MobileSyncException("No target specified")
            val target =
                if (type == Type.syncDown) SyncDownTarget.fromJSON(jsonTarget) else SyncUpTarget.fromJSON(
                    jsonTarget
                )
            val soupName = sync.getString(SYNC_SOUP_NAME)

            val state = SyncState(id, type, name, target, options, soupName)
            state.status = Status.valueOf(sync.getString(SYNC_STATUS))
            state.progress = sync.getInt(SYNC_PROGRESS)
            state.totalSize = sync.getInt(SYNC_TOTAL_SIZE)
            state.maxTimeStamp = sync.optLong(SYNC_MAX_TIME_STAMP, -1)
            state.startTime = sync.optLong(SYNC_START_TIME, 0)
            state.endTime = sync.optLong(SYNC_START_TIME, 0)
            state.error = JSONObjectHelper.optString(sync, SYNC_ERROR, "")
            return state
        }

        /**
         * Get sync state of sync given by id
         *
         * @param store
         * @param id
         * @return
         * @throws JSONException
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun byId(store: SmartStore, id: Long): SyncState? {
            val syncs = store.retrieve(SYNCS_SOUP, id)
            return if (syncs == null || syncs.length() == 0) {
                null
            } else fromJSON(
                syncs.getJSONObject(0)
            )
        }

        /**
         * Get sync state of sync given by name
         *
         * @param store
         * @param name
         * @return
         * @throws JSONException
         */
        @JvmStatic
        @Throws(JSONException::class, MobileSyncException::class)
        fun byName(store: SmartStore, name: String?): SyncState? {
            if (name == null) {
                throw MobileSyncException("name must not be null")
            }
            val syncId = store.lookupSoupEntryId(SYNCS_SOUP, SYNC_NAME, name)
            return if (syncId < 0) null else byId(
                store,
                syncId
            )
        }

        /**
         * Delete row for sync given by id
         * @param store
         * @param id
         */
        @JvmStatic
        fun deleteSync(store: SmartStore, id: Long) {
            store.delete(SYNCS_SOUP, id)
        }

        /**
         * Delete row for sync given by name
         * @param store
         * @param name
         */
        @Throws(MobileSyncException::class)
        @JvmStatic
        fun deleteSync(store: SmartStore, name: String?) {
            if (name == null) {
                throw MobileSyncException("name must not be null")
            }
            val syncId = store.lookupSoupEntryId(SYNCS_SOUP, SYNC_NAME, name)
            if (syncId < 0) return
            deleteSync(store, syncId)
        }

        /**
         * Return true if there is a sync with the given name
         *
         * @param store
         * @param name
         * @return
         */
        @Throws(MobileSyncException::class)
        fun hasSyncWithName(store: SmartStore, name: String?): Boolean {
            if (name == null) {
                throw MobileSyncException("name must not be null")
            }
            val syncId = store.lookupSoupEntryId(SYNCS_SOUP, SYNC_NAME, name)
            return syncId != -1L
        }
    }
}