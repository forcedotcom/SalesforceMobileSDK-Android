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

import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Options for sync up / down
 */
class SyncOptions
/**
 * Private constructor
 * @param fieldlist
 * @param mergeMode
 */ private constructor(val fieldlist: List<String>?, val mergeMode: MergeMode) {

    /**
     * @return json representation of target
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun asJSON(): JSONObject {
        return with(JSONObject()) {
            if (fieldlist != null) put(FIELDLIST, JSONArray(fieldlist))
            put(MERGEMODE, mergeMode.name)
        }
    }

    companion object {
        const val MERGEMODE = "mergeMode"

        // Fieldlist really belongs in sync up/down target - keeping it here for backwards compatibility
        const val FIELDLIST = "fieldlist"

        /**
         * Build SyncOptions from json
         * @param options as json
         * @return
         * @throws JSONException
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun fromJSON(options: JSONObject): SyncOptions {
            val mergeMode = MergeMode.valueOf(options.getString(MERGEMODE))
            val fieldlist = JSONObjectHelper.toList<String>(options.optJSONArray(FIELDLIST))
            return SyncOptions(fieldlist, mergeMode)
        }

        /**
         * @param fieldlist
         * @return
         */
        @JvmStatic
        fun optionsForSyncUp(fieldlist: List<String>): SyncOptions {
            return SyncOptions(fieldlist, MergeMode.OVERWRITE)
        }

        /**
         * @param fieldlist
         * @param mergeMode
         * @return
         */
        @JvmStatic
        fun optionsForSyncUp(fieldlist: List<String>, mergeMode: MergeMode): SyncOptions {
            return SyncOptions(fieldlist, mergeMode)
        }

        /**
         * @param mergeMode
         * @return
         */
        @JvmStatic
        fun optionsForSyncDown(mergeMode: MergeMode): SyncOptions {
            return SyncOptions(null, mergeMode)
        }
    }
}