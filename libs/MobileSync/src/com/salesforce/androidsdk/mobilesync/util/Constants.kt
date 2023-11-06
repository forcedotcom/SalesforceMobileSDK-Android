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

import com.salesforce.androidsdk.rest.RestRequest
import java.text.DateFormat

/**
 * This class contains commonly used constants, such as field names,
 * SObject types, attribute names, etc.
 *
 * @author bhariharan
 */
object Constants {
    const val EMPTY_STRING = ""
    const val NULL_STRING = "null"
    const val ID = "Id"
    const val NAME = "Name"
    const val LAST_NAME = "LastName"
    const val DESCRIPTION = "Description"
    const val TYPE = "Type"
    const val LTYPE = "type" // lower case type in attributes map
    const val ATTRIBUTES = "attributes"
    const val RECENTLY_VIEWED = "RecentlyViewed"
    const val RECORDS = "records"
    const val LID = "id" // lower case id in create response
    const val SOBJECT_TYPE = "attributes.type"
    const val NEXT_RECORDS_URL = "nextRecordsUrl"
    const val TOTAL_SIZE = "totalSize"
    const val RECENT_ITEMS = "recentItems"
    const val LAST_MODIFIED_DATE = "LastModifiedDate"
    const val CONTACTS = "Contacts"
    const val ACCOUNT_KEY_PREFIX = "001"

    /**
     * Salesforce object types.
     */
    const val ACCOUNT = "Account"
    const val CASE = "Case"
    const val OPPORTUNITY = "Opportunity"
    const val CONTACT = "Contact"

    /**
     * Salesforce object type field constants.
     */
    const val KEYPREFIX_FIELD = "keyPrefix"
    const val LABEL_FIELD = "label"

    /**
     * Salesforce object layout column field constants.
     */
    const val LAYOUT_TYPE_COMPACT = "Compact"
    const val FORM_FACTOR_MEDIUM = "Medium"
    const val MODE_EDIT = "Edit"

    /**
     * Salesforce timestamp format.
     */
    @JvmField
    val TIMESTAMP_FORMAT: DateFormat = RestRequest.ISO8601_DATE_FORMAT

    /**
     * Enum for available data fetch modes.
     *
     * CACHE_ONLY - Fetches data from the cache and returns null if no data is available.
     * CACHE_FIRST - Fetches data from the cache and falls back on the server if no data is available.
     * SERVER_FIRST - Fetches data from the server and falls back on the cache if the server doesn't
     * return data. The data fetched from the server is automatically cached.
     */
    enum class Mode {
        CACHE_ONLY, CACHE_FIRST, SERVER_FIRST
    }
}