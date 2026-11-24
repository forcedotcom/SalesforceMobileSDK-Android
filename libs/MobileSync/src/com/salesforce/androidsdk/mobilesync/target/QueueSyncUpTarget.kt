/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.SyncQueue
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder

/**
 * Sync up target that replays the entries of the sync-up change queue
 * ([SyncQueue.QUEUE_SOUP]) in `sequence` order, one at a time, instead of looking at the dirty
 * flags of the records.
 *
 * Use it with a sync up whose soup name is [SyncQueue.QUEUE_SOUP]; the soup of each record comes
 * from the entry itself. The requests are built from the entries:
 * - CREATE: `POST /sobjects/<type>` (or an upsert when [externalIdFieldName] is set and the record
 *   has a value for it) with the `changes` of the entry
 * - UPDATE: `PATCH /sobjects/<type>/<id>` with the `changes` of the entry
 * - DELETE: `DELETE /sobjects/<type>/<id>`
 *
 * The `fieldlist`, `createFieldlist` and `updateFieldlist` options are ignored, and so is the merge
 * mode (until conflict detection is supported for queue syncs). The replay itself is done by
 * [com.salesforce.androidsdk.mobilesync.manager.QueueSyncUpTask].
 *
 * Record-level errors (HTTP 4xx) never fail the sync; transport errors (no response, 5xx,
 * `REQUEST_LIMIT_EXCEEDED`) do.
 */
open class QueueSyncUpTarget : SyncUpTarget {


    /**
     * Constructs a QueueSyncUpTarget.
     *
     * @param externalIdFieldName Name of the external id field used to upsert created records, or null to always create
     * @param idFieldName Name of the id field of the records (defaults to `Id`)
     * @param modificationDateFieldName Name of the modification date field of the records
     */
    @JvmOverloads
    constructor(
        externalIdFieldName: String? = null,
        idFieldName: String? = null,
        modificationDateFieldName: String? = null
    ) : super(null, null, idFieldName, modificationDateFieldName, externalIdFieldName)

    /**
     * Constructs a QueueSyncUpTarget from its JSON representation (used by [SyncUpTarget.fromJSON]).
     *
     * @param target The target as JSON
     * @throws JSONException
     */
    @Throws(JSONException::class)
    constructor(target: JSONObject) : super(target)

    /** Outcome of the replay of one queue entry */
    internal enum class Outcome {
        /** The server accepted the request */
        SUCCESS,

        /** The server rejected the request for that record (HTTP 4xx): the sync goes on */
        RECORD_ERROR,

        /** The request could not be done or the server is unavailable (5xx, limits): the sync fails */
        TRANSPORT_ERROR
    }

    /**
     * Result of the replay of one entry.
     *
     * @property outcome What happened
     * @property serverId For a successful CREATE, the id given by the server
     * @property error For a failure, the error body returned by the server
     */
    internal class ReplayResult(val outcome: Outcome, val serverId: String? = null, val error: String? = null)

    /**
     * Sends the request for one queue entry.
     *
     * @param syncManager The sync manager
     * @param kind Kind of the entry
     * @param sobjectType Type of the record
     * @param recordId Id of the record (a server id, except for a CREATE)
     * @param changes Fields to send (null for a DELETE)
     * @return The outcome; IOException (no response) propagates
     */
    @Throws(JSONException::class, IOException::class)
    internal fun replay(
        syncManager: SyncManager,
        kind: SyncQueue.Kind,
        sobjectType: String,
        recordId: String,
        changes: JSONObject?
    ): ReplayResult {
        lastError = null
        val fields = changes?.let {
            buildFieldsMap(it, it.keys().asSequence().toList(), idFieldName, modificationDateFieldName)
        } ?: HashMap()
        return when (kind) {
            SyncQueue.Kind.CREATE -> create(syncManager, sobjectType, changes, fields)
            SyncQueue.Kind.UPDATE -> {
                val status = updateOnServer(syncManager, sobjectType, recordId, fields)
                result(status)
            }
            SyncQueue.Kind.DELETE -> {
                // A record that is already gone is a success
                val status = deleteOnServer(syncManager, sobjectType, recordId)
                if (status == HttpURLConnection.HTTP_NOT_FOUND) ReplayResult(Outcome.SUCCESS) else result(status)
            }
        }
    }

    private fun create(
        syncManager: SyncManager,
        sobjectType: String,
        changes: JSONObject?,
        fields: Map<String, Any>
    ): ReplayResult {
        // Same decision as createOnServer(syncManager, record, fieldlist): the check on local ids is
        // there for an external id field that is the id field and holds a local id
        val externalId = if (externalIdFieldName != null && changes != null) {
            JSONObjectHelper.optString(changes, externalIdFieldName)
        } else null
        val request = if (externalId != null && !isLocalId(externalId)) {
            RestRequest.getRequestForUpsert(syncManager.apiVersion, sobjectType, externalIdFieldName, externalId, fields)
        } else {
            RestRequest.getRequestForCreate(syncManager.apiVersion, sobjectType, fields)
        }
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        if (!response.isSuccess) {
            lastError = response.asString()
            return result(response.statusCode)
        }
        if (response.statusCode == HttpURLConnection.HTTP_NO_CONTENT && externalId != null) {
            // An upsert that updated an existing record has no body: look the server id up
            return lookupServerId(syncManager, sobjectType, externalId)
        }
        return ReplayResult(Outcome.SUCCESS, serverId = response.asJSONObject().getString(Constants.LID))
    }

    /** Gets the id of the record with the given external id (`GET /sobjects/<type>/<field>/<value>?fields=Id`) */
    private fun lookupServerId(syncManager: SyncManager, sobjectType: String, externalId: String): ReplayResult {
        val encoded = URLEncoder.encode(externalId, "UTF-8").replace("+", "%20")
        val request = RestRequest.getRequestForRetrieve(
            syncManager.apiVersion, sobjectType, "$externalIdFieldName/$encoded", listOf(Constants.ID)
        )
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        if (!response.isSuccess) {
            lastError = response.asString()
            return result(response.statusCode)
        }
        return ReplayResult(Outcome.SUCCESS, serverId = response.asJSONObject().getString(Constants.ID))
    }

    /** Classifies the status of a request, using the error body kept by the base class */
    private fun result(statusCode: Int): ReplayResult {
        if (RestResponse.isSuccess(statusCode)) {
            return ReplayResult(Outcome.SUCCESS)
        }
        val error = lastError
        val transport = statusCode >= HttpURLConnection.HTTP_INTERNAL_ERROR ||
            (statusCode == HttpURLConnection.HTTP_FORBIDDEN && error?.contains(REQUEST_LIMIT_EXCEEDED) == true)
        return ReplayResult(if (transport) Outcome.TRANSPORT_ERROR else Outcome.RECORD_ERROR, error = error ?: "HTTP $statusCode")
    }

    companion object {
        private const val REQUEST_LIMIT_EXCEEDED = "REQUEST_LIMIT_EXCEEDED"
    }
}
