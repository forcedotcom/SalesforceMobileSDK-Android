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
package com.salesforce.androidsdk.mobilesync.manager

import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess.UserAgentInterceptor
import com.salesforce.androidsdk.mobilesync.app.Features
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.target.AdvancedSyncUpTarget
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import com.salesforce.androidsdk.mobilesync.util.SyncState.Status.DONE
import com.salesforce.androidsdk.mobilesync.util.SyncState.Status.FAILED
import com.salesforce.androidsdk.mobilesync.util.SyncState.Status.NEW
import com.salesforce.androidsdk.mobilesync.util.SyncState.Status.RUNNING
import com.salesforce.androidsdk.mobilesync.util.SyncState.Status.STOPPED
import com.salesforce.androidsdk.mobilesync.util.SyncState.Type.syncDown
import com.salesforce.androidsdk.mobilesync.util.SyncState.Type.syncUp
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.smartstore.store.SmartStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max

/**
 * Sync Manager
 */
class SyncManager private constructor(smartStore: SmartStore, restClient: RestClient?) {
    // Keeping track of active syncs (could be waiting for thread or running on a thread)
    private val activeSyncs: MutableMap<Long?, SyncTask> = HashMap()

    // Sync manager state
    private var state: State

    // Thread pool for running syncs
    private val threadPool = Executors.newFixedThreadPool(1)

    // Backing smartstore
    val smartStore: SmartStore

    // Rest client for network calls
    var restClient: RestClient?

    // Api version
    val apiVersion: String =
        ApiVersionStrings.getVersionNumber(SalesforceSDKManager.getInstance().appContext)

    init {
        this.smartStore = smartStore
        this.restClient = restClient
        state = State.ACCEPTING_SYNCS
        SyncState.setupSyncsSoupIfNeeded(smartStore)
        SyncState.cleanupSyncsSoupIfNeeded(smartStore)
    }

    /**
     * Stop the sync manager
     * It might take a while for active syncs to actually get stopped
     * Call isStopped() to see if syncManager is fully paused
     */
    @Synchronized
    fun stop() {
        state = if (activeSyncs.isEmpty()) {
            State.STOPPED
        } else {
            State.STOP_REQUESTED
        }
    }

    /**
     * @return true if stop was requested but there are still active syncs
     */
    val isStopping: Boolean
        get() = state == State.STOP_REQUESTED

    /**
     * @return true if stop was requested and there no syncs are active anymore
     */
    val isStopped: Boolean
        get() = state == State.STOPPED

    /**
     * Check if syncs are allowed to run
     * Throw a SyncManagerStoppedException if sync manager is stopping/stopped
     */
    @Throws(SyncManagerStoppedException::class)
    fun checkAcceptingSyncs() {
        if (state != State.ACCEPTING_SYNCS) {
            throw SyncManagerStoppedException("sync manager has state:${state.name}")
        }
    }

    /**
     * Resume this sync manager
     * Restart all stopped syncs if restartStoppedSyncs is true
     *
     * @param restartStoppedSyncs
     * @param callback
     * @throws JSONException
     */
    @Synchronized
    @Throws(JSONException::class)
    fun restart(restartStoppedSyncs: Boolean, callback: SyncUpdateCallback?) {
        if (isStopped || isStopping) {
            state = State.ACCEPTING_SYNCS
            if (restartStoppedSyncs) {
                val stoppedSyncs: List<SyncState> = SyncState.getSyncsWithStatus(
                    smartStore, STOPPED
                )
                for (sync in stoppedSyncs) {
                    MobileSyncLogger.d(TAG, "restarting", sync)
                    reSync(sync.id, callback)
                }
            }
        } else {
            MobileSyncLogger.d(
                TAG,
                "restart() called on a sync manager that has state:" + state.name
            )
        }
    }

    /**
     * Add to active syncs map
     * @param syncTask
     */
    @Synchronized
    fun addToActiveSyncs(syncTask: SyncTask) {
        activeSyncs[syncTask.syncId] = syncTask
    }

    /**
     * Remove from active syncs map
     * @param syncTask
     */
    @Synchronized
    fun removeFromActiveSyncs(syncTask: SyncTask) {
        activeSyncs.remove(syncTask.syncId)
        if (state == State.STOP_REQUESTED && activeSyncs.isEmpty()) {
            state = State.STOPPED
        }
    }

