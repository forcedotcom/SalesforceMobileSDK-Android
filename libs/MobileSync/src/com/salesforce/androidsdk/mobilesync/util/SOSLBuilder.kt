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

/**
 * Helper class to build a SOSL statement.
 *
 * @author bhariharan
 */
class SOSLBuilder private constructor() {
    private val properties: HashMap<String, Any> = HashMap()
    private val returning: MutableList<Any>

    /**
     * Private constructor.
     */
    init {
        returning = ArrayList()
    }

    /**
     * Adds the 'searchGroup' clause.
     *
     * @param searchGroup Search group clause.
     * @return Instance with 'searchGroup' clause.
     */
    fun searchGroup(searchGroup: String): SOSLBuilder {
        properties["searchGroup"] = searchGroup
        return this
    }

    /**
     * Adds the 'returningSpec' clause.
     *
     * @param returningSpec Returning spec clause.
     * @return Instance with 'returningSpec' clause.
     */
    fun returning(returningSpec: SOSLReturningBuilder): SOSLBuilder {
        returning.add(returningSpec)
        return this
    }

    /**
     * Adds the 'divisionFilter' clause.
     *
     * @param divisionFilter Division filter clause.
     * @return Instance with 'divisionFilter' clause.
     */
    fun divisionFilter(divisionFilter: String): SOSLBuilder {
        properties["divisionFilter"] = divisionFilter
        return this
    }

    /**
     * Adds the 'dataCategory' clause.
     *
     * @param dataCategory Data category clause.
     * @return Instance with 'dataCategory' clause.
     */
    fun dataCategory(dataCategory: String): SOSLBuilder {
        properties["dataCategory"] = dataCategory
        return this
    }

    /**
     * Adds the 'limit' clause.
     *
     * @param limit Limit clause.
     * @return Instance with 'limit' clause.
     */
    fun limit(limit: Int): SOSLBuilder {
        properties["limit"] = limit
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
                Uri.encode(String.format("%ssearch/?q=%s", path, build()))
            } else {
                Uri.encode(String.format("%s/search/?q=%s", path, build()))
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
                String.format("%ssearch/?q=%s", path, build())
            } else {
                String.format("%s/search/?q=%s", path, build())
            }
        }
        return result
    }

    /**
     * Builds the query.
     *
     * @return Query.
     */
    fun build(): String? {
        val query = StringBuilder()
        val searchTerm = properties["searchTerm"] as String?
        if (searchTerm.isNullOrEmpty()) {
            return null
        }
        query.append(String.format("find {%s}", searchTerm))
        val searchGroup = properties["searchGroup"] as String?
        if (!searchGroup.isNullOrEmpty()) {
            query.append(" in ")
            query.append(searchGroup)
        }
        if (returning.size > 0) {
            query.append(" returning ")
            query.append((returning[0] as SOSLReturningBuilder).build())
            for (i in 1 until returning.size) {
                query.append(", ")
                query.append((returning[i] as SOSLReturningBuilder).build())
            }
        }
        val divisionFilter = properties["divisionFilter"] as String?
        if (!divisionFilter.isNullOrEmpty()) {
            query.append(" with ")
            query.append(divisionFilter)
        }
        val dataCategory = properties["dataCategory"] as String?
        if (!dataCategory.isNullOrEmpty()) {
            query.append(" with data category ")
            query.append(dataCategory)
        }
        val limit = properties["limit"] as Int?
        if (limit != null && limit > 0) {
            query.append(" limit ")
            query.append(String.format("%d", limit))
        }
        return query.toString()
    }

    private fun searchTerm(searchTerm: String?): SOSLBuilder {
        var searchValue = searchTerm ?: ""

        // Escapes special characters from search term.
        if (searchValue != "") {
            searchValue = searchValue.replace("\\", "\\\\")
            searchValue = searchValue.replace("+", "\\+")
            searchValue = searchValue.replace("^", "\\^")
            searchValue = searchValue.replace("~", "\\~")
            searchValue = searchValue.replace("'", "\\'")
            searchValue = searchValue.replace("-", "\\-")
            searchValue = searchValue.replace("[", "\\[")
            searchValue = searchValue.replace("]", "\\]")
            searchValue = searchValue.replace("{", "\\{")
            searchValue = searchValue.replace("}", "\\}")
            searchValue = searchValue.replace("(", "\\(")
            searchValue = searchValue.replace(")", "\\)")
            searchValue = searchValue.replace("&", "\\&")
            searchValue = searchValue.replace(":", "\\:")
            searchValue = searchValue.replace("!", "\\!")
        }
        properties["searchTerm"] = searchValue
        return this
    }

    companion object {
        /**
         * Returns an instance of this class based on the search term passed in.
         *
         * @param searchTerm Search term.
         * @return Instance of this class.
         */
        @JvmStatic
        fun getInstanceWithSearchTerm(searchTerm: String?): SOSLBuilder {
            val instance = SOSLBuilder()
            instance.searchTerm(searchTerm)
            instance.limit(0)
            return instance
        }
    }
}