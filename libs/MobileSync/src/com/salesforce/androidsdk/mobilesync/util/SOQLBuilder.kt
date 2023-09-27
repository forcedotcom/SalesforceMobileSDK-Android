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

import android.net.Uri
import android.text.TextUtils
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import java.util.Locale

/**
 * Helper class to build a SOQL statement.
 *
 * @author bhariharan
 */
class SOQLBuilder private constructor() {
    private val properties: HashMap<String, Any?> = HashMap()

    /**
     * Adds the 'from' clause.
     *
     * @param from From clause.
     * @return Instance with 'from' clause.
     */
    fun from(from: String?): SOQLBuilder {
        properties["from"] = from
        return this
    }

    /**
     * Adds the 'where' clause.
     *
     * @param where Where clause.
     * @return Instance with 'where' clause.
     */
    fun where(where: String?): SOQLBuilder {
        properties["where"] = where
        return this
    }

    /**
     * Adds the 'with' clause.
     *
     * @param with With clause.
     * @return Instance with 'with' clause.
     */
    fun with(with: String?): SOQLBuilder {
        properties["with"] = with
        return this
    }

    /**
     * Adds the 'groupBy' clause.
     *
     * @param groupBy Group by clause.
     * @return Instance with 'groupBy' clause.
     */
    fun groupBy(groupBy: String?): SOQLBuilder {
        properties["groupBy"] = groupBy
        return this
    }

    /**
     * Adds the 'having' clause.
     *
     * @param having Having clause.
     * @return Instance with 'having' clause.
     */
    fun having(having: String?): SOQLBuilder {
        properties["having"] = having
        return this
    }

    /**
     * Adds the 'orderBy' clause.
     *
     * @param orderBy Order by clause.
     * @return Instance with 'orderBy' clause.
     */
    fun orderBy(orderBy: String?): SOQLBuilder {
        properties["orderBy"] = orderBy
        return this
    }

    /**
     * Adds the 'limit' clause.
     *
     * @param limit Limit clause.
     * @return Instance with 'limit' clause.
     */
    fun limit(limit: Int?): SOQLBuilder {
        properties["limit"] = limit
        return this
    }

    /**
     * Adds the 'offset' clause.
     *
     * @param offset Offset clause.
     * @return Instance with 'offset' clause.
     */
    fun offset(offset: Int?): SOQLBuilder {
        properties["offset"] = offset
        return this
    }

    /**
     * Builds and encodes the query.
     *
     * @return URI encoded query.
     */
    fun buildAndEncode(): String {
        return Uri.encode(build())
    }

    /**
     * Builds and encodes with path.
     *
     * @param path Path.
     * @return URI encoded query with path.
     */
    fun buildAndEncodeWithPath(path: String?): String? {
        var result: String? = null
        if (path != null) {
            result = if (path.endsWith("/")) {
                Uri.encode(String.format("%squery/?q=%s", path, build()))
            } else {
                Uri.encode(String.format("%s/query/?q=%s", path, build()))
            }
        }
        return result
    }

    /**
     * Builds with path.
     *
     * @param path Path.
     * @return Query with path.
     */
    fun buildWithPath(path: String?): String? {
        var result: String? = null
        if (path != null) {
            result = if (path.endsWith("/")) {
                String.format("%squery/?q=%s", path, build())
            } else {
                String.format("%s/query/?q=%s", path, build())
            }
        }
        return result
    }

    /**
     * Builds the query.
     *
     * @return Query.
     */
    fun build(): String {
        val query = StringBuilder()
        val fieldList = properties["fields"] as? String
        if (fieldList.isNullOrEmpty()) {
            throw SyncManager.MobileSyncException("No field selected")
        }
        query.append("select ")
        query.append(fieldList)
        val from = properties["from"] as String?
        if (from.isNullOrEmpty()) {
            throw SyncManager.MobileSyncException("No table specified")
        }
        query.append(" from ")
        query.append(from)
        val where = properties["where"] as String?
        if (!where.isNullOrEmpty()) {
            query.append(" where ")
            query.append(where)
        }
        val groupBy = properties["groupBy"] as String?
        if (!groupBy.isNullOrEmpty()) {
            query.append(" group by ")
            query.append(groupBy)
        }
        val having = properties["having"] as String?
        if (!having.isNullOrEmpty()) {
            query.append(" having ")
            query.append(having)
        }
        val orderBy = properties["orderBy"] as String?
        if (!orderBy.isNullOrEmpty()) {
            query.append(" order by ")
            query.append(orderBy)
        }
        val limit = properties["limit"] as Int?
        if (limit != null && limit > 0) {
            query.append(" limit ")
            query.append(String.format(Locale.US, "%d", limit))
        }
        val offset = properties["offset"] as Int?
        if (offset != null && offset > 0) {
            query.append(" offset ")
            query.append(String.format(Locale.US, "%d", offset))
        }
        return query.toString()
    }

    private fun fields(fields: String?): SOQLBuilder {
        properties["fields"] = fields
        return this
    }

    companion object {
        /**
         * Returns an instance of this class based on the fields passed as a comma separated string.
         *
         * @param fields Fields.
         * @return Instance of this class.
         */
        fun getInstanceWithFields(fields: String?): SOQLBuilder {
            val instance = SOQLBuilder()
            instance.fields(fields)
            instance.limit(0)
            instance.offset(0)
            return instance
        }

        /**
         * Returns an instance of this class based on the fields passed as an array of strings.
         *
         * @param fields Fields.
         * @return Instance of this class.
         */
        fun getInstanceWithFields(vararg fields: String?): SOQLBuilder {
            return getInstanceWithFields(TextUtils.join(", ", fields))
        }

        /**
         * Returns an instance of this class based on the fields passed as a list of strings.
         *
         * @param fields Fields.
         * @return Instance of this class.
         */
        fun getInstanceWithFields(fields: List<String>): SOQLBuilder {
            return getInstanceWithFields(TextUtils.join(", ", fields))
        }
    }
}