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

import java.util.Locale

/**
 * Helper class to build a SOSL returning statement.
 *
 * @author bhariharan
 */
class SOSLReturningBuilder private constructor() {
    private val properties: HashMap<String, Any> = HashMap()

    /**
     * Adds the 'fields' clause.
     *
     * @param fields Fields clause.
     * @return Instance with 'fields' clause.
     */
    fun fields(fields: String): SOSLReturningBuilder {
        properties["fields"] = fields
        return this
    }

    /**
     * Adds the 'where' clause.
     *
     * @param where Where clause.
     * @return Instance with 'where' clause.
     */
    fun where(where: String): SOSLReturningBuilder {
        properties["where"] = where
        return this
    }

    /**
     * Adds the 'orderBy' clause.
     *
     * @param orderBy Order by clause.
     * @return Instance with 'orderBy' clause.
     */
    fun orderBy(orderBy: String): SOSLReturningBuilder {
        properties["orderBy"] = orderBy
        return this
    }

    /**
     * Adds the 'limit' clause.
     *
     * @param limit Limit clause.
     * @return Instance with 'limit' clause.
     */
    fun limit(limit: Int): SOSLReturningBuilder {
        properties["limit"] = limit
        return this
    }

    /**
     * Adds the 'withNetwork' clause.
     *
     * @param networkId Network ID.
     * @return Instance with 'withNetwork' clause.
     */
    fun withNetwork(networkId: String): SOSLReturningBuilder {
        properties["withNetwork"] = networkId
        return this
    }

    /**
     * Builds the query.
     *
     * @return Query.
     */
    fun build(): String? {
        val query = StringBuilder()
        val objectName = properties["objectName"] as String?
        if (objectName.isNullOrEmpty()) {
            return null
        }
        query.append(" ")
        query.append(objectName)
        val fields = properties["fields"] as String?
        if (!fields.isNullOrEmpty()) {
            query.append(String.format("(%s", fields))
            val where = properties["where"] as String?
            if (!where.isNullOrEmpty()) {
                query.append(" where ")
                query.append(where)
            }
            val orderBy = properties["orderBy"] as String?
            if (!orderBy.isNullOrEmpty()) {
                query.append(" order by ")
                query.append(orderBy)
            }
            val withNetwork = properties["withNetwork"] as String?
            if (!withNetwork.isNullOrEmpty()) {
                query.append(" with network = ")
                query.append(withNetwork)
            }
            val limit = properties["limit"] as Int?
            if (limit != null && limit > 0) {
                query.append(" limit ")
                query.append(String.format(Locale.US, "%d", limit))
            }
            query.append(")")
        }
        return query.toString()
    }

    private fun objectName(name: String): SOSLReturningBuilder {
        properties["objectName"] = name
        return this
    }

    companion object {
        /**
         * Returns an instance of this class based on the object name.
         *
         * @param name Object name.
         * @return Instance of this class.
         */
        @JvmStatic
        fun getInstanceWithObjectName(name: String): SOSLReturningBuilder {
            val instance = SOSLReturningBuilder()
            instance.objectName(name)
            instance.limit(0)
            return instance
        }
    }
}