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
package com.salesforce.androidsdk.mobilesync.target

import com.salesforce.androidsdk.mobilesync.app.Features
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.MobileSyncException
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RecordRequest
import com.salesforce.androidsdk.mobilesync.target.CompositeRequestHelper.RecordResponse
import com.salesforce.androidsdk.mobilesync.target.ParentChildrenSyncTargetHelper.RelationshipType
import com.salesforce.androidsdk.mobilesync.util.ChildrenInfo
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.mobilesync.util.ParentInfo
import com.salesforce.androidsdk.mobilesync.util.SOQLBuilder
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.LinkedList

/**
 * Target for sync that uploads parent with children records
 */
open class ParentChildrenSyncUpTarget(
    private val parentInfo: ParentInfo,
    parentCreateFieldlist: List<String>?,
    parentUpdateFieldlist: List<String>?,
    private val childrenInfo: ChildrenInfo,
    private val childrenCreateFieldlist: List<String>,
    private val childrenUpdateFieldlist: List<String>,
    private val relationshipType: RelationshipType
) : SyncUpTarget(parentCreateFieldlist, parentUpdateFieldlist), AdvancedSyncUpTarget {
    constructor(target: JSONObject) : this(
        ParentInfo(target.getJSONObject(ParentChildrenSyncTargetHelper.PARENT)),
        JSONObjectHelper.toList<String>(target.optJSONArray(CREATE_FIELDLIST)),
        JSONObjectHelper.toList<String>(target.optJSONArray(UPDATE_FIELDLIST)),
        ChildrenInfo(target.getJSONObject(ParentChildrenSyncTargetHelper.CHILDREN)),
        JSONObjectHelper.toList<String>(target.optJSONArray(CHILDREN_CREATE_FIELDLIST)),
        JSONObjectHelper.toList<String>(target.optJSONArray(CHILDREN_UPDATE_FIELDLIST)),
        RelationshipType.valueOf(target.getString(ParentChildrenSyncTargetHelper.RELATIONSHIP_TYPE))
    )

    init {
        MobileSyncSDKManager.getInstance()
            .registerUsedAppFeature(Features.FEATURE_RELATED_RECORDS)
    }

    @Throws(JSONException::class)
    override fun asJSON(): JSONObject {
        return with(super.asJSON()) {
            put(ParentChildrenSyncTargetHelper.PARENT, parentInfo.asJSON())
            put(ParentChildrenSyncTargetHelper.CHILDREN, childrenInfo.asJSON())
            put(CHILDREN_CREATE_FIELDLIST, JSONArray(childrenCreateFieldlist))
            put(CHILDREN_UPDATE_FIELDLIST, JSONArray(childrenUpdateFieldlist))
            put(ParentChildrenSyncTargetHelper.RELATIONSHIP_TYPE, relationshipType.name)
        }
    }

    override fun getDirtyRecordIdsSql(soupName: String, idField: String): String {
        return ParentChildrenSyncTargetHelper.getDirtyRecordIdsSql(
            parentInfo,
            childrenInfo,
            idField
        )
    }

    override fun createOnServer(
        syncManager: SyncManager,
        record: JSONObject,
        fieldlist: List<String>?
    ): String {
        throw UnsupportedOperationException("For advanced sync up target, call syncUpOneRecord")
    }

    override fun deleteOnServer(syncManager: SyncManager, record: JSONObject): Int {
        throw UnsupportedOperationException("For advanced sync up target, call syncUpOneRecord")
    }

    override fun updateOnServer(
        syncManager: SyncManager,
        record: JSONObject,
        fieldlist: List<String>?
    ): Int {
        throw UnsupportedOperationException("For advanced sync up target, call syncUpOneRecord")
    }

    override val maxBatchSize: Int
        get() = 1

    @Throws(JSONException::class, IOException::class, MobileSyncException::class)
    override fun syncUpRecords(
        syncManager: SyncManager,
        records: List<JSONObject>,
        fieldlist: List<String>?,
        mergeMode: MergeMode,
        syncSoupName: String
    ) {
        if (records.size > 1) {
            throw MobileSyncException(javaClass.simpleName + ":syncUpRecords can handle only 1 record at a time")
        }
        if (records.isNotEmpty()) {
            syncUpRecord(syncManager, records[0], fieldlist, mergeMode)
        }
    }

    @Throws(JSONException::class, IOException::class)
    private fun syncUpRecord(
        syncManager: SyncManager,
        record: JSONObject,
        fieldlist: List<String>?,
        mergeMode: MergeMode?
    ) {
        val isCreate = isLocallyCreated(record)
        val isDelete = isLocallyDeleted(record)

        // Getting children
        val children =
            if (relationshipType == RelationshipType.MASTER_DETAIL && isDelete && !isCreate // deleting master in a master-detail relationship will delete the children
            // so no need to actually do any work on the children
            ) JSONArray() else ParentChildrenSyncTargetHelper.getChildrenFromLocalStore(
                syncManager.smartStore,
                parentInfo,
                childrenInfo,
                record
            )
        syncUpRecord(syncManager, record, children, fieldlist, mergeMode)
    }

    @Throws(JSONException::class, IOException::class)
    private fun syncUpRecord(
        syncManager: SyncManager,
        record: JSONObject,
        children: JSONArray,
        fieldlist: List<String>?,
        mergeMode: MergeMode?
    ) {
        val isCreate = isLocallyCreated(record)
        val isDelete = isLocallyDeleted(record)

        // Preparing request for parent
        val recordRequests: MutableList<RecordRequest> = LinkedList()
        val parentId = record.getString(idFieldName)
        val parentRequest = buildRequestForParentRecord(record, fieldlist)

        // Parent request goes first unless it's a delete
        if (parentRequest != null && !isDelete) {
            parentRequest.referenceId = parentId
            recordRequests.add(parentRequest)
        }

        // Preparing requests for children
        for (i in 0 until children.length()) {
            val childRecord = children.getJSONObject(i)
            val childId = childRecord.getString(childrenInfo.idFieldName)

            // Parent will get a server id
            // Children need to be updated
            if (isCreate) {
                childRecord.put(LOCAL, true)
                childRecord.put(LOCALLY_UPDATED, true)
            }
            val childRequest = buildRequestForChildRecord(
                childRecord, isCreate,
                if (isDelete) null else parentId
            )
            if (childRequest != null) {
                childRequest.referenceId = childId
                recordRequests.add(childRequest)
            }
        }

        // Parent request goes last when it's a delete
        if (parentRequest != null && isDelete) {
            parentRequest.referenceId = parentId
            recordRequests.add(parentRequest)
        }

        // Sending composite request
        val refIdToRecordResponses =
            CompositeRequestHelper.sendAsCompositeBatchRequest(syncManager, false, recordRequests)

        // Build refId to server id map
        val refIdToServerId = CompositeRequestHelper.parseIdsFromResponses(refIdToRecordResponses)

        // Will a re-run be required?
        var needReRun = false

        // Update parent in local store
        if (isDirty(record)) {
            needReRun = updateParentRecordInLocalStore(
                syncManager, record, children, mergeMode, refIdToServerId,
                refIdToRecordResponses[record.getString(idFieldName)]
            )
        }

        // Update children local store
        for (i in 0 until children.length()) {
            val childRecord = children.getJSONObject(i)
            if (isDirty(childRecord) || isCreate) {
                needReRun = needReRun || updateChildRecordInLocalStore(
                    syncManager, childRecord, record, mergeMode, refIdToServerId,
                    refIdToRecordResponses[childRecord.getString(childrenInfo.idFieldName)]
                )
            }
        }

        // Re-run if required
        if (needReRun) {
            MobileSyncLogger.d(TAG, "syncUpOneRecord", record)
            syncUpRecord(syncManager, record, children, fieldlist, mergeMode)
        }
    }

    @Throws(JSONException::class, IOException::class)
    protected fun updateParentRecordInLocalStore(
        syncManager: SyncManager,
        record: JSONObject,
        children: JSONArray,
        mergeMode: MergeMode?,
        refIdToServerId: Map<String, String>,
        response: RecordResponse?
    ): Boolean {
        var needReRun = false
        val soupName = parentInfo.soupName
        val lastError = response?.errorJson?.toString()

        // Delete case
        if (isLocallyDeleted(record)) {
            if (isLocallyCreated(record) // we didn't go to the server
                || response?.success == true // or we successfully deleted on the server
                || response?.recordDoesNotExist == true // or the record was already deleted on the server
            ) {
                if (relationshipType == RelationshipType.MASTER_DETAIL) {
                    ParentChildrenSyncTargetHelper.deleteChildrenFromLocalStore(
                        syncManager.smartStore, parentInfo, childrenInfo, record.getString(
                            idFieldName
                        )
                    )
                }
                deleteFromLocalStore(syncManager, soupName, record)
            } else {
                saveRecordToLocalStoreWithError(syncManager, soupName, record, lastError)
            }
        } else {
            // Success case
            if (response?.success == true) {
                // Plugging server id in id field
                CompositeRequestHelper.updateReferences(record, idFieldName, refIdToServerId)

                // Clean and save
                cleanAndSaveInLocalStore(syncManager, soupName, record)
            } else if (response?.recordDoesNotExist == true) {
                // Record needs to be recreated
                if (mergeMode == MergeMode.OVERWRITE) {
                    record.put(LOCAL, true)
                    record.put(LOCALLY_CREATED, true)

                    // Children need to be updated or recreated as well (since the parent will get a new server id)
                    for (i in 0 until children.length()) {
                        val childRecord = children.getJSONObject(i)
                        childRecord.put(LOCAL, true)
                        childRecord.put(
                            if (relationshipType == RelationshipType.MASTER_DETAIL) LOCALLY_CREATED else LOCALLY_UPDATED,
                            true
                        )
                    }
                    needReRun = true
                }
            } else {
                saveRecordToLocalStoreWithError(syncManager, soupName, record, lastError)
            }
        }
        return needReRun
    }

    @Throws(JSONException::class)
    protected fun updateChildRecordInLocalStore(
        syncManager: SyncManager,
        record: JSONObject,
        parentRecord: JSONObject,
        mergeMode: MergeMode?,
        refIdToServerId: Map<String, String>,
        response: RecordResponse?
    ): Boolean {
        var needReRun = false
        val soupName = childrenInfo.soupName
        val lastError = response?.errorJson?.toString()

        // Delete case
        if (isLocallyDeleted(record)) {
            if (isLocallyCreated(record) // we didn't go to the server
                || response?.success == true // or we successfully deleted on the server
                || response?.recordDoesNotExist == true // or the record was already deleted on the server
            ) {
                deleteFromLocalStore(syncManager, soupName, record)
            } else {
                saveRecordToLocalStoreWithError(syncManager, soupName, record, lastError)
            }
        } else {
            // Success case
            if (response?.success == true) {
                // Plugging server id in id field
                CompositeRequestHelper.updateReferences(
                    record,
                    childrenInfo.idFieldName,
                    refIdToServerId
                )

                // Plugging server id in parent id field
                CompositeRequestHelper.updateReferences(
                    record,
                    childrenInfo.parentIdFieldName,
                    refIdToServerId
                )

                // Clean and save
                cleanAndSaveInLocalStore(syncManager, soupName, record)
            } else if (response?.recordDoesNotExist == true) {
                // Record needs to be recreated
                if (mergeMode == MergeMode.OVERWRITE) {
                    record.put(LOCAL, true)
                    record.put(LOCALLY_CREATED, true)

                    // We need a re-run
                    needReRun = true
                }
            } else if (response?.relatedRecordDoesNotExist == true) {
                // Parent record needs to be recreated
                if (mergeMode == MergeMode.OVERWRITE) {
                    parentRecord.put(LOCAL, true)
                    parentRecord.put(LOCALLY_CREATED, true)

                    // We need a re-run
                    needReRun = true
                }
            } else {
                saveRecordToLocalStoreWithError(syncManager, soupName, record, lastError)
            }
        }
        return needReRun
    }

    @Throws(IOException::class, JSONException::class)
    protected fun buildRequestForParentRecord(
        record: JSONObject,
        fieldlist: List<String>?
    ): RecordRequest? {
        return buildRequestForRecord(
            record, fieldlist,
            isParent = true,
            useParentIdReference = false,
            parentId = null
        )
    }

    @Throws(IOException::class, JSONException::class)
    protected fun buildRequestForChildRecord(
        record: JSONObject,
        useParentIdReference: Boolean,
        parentId: String?
    ): RecordRequest? {
        return buildRequestForRecord(
            record,
            null,
            false,
            useParentIdReference,
            parentId
        )
    }

    @Throws(IOException::class, JSONException::class, MobileSyncException::class)
    protected fun buildRequestForRecord(
        record: JSONObject,
        fieldlist: List<String>?,
        isParent: Boolean,
        useParentIdReference: Boolean,
        parentId: String?
    ): RecordRequest? {
        if (!isDirty(record)) {
            return null // nothing to do
        }
        val info = if (isParent) parentInfo else childrenInfo
        val id = record.getString(info.idFieldName)

        // Delete case
        val isDelete = isLocallyDeleted(record)
        val isCreate = isLocallyCreated(record)
        return if (isDelete) {
            if (isCreate) {
                null // no need to go to server
            } else {
                RecordRequest.requestForDelete(info.sobjectType, id)
            }
        } else {
            val fieldlistToUse = if (isParent) {
                if (isCreate) {
                    createFieldlist ?: fieldlist
                } else {
                    updateFieldlist ?: fieldlist
                }
            } else {
                if (isCreate) {
                    childrenCreateFieldlist
                } else {
                    childrenUpdateFieldlist
                }
            } ?: throw MobileSyncException("No fields specified")
            val fields =
                buildFieldsMap(
                    record,
                    fieldlistToUse,
                    info.idFieldName,
                    info.modificationDateFieldName
                )
            if (parentId != null) {
                fields[(info as ChildrenInfo).parentIdFieldName] =
                    if (useParentIdReference) String.format(
                        "@{%s.%s}",
                        parentId,
                        Constants.LID
                    ) else parentId
            }
            if (isCreate) {
                val externalId = if (info.externalIdFieldName != null) JSONObjectHelper.optString(
                    record,
                    info.externalIdFieldName
                ) else null
                if (externalId != null // the following check is there for the case
                    // where the the external id field is the id field
                    // and the field is populated by a local id
                    && !isLocalId(externalId)
                ) {
                    RecordRequest.requestForUpsert(
                        info.sobjectType,
                        info.externalIdFieldName,
                        externalId,
                        fields
                    )
                } else {
                    RecordRequest.requestForCreate(info.sobjectType, fields)
                }
            } else {
                RecordRequest.requestForUpdate(
                    info.sobjectType,
                    id,
                    fields
                )
            }
        }
    }

    @Throws(JSONException::class, IOException::class)
    override fun isNewerThanServer(syncManager: SyncManager, record: JSONObject): Boolean {
        if (isLocallyCreated(record)) {
            return true
        }
        val idToLocalTimestamps = getLocalLastModifiedDates(syncManager, record)
        val idToRemoteTimestamps = fetchLastModifiedDates(syncManager, record)
        for (id in idToLocalTimestamps.keys) {
            val localModDate = idToLocalTimestamps[id]
            val remoteTimestamp = idToRemoteTimestamps[id]
            val remoteModDate = RecordModDate(
                remoteTimestamp,
                remoteTimestamp == null // if it wasn't returned by fetchLastModifiedDates, then the record must have been deleted
            )
            if (!super.isNewerThanServer(localModDate, remoteModDate)) {
                return false // no need to go further
            }
        }
        return true
    }

    /**
     * Get local last modified dates for a given record and its children
     * @param syncManager
     * @param record
     * @return
     */
    @Throws(JSONException::class)
    protected fun getLocalLastModifiedDates(
        syncManager: SyncManager,
        record: JSONObject
    ): Map<String, RecordModDate> {
        val idToLocalTimestamps: MutableMap<String, RecordModDate> = HashMap()
        val isParentDeleted = isLocallyDeleted(record)
        val parentModDate = RecordModDate(
            JSONObjectHelper.optString(record, modificationDateFieldName),
            isParentDeleted
        )
        idToLocalTimestamps[record.getString(idFieldName)] = parentModDate
        val children = ParentChildrenSyncTargetHelper.getChildrenFromLocalStore(
            syncManager.smartStore,
            parentInfo,
            childrenInfo,
            record
        )
        for (i in 0 until children.length()) {
            val childRecord = children.getJSONObject(i)
            val childModDate = RecordModDate(
                JSONObjectHelper.optString(childRecord, childrenInfo.modificationDateFieldName),
                isLocallyDeleted(childRecord) || isParentDeleted && relationshipType == RelationshipType.MASTER_DETAIL
            )
            idToLocalTimestamps[childRecord.getString(childrenInfo.idFieldName)] = childModDate
        }
        return idToLocalTimestamps
    }

    /**
     * Fetch last modified dates for a given record and its chidlren
     * @param syncManager
     * @param record
     * @return
     */
    @Throws(JSONException::class, IOException::class)
    protected fun fetchLastModifiedDates(
        syncManager: SyncManager,
        record: JSONObject
    ): Map<String, String> {
        val idToRemoteTimestamps: MutableMap<String, String> = HashMap()
        if (!isLocallyCreated(record)) {
            val parentId = record.getString(idFieldName)
            val lastModRequest = getRequestForTimestamps(syncManager.apiVersion, parentId)
            val lastModResponse = syncManager.sendSyncWithMobileSyncUserAgent(lastModRequest)
            val rows = if (lastModResponse.isSuccess) lastModResponse.asJSONObject().getJSONArray(
                Constants.RECORDS
            ) else null
            if (rows != null && rows.length() > 0) {
                val row = rows.getJSONObject(0)
                idToRemoteTimestamps[row.getString(idFieldName)] = row.getString(
                    modificationDateFieldName
                )
                if (row.has(childrenInfo.sobjectTypePlural) && !row.isNull(childrenInfo.sobjectTypePlural)) {
                    val childrenRows =
                        row.getJSONObject(childrenInfo.sobjectTypePlural).getJSONArray(
                            Constants.RECORDS
                        )
                    for (i in 0 until childrenRows.length()) {
                        val childRow = childrenRows.getJSONObject(i)
                        idToRemoteTimestamps[childRow.getString(childrenInfo.idFieldName)] =
                            childRow.getString(
                                childrenInfo.modificationDateFieldName
                            )
                    }
                }
            }
        }
        return idToRemoteTimestamps
    }

    /**
     * Build SOQL request to get current time stamps
     *
     * @param apiVersion
     * @param parentId
     * @return
     * @throws UnsupportedEncodingException
     */
    @Throws(UnsupportedEncodingException::class)
    protected fun getRequestForTimestamps(apiVersion: String, parentId: String): RestRequest {
        val builderNested: SOQLBuilder = SOQLBuilder.getInstanceWithFields(
            childrenInfo.idFieldName,
            childrenInfo.modificationDateFieldName
        )
        builderNested.from(childrenInfo.sobjectTypePlural)
        val builder: SOQLBuilder = SOQLBuilder.getInstanceWithFields(
            idFieldName,
            modificationDateFieldName,
            String.format("(%s)", builderNested.build())
        )
        builder.from(parentInfo.sobjectType)
        builder.where(String.format("%s = '%s'", idFieldName, parentId))
        return RestRequest.getRequestForQuery(apiVersion, builder.build())
    }

    companion object {
        // Constants
        const val CHILDREN_CREATE_FIELDLIST = "childrenCreateFieldlist"
        const val CHILDREN_UPDATE_FIELDLIST = "childrenUpdateFieldlist"
    }
}