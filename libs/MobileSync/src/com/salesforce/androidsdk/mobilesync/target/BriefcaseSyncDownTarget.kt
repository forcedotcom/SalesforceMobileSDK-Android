/*
 * Copyright (c) 2022-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.target

import com.salesforce.androidsdk.mobilesync.app.Features
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.util.BriefcaseObjectInfo
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.rest.PrimingRecordsResponse
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.ParseException
import java.util.SortedSet
import java.util.TreeSet
import kotlin.math.ceil
import kotlin.math.min

/**
 * Target for sync that downloads records using the briefcase (priming records) API
 */
open class BriefcaseSyncDownTarget internal constructor(
    private val infos: List<BriefcaseObjectInfo>,
    countIdsPerRetrieve: Int
) : SyncDownTarget() {
    private val infosMap: MutableMap<String, BriefcaseObjectInfo>

    // NB: For each sync run - a fresh sync down target is created (by deserializing it from smartstore)
    // The following members are specific to a run
    protected var maxTimeStamp = 0L
    protected var relayToken: String? = null

    // When we get many ids, we don't fetch all the records for them at once
    protected val fetchedTypedIds = TypedIds()
    protected var currentSliceIndex = 0

    // Number of records to fetch per call (with ids obtained from priming record api)
    private val countIdsPerRetrieve: Int

    /**
     * Construct BriefcaseSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    constructor(target: JSONObject) : this(
        BriefcaseObjectInfo.fromJSONArray(target.getJSONArray(INFOS)),
        target.optInt(COUNT_IDS_PER_RETRIEVE, MAX_COUNT_IDS_PER_RETRIEVE)
    )

    /**
     * Construct BriefcaseSyncDownTarget
     *
     * @param infos
     */
    constructor(infos: List<BriefcaseObjectInfo>) : this(infos, MAX_COUNT_IDS_PER_RETRIEVE)

    init {
        queryType = QueryType.briefcase
        this.countIdsPerRetrieve = min(countIdsPerRetrieve, MAX_COUNT_IDS_PER_RETRIEVE)
        MobileSyncSDKManager.getInstance()
            .registerUsedAppFeature(Features.FEATURE_BRIEFCASE)

        // Build infosMap
        infosMap = HashMap()
        for (info in infos) {
            infosMap[info.sobjectType] = info
        }
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    @Throws(JSONException::class)
    override fun asJSON(): JSONObject {
        return with(super.asJSON()) {
            val infosJson = JSONArray()
            infos.forEach { info -> infosJson.put(info.asJSON()) }
            put(INFOS, infosJson)
            put(COUNT_IDS_PER_RETRIEVE, countIdsPerRetrieve)
        }
    }

    @Throws(IOException::class, JSONException::class)
    override fun startFetch(syncManager: SyncManager, maxTimeStamp: Long): JSONArray {
        this.maxTimeStamp = maxTimeStamp
        relayToken = null
        totalSize = -1
        return getIdsFromBriefcasesAndFetchFromServer(syncManager)
    }

    @Throws(IOException::class, JSONException::class)
    override fun continueFetch(syncManager: SyncManager): JSONArray? {
        return if (relayToken == null && fetchedTypedIds.isEmpty()) {
            null
        } else {
            getIdsFromBriefcasesAndFetchFromServer(syncManager)
        }
    }

    @Throws(JSONException::class, IOException::class)
    override fun cleanGhosts(syncManager: SyncManager, soupName: String, syncId: Long): Int {
        var countGhosts = 0

        // Get all ids
        val typedIds = TypedIds()
        var relayToken: String? = null
        do {
            relayToken = getIdsFromBriefcases(syncManager, typedIds, relayToken, 0)
        } while (relayToken != null)
        val objectTypeToIds = typedIds.toMap()

        // Cleaning up ghosts one object type at a time
        for ((objectType, value) in objectTypeToIds) {
            val info = infosMap[objectType] ?: continue
            val remoteIds: SortedSet<String> = TreeSet(
                value
            )
            val localIds = getNonDirtyRecordIds(
                syncManager, info.soupName, info.idFieldName,
                buildSyncIdPredicateIfIndexed(syncManager, info.soupName, syncId)
            )
            localIds.removeAll(remoteIds)
            val localIdSize = localIds.size
            if (localIdSize > 0) {
                deleteRecordsFromLocalStore(syncManager, info.soupName, localIds, info.idFieldName)
            }
            countGhosts += localIdSize
        }
        return countGhosts
    }

    @Throws(JSONException::class)
    override fun getIdsToSkip(syncManager: SyncManager, soupName: String): Set<String> {
        val dirtyRecordIds: MutableSet<String> = HashSet()
        // Aggregating ids of dirty records across all the soups
        for (info in infos) {
            dirtyRecordIds.addAll(getDirtyRecordIds(syncManager, info.soupName, info.idFieldName))
        }
        return dirtyRecordIds
    }

    @Throws(IOException::class, JSONException::class)
    override fun getRemoteIds(syncManager: SyncManager, localIds: Set<String>): Set<String> {
        // Not used - we are overriding cleanGhosts entirely since we could have multiple soups
        return emptySet()
    }

    /**
     * Method that calls the priming records API to get ids to fetch
     * then use sObject collection retrieve to get record fields
     *
     * @param syncManager
     * @return
     */
    @Throws(IOException::class, JSONException::class)
    private fun getIdsFromBriefcasesAndFetchFromServer(syncManager: SyncManager): JSONArray {
        val records = JSONArray()

        // Run priming record request unless we have fetched typed ids we have not yet processed
        if (fetchedTypedIds.isEmpty()) {
            currentSliceIndex = 0
            relayToken =
                getIdsFromBriefcases(syncManager, fetchedTypedIds, relayToken, maxTimeStamp)
        }

        // Getting ids of records to fetch in a map
        val objectTypeToIds = fetchedTypedIds.slice(
            currentSliceIndex,
            countIdsPerRetrieve
        ).toMap()

        // Get records using sObject collection retrieve one object type at a time
        for ((objectType, idsToFetch) in objectTypeToIds) {
            if (idsToFetch.size > 0) {
                val info = infosMap[objectType] ?: continue
                val fieldlistToFetch = ArrayList(
                    info.fieldlist
                )
                for (fieldName in listOf(info.idFieldName, info.modificationDateFieldName)) {
                    if (!fieldlistToFetch.contains(fieldName)) {
                        fieldlistToFetch.add(fieldName)
                    }
                }
                val fetchedRecords =
                    fetchFromServer(syncManager, info.sobjectType, idsToFetch, fieldlistToFetch)
                JSONObjectHelper.addAll(records, fetchedRecords)
            }
        }
        if (totalSize == -1) {
            // FIXME once 238 is GA
            //  - this will only be correct if there is only one "page" of results
            //  - using response.stats.recordCountTotal would only be correct if the filtering by
            //  timestamp did not exclude any results
            //  - also in 236, response.stats.recordCountTotal seems wrong (it says 1000 all the time)
            totalSize = fetchedTypedIds.size()
        }

        // Incrementing current slice index and checking if we have reached the end
        currentSliceIndex++
        if (currentSliceIndex >= fetchedTypedIds.countSlices(countIdsPerRetrieve)) {
            fetchedTypedIds.clear()
            currentSliceIndex = 0
        }
        return records
    }

    /**
     * Go to the priming record API and return ids (grouped by object type)
     *
     * @param syncManager
     * @param typedIds - gets populated from the response to the priming records API
     * @param relayToken
     * @param maxTimeStamp - only ids with a greater time stamp are returned
     * @return new relay token
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class, IOException::class)
    protected fun getIdsFromBriefcases(
        syncManager: SyncManager,
        typedIds: TypedIds,
        relayToken: String?,
        maxTimeStamp: Long
    ): String? {
        val request = RestRequest.getRequestForPrimingRecords(
            syncManager.apiVersion,
            relayToken,
            maxTimeStamp
        )
        val response: PrimingRecordsResponse = try {
            PrimingRecordsResponse(
                syncManager.sendSyncWithMobileSyncUserAgent(request).asJSONObject()
            )
        } catch (e: ParseException) {
            throw IOException("Could not parse response from priming record API", e)
        }
        val allPrimingRecords = response.primingRecords
        for (info in infos) {
            allPrimingRecords[info.sobjectType]?.values?.forEach { primingRecords ->
                primingRecords.forEach { primingRecord ->
                    typedIds.add(info.sobjectType, primingRecord.id)
                }
            }
        }
        return response.relayToken
    }

    @Throws(IOException::class, JSONException::class)
    protected fun fetchFromServer(
        syncManager: SyncManager,
        sobjectType: String?,
        ids: List<String>?,
        fieldlist: List<String>?
    ): JSONArray {
        syncManager.checkAcceptingSyncs()
        val request = RestRequest.getRequestForCollectionRetrieve(
            syncManager.apiVersion,
            sobjectType,
            ids,
            fieldlist
        )
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        return response.asJSONArray()
    }

    /**
     * Overriding saveRecordsToLocalStore since we might want records in different soups
     *
     * @param syncManager
     * @param soupName
     * @param records
     * @param syncId
     * @throws JSONException
     */
    @Throws(JSONException::class)
    override fun saveRecordsToLocalStore(
        syncManager: SyncManager, soupName: String, records: JSONArray,
        syncId: Long
    ) {
        val smartStore = syncManager.smartStore
        synchronized(smartStore.database) {
            try {
                smartStore.beginTransaction()
                for (i in 0 until records.length()) {
                    val record = records.getJSONObject(i)
                    val info = getMatchingBriefcaseInfo(record)
                    if (info != null) {
                        addSyncId(record, syncId)
                        cleanAndSaveInSmartStore(
                            smartStore,
                            info.soupName,
                            record,
                            info.idFieldName,
                            false
                        )
                    } else {
                        // That should never happened
                        MobileSyncLogger.e(
                            TAG,
                            String.format(
                                "No matching briefcase info - Don't know how to save record %s",
                                record.toString()
                            )
                        )
                    }
                }
                smartStore.setTransactionSuccessful()
            } finally {
                smartStore.endTransaction()
            }
        }
    }

    @Throws(JSONException::class)
    protected fun getObjectType(record: JSONObject): String? {
        val attributes = record.optJSONObject(Constants.ATTRIBUTES)
        return attributes?.getString(Constants.LTYPE)
    }

    @Throws(JSONException::class)
    protected fun getMatchingBriefcaseInfo(record: JSONObject): BriefcaseObjectInfo? {
        val sobjectType = getObjectType(record)
        return if (sobjectType != null) {
            infosMap[sobjectType]
        } else null
    }

    companion object {
        private const val TAG = "BriefcaseSyncDownTarget"
        const val INFOS = "infos"
        const val COUNT_IDS_PER_RETRIEVE = "countIdsPerRetrieve"
        private const val MAX_COUNT_IDS_PER_RETRIEVE = RestRequest.MAX_COLLECTION_RETRIEVE_SIZE
    }
}

class TypedId(var sobjectType: String, var id: String)
class TypedIds @JvmOverloads constructor(var listTypedIds: MutableList<TypedId> = ArrayList()) {
    fun add(objectType: String, id: String) {
        listTypedIds.add(TypedId(objectType, id))
    }

    fun countSlices(sliceSize: Int): Int {
        return ceil(listTypedIds.size.toDouble() / sliceSize).toInt()
    }

    fun size(): Int {
        return listTypedIds.size
    }

    fun slice(sliceIndex: Int, sliceSize: Int): TypedIds {
        val idsOfSlice = listTypedIds
            .subList(
                sliceIndex * sliceSize,
                min(listTypedIds.size, (sliceIndex + 1) * sliceSize)
            )
        return TypedIds(idsOfSlice)
    }

    fun toMap(): Map<String, MutableList<String>> {
        val typeToIds = mutableMapOf<String, MutableList<String>>()
        for (typedId in listTypedIds) {
            val objectType = typedId.sobjectType
            val listIds = typeToIds.getOrPut(objectType) { mutableListOf() }
            listIds.add(typedId.id)
        }
        return typeToIds
    }

    fun clear() {
        listTypedIds.clear()
    }

    fun isEmpty(): Boolean {
        return listTypedIds.isEmpty()
    }
}