    /**
     * Get details of a sync by id
     *
     * @param syncId
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun getSyncStatus(syncId: Long): SyncState? {
        return SyncState.byId(smartStore, syncId)
    }

    /**
     * Get details of a sync by name
     *
     * @param name
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun getSyncStatus(name: String?): SyncState? {
        return SyncState.byName(smartStore, name)
    }

    /**
     * Return true if there is a sync with the given name
     *
     * @param name
     * @return
     */
    fun hasSyncWithName(name: String?): Boolean {
        return SyncState.hasSyncWithName(smartStore, name)
    }

    /**
     * Delete sync by id
     *
     * @param syncId
     * @return
     */
    fun deleteSync(syncId: Long) {
        SyncState.deleteSync(smartStore, syncId)
    }

    /**
     * Delete sync by name
     *
     * @param name
     * @return
     */
    fun deleteSync(name: String?) {
        SyncState.deleteSync(smartStore, name)
    }

    /**
     * Create and run a sync down that will overwrite any modified records
     *
     * @param target
     * @param soupName
     * @param callback
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun syncDown(
        target: SyncDownTarget,
        soupName: String,
        callback: SyncUpdateCallback?
    ): SyncState {
        val options: SyncOptions = SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE)
        return syncDown(target, options, soupName, callback)
    }

    /**
     * Create and run a sync down without a name
     *
     * @param target
     * @param options
     * @param soupName
     * @param callback
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun syncDown(
        target: SyncDownTarget,
        options: SyncOptions,
        soupName: String,
        callback: SyncUpdateCallback?
    ): SyncState {
        return syncDown(target, options, soupName, null, callback)
    }

    /**
     * Create and run a sync down
     *
     * @param target
     * @param options
     * @param soupName
     * @param syncName
     * @param callback
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun syncDown(
        target: SyncDownTarget,
        options: SyncOptions,
        soupName: String,
        syncName: String?,
        callback: SyncUpdateCallback?
    ): SyncState {
        val sync = createSyncDown(target, options, soupName, syncName)
        MobileSyncLogger.d(TAG, "syncDown called", sync)
        runSync(sync, callback)
        return sync
    }

    /**
     * Create a sync down
     *
     * @param target
     * @param options
     * @param soupName
     * @param syncName
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun createSyncDown(
        target: SyncDownTarget,
        options: SyncOptions,
        soupName: String,
        syncName: String?
    ): SyncState {
        return SyncState.createSyncDown(smartStore, target, options, soupName, syncName)
    }

    /**
     * Re-run sync but only fetch new/modified records
     *
     * @param syncId
     * @param callback
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun reSync(syncId: Long, callback: SyncUpdateCallback?): SyncState {
        val sync = checkExistsById(syncId)
        return reSync(sync, callback)
    }

    /**
     * Re-run sync but only fetch new/modified records
     *
     * @param syncName
     * @param callback
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun reSync(syncName: String, callback: SyncUpdateCallback?): SyncState {
        val sync = checkExistsByName(syncName)
        return reSync(sync, callback)
    }

    private fun reSync(sync: SyncState, callback: SyncUpdateCallback?): SyncState {
        sync.totalSize = -1
        if (sync.isStopped) {
            // Sync was interrupted, refetch records including those with maxTimeStamp
            val maxTimeStamp = sync.maxTimeStamp
            sync.maxTimeStamp = max(maxTimeStamp - 1, -1L)
        }
        MobileSyncLogger.d(TAG, "reSync called", sync)
        runSync(sync, callback)
        return sync
    }

    /**
     * Run a sync
     *
     * @param sync
     * @param callback
     */
    fun runSync(sync: SyncState, callback: SyncUpdateCallback?) {
        checkNotRunning("runSync", sync.id)
        checkAcceptingSyncs()
        val syncTask = when (sync.type) {
            syncDown -> SyncDownTask(
                this,
                sync,
                callback
            )

            syncUp -> if (sync.target is AdvancedSyncUpTarget) {
                AdvancedSyncUpTask(this, sync, callback)
            } else {
                SyncUpTask(this, sync, callback)
            }
        }
        threadPool.execute(syncTask)
    }

