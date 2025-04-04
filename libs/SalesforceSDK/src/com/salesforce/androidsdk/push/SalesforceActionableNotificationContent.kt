/*
 * Copyright (c) 2025-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.push

import com.salesforce.androidsdk.rest.SfapApiClient.Companion.jsonIgnoreUnknownKeys
import kotlinx.serialization.Serializable

/**
 * Models the content of a Salesforce actionable notification.
 * See https://salesforce.quip.com/rpfRAi91OHI2
 * TODO: Replace the documentation link with the final documentation. ECJ20250320
 */
@Serializable
data class SalesforceActionableNotificationContent(
    val sfdc: Sfdc?
) {

    /** The original JSON used to initialize this Salesforce actionable notification */
    var sourceJson: String? = null
        private set

    @Serializable
    data class Sfdc(
        val notifType: String? = null,
        val nid: String? = null,
        val oid: String? = null,
        val type: Int? = null,
        val alertTitle: String? = null,
        val sid: String? = null,
        val rid: String? = null,
        val targetPageRef: String? = null,
        val badge: Int? = null,
        val uid: String? = null,
        val act: Act? = null,
        val alertBody: String? = null,
        val alert: String? = null,
        val cid: String? = null,
        val timestamp: Int? = null
    ) {

        @Serializable
        data class Act(
            val group: String? = null,
            val type: String? = null,
            val description: String? = null,
            val properties: Properties? = null
        ) {

            @Serializable
            data class Properties(
                val group: Group? = null
            ) {

                @Serializable
                data class Group(
                    val type: String? = null,
                    val description: String? = null,
                    val notification: Boolean? = null
                )
            }
        }
    }

    // region Companion

    companion object {

        /**
         * Returns a Salesforce actionable notification from the JSON text.
         * @param json The JSON text
         * @return The Salesforce actionable notification
         */
        fun fromJson(json: String): SalesforceActionableNotificationContent {

            val result = jsonIgnoreUnknownKeys.decodeFromString<SalesforceActionableNotificationContent>(json)
            result.sourceJson = json
            return result
        }
    }

    // endregion
}
