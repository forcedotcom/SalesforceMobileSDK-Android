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
     * @property serverChanged True if the server accepted a write (2xx); false for a DELETE answered 404 or a failure
     */
    internal class ReplayResult(
        val outcome: Outcome,
        val serverId: String? = null,
        val error: String? = null,
        val serverChanged: Boolean = outcome == Outcome.SUCCESS
    )

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
                if (status == HttpURLConnection.HTTP_NOT_FOUND) ReplayResult(Outcome.SUCCESS, serverChanged = false) else result(status)
            }
        }
    }

    /**
     * The external id value of a CREATE that is an upsert, null for a plain create. The single validator
     * for request selection, body, 204 lookup, destination lookup, lookup cache and same-value deferral.
     * - nonblank string that is not a local id: the key
     * - number (not boolean): the key in its canonical string form (integers as plain digits, others in
     *   the shortest round-trip decimal form, no exponent), if it can be preserved (see [canonicalNumber])
     * - absent, JSON null, blank, local id: no key (plain create)
     * - boolean, object, array, or a number that cannot be preserved: no key too, but [unsupportedExternalIdType] rejects the entry
     * An upsert can land on any existing record of its type.
     */
    internal fun upsertExternalId(changes: JSONObject?): String? {
        if (externalIdFieldName == null || changes == null) return null
        return when (val raw = changes.opt(externalIdFieldName)) {
            is String -> raw.takeIf { it.isNotBlank() && !isLocalId(it) }
            is Number -> canonicalNumber(raw)
            else -> null
        }
    }

    /** Message if the external id value of [changes] has an unsupported type (boolean, object, array...), else null */
    internal fun unsupportedExternalIdType(changes: JSONObject?): String? {
        if (externalIdFieldName == null || changes == null) return null
        return when (val raw = changes.opt(externalIdFieldName)) {
            null, JSONObject.NULL, is String -> null
            is Number -> if (canonicalNumber(raw) == null) {
                "External id $externalIdFieldName is a number whose precision cannot be preserved; store it as a string"
            } else null
            else -> "Entry has an unsupported external id value type for $externalIdFieldName: ${raw.javaClass.simpleName}"
        }
    }

    /**
     * Canonical string of a number external id, or null if it cannot be preserved exactly (persistence in
     * SmartStore reads decimals back as doubles): integers that fit a Long give plain digits; floating
     * values (and decimals that are not integral) are accepted only if their shortest round-trip decimal
     * form has at most [MAX_FLOATING_DIGITS] significant digits, written without exponent.
     */
    private fun canonicalNumber(number: Number): String? {
        val decimal: java.math.BigDecimal = when (number) {
            is Int, is Long, is Short, is Byte -> return number.toLong().toString()
            is java.math.BigInteger -> return if (number.bitLength() <= 63) number.toString() else null
            is java.math.BigDecimal -> {
                val stripped = number.stripTrailingZeros()
                if (stripped.scale() <= 0) {
                    return if (stripped.toBigInteger().bitLength() <= 63) stripped.toBigInteger().toString() else null
                }
                // Not integral: must be exactly what its double form says
                val d = number.toDouble()
                if (d.isNaN() || d.isInfinite() || java.math.BigDecimal(d.toString()).compareTo(number) != 0) return null
                number
            }
            is Float -> if (number.isNaN() || number.isInfinite()) return null else java.math.BigDecimal(number.toString())
            else -> {
                val d = number.toDouble()
                if (d.isNaN() || d.isInfinite()) return null
                java.math.BigDecimal(d.toString()) // shortest round-trip form of the double
            }
        }
        val stripped = decimal.stripTrailingZeros()
        if (stripped.precision() > MAX_FLOATING_DIGITS) return null
        return (if (stripped.scale() <= 0) stripped.setScale(0) else stripped).toPlainString()
    }

    private fun create(
        syncManager: SyncManager,
        sobjectType: String,
        changes: JSONObject?,
        fields: Map<String, Any>
    ): ReplayResult {
        // Same decision as createOnServer(syncManager, record, fieldlist): the check on local ids is
        // there for an external id field that is the id field and holds a local id
        val externalId = upsertExternalId(changes)
        val request = if (externalId != null) {
            // The server rejects an upsert whose body repeats the external id field (INVALID_FIELD):
            // the value travels in the path only. The queued changes and the record keep the field.
            val body = fields.filterKeys { it != externalIdFieldName }
            RestRequest.getRequestForUpsert(syncManager.apiVersion, sobjectType, externalIdFieldName, encodePathSegment(externalId), body)
        } else {
            // No upsert key: the external id field (null, blank, non-string...) is left out of the plain POST too
            RestRequest.getRequestForCreate(syncManager.apiVersion, sobjectType, fields.filterKeys { it != externalIdFieldName })
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
        return serverIdResult(response.asJSONObject().opt(Constants.LID), "creating")
    }

    /** Gets the id of the record with the given external id (`GET /sobjects/<type>/<field>/<value>?fields=Id`) */
    private fun lookupServerId(syncManager: SyncManager, sobjectType: String, externalId: String): ReplayResult {
        val request = lookupRequest(syncManager, sobjectType, externalId)
        val response = syncManager.sendSyncWithMobileSyncUserAgent(request)
        if (!response.isSuccess) {
            lastError = response.asString()
            return result(response.statusCode)
        }
        return serverIdResult(response.asJSONObject().opt(Constants.ID), "looking up")
    }

    private fun lookupRequest(syncManager: SyncManager, sobjectType: String, externalId: String) =
        RestRequest.getRequestForRetrieve(
            syncManager.apiVersion, sobjectType, "$externalIdFieldName/${encodePathSegment(externalId)}", listOf(Constants.ID)
        )

    /** Answer of [lookupDestination] */
    internal class Destination(val kind: DestinationKind, val id: String? = null, val error: String? = null)

    internal enum class DestinationKind {
        /** An existing record has this external id: [Destination.id] */
        FOUND,

        /** No record has it (404): an upsert would create one */
        NOT_FOUND,

        /** Record-level error other than 404 (300, 403...): the destination is unknown */
        INCONCLUSIVE,

        /** The request failed like any transport failure (5xx, 401, 429...), or a 2xx answer has no usable id */
        TRANSPORT_ERROR
    }

    /**
     * Finds which record an upsert of [externalId] would hit, with the same lookup as for a 204
     * answer, without writing anything.
     *
     * @throws IOException if the request cannot be sent
     */
    @Throws(IOException::class)
    internal fun lookupDestination(syncManager: SyncManager, sobjectType: String, externalId: String): Destination {
        val response = syncManager.sendSyncWithMobileSyncUserAgent(lookupRequest(syncManager, sobjectType, externalId))
        if (!response.isSuccess) {
            if (response.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return Destination(DestinationKind.NOT_FOUND)
            }
            lastError = response.asString()
            val failure = result(response.statusCode)
            return Destination(
                if (failure.outcome == Outcome.TRANSPORT_ERROR) DestinationKind.TRANSPORT_ERROR else DestinationKind.INCONCLUSIVE,
                error = failure.error
            )
        }
        val id = try {
            response.asJSONObject().opt(Constants.ID)
        } catch (e: JSONException) {
            null
        }
        return if (id is String && id.isNotBlank()) {
            Destination(DestinationKind.FOUND, id = id)
        } else {
            // A 2xx answer that does not tell the record is as unusable as a failed request
            Destination(DestinationKind.TRANSPORT_ERROR, error = "No valid id in the lookup answer")
        }
    }

    /** A success must come with a nonblank string id: anything else is treated like an unusable answer */
    private fun serverIdResult(id: Any?, action: String): ReplayResult =
        if (id is String && id.isNotBlank()) {
            ReplayResult(Outcome.SUCCESS, serverId = id)
        } else {
            ReplayResult(Outcome.TRANSPORT_ERROR, error = "No valid id returned when $action the record: $id")
        }

    private fun encodePathSegment(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** Classifies the status of a request, using the error body kept by the base class */
    private fun result(statusCode: Int): ReplayResult {
        if (RestResponse.isSuccess(statusCode)) {
            return ReplayResult(Outcome.SUCCESS)
        }
        val error = lastError
        // Only a clear rejection of this record lets the sync go on; auth, timeout, throttling and
        // server errors may hit every entry, so they stop the sync
        val transport = statusCode >= HttpURLConnection.HTTP_INTERNAL_ERROR ||
            statusCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
            statusCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT ||
            statusCode == HTTP_TOO_MANY_REQUESTS ||
            (statusCode == HttpURLConnection.HTTP_FORBIDDEN && error?.contains(REQUEST_LIMIT_EXCEEDED) == true)
        return ReplayResult(if (transport) Outcome.TRANSPORT_ERROR else Outcome.RECORD_ERROR, error = error ?: "HTTP $statusCode")
    }

    companion object {
        /** Most significant digits a floating-point external id may have to be preserved by the JSON round trip */
        private const val MAX_FLOATING_DIGITS = 15
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val REQUEST_LIMIT_EXCEEDED = "REQUEST_LIMIT_EXCEEDED"
    }
}