    /**
     * Create and run a sync up without a name
     *
     * @param target
     * @param options
     * @param soupName
     * @param callback
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun syncUp(
        target: SyncUpTarget,
        options: SyncOptions,
        soupName: String,
        callback: SyncUpdateCallback?
    ): SyncState {
        return syncUp(target, options, soupName, null, callback)
    }

    /**
     * Create and run a sync up
     *
     * @param target
     * @param options
     * @param soupName
     * @param callback
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun syncUp(
        target: SyncUpTarget,
        options: SyncOptions,
        soupName: String,
        syncName: String?,
        callback: SyncUpdateCallback?
    ): SyncState {
        val sync = createSyncUp(target, options, soupName, syncName)
        MobileSyncLogger.d(TAG, "syncUp called", sync)
        runSync(sync, callback)
        return sync
    }

    /**
     * Co-routine wrapper for reSync(syncId)
     *
     * @param syncId: id of sync
     */
    suspend fun suspendReSync(syncId: Long): SyncState {
        try {
            val sync = checkExistsById(syncId)
            return suspendReSync(sync)
        } catch (ex: Exception) {
            throw ReSyncException.FailedToStart(
                message = "Resync operation failed to start",
                cause = ex
            )
        }
    }

    /**
     * Co-routine wrapper for reSync(syncName)
     *
     * @param syncName: name of sync
     */
    suspend fun suspendReSync(syncName: String): SyncState {
        try {
            val sync = checkExistsByName(syncName)
            return suspendReSync(sync)
        } catch (ex: Exception) {
            throw ReSyncException.FailedToStart(
                message = "Resync operation failed to start",
                cause = ex
            )
        }
    }

    private suspend fun suspendReSync(sync: SyncState): SyncState = withContext(NonCancellable) {
        suspendCoroutine { cont ->
            val callback: (SyncState) -> Unit = {
                when (it.status) {
                    // terminal states
                    DONE -> cont.resume(it)

                    FAILED,
                    STOPPED -> cont.resumeWithException(
                        ReSyncException.FailedToFinish(
                            message = "Re sync operation failed with terminal Sync State = $it"
                        )
                    )

                    NEW,
                    RUNNING,
                    null -> {
                        /* no-op; suspending for terminal state */
                    }
                }
            }

            reSync(sync, object : SyncUpdateCallback {
                override fun onUpdate(sync: SyncState) {
                    callback(sync)
                }
            })
        }
    }

    /**
     * Create a sync up
     *
     * @param target
     * @param options
     * @param soupName
     * @param syncName
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun createSyncUp(
        target: SyncUpTarget,
        options: SyncOptions,
        soupName: String,
        syncName: String?
    ): SyncState {
        return SyncState.createSyncUp(smartStore, target, options, soupName, syncName)
    }

    /**
     * Removes local copies of records that have been deleted on the server
     * or do not match the query results on the server anymore.
     *
     * @param syncId
     * @param callback Callback to get clean resync ghosts completion status.
     * @throws JSONException
     * @throws IOException
     */
    @JvmOverloads
    @Throws(JSONException::class)
    fun cleanResyncGhosts(syncId: Long, callback: CleanResyncGhostsCallback? = null) {
        val sync = checkExistsById(syncId)
        cleanResyncGhosts(sync, callback)
    }

    /**
     * Co-routine wrapper for cleanResyncGhosts(syncName)
     *
     * @param syncName: name of sync
     */
    @Throws(CleanResyncGhostsException::class)
    suspend fun suspendCleanResyncGhosts(syncName: String): Int {
        try {
            val sync = checkExistsByName(syncName)
            return suspendCleanResyncGhosts(sync)
        } catch (ex: Exception) {
            throw CleanResyncGhostsException.FailedToStart(
                message = "Clean Resync Ghosts operation failed to start",
                cause = ex
            )
        }
    }

    /**
     * Co-routine wrapper for cleanResyncGhosts(syncId)
     *
     * @param syncId: id of sync
     */
    @Throws(CleanResyncGhostsException::class)
    suspend fun suspendCleanResyncGhosts(syncId: Long): Int {
        try {
            val sync = checkExistsById(syncId)
            return suspendCleanResyncGhosts(sync)
        } catch (ex: Exception) {
            throw CleanResyncGhostsException.FailedToStart(
                message = "Clean Resync Ghosts operation failed to start",
                cause = ex
            )
        }
    }

    private suspend fun suspendCleanResyncGhosts(sync: SyncState) = withContext(NonCancellable) {
        suspendCoroutine<Int> { cont ->
            val callback = object : CleanResyncGhostsCallback {
                override fun onSuccess(numRecords: Int) = cont.resume(numRecords)

                override fun onError(e: java.lang.Exception?) {
                    cont.resumeWithException(
                        CleanResyncGhostsException.FailedToFinish(
                            message = "Clean Resync Ghosts failed to run to completion",
                            cause = e
                        )
                    )
                }
            }

            cleanResyncGhosts(sync, callback)
        }
    }

    /**
     * Removes local copies of records that have been deleted on the server
     * or do not match the query results on the server anymore.
     *
     * @param syncName
     * @param callback Callback to get clean resync ghosts completion status.
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class)
    fun cleanResyncGhosts(syncName: String, callback: CleanResyncGhostsCallback?) {
        val sync = checkExistsByName(syncName)
        cleanResyncGhosts(sync, callback)
    }

    @Throws(MobileSyncException::class)
    private fun cleanResyncGhosts(sync: SyncState, callback: CleanResyncGhostsCallback?) {
        checkNotRunning("cleanResyncGhosts", sync.id)
        checkAcceptingSyncs()
        if (sync.type != syncDown) {
            throw MobileSyncException(buildString {
                append("Cannot run cleanResyncGhosts:")
                append(sync.id)
                append(": wrong type:")
                append(sync.type)
            })
        }

        // Ask target to clean up ghosts
        MobileSyncLogger.d(TAG, "cleanResyncGhosts called", sync)
        threadPool.execute(CleanSyncGhostsTask(this, sync, callback))
    }

    /**
     * Send request after adding user-agent header that says MobileSync.
     * @param restRequest
     * @return
     * @throws IOException
     */
    @Throws(IOException::class, MobileSyncException::class)
    fun sendSyncWithMobileSyncUserAgent(restRequest: RestRequest): RestResponse {
        MobileSyncLogger.d(
            TAG,
            "sendSyncWithMobileSyncUserAgent called with request: ",
            restRequest
        )
        return restClient?.sendSync(
            restRequest,
            UserAgentInterceptor(SalesforceSDKManager.getInstance().getUserAgent(MOBILE_SYNC))
        ) ?: throw MobileSyncException("No rest client")
    }

    /**
     * Throw exception if syncId is already running
     * @param operation
     * @param syncId
     */
    @Throws(MobileSyncException::class)
    private fun checkNotRunning(operation: String, syncId: Long) {
        if (activeSyncs.containsKey(syncId)) {
            throw MobileSyncException("Cannot run $operation $syncId - sync is still running")
        }
    }

    /**
     * Throw excpetion if no sync found with id syncId
     * @param syncId Id of sync to look for.
     * @return sync if found.
     */
    @Throws(JSONException::class, MobileSyncException::class)
    private fun checkExistsById(syncId: Long): SyncState {
        return getSyncStatus(syncId) ?: throw MobileSyncException("Sync $syncId does not exist")
    }

    /**
     * Throw excpetion if no sync found with name syncName
     * @param syncName Name of sync to look for.
     * @return sync if found.
     */
    @Throws(JSONException::class, MobileSyncException::class)
    private fun checkExistsByName(syncName: String): SyncState {
        return getSyncStatus(syncName)
            ?: throw MobileSyncException("Sync $syncName does not exist")
    }

    /**
     * Exception thrown by mobile sync manager
     */
    open class MobileSyncException @JvmOverloads constructor(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : RuntimeException(message, cause)

    /**
     * Exception thrown when sync manager is stopped
     */
    class SyncManagerStoppedException(
        message: String?,
        cause: Throwable? = null
    ) : MobileSyncException(message, cause)

    /**
     * Sealed class representing the failure modes for suspendCleanResyncGhosts
     */
    sealed class CleanResyncGhostsException(message: String?, cause: Throwable?) :
        MobileSyncException(message, cause) {
        data class FailedToFinish(
            override val message: String?,
            override val cause: Throwable? = null
        ) : CleanResyncGhostsException(message, cause)

        data class FailedToStart(
            override val message: String?,
            override val cause: Throwable?
        ) : CleanResyncGhostsException(message, cause)
    }

    /**
     * Sealed class representing the failure modes for suspendResync
     */
    sealed class ReSyncException(message: String?, cause: Throwable?) :
        MobileSyncException(message, cause) {
        data class FailedToFinish(
            override val message: String? = null,
            override val cause: Throwable? = null
        ) : CleanResyncGhostsException(message, cause)

        data class FailedToStart(
            override val message: String? = null,
            override val cause: Throwable? = null
        ) : CleanResyncGhostsException(message, cause)
    }

    /**
     * Callback to get sync status udpates
     */
    interface SyncUpdateCallback {
        fun onUpdate(sync: SyncState)
    }

    /**
     * Callback to get clean resync ghosts completion status
     */
    interface CleanResyncGhostsCallback {
        /**
         * Called when clean resync ghosts completes successfully
         * @param numRecords Number of local ghosts found (and removed)
         */
        fun onSuccess(numRecords: Int)

        /**
         * Called when clean resync ghosts fails with an error
         * @param e Error
         */
        fun onError(e: Exception?)
    }

    /**
     * Enum for sync manager state
     *
     */
    enum class State {
        ACCEPTING_SYNCS,  // you can submit a sync to be run
        STOP_REQUESTED,  // sync manager is stopping - sync submitted before the stop request are finishing up - submitting a sync to run will fail
        STOPPED // sync manager is stopped - no sync are running anymore - submitting a sync to run will fail
    }

    companion object {
        // Constants
        private const val TAG = "SyncManager"

        // For user agent
        private const val MOBILE_SYNC = "MobileSync"

        // Static member
        private val INSTANCES: MutableMap<String, SyncManager> = HashMap()

        /**
         * Returns the instance of this class associated with current user.
         *
         * @return Instance of this class.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(): SyncManager {
            return getInstance(null, null)
        }

        /**
         * Returns the instance of this class associated with this user account.
         *
         * @param account User account.
         * @return Instance of this class.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(account: UserAccount?): SyncManager {
            return getInstance(account, null)
        }

        /**
         * Returns the instance of this class associated with this user and community.
         * Sync manager returned is ready to use.
         *
         * @param account     User account.
         * @param communityId Community ID.
         * @return Instance of this class.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(account: UserAccount?, communityId: String?): SyncManager {
            return getInstance(account, communityId, null)
        }

        /**
         * Returns the instance of this class associated with this user, community and smartstore.
         *
         * @param account     User account. Pass null to user current user.
         * @param communityId Community ID. Pass null if not applicable
         * @param smartStore  SmartStore instance. Pass null to use current user default smartstore.
         * @return Instance of this class.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(
            account: UserAccount?,
            communityId: String?,
            smartStore: SmartStore?
        ): SyncManager {
            val user =
                account ?: MobileSyncSDKManager.getInstance().userAccountManager.cachedCurrentUser
            val store =
                smartStore ?: MobileSyncSDKManager.getInstance().getSmartStore(user, communityId)
            val uniqueId = ((if (user != null) user.userId else "") + ":" + store.database.path)
            var instance = INSTANCES[uniqueId]
            if (instance == null) {
                /*
                 * If account is still null, there is no user logged in, which means, the default
                 * RestClient should be set to the unauthenticated RestClient instance.
                 */
                val restClient: RestClient? = if (user == null) {
                    SalesforceSDKManager.getInstance().clientManager.peekUnauthenticatedRestClient()
                } else {
                    SalesforceSDKManager.getInstance().clientManager.peekRestClient(user)
                }
                instance = SyncManager(store, restClient)
                instance.also { INSTANCES[uniqueId] = it }
            }
            SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_MOBILE_SYNC)
            return instance
        }

        /**
         * Resets all the sync managers
         */
        @Synchronized
        @JvmStatic
        fun reset() {
            for (syncManager in INSTANCES.values) {
                syncManager.stop()
                syncManager.threadPool.shutdownNow()
            }
            INSTANCES.clear()
        }

        /**
         * Resets the sync managers for this user account
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
                        val syncManager = INSTANCES[key] ?: continue
                        syncManager.stop()
                        syncManager.threadPool.shutdownNow()
                    }
                }
                // NB: keySet returns a Set view of the keys contained in this map.
                // The set is backed by the map, so changes to the map are reflected in the set, and vice-versa.
                INSTANCES.keys.removeAll(keysToRemove)
            }
        }
    }
}